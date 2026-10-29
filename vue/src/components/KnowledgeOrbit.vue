<template>
  <figure class="knowledge-orbit ink-landscape">
    <div class="landscape-window">
      <img :src="'/art/wind-and-water.jpg'" alt="水墨远山、江流与岸边书斋，《风雨图》局部" width="484" height="1200" decoding="async" />
      <div class="landscape-inscription" aria-hidden="true"><span>山水入卷</span><span>知有来处</span><b>知</b></div>
    </div>
    <figcaption><span>一卷山河，一页新知</span><a href="https://www.metmuseum.org/art/collection/search/51508" target="_blank" rel="noopener noreferrer">《风雨图》 · The Met ↗</a></figcaption>
  </figure>
</template>

<style scoped>
.ink-landscape { width: 390px; max-width: 100%; margin: 16px auto; flex-shrink: 0; color: #34483e; }
.landscape-window { position: relative; height: 258px; overflow: hidden; background: #e5dfcd; }
.landscape-window img { display: block; width: 100%; height: 100%; object-fit: cover; object-position: center 31%; filter: saturate(.45) brightness(1.22); }
.landscape-inscription { position: absolute; top: 0; right: 0; bottom: 0; display: flex; align-items: center; gap: 12px; padding: 24px 18px; background: #f4efdf; }
.landscape-inscription span { writing-mode: vertical-rl; font-family: 'STSong', 'SimSun', serif; font-size: 18px; letter-spacing: .25em; line-height: 1.6; }
.landscape-inscription b { align-self: flex-end; padding: 5px; border: 1px solid #984737; color: #984737; font-family: 'STSong', 'SimSun', serif; font-size: 16px; font-weight: 400; }
figcaption { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 8px; padding-top: 12px; font-size: 11px; line-height: 1.7; }
figcaption > span { letter-spacing: .12em; }
figcaption a { color: #5c6154; text-decoration: underline; text-underline-offset: 3px; }
figcaption a:hover { color: #8c3e30; }
figcaption a:focus-visible { outline: 2px solid #285541; outline-offset: 4px; }
@media (max-width: 767px) { .landscape-window { height: 200px; } }
</style>
