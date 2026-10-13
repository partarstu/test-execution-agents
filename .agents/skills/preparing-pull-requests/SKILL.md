---
name: preparing-pull-requests
description: Prepares the current branch of the Test Execution Agents repository for a pull request by running the CI checks locally (Maven build and unit tests, license headers, dependency analysis, CALM validation), checking compliance with JAVA_GUIDELINES.md, documentation and smoke-suite coverage, then committing, pushing and opening the PR once the user approves. Use when the user wants to open a PR or get a branch ready for review.
---

# Preparing Pull Requests

The checks mirror `.github/workflows/package.yml` and `.github/workflows/architecture.yml`; the commands are in *Commands* of `AGENTS.md`. Commits, pushes and pull requests happen only after the user approves them in step 8.

Copy this checklist and track progress:

```
- [ ] 1. Determine the scope
- [ ] 2. Build and unit tests
- [ ] 3. Java guidelines
- [ ] 4. License headers
- [ ] 5. Dependencies
- [ ] 6. CALM validation
- [ ] 7. Documentation, skills and smoke coverage
- [ ] 8. Review with the user and get approval
- [ ] 9. Commit, push and open the PR
```

## 1. Determine the scope

If the current branch is `main`, stop and ask the user to create a feature branch.

```bash
git fetch origin main
git diff --name-only $(git merge-base origin/main HEAD)
git ls-files --others --exclude-standard
```

The union of both lists (committed, uncommitted and untracked changes) is the scope of every following step. Ask the user about untracked files that look unrelated to the change.

## 2. Build and unit tests

Build all modules with their unit tests. If compilation fails, present the errors to the user with a proposed fix. If tests fail, fix them with the `running-unit-tests` skill before continuing.

## 3. Java guidelines

The compiler enforces only part of `JAVA_GUIDELINES.md`. Read the changed Java code, tests included, against the whole document and fix violations in the lines this change touches. Report the ones whose fix would change behaviour to the user instead of fixing them. Check that the build output shows no new warnings for changed files (§ 16). Compact every Javadoc and comment in those lines that violates *Comments and docstrings* in `AGENTS.md`.

## 4. License headers

Check the license headers. If headers are missing in files in scope, add them and include only the files in scope in the change; revert header changes to untouched files. Ask the user about generated or third-party code.

## 5. Dependencies

Only when a `pom.xml` is in scope:

```bash
mvn dependency:tree -pl <changed module>
mvn dependency:analyze -pl <changed module> -am
```

Check that new versions are managed in the parent `pom.xml`, that no used dependency is only transitive, and that new dependencies meet `JAVA_GUIDELINES.md` § 15. Report version conflicts and known vulnerabilities to the user with a proposed upgrade.

## 6. CALM validation

Validate the CALM files. Then stop and tell the user when the branch contains an architecture change (*Architecture first (CALM)* in `AGENTS.md`) without updating both CALM files, or without the approval line in its plan: a CALM update bolted on now does not count.

## 7. Documentation, skills and smoke coverage

Compare the scope from step 1 with:

- **`README.md` and the module READMEs**: sections describing changed features, configuration properties, environment variables, build, deployment or smoke tests. Remove statements that are no longer true.
- **Versions**: every bump *Versioning* in `AGENTS.md` requires for the branch. Add a missing bump and list every bump in the PR description.
- **`JAVA_GUIDELINES.md` and `AGENTS.md`**: rules the change made inaccurate, e.g. a raised Java version.
- **Skills in `.agents/skills/`**: any instruction, template or referenced class or path the change made inaccurate.
- **Smoke suite**: the coverage *Smoke suite* in `AGENTS.md` requires for the branch. If it is missing, stop and tell the user.

## 8. Review with the user and get approval

Present:

- the result of every check (pass, fixed, or open issue)
- the files this skill changed (guideline fixes, headers, documentation)
- the proposed commit message, PR title and PR description

Ask for explicit approval to commit, push and create the PR, then wait. Apply requested changes and re-run the affected checks.

## 9. Commit, push and open the PR

Only after approval:

```bash
git add <files in scope>
git commit -m "<type>: <summary>"
git push -u origin HEAD
gh pr create --base main --title "<type>: <summary>" --body-file <path to PR description file>
```

- Stage files by name; `git add -A` also picks up unrelated local files.
- Write the description from [resources/pr_body_template.md](resources/pr_body_template.md) into a temporary file and pass it with `--body-file`; multi-line `--body` arguments break in some shells.
- Report the PR URL to the user.
