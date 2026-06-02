# CLI Stochastic Board Analyzer & Arena

The Dice Chess CLI Analyzer (`Main.scala`) is a powerful, interactive terminal tool designed for inspecting game states, debugging AI bots, and analyzing probabilistic outcomes directly on your local machine.

## How to Run
From the project root, start an interactive Scala shell and run the application:

```bash
mise run console
# In the sbt shell:
project rootJVM
run
```

## Available Commands

Once the analyzer starts, you will see the board state, the dice pool, and capture probabilities. You can enter the following commands:

| Command | Description |
| :--- | :--- |
| `e2e4` | Execute a manual micro-move (e.g., `e2e4`, `d2d4`). |
| `<bot_id>` | Delegate the current turn to an AI bot (e.g., `prudent`, `greedy`, `aggressive`). |
| `dice 1 2 3` | Update the current turn's dice pool with specific values (1-6). |
| `help` | Show this help menu. |
| `exit` | Exit the application. |

## Interactive Features

* **Board Rendering**: The analyzer prints the current board state using standard chess notation (`P/p` = Pawn, `N/n` = Knight, etc.).
* **Probability Radar**:
    * **King Capture Prob**: Probability that the opponent captures your King on their next turn, based on all 216 dice combinations.
    * **Queen Capture Prob**: Probability that the opponent captures your Queen on their next turn.
* **Material Score**: A signed centipawn score evaluating the material balance from the perspective of the active player.

## Example Session

```text
  a b c d e f g h
8 r n b q k b n r  8
7 p p p p p p p p  7
...
Dice: List()

--- Analysis (Active: White) ---
King Capture Prob:  0.00
Queen Capture Prob: 0.00
Material Score:     0 cp

Enter UCI move (e.g., e2e4), bot ID (e.g., prudent), 'dice 1 2 3', 'help', or 'exit': dice 1 2 3
...
Dice: List(1, 2, 3)

Enter UCI move (e.g., e2e4), bot ID (e.g., prudent), 'dice 1 2 3', 'help', or 'exit': prudent
Bot 'prudent' suggests: e2e4
...
```
