# 山水入卷：资源与项目结合

检索及核对日期：2026-10-27。

在现有「封面 → 翻书 → 登录 → 章节扉页 → 知识工作区」上增加中国山水的视觉线索。保留现有业务文案、功能、书本转场与书签导航。山水集中在封面和扉页，表单、检索结果、正文不叠加画作。

## 已采用

- **《风雨图》 / Wind and Water: Landscape in the Style of Mi Fu**，明／清，佚名，旧传方从义；纸本水墨。不可直接署名为米芾或方从义。
- 藏品编号：13.220.107；The Metropolitan Museum of Art。
- 馆方页面：https://www.metmuseum.org/art/collection/search/51508
- 下载地址：https://collectionapi.metmuseum.org/api/collection/v1/iiif/51508/164205/main-image
- 本地：`vue/public/art/wind-and-water.jpg`（约 102 KiB）。图片原文件保留，页面以 CSS 裁切、降低饱和度、提高亮度；图片替代文本注明为局部，图注提供馆方链接。
- 封面：画作淡印在书衣上，保留书名及开卷交互；朱砂「知」印连接知识主题。
- 登录／章节：江流、远山与书斋对应知识的汇集、探索与沉淀；竖排题签「山水入卷，知有来处」。装饰不新增动画，沿用现有翻书节奏。

## 备选资源

| 作品 | 馆方来源 | 适用位置 |
| --- | --- | --- |
| 《烟岚秋晓图》 / Autumn Landscape，佚名，明／清，旧传范宽 | https://www.metmuseum.org/art/collection/search/51417 | 书本背后的全屏山河底板；本地 `vue/public/art/autumn-landscape.jpg`，通过 CSS 灰度与纸色蒙层呈现淡墨效果 |
| 王蒙《素庵图》，约 1370 | https://www.metmuseum.org/art/collection/search/39550 | 书斋主题、专题封面；当前仅作研究参考，未下载 |

《烟岚秋晓图》下载：https://collectionapi.metmuseum.org/api/collection/v1/iiif/51417/183221/main-image （馆方提供的局部图片，非整幅长卷）。

山河底板为 `App.vue` 中独立的固定装饰层，贯穿封面、登录和知识工作区，位于书本下方，不接收点击，也不进入无障碍阅读顺序。随视口裁切，不增加动画；纸面不透明度为 85%，文字与按钮保持完全不透明。

## 第二版：青绿山河长卷

用户反馈第一版底板过于单调后，背景改为内置 image_gen 生成的横向山河长卷：`vue/public/art/shanshui-panorama-v2.png`。该图为 AI 生成插画，并非馆藏原作；前述馆藏图仍用于封面与扉页。《烟岚秋晓图》回归备选，不再用于背景。

保留青绿矿物色、浓淡墨层次与少量秋色；中央江面留白托住书本，两侧山峰、松石、亭阁和小舟构成远近景。取消统一灰度和大面积白色蒙层。手机裁切偏向右侧山峰。背景本地托管，没有附加动画或网络依赖。

生成方式：内置 image_gen；不使用 CLI。最终提示词如下：

> Create a wide 16:9 Chinese shanshui ink-and-mineral-color landscape painting as a full viewport website background behind a centered antique book. This is a landscape ART ASSET, not a website screenshot. Delicate traditional brushwork on warm ivory xuan paper, exquisite contemporary interpretation of Song dynasty landscape. Spatial depth: dark expressive pine branches and textured rock in lower left corner, blue-green and muted jade mountains rising at far left and far right, multiple pale misty mountain ridges receding into the distance. A winding broad river opens through the middle, with a very small wooden boat at lower right, a tiny warm ochre pavilion on the right riverbank, restrained rusty orange autumn foliage as accents. Keep the central 35 percent spacious pale mist and river so a book placed on top has quiet contrast. Stronger detailed dark ink at lateral edges and lower corners, soft luminous distance, varied values and intentional asymmetry. Refined, serene, rich in details but not busy. Full bleed, no frame, no mounting borders, no text, no calligraphy, no seals, no book, no UI. Subtle real paper texture, crisp detailed brush strokes, never a uniform grey wash.

## 授权与维护

以上藏品页均标为 Public Domain。The Met 的 Open Access 政策将公版藏品图像以 CC0 提供，支持下载、分享和改编：https://www.metmuseum.org/hubs/open-access 。保留出处便于审计与溯源；不表示博物馆认可本项目。

页面素材本地托管，不依赖外站图片热链。图注外链使用 `noopener noreferrer`。手机端沿用现有紧凑布局；知识工作区的章节插画在小屏隐藏，登录布局以原有断点为准。新增样式独立放在 `ink.css`，避免混入已有书页转场改动。
