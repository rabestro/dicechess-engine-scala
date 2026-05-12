# AGENTS.md

Branch naming rules and agent guidance

Allowed branch prefixes:
- `task` — work items / tasks
- `feat`  — new features
- `bug`   — bug fixes

Branch name pattern (required):
  (task|feat|bug)/<issue-number>-<short-description>
Example: `task/1234-fen-parser`

Agent rules (Copilot / automation):
- Do not implement or open a PR unless an issue exists and the branch is named according to the pattern.
- Agents may create draft changes, suggest code, and open the PR linked to the issue.
- Agents should run `sbt scalafmtAll` on any generated code and ensure tests compile locally before proposing a PR.
- Human retains the ultimate authority to review, approve, and merge the PR.

