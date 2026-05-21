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
		if (pt && diceRolls.includes(pt)) {
			// Consume the die
			const idx = diceRolls.indexOf(pt);
			diceRolls.splice(idx, 1);
			diceRolls = [...diceRolls]; // trigger reactivity
		}

		const newFen = EngineFacade.applyMove(fen, orig, dest, 'q');
		if (newFen) {
			// Check if any legal moves remain for the remaining dice using the prospective FEN
			// Note: applyMove flips the color naturally, so we must construct a temporary FEN 
			// with the preserved color to accurately ask getLegalDests
			const parts = newFen.split(' ');
			parts[1] = activeColor === 'white' ? 'w' : 'b';
			const tempFenPreserved = parts.join(' ');
			
			const remainingDests = EngineFacade.getLegalDests(tempFenPreserved, diceRolls) || {};
			const hasMoves = diceRolls.length > 0 && Object.keys(remainingDests).length > 0;

			if (hasMoves) {
				// We still have dice and moves. Apply the preserved color FEN.
				fen = tempFenPreserved;
			} else {
				// Turn is over, accept the natural color flip from applyMove
				fen = newFen;
				diceRolls = [];
				if (fen.split(' ')[1] === 'b') {
					engineThinking = true;
					setTimeout(startEngineTurn, 500);
				}
			}
		} else {
			fen = fen; // Force redraw on invalid
		}
	}

	function startEngineTurn() {
		// Roll 3 dice for the engine
		diceRolls = [
			Math.floor(Math.random() * 6) + 1,
			Math.floor(Math.random() * 6) + 1,
			Math.floor(Math.random() * 6) + 1
		];
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

			const newFen = EngineFacade.applyMove(fen, chosenMove.from, chosenMove.to, chosenMove.promotion);
			if (newFen) {
				if (diceRolls.length > 0) {
					// Flip back
					const parts = newFen.split(' ');
					parts[1] = 'b';
					fen = parts.join(' ');
					setTimeout(executeEngineMicroMove, 600); // 600ms delay between micro-moves
				} else {
					fen = newFen; // Turn ends, natural flip to white
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
		diceRolls = [
			Math.floor(Math.random() * 6) + 1,
			Math.floor(Math.random() * 6) + 1,
			Math.floor(Math.random() * 6) + 1
		];
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

<div class="flex flex-col items-center gap-6 p-4 w-full max-w-4xl mx-auto">
	<div class="w-full flex flex-col md:flex-row justify-between items-center bg-slate-800/50 p-6 rounded-xl backdrop-blur-md border border-slate-700/50 shadow-xl gap-4">
		<div class="flex flex-col">
			<span class="text-sm text-slate-400 font-medium tracking-wider uppercase">Active Color</span>
			<span class="text-2xl font-bold {activeColor === 'white' ? 'text-white' : 'text-slate-300'} capitalize">
				{activeColor}
			</span>
		</div>
		
		<div class="flex flex-col items-center flex-grow">
			<span class="text-sm text-slate-400 font-medium tracking-wider uppercase">Available Dice</span>
			<div class="flex items-center gap-3 mt-2 min-h-[48px]">
				{#if diceRolls.length === 0}
					<span class="text-slate-500 italic">No dice rolled</span>
				{:else}
					{#each diceRolls as die, i (i)}
						<div class="w-12 h-12 flex flex-col items-center justify-center bg-indigo-500 rounded-lg shadow-lg shadow-indigo-500/30 text-white transition-all transform hover:scale-105">
							<span class="text-xl font-black">{die}</span>
							<span class="text-[10px] uppercase font-bold opacity-80 -mt-1">{diceNames[die - 1].substring(0, 3)}</span>
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
					class="px-6 py-2 bg-emerald-500 hover:bg-emerald-400 disabled:opacity-50 text-white font-bold rounded-lg transition-all shadow-lg shadow-emerald-500/20 active:scale-95"
				>
					Roll 3 Dice
				</button>
			{/if}
			{#if diceRolls.length > 0 && activeColor === 'white'}
				<button 
					onclick={endTurn}
					class="px-6 py-2 bg-amber-500 hover:bg-amber-400 text-white font-bold rounded-lg transition-all shadow-lg shadow-amber-500/20 active:scale-95"
				>
					End Turn Early
				</button>
			{/if}
		</div>
	</div>

	<div class="w-[300px] h-[300px] sm:w-[500px] sm:h-[500px] md:w-[600px] md:h-[600px] rounded-sm overflow-hidden shadow-2xl shadow-black/50 border-4 border-slate-800">
		<Chessground config={cgConfig} />
	</div>
	
	{#if engineThinking}
		<div class="text-indigo-400 font-medium animate-pulse flex items-center gap-2 text-lg mt-2">
			<div class="w-3 h-3 bg-indigo-400 rounded-full"></div>
			Engine is executing micro-moves...
		</div>
	{/if}
</div>
