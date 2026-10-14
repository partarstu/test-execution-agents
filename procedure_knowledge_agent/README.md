# Procedure Knowledge Agent

## Overview

The procedure knowledge agent selects, for each precondition and test step of a designed test case, up to three recorded procedures of the shared Neo4j knowledge graph (the procedures the UI test execution agents recorded) that really match it, and up to three for each run of consecutive items that one recorded workflow covers. It returns them in their original form, as trees down to the atomic procedures, so that the agents that use them (QuAIA's knowledge-aware test case generator and its reviewer) assess them independently. It only reads the graph.

QuAIA's orchestrator discovers the agent like every execution agent and delegates the requests of the test case design agent to it; the agent never talks to the design agent directly.

## How a request is served

1. **Request validation** (`KnowledgeRequestReader`): the message must carry exactly one A2A data part with the test case; its name, preconditions, step actions and test data entries must not be blank, every text is capped in length, and the number of preconditions and steps together is capped. An invalid request fails the task with the reason.
2. **Item runs** (`KnowledgeSelectionService`, `ProcedureSelector.selectForItem`): one LLM run per precondition and test step, all in parallel on virtual threads. The item runs of all tasks share `knowledge.item.concurrency` slots, so that several test cases at once stay within the model provider's rate limits. Each run explores the graph with its tools and returns its selection through `returnItemSelection`; a run that fails selects nothing.
3. **Aggregation run** (`ProcedureSelector.selectAggregations`): one LLM run that starts from the item selections and selects procedures for runs of consecutive items of one kind through `returnAggregationSelections`.
4. **Trees** (`ProcedureTreeAssembler`): every selected ID is replaced by its original tree, capped by `knowledge.max.depth` and `knowledge.tree.max.nodes`; a procedure no longer in the graph is left out.
5. **Result** (`KnowledgeAgentExecutor`): the task's token usage is reported as the `token_usage` artifact, then the selection as the `procedure_selection` artifact with one data part, and the task completes. A run started after the task used up `agent.token.budget`, item and aggregation runs that together take longer than `agent.execution.time.budget.seconds`, or any other error fail the task with a reason; internal details stay in the logs.

The usage and the token budget of every run are accounted to its task (`BudgetManager.callInTask`), so tasks running at the same time keep separate accounts. `tasks/cancel` interrupts the task's runs and emits no further event for it.

### Tools

| Tool | Returns | Used by |
|------|---------|---------|
| `searchProcedures(query)` | Up to `knowledge.search.top.n` hits above `knowledge.search.min.score`, each with its own data only | Item and aggregation runs |
| `getProcedureTree(id)` | The whole tree of the procedure down to the atomic procedures | Item and aggregation runs |
| `getContainingProcedures(id)` | The procedures one level up that contain it | Item and aggregation runs |
| `findCommonContainingProcedures(ids)` | The procedures that contain all the given procedures at any depth | Aggregation run |

Each run may call the exploration tools `knowledge.exploration.max.tool.calls` times; a selection naming an ID no tool of the run returned, an unknown item or a run that is not consecutive and of one kind is returned to the model to correct, up to `knowledge.selection.max.retries` times.

## Request and response

The request is one data part (snake_case JSON); the item IDs follow from the positions: `P1`, `P2`, … for the preconditions and `S1`, `S2`, … for the steps.

```json
{
  "name": "Order a lamp",
  "preconditions": ["The user is logged in"],
  "steps": [
    {"action": "Open the catalog", "test_data": [], "expected_results": "The catalog is shown"},
    {"action": "Add the lamp to the cart", "test_data": ["product=Lamp"], "expected_results": "The cart holds the lamp"}
  ]
}
```

The `procedure_selection` artifact holds one data part with an entry per item, in item order, and one per aggregation; a list is empty where nothing matches:

```json
{
  "items": [{"item_id": "S1", "procedures": [{"id": "…", "description": "Open the product catalog", "atomic": false, "children": [ … ]}]}],
  "aggregations": [{"item_ids": ["S1", "S2"], "procedures": [ … ]}]
}
```

A tree node carries `id`, `description`, `atomic`, `is_precondition`, `optional`, `test_data` (the labels), `expected_results`, `prerequisites`, `effects`, `negated_effects`, `additional_info`, `success_count`, `ui_element` for an atomic node, `children` in their recorded order and `children_truncated` when a cap cut them off; fields without a value are left out.

## Agent card

The card describes the capability the orchestrator routes by ("Provides the recorded procedures (knowledge items) that best match each precondition and test step of a test case, and those that combine consecutive ones into one workflow.") in its description and its single skill, takes and returns `application/json`, and declares the non-required extension `urn:quaia:extensions:task-capacity:v1` with the parameter `capacity` (`knowledge.task.capacity`): the number of tasks the orchestrator may send at the same time.

## Configuration

The shared settings (`port`, `agent.auth.token`, `external.url`, `vector.db.url`, `vector.db.key`, budgets, model providers) are those of `AgentConfig` in the [main README](../README.md#core-configuration). This agent's defaults are in `src/main/resources/config.properties`:

| Property | Environment variable | Default | Description |
|----------|----------------------|---------|-------------|
| `port` | `PORT` | `8007` | Server port |
| `agent.version` | `AGENT_VERSION` | `1.0.0` | Version advertised in the agent card |
| `agent.token.budget` | `AGENT_TOKEN_BUDGET` | `5000000` | Tokens one task may use across all its runs |
| `agent.execution.time.budget.seconds` | `AGENT_EXECUTION_TIME_BUDGET_SECONDS` | `840` | Time the item and aggregation runs of a task may take together; below the design agent's request timeout (900 s) |
| `neo4j.username` | `NEO4J_USERNAME` | `neo4j` | Graph user |
| `neo4j.database` | `NEO4J_DATABASE` | `neo4j` | Graph database |
| `neo4j.max.connection.pool.size` | `NEO4J_MAX_CONNECTION_POOL_SIZE` | `20` | Driver connection pool size |
| `neo4j.connection.acquisition.timeout.seconds` | `NEO4J_CONNECTION_ACQUISITION_TIMEOUT_SECONDS` | `30` | Wait for a pooled connection |
| `neo4j.connection.timeout.seconds` | `NEO4J_CONNECTION_TIMEOUT_SECONDS` | `10` | Connection timeout |
| `neo4j.max.transaction.retry.time.seconds` | `NEO4J_MAX_TRANSACTION_RETRY_TIME_SECONDS` | `120` | Retry time of a transaction |
| `knowledge.embedding.model` | `KNOWLEDGE_EMBEDDING_MODEL` | `bge-small-en-v15` | Must equal the UI agents' model, otherwise the search compares incompatible vectors |
| `knowledge.embedding.batch.size` | `KNOWLEDGE_EMBEDDING_BATCH_SIZE` | `10` | Embedding batch size |
| `knowledge.search.min.score` | `KNOWLEDGE_SEARCH_MIN_SCORE` | `0.80` | Search floor; below the UI agents' execution floor (0.88) because designed wording differs from recorded wording |
| `knowledge.search.top.n` | `KNOWLEDGE_SEARCH_TOP_N` | `10` | Hits per search |
| `knowledge.selection.max.per.item` | `KNOWLEDGE_SELECTION_MAX_PER_ITEM` | `3` | Procedures selected per item or aggregation |
| `knowledge.selection.max.retries` | `KNOWLEDGE_SELECTION_MAX_RETRIES` | `3` | Corrections of a rejected selection per run |
| `knowledge.exploration.max.tool.calls` | `KNOWLEDGE_EXPLORATION_MAX_TOOL_CALLS` | `40` | Exploration tool calls per run |
| `knowledge.max.depth` | `KNOWLEDGE_MAX_DEPTH` | `5` | Depth cap of a tree |
| `knowledge.tree.max.nodes` | `KNOWLEDGE_TREE_MAX_NODES` | `200` | Size cap of a tree |
| `knowledge.request.max.items` | `KNOWLEDGE_REQUEST_MAX_ITEMS` | `100` | Preconditions and steps a request may carry together |
| `knowledge.request.max.text.length` | `KNOWLEDGE_REQUEST_MAX_TEXT_LENGTH` | `4000` | Characters of any single text of a request |
| `knowledge.item.concurrency` | `KNOWLEDGE_ITEM_CONCURRENCY` | `8` | Item runs at the same time, across all tasks |
| `knowledge.task.capacity` | `KNOWLEDGE_TASK_CAPACITY` | `4` | Tasks at the same time, declared in the agent card |
| `procedure.item.selection.agent.model.name` / `.model.provider` / `.prompt.version` | `PROCEDURE_ITEM_SELECTION_AGENT_MODEL_NAME` / `_MODEL_PROVIDER` / `_PROMPT_VERSION` | `gemini-3.5-flash` / `google` / `v1.0.0` | Model and prompt of the item runs |
| `procedure.aggregation.selection.agent.model.name` / `.model.provider` / `.prompt.version` | `PROCEDURE_AGGREGATION_SELECTION_AGENT_MODEL_NAME` / `_MODEL_PROVIDER` / `_PROMPT_VERSION` | `gemini-3.5-flash` / `google` / `v1.0.0` | Model and prompt of the aggregation run |

The agent refuses to start when the graph's schema version differs from the one the shared code reads; the UI agent runs the schema migrations.

## Smoke Tests

`org.tarik.ta.smoke.KnowledgeAgentA2aSmokeTest` starts the real `AbstractServer` with the real executor, selection service, selector, tools and tree assembler; only the LLM (agent-core's `ScriptedChatModel`) and the Neo4j repository and embedding service are mocked. It asserts the item and aggregation selections as original trees, the task's `token_usage` artifact, and that a request without the bearer token gets `401`.

```bash
# Run from the reactor root so agent_core is compiled fresh.
mvn -B test -pl procedure_knowledge_agent -am -Dtest.excluded.groups= -Dgroups=smoke -Djacoco.skip=true -Dsurefire.failIfNoSpecifiedTests=false
```

## Deployment

The agent runs on Cloud Run with internal ingress, so only callers inside the VPC (QuAIA's orchestrator) reach it, and the bearer token (`AGENT_AUTH_TOKEN`) guards its endpoint. Direct VPC egress (`--vpc-egress=private-ranges-only`) routes the traffic to the Neo4j VM's internal IP through the VPC while the model provider stays reachable over the internet. The Cloud Run concurrency equals the task capacity, and the request timeout (900 s) is above the execution time budget.

```bash
# Standalone deployment; the Bolt URL is the Neo4j VM's internal IP printed by the Neo4j deployment
gcloud builds submit --config=procedure_knowledge_agent/deployment/cloud/cloudbuild.yaml --substitutions=_VECTOR_DB_URL=bolt://10.128.0.5:7687

# Or via the parent project
gcloud builds submit --config=cloudbuild.yaml --substitutions=_DEPLOY_TARGET=knowledge,_KNOWLEDGE_AGENT_VECTOR_DB_URL=bolt://10.128.0.5:7687
```

It needs the secrets `GOOGLE_API_KEY`, `AGENT_AUTH_TOKEN`, `NEO4J_USERNAME` and `VECTOR_DB_KEY` (the last two created by the Neo4j deployment). After the first deployment, set `_EXTERNAL_URL` to the service URL so that the agent card advertises it, and add that URL to the orchestrator's `REMOTE_EXECUTION_AGENT_HOSTS`.

### Local development

```bash
mvn clean package -pl procedure_knowledge_agent -am -DskipTests
VECTOR_DB_URL=bolt://localhost:7687 VECTOR_DB_KEY=<password> GOOGLE_API_KEY=<key> java -jar procedure_knowledge_agent/target/procedure-knowledge-agent-3.1.0-SNAPSHOT.jar
```
