<script lang="ts">
  import { Chessground } from 'svelte5-chessground';
  import 'chessground/assets/chessground.base.css';
  import 'chessground/assets/chessground.brown.css';
  import 'chessground/assets/chessground.cburnett.css';
  import { EngineFacade } from 'dicechess-engine';

  let fen = $state('rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1');
  let diceRolls = $state<number[]>([]);
  let engineThinking = $state(false);

  let activeColor = $derived(fen.split(' ')[1] === 'w' ? 'white' : 'black');

  const diceNames = ['Pawn', 'Knight', 'Bishop', 'Rook', 'Queen', 'King'];

  // Calculate legal destinations natively expected by chessground (Map<Key, Key[]>)
  let legalDests = $derived.by(() => {
    const dests = new Map<string, string[]>();
    if (diceRolls.length > 0) {
      const rawDests = EngineFacade.getLegalDests(fen, diceRolls) || {};
      for (const [k, v] of Object.entries(rawDests)) {
        dests.set(k, v as string[]);
      }
    }
    return dests;
  });

  function rollThreeDice(): number[] {
    return [
      Math.floor(Math.random() * 6) + 1,
      Math.floor(Math.random() * 6) + 1,
      Math.floor(Math.random() * 6) + 1
    ];
  }

  function endTurn() {
    // Flip the active color in the FEN
    const parts = fen.split(' ');
    parts[1] = parts[1] === 'w' ? 'b' : 'w';
    fen = parts.join(' ');
    diceRolls = [];

    if (parts[1] === 'b') {
      engineThinking = true;
      setTimeout(startEngineTurn, 500);
    }
  }

  function handleHumanMove(orig: string, dest: string, metadata: any) {
    const pt = EngineFacade.getPieceTypeAt(fen, orig);

    if (!pt || !diceRolls.includes(pt)) {
      // Invalid move: piece type not matching available dice
      fen = fen + ' '; // Force redraw
      setTimeout(() => {
        fen = fen.trim();
      }, 0);
      return;
    }

    // Consume the die
    const idx = diceRolls.indexOf(pt);
    diceRolls.splice(idx, 1);
    diceRolls = [...diceRolls]; // trigger reactivity

    const newFen = EngineFacade.applyMove(fen, orig, dest, 'q');
    if (newFen) {
      // Preserve the current turn color to construct a prospective FEN
      const parts = newFen.split(' ');
      parts[1] = activeColor === 'white' ? 'w' : 'b';
      const tempFenPreserved = parts.join(' ');

      const remainingDests = EngineFacade.getLegalDests(tempFenPreserved, diceRolls) || {};
      const hasMoves = diceRolls.length > 0 && Object.keys(remainingDests).length > 0;

      if (hasMoves) {
        fen = tempFenPreserved;
      } else {
        fen = newFen;
        diceRolls = [];
        if (fen.split(' ')[1] === 'b') {
          engineThinking = true;
          setTimeout(startEngineTurn, 500);
        }
      }
    } else {
      fen = fen + ' '; // Force redraw
      setTimeout(() => {
        fen = fen.trim();
      }, 0);
    }
  }

  function startEngineTurn() {
    // Roll 3 dice for the engine
    diceRolls = rollThreeDice();
    executeEngineMicroMove();
  }

  function executeEngineMicroMove() {
    if (diceRolls.length === 0) {
      engineThinking = false;
      return;
    }

    const rawDests = EngineFacade.getLegalDests(fen, diceRolls) || {};
    if (Object.keys(rawDests).length === 0) {
      // No legal moves left, pass turn
      endTurn();
      engineThinking = false;
      return;
    }

    // Try to find a valid move from the available dice
    let chosenMove = null;
    let chosenDieIdx = -1;

    // Bot logic: prioritize capturing the king if ANY die can do it
    for (let i = 0; i < diceRolls.length; i++) {
      const move = EngineFacade.getBotMove(fen, diceRolls[i]);
      if (move) {
        chosenMove = move;
        chosenDieIdx = i;
        // If we exported a way to know it's a king capture, we would break here.
        // Since we didn't explicitly export "isKingCapture" flag, getBotMove already prioritizes it internally for that specific die.
        // We just take the first valid move for now.
        break;
      }
    }

    if (chosenMove) {
      diceRolls.splice(chosenDieIdx, 1);
      diceRolls = [...diceRolls];

      const newFen = EngineFacade.applyMove(
        fen,
        chosenMove.from,
        chosenMove.to,
        chosenMove.promotion
      );
      if (newFen) {
        if (diceRolls.length > 0) {
          // Flip back to preserve engine color
          const parts = newFen.split(' ');
          parts[1] = activeColor === 'white' ? 'w' : 'b';
          fen = parts.join(' ');
          setTimeout(executeEngineMicroMove, 600); // 600ms delay between micro-moves
        } else {
          fen = newFen; // Turn ends, natural flip back to human
          engineThinking = false;
        }
      } else {
        endTurn();
        engineThinking = false;
      }
    } else {
      endTurn();
      engineThinking = false;
    }
  }

  function rollDice() {
    diceRolls = rollThreeDice();
  }

  let cgConfig = $derived({
    fen,
    turnColor: activeColor,
    movable: {
      color: activeColor,
      free: false,
      dests: legalDests,
      events: {
        after: handleHumanMove
      }
    }
  });
