'use strict'
const key=new URL(location.href).searchParams.get('key')||''
const url=(path)=>path+(path.includes('?')?'&':'?')+'key='+encodeURIComponent(key)
const byId=id=>document.getElementById(id)
let cursor=0,next=0,history=[]
byId('archive').href=url('/api/archive.zip');byId('api').href=url('/api/photos')
function picture(photo,thumbnail){
  const turn=photo.rotationClockwise??90,w=photo.width||240,h=photo.height||320,rotated=Math.abs(turn%180)===90
  const ratio=rotated?h/w:w/h
  const frame=document.createElement('div');frame.className='picture';frame.dataset.ratio=String(ratio);frame.style.paddingTop=(100/ratio)+'%'
  const img=document.createElement('img');img.src=thumbnail?photo.thumbnailUrl:photo.url;img.alt='观察照片';img.loading=thumbnail?'lazy':'eager'
  img.style.width=(rotated?100/ratio:100)+'%';img.style.height=(rotated?ratio*100:100)+'%';img.style.transform='translate(-50%,-50%) rotate('+turn+'deg)'
  frame.append(img);return frame
}
function open(photo){
  byId('photo-title').textContent=new Date(photo.capturedAt).toLocaleString()+' · '+photo.width+'×'+photo.height+' · 相机 '+(photo.cameraId||'2')
  byId('photo-area').classList.remove('zoom');byId('zoom').textContent='放大 2×'
  byId('photo-area').replaceChildren(picture(photo,false));byId('download').href=photo.downloadUrl
  byId('viewer').showModal()
}
async function refresh(){
  byId('error').textContent=''
  try{
    const response=await fetch('/api/photos?before='+cursor,{headers:{Authorization:'Bearer '+key},cache:'no-store'})
    if(!response.ok)throw Error(response.status===401?'访问密钥无效，请从设备重新复制网址。':'设备暂时无法读取照片。')
    const data=await response.json();next=data.nextBefore||0
    byId('summary').textContent='照片 '+data.total+' 张'
    byId('grid').replaceChildren()
    for(const photo of data.items){
      const card=document.createElement('article');card.className='card'
      const button=document.createElement('button');button.className='open';button.ariaLabel='查看照片大图';button.append(picture(photo,true));button.addEventListener('click',()=>open(photo))
      const caption=document.createElement('div');caption.className='caption'
      const date=document.createElement('span');date.textContent=new Date(photo.capturedAt).toLocaleString()
      const download=document.createElement('a');download.href=photo.downloadUrl;download.textContent='下载'
      caption.append(date,download);card.append(button,caption);byId('grid').append(card)
    }
    byId('empty').hidden=data.items.length>0;byId('newer').disabled=!history.length;byId('older').disabled=!next
  }catch(e){byId('error').textContent=e.message}
}
byId('refresh').addEventListener('click',refresh)
byId('older').addEventListener('click',()=>{history.push(cursor);cursor=next;refresh()})
byId('newer').addEventListener('click',()=>{cursor=history.pop()||0;refresh()})
byId('close').addEventListener('click',()=>byId('viewer').close())
byId('zoom').addEventListener('click',()=>{const enlarged=byId('photo-area').classList.toggle('zoom');byId('zoom').textContent=enlarged?'适合屏幕':'放大 2×';const frame=byId('photo-area').firstElementChild;frame.style.paddingTop=(100/Number(frame.dataset.ratio)*(enlarged?2:1))+'%'})
refresh()
let streaming=false,streamTimer=null,previousCapture='',capturePending=false
const cameraRequest=async(action)=>{
 const r=await fetch('/api/camera/'+action,{method:'POST',headers:{Authorization:'Bearer '+key},cache:'no-store'})
 if(!r.ok)throw Error(r.status===409?'请在设备开启相机和保存观察。':'相机操作失败，请刷新重试。')
}
function showStream(){
 byId('live-area').hidden=false;byId('live-image').src=url('/api/camera/stream')+'&t='+Date.now()
 clearInterval(streamTimer);streamTimer=setInterval(()=>{if(streaming)byId('live-image').src=url('/api/camera/stream')+'&t='+Date.now()},60000)
}
async function cameraStatus(){
 try{
  const r=await fetch('/api/camera',{headers:{Authorization:'Bearer '+key},cache:'no-store'});if(!r.ok)return
  const s=await r.json();byId('camera-status').textContent='相机 '+(s.cameraId||'—')+' · '+(s.status||'请在设备开启相机')
  byId('start-stream').disabled=!s.available||streaming;byId('capture').disabled=!s.available||!s.saving||capturePending;byId('stop-stream').disabled=!streaming
  if(streaming&&!s.live){streaming=false;clearInterval(streamTimer);byId('live-image').removeAttribute('src');byId('live-area').hidden=true}
  if(capturePending&&s.lastCaptureId&&s.lastCaptureId!==previousCapture){capturePending=false;byId('capture-status').textContent='已保存到图库';cursor=0;history=[];refresh()}
 }catch{}
}
byId('start-stream').addEventListener('click',async()=>{
 try{await cameraRequest('start');await new Promise(r=>setTimeout(r,300));streaming=true;if(byId("show-detections").checked)await cameraRequest("detection/on");showStream();cameraStatus()}catch(e){byId('capture-status').textContent=e.message}
})
async function stopStream(){streaming=false;clearInterval(streamTimer);byId('live-image').removeAttribute('src');byId('live-area').hidden=true;try{await cameraRequest('stop')}catch{}cameraStatus()}
byId('stop-stream').addEventListener('click',stopStream)
byId('capture').addEventListener('click',async()=>{
 try{
  const s=await(await fetch('/api/camera',{headers:{Authorization:'Bearer '+key}})).json();previousCapture=s.lastCaptureId||''
  await cameraRequest('capture');capturePending=true;byId('capture-status').textContent='正在拍摄…';cameraStatus()
  setTimeout(()=>{if(capturePending){capturePending=false;byId('capture-status').textContent='未收到照片，请重试。';cameraStatus()}},12000)
 }catch(e){byId('capture-status').textContent=e.message}
})
document.addEventListener('visibilitychange',()=>{if(document.hidden&&streaming)stopStream()})
window.addEventListener('pagehide',()=>{if(streaming)fetch('/api/camera/stop',{method:'POST',headers:{Authorization:'Bearer '+key},keepalive:true}).catch(()=>{})})
cameraStatus();setInterval(cameraStatus,2000)

