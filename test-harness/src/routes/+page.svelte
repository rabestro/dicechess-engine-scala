<script lang="ts">
  import GamePlay from '$lib/GamePlay.svelte';
</script>

<svelte:head>
  <title>Dice Chess - Bot Test Harness</title>
  <meta name="description" content="Test harness for the Scala 3 Dice Chess Engine" />
</svelte:head>

<main class="min-h-screen bg-[#0f172a] font-sans text-slate-200 selection:bg-indigo-500/30">
  <div
    class="pointer-events-none fixed inset-0 -z-10 bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-indigo-900/20 via-[#0f172a] to-[#0f172a]"
  ></div>

  <header
    class="relative z-10 flex w-full flex-col items-center justify-center gap-2 border-b border-white/5 bg-white/5 p-6 backdrop-blur-lg"
  >
    <h1
      class="bg-gradient-to-r from-emerald-400 via-indigo-400 to-purple-400 bg-clip-text text-4xl font-black tracking-tight text-transparent drop-shadow-sm md:text-5xl"
    >
      Dice Chess Engine
    </h1>
    <p class="font-medium tracking-wide text-slate-400">Test Harness v0.4</p>
  </header>

  <section class="relative z-10 flex flex-col items-center justify-center p-4 md:p-8">
    <GamePlay />
  </section>
</main>
