<script lang="ts">
	import GamePlay from '$lib/GamePlay.svelte';
</script>

<svelte:head>
	<title>Dice Chess - Bot Test Harness</title>
	<meta name="description" content="Test harness for the Scala 3 Dice Chess Engine" />
</svelte:head>

<main class="min-h-screen bg-[#0f172a] text-slate-200 font-sans selection:bg-indigo-500/30">
	<div class="fixed inset-0 pointer-events-none bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-indigo-900/20 via-[#0f172a] to-[#0f172a] -z-10"></div>
	
	<header class="w-full p-6 flex flex-col items-center justify-center gap-2 relative z-10 border-b border-white/5 bg-white/5 backdrop-blur-lg">
		<h1 class="text-4xl md:text-5xl font-black tracking-tight text-transparent bg-clip-text bg-gradient-to-r from-emerald-400 via-indigo-400 to-purple-400 drop-shadow-sm">
			Dice Chess Engine
		</h1>
		<p class="text-slate-400 font-medium tracking-wide">
			Test Harness v0.4
		</p>
	</header>

	<section class="p-4 md:p-8 relative z-10 flex flex-col items-center justify-center">
		<GamePlay />
	</section>
</main>
