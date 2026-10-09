// Windows-side ADB + WebView inspection. No dependencies beyond Node 22+.
import { execFileSync } from 'node:child_process'
import { writeFileSync, mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..')
const adb=process.env.ADB||'adb'
const devices=execFileSync(adb,['devices'],{encoding:'utf8'}).split(/\r?\n/).filter(x=>/\tdevice$/.test(x)).map(x=>x.split('\t')[0])
const serial=process.env.N5D_SERIAL||(devices.length===1?devices[0]:null)
if(!serial)throw Error('Set N5D_SERIAL when zero or multiple devices are connected')
const run=(...args)=>execFileSync(adb,['-s',serial,...args],{encoding:'utf8',timeout:15000}).trim()
const pid=run('shell','pidof','com.cyberyichen.bloub')
run('forward','tcp:19222',`localabstract:webview_devtools_remote_${pid}`)
let pages
for(let attempt=0;attempt<8;attempt++){
  try{pages=await fetch('http://127.0.0.1:19222/json/list').then(r=>r.json());if(pages.length)break}catch{}
  await new Promise(r=>setTimeout(r,500))
}
if(!pages?.length)throw Error('WebView debugging endpoint is not ready')
const page=pages.find(p=>p.url.startsWith('https://appassets.androidplatform.net/'))
if(!page)throw Error('Bloub WebView is not running')
const ws=new WebSocket(page.webSocketDebuggerUrl)
await new Promise((resolve,reject)=>{ws.addEventListener('open',resolve,{once:true});ws.addEventListener('error',reject,{once:true})})
let count=0
const pending=new Map()
ws.addEventListener('message',({data})=>{const m=JSON.parse(String(data)),p=pending.get(m.id);if(p){pending.delete(m.id);m.error?p.reject(Error(JSON.stringify(m.error))):p.resolve(m.result)}})
const send=(method,params={})=>new Promise((resolve,reject)=>{const id=++count;pending.set(id,{resolve,reject});ws.send(JSON.stringify({id,method,params}));setTimeout(()=>{if(pending.delete(id))reject(Error(`Timed out: ${method}`))},10000).unref()})
const evaluate=async expression=>{const r=await send('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});if(r.exceptionDetails)throw Error(JSON.stringify(r.exceptionDetails));return r.result.value}
const sleep=ms=>new Promise(r=>setTimeout(r,ms))
const output=path.join(root,'n5d-reports');mkdirSync(output,{recursive:true})
const ring=()=>{const s=JSON.parse(run('shell','cat','/sdcard/Android/data/com.codex.ringlab/files/effects-state.json'));return {mode:s.mode,external:s.api.external_control,owner:s.api.owner,frames:s.frames,brightness:s.frame_fade,settings:s.settings,channels:s.channels}}
const capture=async name=>{run('shell','screencap','-p','/sdcard/bloub-inspect.png');run('pull','/sdcard/bloub-inspect.png',path.join(output,`${name}.png`))}
try{
  const command=process.argv[2]||'snapshot'
  for(let attempt=0;attempt<20;attempt++){
    if(await evaluate('Boolean(window.companion||window.n5dRig)'))break
    await sleep(150)
  }
  if(command==='v03'){
    const evidence={ui:[],ring:[]}
    if(!await evaluate("Boolean(document.querySelector('.controls'))"))await evaluate("window.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape'}))")
    await sleep(300)
    for(const [i,name] of ['companion','senses','about'].entries()){
      await evaluate("document.querySelectorAll('.dock-rail nav button')["+i+"].click()")
      await sleep(250);await capture('ui-'+name)
      const metrics=await evaluate("({body:document.querySelector('.dock-body').clientHeight,content:document.querySelector('.dock-body').scrollHeight,text:document.querySelector('h1').textContent})")
      if(metrics.content>metrics.body+2)throw Error('Clipped settings page: '+name)
      if(ring().external)throw Error('Settings owns the light ring')
      evidence.ui.push({name,...metrics})
    }
    await evaluate("document.querySelector('.close').click();window.n5dDebug.seek(155);window.n5dDebug.resume()")
    await sleep(3500)
    const groups=[2,21,17,13,9,5,1,4,8,12,16,20,0,23,19,15,11,7,3,22,18,14,10,6]
    const whites=[78,77,89,76,88,75,87,84,72,73,85,74,86,83,95,82,81,94,93,80,92,91,79,90]
    for(const t of [156,158.5,161,166,171,175.9]){
      await evaluate("window.n5dDebug.seek("+t+")");await sleep(900)
      const s=await evaluate('window.n5dDebug.snapshot()'),hardware=ring()
      if(!hardware.external)throw Error('Missing physical ring ownership')
      const radius=32.8/.0942
      const nearest=white=>Array.from({length:24},(_,i)=>{
        const a=(i+(white?.5:0))*Math.PI/12
        return {i,d:Math.hypot(2150+radius*Math.sin(a)-s.x,360-radius*Math.cos(a)-s.y)}
      }).sort((a,b)=>a.d-b.d)[0].i
      const rgbIndex=nearest(false),whiteIndex=nearest(true),g=groups[rgbIndex]*3
      if(hardware.channels.slice(g,g+3).some(v=>v!==0)||hardware.channels[whites[whiteIndex]]!==0)throw Error('Black body was not dark in both LED banks')
      if(whites.filter(i=>hardware.channels[i]===9).length<18)throw Error('Surrounding ring was not illuminated')
      evidence.ring.push({t,rgbIndex,whiteIndex,scene:s,hardware});console.log(JSON.stringify({t,rgbIndex,whiteIndex,owner:hardware.owner}))
    }
    await evaluate("window.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape'}))");await sleep(900)
    if(ring().external)throw Error('Opening settings did not release ownership')
    const paused=await evaluate('window.n5dDebug.snapshot().clock');await sleep(300)
    if(await evaluate('window.n5dDebug.snapshot().clock')!==paused)throw Error('Settings did not pause the story')
    await evaluate("document.querySelector('.close').click();window.n5dDebug.seek(191)")
    await sleep(900);if(ring().external)throw Error('Trip did not restore local lights')
    writeFileSync(path.join(output,'v03-evidence.json'),JSON.stringify(evidence,null,2))
  }else if(command==='music-demo'){
    await evaluate(`window.n5dDebug.seek(126);window.n5dDebug.resume();
      window.__realSensors=window.n5dSensors;window.n5dSensors=()=>{};
      window.__demoTimer=setInterval(()=>window.__realSensors({level:.35,music:.95,speech:0,distance:-1,motion:0,faces:0,faceX:0,faceY:0,micStatus:'音乐画面诊断',cameraStatus:'关闭',tofStatus:'关闭',cameraOn:false,cameraFrames:0}),100);
      setTimeout(()=>{clearInterval(window.__demoTimer);window.n5dSensors=window.__realSensors},7000)`);
    await sleep(3300);await capture('music-demo');
    const a=await evaluate('({snapshot:window.n5dDebug.snapshot(),transform:document.querySelector(".companion-position").style.transform})');
    await sleep(400);const b=await evaluate('document.querySelector(".companion-position").style.transform');
    if(a.transform===b)throw Error('Music motion did not advance');
    writeFileSync(path.join(output,'music-demo-evidence.json'),JSON.stringify({kind:'synthetic diagnostic input, not live song recognition',a,b},null,2));
    await sleep(3300);
  }else if(command==='eval'){console.log(JSON.stringify(await evaluate(process.argv[3]),null,2))}
  else if(command==='story'){
    const evidence=[]
    for(const [name,t] of [['paper-idle',5],['buddy',40],['inside',54],['peek',61],['bubbles',89],['grown',102],['ring-leaving',151],['ring-running',161],['ring-returning',181],['released',191],['echo-out',221.2],['echo-back',233.8],['echo-released',243],['comet',263],['orbit',270],['nap',318]]){
      await evaluate(`window.n5dDebug.seek(${t});window.n5dDebug.resume()`);await sleep(1300)
      const state=await evaluate('window.n5dDebug.snapshot()'),hardware=ring()
      await capture(name);evidence.push({name,scene:state,ring:hardware});console.log(JSON.stringify({name,state,ring:hardware}))
      if(state.lightActive&&!hardware.external)throw Error(`Missing ownership at ${name}`)
      if(!state.lightActive&&hardware.external)throw Error('Light session was not released')
    }
    writeFileSync(path.join(output,'story-evidence.json'),JSON.stringify(evidence,null,2))
  }else if(command==='rig'){
    const evidence=[]
    for(const [name,t] of [['rig-buddy',41],['rig-bubbles',90],['rig-ring',162],['rig-wave',222.4]]){
      await evaluate(`window.n5dRig.seek(${t})`);await sleep(1100);await capture(name)
      evidence.push({name,...await evaluate('window.n5dRig.snapshot()')})
    }
    writeFileSync(path.join(output,'rig-evidence.json'),JSON.stringify(evidence,null,2))
  }else if(command==='themes'){
    await evaluate("window.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape'}))")
    const evidence=[]
    for(const name of ['深海薄荷','暖沙棕色','黑底银白','白底黑色','自动轮换']){
      await evaluate(`Array.from(document.querySelectorAll('.theme-row button')).find(x=>x.textContent.includes(${JSON.stringify(name)})).click();window.n5dDebug.resume()`)
      await sleep(3400)
      const colors=await evaluate("({background:getComputedStyle(document.querySelector('.stage')).backgroundColor,body:document.querySelector('g[mask],path[mask]').getAttribute('fill'),selected:document.querySelector('.theme-row [aria-pressed=true]').textContent.trim()})")
      evidence.push({name,...colors});console.log(JSON.stringify({name,...colors}))
    }
    await capture('settings');writeFileSync(path.join(output,'theme-evidence.json'),JSON.stringify(evidence,null,2))
    await evaluate("Array.from(document.querySelectorAll('.theme-row button')).find(x=>x.textContent.includes('白底黑色')).click();document.querySelector('.close').click()")
  }else{
    console.log(JSON.stringify({page,scene:await evaluate('window.n5dDebug?.snapshot()'),ring:ring()},null,2));await capture('current')
  }
}finally{ws.close()}
