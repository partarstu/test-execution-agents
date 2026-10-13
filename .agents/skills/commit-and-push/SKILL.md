---
name: commit-and-push
description: Commits all changed and untracked files of the current branch with a generated commit message and user-chosen commit and author timestamps, then pushes to the remote repository. Use only when the user explicitly asks to commit and push everything.
---

# Commit and Push

Copy this checklist and track progress:

```
- [ ] 1. Ask for the commit time
- [ ] 2. Write the commit message
- [ ] 3. Commit all files
- [ ] 4. Push
```

1. Ask the user which hour and minute of today to use for the commit and author dates.
2. Read `git status` and `git diff` (including untracked files) and write a short commit message (*Git* in `AGENTS.md`) that summarises all changes.
3. Stage all files, including untracked ones, and commit with both dates set to today at the chosen time:
   ```bash
   git add -A
   GIT_AUTHOR_DATE="<YYYY-MM-DD>T<HH>:<MM>:00" GIT_COMMITTER_DATE="<YYYY-MM-DD>T<HH>:<MM>:00" git commit -m "<message>"
   ```
   In PowerShell, set `$env:GIT_AUTHOR_DATE` and `$env:GIT_COMMITTER_DATE` before `git commit` and remove them afterwards.
4. Push the current branch with `git push -u origin HEAD` once the user has consented to this push (*Git* in `AGENTS.md`). Report the commit hash and the push result.
