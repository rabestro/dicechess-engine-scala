## Context
We now have a comprehensive JSON-based test suite for `KingCaptureProbability`. To make these test cases visible to developers and easily verifiable, we need to generate a visual documentation catalog, similar to the one we have for move generation tests.

## Objective
Implement a documentation generator that reads `jvm/src/test/resources/search/king_capture_probabilities.json` and produces a visual documentation page.

## Implementation Details
- Implement `KingCaptureProbabilityDocGenerator.scala` (or extend the existing `DocGenerator`).
- The generated page should:
  - Display the position (using Lichess FEN image URL).
  - Show the description, FEN string, and expected probability for each test case.
- Place the generated Markdown file in `docs/src/content/docs/architecture/search/king-capture-probability-test-cases.md`.
- Add a new task in `mise.toml` for generating this documentation.

## Definition of Done (DoD)
- [ ] Create `KingCaptureProbabilityDocGenerator.scala`.
- [ ] Run the generator to produce the documentation page.
- [ ] Add `docs:search:generate` task to `mise.toml`.
- [ ] Verify documentation generation works and the generated file is correct.
- [ ] Run `mise run format` and `mise run check`.
