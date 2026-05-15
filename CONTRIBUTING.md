# Contributing

Workflow Order: Issue -> Branch -> Implementation -> PR (mandatory).

- Create an issue (Context, Objective, Definition of Done) before implementation.
- Create a branch using the naming convention: `task/<ID>-<short-desc>`, `feat/<ID>-<short-desc>`, `bug/<ID>-<short-desc>`.
- Implement changes on that branch only. Do not work directly on `main`.
- Run `sbt scalafmtAll` and `sbt test` before opening a PR.
- In PR body include `Closes #<issue-number>` and a checklist matching the issue DoD.

See `AGENTS.md` for branch-name patterns and agent-specific guidance.