byId('show-detections').addEventListener('change',async()=>{if(!streaming)return;try{await cameraRequest(byId('show-detections').checked?'detection/on':'detection/off')}catch(e){byId('capture-status').textContent=e.message;byId('show-detections').checked=false}})
let presenceData=null,selectedDay=''
const presenceDuration=ms=>{const n=Math.floor(ms/60000);return n>=60?Math.floor(n/60)+'小时 '+n%60+'分':ms>0&&n===0?'不到1分钟':n+'分钟'}
const compactDuration=ms=>{const n=Math.floor(ms/60000);return n>=60?Math.floor(n/60)+'h'+n%60+'m':n+'m'}
function presenceClock(at){if(!at)return '—';try{return new Intl.DateTimeFormat('zh-CN',{timeZone:presenceData.timezone,hour:'2-digit',minute:'2-digit',hour12:false}).format(at)}catch{return new Date(at).toLocaleTimeString()}}
function drawPresence(){
 if(!presenceData?.days?.length)return
 const days=presenceData.days,today=days.at(-1),day=days.find(d=>d.date===selectedDay)||today
 byId('presence-status').textContent=presenceData.status+(presenceData.ready?'':' · 请在设备校准座位')
 byId('presence-total').textContent=(day.date===today.date?'今日':day.date)+'在位 '+presenceDuration(day.occupiedMs)
 byId('presence-first').textContent='首次在位 '+presenceClock(day.firstOccupiedAt)
 byId('presence-track').replaceChildren()
 for(const part of day.intervals){const span=document.createElement('span');span.className=part.state;span.style.left=Math.max(0,(part.start-day.start)/(day.end-day.start))*100+'%';span.style.width=Math.max(0,(part.end-part.start)/(day.end-day.start))*100+'%';span.title=presenceClock(part.start)+'–'+presenceClock(part.end)+' '+(part.state==='occupied'?'有人':part.state==='empty'?'无人':'待确认');byId('presence-track').append(span)}
 byId('presence-days').replaceChildren();const max=Math.max(3600000,...days.map(d=>d.occupiedMs))
 for(const d of days){const button=document.createElement('button');button.setAttribute('aria-pressed',String(d.date===day.date));button.title=d.date+' '+presenceDuration(d.occupiedMs);const value=document.createElement('small');value.textContent=compactDuration(d.occupiedMs);const bar=document.createElement('div'),fill=document.createElement('i');fill.style.height=Math.max(d.occupiedMs>0?3:0,d.occupiedMs/max*100)+'%';bar.append(fill);const date=document.createElement('span');date.textContent=d.date.slice(5);button.append(value,bar,date);button.addEventListener('click',()=>{selectedDay=d.date;drawPresence()});byId('presence-days').append(button)}
}
async function refreshPresence(){try{const r=await fetch('/api/presence?days=7',{headers:{Authorization:'Bearer '+key},cache:'no-store'});if(!r.ok)throw Error();presenceData=await r.json();drawPresence()}catch{byId('presence-status').textContent='工位记录暂不可用'}}
async function refreshDetections(){if(!streaming||!byId('show-detections').checked){byId('detection-summary').textContent='';return}try{const r=await fetch('/api/detections',{headers:{Authorization:'Bearer '+key},cache:'no-store'});if(!r.ok)return;const d=await r.json();byId('detection-summary').textContent=d.enabled?(d.boxes.length+'个目标'):'检测准备中'}catch{}}
refreshPresence();setInterval(refreshPresence,10000);setInterval(refreshDetections,1000)
