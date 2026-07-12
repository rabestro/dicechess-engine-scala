# Code review style guide

Generated extract of the review-relevant rules in AGENTS.md — keep the two in sync manually.

## Language and scope

- All code comments, Scaladoc, commit messages, PR text, and review replies must be in English.
- This is a public AGPL-3.0 repo: flag any private infrastructure details (hostnames, IPs, tokens) that appear in code, docs, or config.

## Code conventions

- Scala 3 "new" syntax only: braceless bodies with colons, extension methods, opaque types; `maxColumn` 120, 2-space indent.
- `null`, `return`, and `throw` are banned (scalafix `DisableSyntax`). Errors flow through `Either`.
- The build compiles with `-Werror`, `-Wunused:all`, `-language:strictEquality`, `-Yexplicit-nulls`: flag `==` on custom types without a `CanEqual` instance and unhandled `| Null` from Java interop.
- Opaque types must document their bitwise memory layout in Scaladoc; companion objects carry the operations.
- Hot paths (`movegen/`, `search/`): bitwise ops on `Long`, `inline`/opaque zero-cost abstractions, no allocations in inner loops — flag boxing, collection churn, or closures introduced there.

## Scaladoc

- Document *why*, not *what*; Markdown formatting, `[[Type]]` cross-references, never legacy `{{{ }}}`.
- Every ```` ```scala ```` fence in Scaladoc is compiled by `sbt doc`. Non-Scala examples (JSON, pseudocode) must use ```` ```text ```` or ```` ```json ```` fences — a wrong fence breaks the docs deploy after merge.

## Testing

- MUnit `FunSuite` (+ `munit-scalacheck` for properties); suites named `*Suite`/`*Spec`, sentence-style test names; regression suites cite the issue number in the Scaladoc header.
- Positions in tests are built either directly via `FenParser.parse` + `withDicePool` (most suites) or via the `ChessDsl` test DSL (`"<fen>".withDice(...)`, JSON-fixture specs) — both are accepted; do not flag either pattern.
- Statement coverage must stay >= 85% (JVM): new logic needs tests in the same PR.
- Shared-code tests also run on the slower JS/Wasm Node runner — flag tight time budgets or wall-clock assertions in tests.
- `BotRegistry` is a process-wide mutable singleton — flag tests that depend on its global state without isolation.

## Review-critical gotchas

- Turn maximality is measured in dice consumed, not move count (castling spends two dice); the active color never changes within a turn — scrutinize any change that assumes one die per move.
- Chess960 castling is unsupported (e1/h1/a1 hardcoded) — flag changes that silently assume it works.
- `publish.yaml` and `release.yaml` intentionally duplicate publish steps (tag-push recursion guard) — a change to one usually needs the same change in the other.
- The root `package.json` version is dead weight (real version comes from sbt at packaging time) — flag PRs that "fix" it.
- Changes to the JS API surface (`JsApi.scala`, `EngineFacade.scala`) must update `js/dicechess-engine.d.ts` in the same PR.
