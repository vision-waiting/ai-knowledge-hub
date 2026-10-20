<template>
  <div class="knowledge-orbit" aria-hidden="true">
    <div class="orbit-ring orbit-ring-outer"></div>
    <div class="orbit-ring orbit-ring-inner"></div>
    <span class="orbit-spark spark-one">✳</span><span class="orbit-spark spark-two">+</span>
    <div class="orbit-sheet sheet-back"><span>COLLECT / 收集</span><i></i><i></i><i></i></div>
    <div class="book-stage">
    <div class="orbit-sheet sheet-front">
      <div class="sheet-heading"><span>KNOWLEDGE</span><b>↗</b></div>
      <strong>灵感有迹可循<br />知识自成脉络</strong>
      <div class="sheet-lines"><i></i><i></i><i></i></div>
      <div class="sheet-footer"><span>连接 · 理解 · 生长</span><b>✳</b></div>
    </div>
    <div class="book-leaf">
      <div class="leaf-face leaf-cover">
        <span class="cover-eyebrow">THE KNOWLEDGE COLLECTION</span>
        <span class="cover-emblem">✳</span>
        <strong>每翻一页，<br />多一个世界。</strong>
        <span class="cover-rule"></span>
        <small>从好奇开始 / OPEN YOUR MIND</small>
      </div>
      <div class="leaf-face leaf-reverse">
        <span class="reverse-eyebrow">给好奇心，留一页</span>
        <span class="reverse-illustration">✳</span>
        <i></i><i></i><i></i><i></i>
        <small>01 — 探索，从这里开始</small>
      </div>
    </div>
    </div>
    <span class="orbit-tag tag-source"><span>↗</span> 每个答案，都有出处</span>
    <span class="orbit-tag tag-idea">✦ &nbsp; 让理解发生</span>
    <span class="orbit-caption">A SPACE FOR YOUR NEXT IDEA</span>
  </div>
</template>

<style scoped>
.knowledge-orbit { position: relative; width: 390px; height: 310px; flex-shrink: 0; color: #214d42; }
.orbit-ring { position: absolute; border: 1px solid #365c4324; border-radius: 50%; pointer-events: none; }
.orbit-ring-outer { width: 320px; height: 280px; left: 40px; top: 6px; transform: rotate(-25deg); }
.orbit-ring-inner { width: 370px; height: 180px; left: 8px; top: 60px; transform: rotate(-25deg); }
.orbit-sheet { position: absolute; width: 184px; height: 223px; padding: 21px; border-radius: 5px 14px 14px 5px; }
.sheet-back { top: 22px; left: 72px; background: #bdd2b4; transform: rotate(-17deg); box-shadow: -6px 6px 0 #a9bfa0; }
.sheet-back > span { font-size: 8px; letter-spacing: 1.6px; }
.sheet-back > i { display: block; background: #66876340; height: 3px; margin-top: 14px; }
.book-stage { position: absolute; top: 42px; left: 158px; width: 184px; height: 223px; perspective: 1000px; transform: rotate(9deg); }
.sheet-front { inset: 0; background: #fffef8; border: 1px solid #d5d7c8; box-shadow: 4px 5px 0 #e8e6d9, 5px 6px 0 #b9c1ad, 0 18px 34px #28433414; }
.book-leaf { position: absolute; inset: 0; transform-origin: left center; transform-style: preserve-3d; transform: rotateY(-154deg); }
.leaf-face { position: absolute; inset: 0; padding: 22px 18px; border: 1px solid #c2ceb0; border-radius: 3px 12px 12px 3px; backface-visibility: hidden; -webkit-backface-visibility: hidden; }
.leaf-cover { display: flex; flex-direction: column; align-items: center; background: #305744; color: #edf0cf; box-shadow: inset 5px 0 #244635, inset 7px 0 #b4c59240; }
.cover-eyebrow { font-size: 6px; letter-spacing: 1.3px; text-align: center; line-height: 1.7; }
.cover-emblem { font-size: 42px; color: #d1dfa3; margin: 12px 0 7px; line-height: 1; }
.leaf-cover strong { font-family: "STSong", "SimSun", serif; font-weight: 400; font-size: 20px; line-height: 1.6; }
.cover-rule { width: 32px; height: 1px; background: #b8c892; margin: 13px 0; }
.leaf-cover small { font-size: 6px; letter-spacing: .5px; }
.leaf-reverse { background: linear-gradient(90deg, #fffdf0 75%, #e0e3d1); transform: rotateY(180deg); color: #61744d; }
.reverse-eyebrow { font-size: 10px; letter-spacing: 1px; }
.reverse-illustration { display: block; font-size: 49px; color: #9cb17b; text-align: center; margin: 12px 0; }
.leaf-reverse i { display: block; height: 2px; background: #d7dfc9; margin: 9px 0; }
.leaf-reverse i:nth-last-of-type(1) { width: 65%; }
.leaf-reverse small { display: block; margin-top: 15px; font-size: 7px; }
.sheet-heading { display: flex; justify-content: space-between; align-items: center; font-size: 8px; letter-spacing: 1.7px; border-bottom: 1px solid #dce1d4; padding-bottom: 12px; }
.sheet-heading b { font-size: 20px; font-weight: 400; }
.sheet-front > strong { display: block; font-family: "STSong", "SimSun", serif; font-size: 20px; font-weight: 500; line-height: 1.7; margin-top: 15px; }
.sheet-lines { margin-top: 15px; }
.sheet-lines i { display: block; height: 2px; background: #d9dfd2; margin-top: 7px; }
.sheet-lines i:last-child { width: 65%; }
.sheet-footer { margin-top: 17px; display: flex; justify-content: space-between; align-items: center; font-size: 8px; }
.sheet-footer b { font-size: 22px; color: #678b51; }
.orbit-tag { position: absolute; z-index: 1; padding: 11px 15px; border-radius: 8px; font-size: 11px; box-shadow: 0 6px 20px #243d3010; white-space: nowrap; }
.tag-source { top: 224px; left: 31px; background: #224e43; color: #fffef7; transform: rotate(-6deg); }
.tag-source > span { color: #dbea9c; margin-right: 6px; }
.tag-idea { top: 30px; right: 0; background: #e8edb8; transform: rotate(7deg); }
.orbit-spark { position: absolute; color: #6b8654; font-size: 39px; }
.spark-one { left: 34px; top: 69px; }.spark-two { right: 25px; bottom: 40px; font-size: 27px; }
.orbit-caption { position: absolute; left: 90px; bottom: 0; font-size: 8px; letter-spacing: 2.2px; color: #63735b; }
@media (prefers-reduced-motion: no-preference) {
  .book-leaf { animation: book-open 4.6s cubic-bezier(.36, 0, .24, 1) both; }
  .book-stage { transition: transform .6s ease; }
  .knowledge-orbit:hover .book-stage { transform: rotate(4deg) translateY(-3px); }
  .tag-source { animation: tag-arrive 1s ease-out both; }
  @keyframes book-open {
    0%, 18% { transform: rotateY(0deg); }
    55% { transform: rotateY(-85deg); }
    82% { transform: rotateY(-162deg); }
    100% { transform: rotateY(-154deg); }
  }
  @keyframes tag-arrive { from { opacity: 0; transform: translateY(12px) rotate(-6deg); } to { opacity: 1; transform: translateY(0) rotate(-6deg); } }
}
</style>
