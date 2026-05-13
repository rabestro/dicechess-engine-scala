---
title: Agent Workflows
description: Branch naming conventions, local task runners, and GitHub lifecycle management rules for human and AI contributors.
---

To ensure pristine git history, absolute type safety, and consistent validation, all human and AI developers must strictly follow these workflows.

---

## 🌿 Branch Conventions

All changes must occur on dedicated branches. Standard prefixes are mandatory:
* `task/` — scoped tasks or chores
* `feat/` — new functionality
* `bug/` — bug fixes or stabilization

**Pattern**: `(task|feat|bug)/<issue-number>-<description>`  
**Example**: `task/6-refactor-agent-guidelines`

---

## 🚀 Developer Workflows (Mise)

We use [Mise](https://mise.jdx.co/) to unify environment dependencies and runnable scripts. From the root directory, run:

| Command | Action |
| :--- | :--- |
| `mise run format` | Runs `scalafmt` automatically on all Scala sources. |
| `mise run check` | Validates formatting and runs the entire MUnit test suite sequentially. |
| `mise run console` | Spins up a Scala 3 REPL configured with the project classpath. |

---

## ⚙️ Issue Lifecycle

1. **Create Issue**: Formulate a Context, Objective, and specific Definition of Done (DoD).
2. **Branch & Draft**: Checkout a branch using the pattern. Create an Implementation Plan for complex changes.
3. **Implement & Verify**: Run `mise run check` locally. Ensure all checks pass.
4. **Pull Request**: Open a PR linking back to the issue (e.g., `Closes #6`).

---

## 🤖 AI Automation Snippets

### Zsh/Bash Template
```bash
cat << 'EOF' > temp_issue.md
## Context
[Describe task context]

## Objective
[Describe task objective]

## Definition of Done (DoD)
- [ ] Feature complete
- [ ] Tests passed
EOF

gh issue create \
  --title "Short descriptive title" \
  --body-file "temp_issue.md" \
  --milestone "v0.1 - Foundation & Core Types" \
  --label "enhancement" \
  --label "ai-ready"

rm temp_issue.md
```

### PowerShell Template (Mac / Windows)
```powershell
$issueBody = @'
## Context
[Describe task context]

## Objective
[Describe task objective]

## Definition of Done (DoD)
- [ ] Feature complete
- [ ] Tests passed
'@

$issueBody | Out-File -FilePath "temp_issue.md" -Encoding utf8

gh issue create `
  --title "Short descriptive title" `
  --body-file "temp_issue.md" `
  --milestone "v0.1 - Foundation & Core Types" `
  --label "enhancement" `
  --label "ai-ready"

Remove-Item "temp_issue.md"
```
