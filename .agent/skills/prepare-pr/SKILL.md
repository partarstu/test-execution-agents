---
name: prepare-pr
description: Prepares code for a pull request by running Maven build, tests (JUnit 5), license checks, and dependency analysis. Use when ready to create a PR or before committing changes.
---

# Prepare Pull Request

This skill provides a comprehensive workflow to prepare your Java code for a pull request. It runs all CI checks locally, fixes any issues,
and creates a well-documented pull request.

## ⚠️ User Intervention Policy

Proceed **autonomously** when steps complete successfully. **STOP and ASK the user** only for: blockers/unfixable errors, ambiguous
decisions, or approval before committing. Do NOT ask for confirmation on routine passing steps.

## Overview

1. Run Maven build and verify compilation
2. Run unit tests (JUnit 5) and fix failures
3. Run license and dependency checks
4. Analyze changes and update documentation
5. Review changes with user and commit
6. Create temp branch with squashed changes and open PR

## Prerequisites

- `Java 25` (Temurin recommended) | `Maven 3.9+` | `gh` (GitHub CLI)

## Step-by-Step Instructions

### Step 1: Run Maven Build

```powershell
mvn clean install -DskipTests      # Initial build
mvn clean install -DskipTests -e   # If build fails, run with full output
```

If compilation errors occur, **STOP**, present errors to user, apply fixes per user guidance, and re-run until build succeeds.

### Step 2: Run Unit Tests

```powershell
mvn test                    # Run all tests with coverage
mvn test -pl <module_name>  # Test specific module (e.g., agent_core, ui_test_execution_agent)
```

If tests fail, **STOP** and present failure details. Analyze causes (code bug vs. outdated test vs. missing dependencies), **ASK** user
which approach to take, apply fixes, and re-run until all tests pass.

### Step 3: Run License and Dependency Checks

```powershell
mvn license:check           # Verify license headers
mvn license:format          # Auto-fix missing headers (if issues found)
mvn dependency:analyze      # Check for unused/undeclared dependencies
mvn dependency:tree         # View full tree for conflict analysis
```

For dependency issues (unused, undeclared, conflicts), **STOP**, present findings, and **ASK** user how to proceed.

### Step 4: Analyze Changes and Update Documentation

#### 4.1: Get Diff and Categorize Changes

```powershell
git fetch origin main
git diff origin/main...HEAD
```

Review the diff and categorize changes according to the structure in `resources/pr_body_template.md` (Features, Bug Fixes, Refactoring,
Tests, Documentation, Dependencies, Configuration). This analysis will be used for README updates and PR description.

#### 4.2: Update README Documentation

Review and update `README.md` to reflect current code state:

- Update feature descriptions, usage examples, configuration sections as needed
- Add new sections for significant functionality, remove outdated information
- Only **STOP** for complex documentation decisions requiring user input

#### 4.3: Update Relevant Skills

Check if changes affect any skills in `.agent/skills/`:

```powershell
Get-ChildItem -Path ".agent/skills" -Directory | Select-Object Name
```

Update SKILL.md, resources, scripts, or examples if workflow steps, templates, or patterns changed. Explicitly note if no skill updates are
needed.

### Step 5: Review Changes and Commit

```powershell
git status          # Summary of changed files
git diff            # Detailed diff
git diff --cached   # Diff of staged files
```

Present summary of all changes made, then proceed to commit:

```powershell
git add -A
git commit -m "<type>: <description>"
git push -u origin HEAD
```

**Commit/PR Naming Conventions** (conventional commit style):
| Prefix | Use Case |
|--------|----------|
| `feat:` | New features |
| `fix:` | Bug fixes |
| `refactor:` | Code improvements |
| `chore:` | Maintenance, license fixes, dependency updates |
| `docs:` | Documentation updates |

### Step 6: Create Temp Branch and Pull Request

#### 6.1: Create Clean Temp Branch

```powershell
$currentBranch = git branch --show-current
git fetch origin main
git checkout -b temp origin/main
git merge --squash $currentBranch
git commit -m "PR preparation"
```

#### 6.2: Push and Clean Up

```powershell
git push -u origin temp
git branch -D $currentBranch
git push origin --delete $currentBranch 2>$null
```

#### 6.3: Create Pull Request

```powershell
gh pr create --title "<type>: <short summary>" --body "<description>"
```

Use the change analysis from Step 4 and the template structure from `resources/pr_body_template.md` for the PR body.

## Troubleshooting

| Issue                | Solution                                                                                                        |
|----------------------|-----------------------------------------------------------------------------------------------------------------|
| Compilation errors   | Check missing imports, incompatible types; use `-P windows` or `-P linux` for OS-specific builds                |
| Test failures        | Verify mock initialization (`MockitoAnnotations.openMocks(this)`), check test resources in `src/test/resources` |
| License issues       | Run `mvn license:format`; check template config in pom.xml                                                      |
| Dependency conflicts | Use `mvn dependency:tree` to identify conflicts; add explicit declarations or exclusions                        |
| PR creation fails    | Run `gh auth login`; ensure branch is pushed first; rebase if behind main                                       |

## Pre-Flight Checklist

Before creating the PR:

- [ ] Build succeeds (`mvn clean install -DskipTests`)
- [ ] All tests pass (`mvn test`)
- [ ] License and dependency checks pass
- [ ] README.md and skills are up-to-date
- [ ] Changes squashed into temp branch, original branch deleted
- [ ] PR has descriptive title and comprehensive description
