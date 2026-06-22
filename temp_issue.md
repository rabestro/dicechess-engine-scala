## Context
We need to support an Opening Book for bots to allow them to make instant, statistically proven moves at the start of the game without wasting their time budget. The opening book data will be supplied by external consumers (like private extensions or the analytics backend) to protect the dataset.

## Objective
Implement a decorator `OpeningBookBot` that takes a parsed `Map` of opening moves and an underlying `SearchAlgorithm`. If the position exists in the book, it returns the move instantly; otherwise, it delegates to the underlying bot.

## Definition of Done (DoD)
- [x] Implement `OpeningBookBot.scala`.
- [x] Implement a parser utility `OpeningBookParser.scala` to deserialize JSON maps into `Map[String, String]` or `Map[String, List[Move]]`.
- [x] Add unit tests for the decorator.
- [x] Pass `mise run check`.