</script>

<div class="mx-auto flex w-full max-w-4xl flex-col items-center gap-6 p-4">
  <div
    class="flex w-full flex-col items-center justify-between gap-4 rounded-xl border border-slate-700/50 bg-slate-800/50 p-6 shadow-xl backdrop-blur-md md:flex-row"
  >
    <div class="flex flex-col">
      <span class="text-sm font-medium tracking-wider text-slate-400 uppercase">Active Color</span>
      <span
        class="text-2xl font-bold {activeColor === 'white'
          ? 'text-white'
          : 'text-slate-300'} capitalize"
      >
        {activeColor}
      </span>
    </div>

    <div class="flex flex-grow flex-col items-center">
      <span class="text-sm font-medium tracking-wider text-slate-400 uppercase">Available Dice</span
      >
      <div class="mt-2 flex min-h-[48px] items-center gap-3">
        {#if diceRolls.length === 0}
          <span class="text-slate-500 italic">No dice rolled</span>
        {:else}
          {#each diceRolls as die, i (i)}
            <div
              class="flex h-12 w-12 transform flex-col items-center justify-center rounded-lg bg-indigo-500 text-white shadow-lg shadow-indigo-500/30 transition-all hover:scale-105"
            >
              <span class="text-xl font-black">{die}</span>
              <span class="-mt-1 text-[10px] font-bold uppercase opacity-80"
                >{diceNames[die - 1].substring(0, 3)}</span
              >
            </div>
          {/each}
        {/if}
      </div>
    </div>

    <div class="flex flex-col items-end gap-2">
      {#if diceRolls.length === 0 && activeColor === 'white'}
        <button
          onclick={rollDice}
          disabled={engineThinking}
          class="rounded-lg bg-emerald-500 px-6 py-2 font-bold text-white shadow-lg shadow-emerald-500/20 transition-all hover:bg-emerald-400 active:scale-95 disabled:opacity-50"
        >
          Roll 3 Dice
        </button>
      {/if}
      {#if diceRolls.length > 0 && activeColor === 'white'}
        <button
          onclick={endTurn}
          class="rounded-lg bg-amber-500 px-6 py-2 font-bold text-white shadow-lg shadow-amber-500/20 transition-all hover:bg-amber-400 active:scale-95"
        >
          End Turn Early
        </button>
      {/if}
    </div>
  </div>

  <div
    class="h-[300px] w-[300px] overflow-hidden rounded-sm border-4 border-slate-800 shadow-2xl shadow-black/50 sm:h-[500px] sm:w-[500px] md:h-[600px] md:w-[600px]"
  >
    <Chessground config={cgConfig} />
  </div>

  {#if engineThinking}
    <div class="mt-2 flex animate-pulse items-center gap-2 text-lg font-medium text-indigo-400">
      <div class="h-3 w-3 rounded-full bg-indigo-400"></div>
      Engine is executing micro-moves...
    </div>
  {/if}
</div>
