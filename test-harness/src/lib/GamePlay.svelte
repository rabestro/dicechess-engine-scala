<script lang="ts">
  import { createDiceChess, DiceChessBoard } from '@rabestro/svelte-dicechess';
  import { EngineFacade } from 'dicechess-engine';

  let engineThinking = $state(false);

  const store = createDiceChess({
    onTurnEnd: () => {
      if (store.activeColor === 'black') {
        engineThinking = true;
        setTimeout(startEngineTurn, 500);
      }
    }
  });

  const diceNames = ['Pawn', 'Knight', 'Bishop', 'Rook', 'Queen', 'King'];

  function rollDice() {
    store.rollDice();
  }

  function endTurn() {
    store.passTurn();
  }

  function startEngineTurn() {
    // Roll 3 dice for the engine
    store.rollDice();
    executeEngineMicroMove();
  }

  function executeEngineMicroMove() {
    if (store.diceRolls.length === 0) {
      engineThinking = false;
      return;
    }

    if (store.legalDests.size === 0) {
      // No legal moves left, pass turn
      store.passTurn();
      engineThinking = false;
      return;
    }

    // Try to find a valid move from the available dice
    let chosenMove = null;
    let chosenDieIdx = -1;

    for (let i = 0; i < store.diceRolls.length; i++) {
      const move = EngineFacade.getBotMove(store.fen, store.diceRolls[i]);
      if (move) {
        chosenMove = move;
        chosenDieIdx = i;
        break;
      }
    }

    if (chosenMove) {
      const success = store.applyMove(chosenMove.from, chosenMove.to, chosenMove.promotion);
      if (success) {
        if (store.activeColor === 'black' && store.diceRolls.length > 0) {
          setTimeout(executeEngineMicroMove, 600); // 600ms delay between micro-moves
        } else {
          engineThinking = false;
        }
      } else {
        store.passTurn();
        engineThinking = false;
      }
    } else {
      store.passTurn();
      engineThinking = false;
    }
  }
</script>

<div class="mx-auto flex w-full max-w-4xl flex-col items-center gap-6 p-4">
  <div
    class="flex w-full flex-col items-center justify-between gap-4 rounded-xl border border-slate-700/50 bg-slate-800/50 p-6 shadow-xl backdrop-blur-md md:flex-row"
  >
    <div class="flex flex-col">
      <span class="text-sm font-medium tracking-wider text-slate-400 uppercase">Active Color</span>
      <span
        class="text-2xl font-bold {store.activeColor === 'white'
          ? 'text-white'
          : 'text-slate-300'} capitalize"
      >
        {store.activeColor}
      </span>
    </div>

    <div class="flex flex-grow flex-col items-center">
      <span class="text-sm font-medium tracking-wider text-slate-400 uppercase">Available Dice</span
      >
      <div class="mt-2 flex min-h-[48px] items-center gap-3">
        {#if store.diceRolls.length === 0}
          <span class="text-slate-500 italic">No dice rolled</span>
        {:else}
          {#each store.diceRolls as die, i (i)}
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
      {#if store.diceRolls.length === 0 && store.activeColor === 'white'}
        <button
          onclick={rollDice}
          disabled={engineThinking}
          class="rounded-lg bg-emerald-500 px-6 py-2 font-bold text-white shadow-lg shadow-emerald-500/20 transition-all hover:bg-emerald-400 active:scale-95 disabled:opacity-50"
        >
          Roll 3 Dice
        </button>
      {/if}
      {#if store.diceRolls.length > 0 && store.activeColor === 'white'}
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
    <DiceChessBoard {store} />
  </div>

  {#if engineThinking}
    <div class="mt-2 flex animate-pulse items-center gap-2 text-lg font-medium text-indigo-400">
      <div class="h-3 w-3 rounded-full bg-indigo-400"></div>
      Engine is executing micro-moves...
    </div>
  {/if}
</div>
