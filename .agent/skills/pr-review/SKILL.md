---
name: pr-review
description: Reviews an open GitHub PR for the current branch, applying project-specific and Java best practice criteria, and adds inline comments directly to the PR.
---

# PR Review Skill

This skill provides a comprehensive workflow to review pull requests on GitHub. It finds the open PR for the current branch, analyzes
code changes against project-specific guidelines and industry best practices, and adds inline review comments directly to the PR.

## ⚠️ User Intervention Policy

Proceed **autonomously** when steps complete successfully. **STOP and ASK the user** only for: no PR found, ambiguous review decisions,
or critical issues requiring discussion. Do NOT ask for confirmation on routine review steps.

## Overview

1. Find the open PR for the current branch
2. Fetch the PR diff and file changes
3. Analyze changes against review criteria
4. Add inline comments to specific code lines in GitHub
5. Submit the review with a summary

## Prerequisites

- `gh` (GitHub CLI) authenticated with `gh auth login`
- Current branch must have an open PR

## Step-by-Step Instructions

### Step 1: Find the Open PR for Current Branch

```powershell
$currentBranch = git branch --show-current
gh pr list --head $currentBranch --json number,title,url,headRefName --state open
```

If no PR is found, **STOP** and inform the user. They need to create a PR first using the `prepare-pr` skill.

Extract the PR number for subsequent commands:
```powershell
$prNumber = (gh pr list --head $currentBranch --json number --state open | ConvertFrom-Json)[0].number
```

### Step 2: Fetch PR Details and Diff

#### 2.1: Get PR Metadata
```powershell
gh pr view $prNumber --json files,additions,deletions,changedFiles,commits
```

#### 2.2: Get Full Diff for Analysis
```powershell
gh pr diff $prNumber
```

#### 2.3: Get Individual File Contents (for detailed review)
For each changed file that needs detailed review:
```powershell
gh pr diff $prNumber --patch
```

### Step 3: Analyze Changes Against Review Criteria

Review the diff using the criteria defined in `resources/review_criteria.md`. This includes:

- **Project-Specific Rules** (from GEMINI.md)
- **Java 25 Best Practices**
- **Code Quality Standards**
- **Security Considerations**
- **Testing Requirements**

For each file in the diff:
1. Identify the file type and applicable criteria
2. Check for violations of coding standards
3. Look for potential bugs, security issues, or performance problems
4. Verify documentation and test coverage
5. Note any improvements or suggestions

### Step 4: Add Inline Comments to PR

Use the GitHub REST API via `gh api` to add inline comments. For each issue found:

#### 4.1: Get the Latest Commit SHA
```powershell
$commitSha = (gh pr view $prNumber --json headRefOid | ConvertFrom-Json).headRefOid
```

#### 4.2: Get Repository Owner and Name
```powershell
$repoInfo = gh repo view --json owner,name | ConvertFrom-Json
$owner = $repoInfo.owner.login
$repo = $repoInfo.name
```

#### 4.3: Add an Inline Comment
For each review comment, use:
```powershell
gh api repos/$owner/$repo/pulls/$prNumber/comments `
  -f body="<comment text>" `
  -f path="<relative/path/to/file.java>" `
  -f commit_id="$commitSha" `
  -F line=<line_number> `
  -f side="RIGHT"
```

**Parameters:**
- `body`: The comment text (use template from `resources/comment_template.md`)
- `path`: Relative path to the file from repo root
- `commit_id`: The HEAD commit SHA of the PR
- `line`: The line number in the NEW file (right side of diff)
- `side`: Use `RIGHT` for additions/changes, `LEFT` for deletions

#### 4.4: For Multi-Line Comments (Code Blocks)
```powershell
gh api repos/$owner/$repo/pulls/$prNumber/comments `
  -f body="<comment text>" `
  -f path="<relative/path/to/file.java>" `
  -f commit_id="$commitSha" `
  -F start_line=<start_line> `
  -F line=<end_line> `
  -f start_side="RIGHT" `
  -f side="RIGHT"
```

### Step 5: Submit the Review

After adding all inline comments, submit the overall review:

```powershell
gh pr review $prNumber --comment --body "## PR Review Summary

<Summary of the review findings>

### Issues Found
- <List of issues by category>

### Suggestions
- <List of improvement suggestions>

### Positive Observations
- <Things done well>

---
*Review performed using project guidelines and Java 25 best practices.*"
```

**Review Actions:**
- `--comment`: General feedback (no approval/rejection)
- `--approve`: Approve the PR (only if no blocking issues)
- `--request-changes`: Request changes (if blocking issues found)

## Review Severity Levels

When adding comments, prefix with severity:

| Severity | Prefix | Description |
|----------|--------|-------------|
| 🔴 **BLOCKER** | `[BLOCKER]` | Must fix before merge (security, bugs, breaking changes) |
| 🟠 **MAJOR** | `[MAJOR]` | Should fix (code quality, performance, maintainability) |
| 🟡 **MINOR** | `[MINOR]` | Nice to fix (style, minor improvements) |
| 🔵 **SUGGESTION** | `[SUGGESTION]` | Optional improvement ideas |
| ✅ **PRAISE** | `[PRAISE]` | Positive feedback for good practices |

## Troubleshooting

| Issue | Solution |
|-------|----------|
| No PR found | Create PR first using `prepare-pr` skill or `gh pr create` |
| Authentication failed | Run `gh auth login` and authenticate |
| Comment failed (422) | Verify line number exists in the diff; use only changed lines |
| Rate limit exceeded | Wait and retry; check `gh api rate_limit` |
| Wrong line number | Line must be in the diff hunk; get exact line from `gh pr diff` |

## Quick Reference: Comment API

```powershell
# Single-line comment
gh api repos/{owner}/{repo}/pulls/{pr}/comments -f body="Comment" -f path="src/File.java" -f commit_id="{sha}" -F line=42 -f side="RIGHT"

# Multi-line comment
gh api repos/{owner}/{repo}/pulls/{pr}/comments -f body="Comment" -f path="src/File.java" -f commit_id="{sha}" -F start_line=40 -F line=45 -f start_side="RIGHT" -f side="RIGHT"

# Reply to existing comment
gh api repos/{owner}/{repo}/pulls/{pr}/comments/{comment_id}/replies -f body="Reply text"
```

## Pre-Review Checklist

Before submitting the review:

- [ ] All changed files have been analyzed
- [ ] Comments reference correct line numbers (verified against diff)
- [ ] Severity levels are appropriate
- [ ] Comments are constructive and actionable
- [ ] Summary accurately reflects findings
