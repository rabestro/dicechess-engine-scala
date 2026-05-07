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
- Agents may create draft changes or suggest code, but a human must open the PR and confirm the `Closes #<issue-number>` link in the PR body.
- Agents should run `sbt scalafmtAll` on any generated code and ensure tests compile locally before proposing a PR.

