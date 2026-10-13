# Holo grounding service

Serves a Holo3.1 GUI grounding model behind an OpenAI-compatible API on Cloud Run, for the UI agent's
`element.bounding.box.agent` when `element.bounding.box.agent.model.provider=hcompany`.

Every command below takes the project from `PROJECT_ID`, so set it to your Google Cloud project first:

```bash
export PROJECT_ID=<your project>
```

## Build

```bash
gcloud builds submit --config cloudbuild.yaml --project "$PROJECT_ID" .
```

Downloads the checkpoint from Hugging Face, mirrors it to `gs://${PROJECT_ID}-holo-weights` as the source of truth,
and bakes it into the image. Weights are baked in rather than mounted because reading them through a Cloud Storage FUSE
mount cost 464-560 s of cold start.

Override `_MODEL_REPO`, `_MODEL_DIR` and `_IMAGE` to build a different checkpoint.

## Deploy

```bash
envsubst '${PROJECT_ID}' < service-35b.yaml > /tmp/holo-grounding-35b.yaml
gcloud run services replace /tmp/holo-grounding-35b.yaml --region europe-west4 --project "$PROJECT_ID"
gcloud run services add-iam-policy-binding holo-grounding-35b --region europe-west4 --project "$PROJECT_ID" \
    --member="serviceAccount:holo-grounding-invoker@${PROJECT_ID}.iam.gserviceaccount.com" \
    --role=roles/run.invoker
```

`replace` rather than `deploy`: the service needs a startup probe longer than Cloud Run's default 4 minutes, which a
24 GB checkpoint does not load within.

`replace` takes the YAML as it is, so `envsubst` fills the project into the image reference first.

The agent reads the service URL from `HCOMPANY_ENDPOINT` (`https://<service-url>/v1/`), which the VM deployment takes from the Secret Manager secret of the same name.

The service requires authentication. The agent mints an identity token for it through application default credentials
(`CloudRunCredentials`), so no key ends up in configuration - run
`gcloud auth application-default login --impersonate-service-account="holo-grounding-invoker@${PROJECT_ID}.iam.gserviceaccount.com"`
once on each machine that needs to reach it.

## Known-good serving configuration

The configuration below is the one the benchmark in `Holo31GroundingComparisonTest` - nine cases run at seven screenshot
size budgets - was measured against and is the baseline to fall back to. Anything added on top of it is provisional
until that benchmark has been re-run:

```yaml
image: europe-west4-docker.pkg.dev/${PROJECT_ID}/holo/holo-35b-nvfp4:vllm-8efa13b
args:
  - --model
  - /model
  - --served-model-name
  - Hcompany/Holo-3.1-35B-A3B-NVFP4
  - --port
  - '8000'
  - --max-model-len
  - '16384'
  - --gpu-memory-utilization
  - '0.90'
  - --enable-auto-tool-choice
  - --tool-call-parser
  - qwen3_coder
```

`--max-model-len` bounds how large a screenshot may reach the model: its processor merges 16-pixel patches in 2x2
groups, so one visual token covers 32x32 pixels and a 3840x2160 screenshot alone costs about 8100 of the 16384 tokens.
That is why the benchmark's largest budget, `bbox.screenshot.max.size.megapixels=8.39`, is the one that still leaves a
4K screenshot untouched.

The live service has since moved to `--max-model-len 32768` and `containerConcurrency: 2`, because it now serves the
sub-agents as well as grounding. Neither number is about the screenshot: vLLM rejects a request on its prompt plus its
`max_tokens` rather than on what it generates, so an agent's ~11k prompt against the configured 8192 output cap
oversubscribes 16384 before a token is produced, and four concurrent 32768-token conversations want more KV cache than
one GPU has to spare. The loop itself fits either way - one screenshot is sent once, tool results are short, and the
chat template drops past reasoning between turns, so six round trips add barely a thousand tokens. Both settings are
provisional in the sense above: `Holo31GroundingComparisonTest` has not been re-run against them.

`holo-grounding-35b-00004-85t` is the last revision that served exactly those args. To roll back, either restore them
and `gcloud run services replace` again, or - faster, because it skips the model load - send traffic straight back to
that revision:

```bash
gcloud run services update-traffic holo-grounding-35b --region europe-west4 --project "$PROJECT_ID" \
    --to-revisions holo-grounding-35b-00004-85t=100
```

`gcloud run revisions list --service holo-grounding-35b --region europe-west4 --project "$PROJECT_ID"` lists what
is still available, since Cloud Run eventually garbage-collects inactive revisions.

## The vLLM pin is deliberate

The image is pinned to the nightly `8efa13b700f1836657699cae2503dc2feab27fa0`, never to `stable` or a floating tag. A
stable vLLM release on Blackwell (SM120) returns confident, well-formed, **wrong** coordinates - it does not error and
its output passes every schema check. Re-run the benchmark in `Holo31GroundingComparisonTest` against any new
image, GPU or serving-stack version before trusting it. See `findings_holo.md`.
