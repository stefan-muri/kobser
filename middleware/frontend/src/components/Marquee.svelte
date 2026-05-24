<script>
  import { onMount } from 'svelte';

  export let text = '';
  export let cls = '';

  const GAP = 48;

  let wrap;
  let span;
  let scrolling = false;
  let loopPx = 0;
  let secs = '3.0';

  let raf;
  function measure() {
    if (!wrap || !span) return;
    const tw = span.scrollWidth;
    const cw = wrap.clientWidth;
    scrolling = tw > cw + 2;
    loopPx = tw + GAP;
    secs = Math.max(3, loopPx / 50).toFixed(1);
  }
  function schedule() {
    cancelAnimationFrame(raf);
    raf = requestAnimationFrame(() => requestAnimationFrame(measure));
  }

  $: text, schedule();

  onMount(() => {
    window.addEventListener('resize', schedule);
    return () => {
      cancelAnimationFrame(raf);
      window.removeEventListener('resize', schedule);
    };
  });
</script>

<div bind:this={wrap} class="overflow-hidden">
  <div
    style={scrolling
      ? `display:inline-flex;gap:${GAP}px;--mlp:-${loopPx}px;animation:marquee-continuous ${secs}s linear 1.5s infinite`
      : ''}
  >
    <span bind:this={span} class="block whitespace-nowrap {cls}">{text}</span>
    {#if scrolling}
      <span class="whitespace-nowrap {cls}" aria-hidden="true">{text}</span>
    {/if}
  </div>
</div>
