async page => {
  const ensure = (ok, message) => { if (!ok) throw new Error(message); };
  await page.unrouteAll();
  await page.evaluate(()=>['ai_kh_token','ai_kh_user','ai_kh_reading_preferences'].forEach(key=>localStorage.removeItem(key)));
  await page.goto('http://127.0.0.1:5173/login');
  await page.setViewportSize({width:1440,height:900});
  await page.screenshot({path:'output/reading-cover-desktop.png'});
  await page.getByRole('button',{name:'打开书本，进入登录界面'}).click();
  await page.locator('.book-cover-scene').waitFor({state:'detached'});
  await page.screenshot({path:'output/reading-login-desktop.png'});
  const login = await page.evaluate(() => ({opacity:getComputedStyle(document.querySelector('.login-book')).opacity,paper:getComputedStyle(document.querySelector('.login-panel'),'::before').backgroundImage}));
  ensure(login.opacity==='1' && login.paper.includes('0.85'),'Login opacity is wrong');
  await page.route('http://127.0.0.1:5173/api/**', route => {
    const path=new URL(route.request().url()).pathname;
    const fixtures={
      '/api/system/status':{llmMode:'LOCAL_DEMO',embeddingMode:'LOCAL_DEMO',vectorStore:'LOCAL',redisEnabled:false},
      '/api/notifications/unread-count':{count:0}, '/api/knowledge-bases':[],
      '/api/dashboard/overview':{knowledgeBaseCount:0,documentCount:0,chatCount:0,aiCallCount:0,failedDocumentCount:0},
      '/api/dashboard/recent':{sessions:[],documents:[],failedDocuments:[],dueReviewCount:0}
    };
    return route.fulfill({status:path in fixtures?200:503,contentType:'application/json',body:JSON.stringify(fixtures[path] ?? {message:'视觉测试未模拟此接口'})});
  });
  await page.evaluate(()=>{localStorage.setItem('ai_kh_token','visual-check');localStorage.setItem('ai_kh_user',JSON.stringify({id:0,username:'界面验证',role:'USER'}));});
  await page.goto('http://127.0.0.1:5173/dashboard');
  await page.getByRole('group',{name:'阅读设置'}).waitFor();
  await page.screenshot({path:'output/reading-normal-desktop.png'});
  const normalWidth=await page.locator('.main-area').evaluate(el=>el.getBoundingClientRect().width);
  await page.getByLabel('翻页速度').selectOption('quick');
  await page.getByRole('button',{name:'专注阅读',exact:true}).click();
  const focusedWidth=await page.locator('.main-area').evaluate(el=>el.getBoundingClientRect().width);
  ensure(focusedWidth>normalWidth*1.5,'Focus mode did not enlarge the reading area');
  await page.screenshot({path:'output/reading-focus-desktop.png'});
  await page.reload();
  ensure(await page.getByLabel('翻页速度').inputValue()==='quick','Speed was not persisted');
  ensure(await page.getByRole('button',{name:'退出专注'}).getAttribute('aria-pressed')==='true','Focus was not persisted');
  await page.getByRole('button',{name:'打开导航',exact:true}).click();
  await page.getByRole('dialog',{name:'主导航面板'}).waitFor();
  await page.keyboard.press('Escape');
  ensure(await page.getByRole('button',{name:'打开导航',exact:true}).evaluate(el=>el===document.activeElement),'Navigation did not restore focus');
  await page.evaluate(()=>{window.readingDurations=[];const native=Element.prototype.animate;Element.prototype.animate=function(frames,options){window.readingDurations.push(options.duration);return native.call(this,frames,options);};});
  await page.getByRole('button',{name:'打开导航',exact:true}).click();
  await page.locator('.sidebar a[href="/knowledge"]').click();
  await page.waitForURL('**/knowledge');
  await page.locator('.book-transition-overlay').waitFor({state:'detached'});
  const durations=await page.evaluate(()=>window.readingDurations);
  ensure(durations.includes(600) && Math.max(...durations)===600,'Quick turn did not use 600ms');
  await page.getByLabel('翻页速度').selectOption('off');
  await page.evaluate(()=>window.readingDurations=[]);
  await page.getByRole('button',{name:'打开导航',exact:true}).click();
  await page.locator('.sidebar nav a[href="/dashboard"]').click();
  await page.waitForURL('**/dashboard');
  ensure((await page.evaluate(()=>window.readingDurations)).length===0,'Off mode still animated');
  await page.getByRole('button',{name:'退出专注'}).click();
  await page.setViewportSize({width:390,height:844});
  await page.screenshot({path:'output/reading-normal-mobile.png'});
  ensure(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Normal mobile layout overflows');
  await page.getByRole('button',{name:'专注阅读',exact:true}).click();
  await page.screenshot({path:'output/reading-focus-mobile.png'});
  ensure(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Focus mobile layout overflows');
  await page.getByRole('button',{name:'打开导航',exact:true}).click();
  await page.screenshot({path:'output/reading-navigation-mobile.png'});
  await page.getByRole('button',{name:'关闭导航',exact:true}).click();
  return {login,normalWidth,focusedWidth,quickDurations:durations,persistence:true,disabledAnimation:true,mobileOverflow:false};
}
