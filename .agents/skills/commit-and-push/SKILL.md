---
name: commit-and-push
description: Allows to commit all files and push them to the remote repository.
---

// turbo-all

## Overview

1. Ask user which hour and minute as the part of the timestamp to use for commit and author dates on commit
2. Generate a short description of all changes present in those files as the commit message
3. Commit all files (including untracked ones) using the generated commit message and timestamps with specified hour
4. Push the changes to the remote repository