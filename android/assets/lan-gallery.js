'use strict'
let key=new URL(location.href).searchParams.get('key')||''
const url=(path)=>path+(path.includes('?')?'&':'?')+'key='+encodeURIComponent(key)
const byId=id=>document.getElementById(id)
let page=1,totalPages=1,pageSize=24,selectedPhoto=null;try{pageSize=Number(localStorage.getItem('bloub.gallery.pageSize'))||24}catch{}
if(![12,24,48,96].includes(pageSize))pageSize=24;byId('page-size').value=String(pageSize);let refreshGeneration=0,refreshAbort=null
let monitorPreviewWanted=false,monitorPreviewVersion=0,monitorPreviewQueue=Promise.resolve(),monitorPreviewTimer=null,monitorPreviewDebounce=null,monitorPreviewLoadTimer=null,monitorEntered=false;
let filterQuery=""
function updateExport(){byId('archive').href=url('/api/archive.zip'+(filterQuery?'?'+filterQuery:''));byId('api').href=url('/api/photos'+(filterQuery?'?'+filterQuery:''))}updateExport()
function picture(photo,thumbnail){
  const turn=photo.rotationClockwise??90,w=photo.width||240,h=photo.height||320,rotated=Math.abs(turn%180)===90
  const ratio=rotated?h/w:w/h
  const frame=document.createElement('div');frame.className='picture';frame.dataset.ratio=String(ratio);frame.style.paddingTop=(100/ratio)+'%'
  const img=document.createElement('img');img.src=thumbnail?photo.thumbnailUrl:photo.url;img.alt='观察照片';img.loading=thumbnail?'lazy':'eager';img.decoding='async';img.addEventListener('load',()=>frame.classList.add('loaded'),{once:true});img.addEventListener('error',()=>{frame.classList.add('failed');img.alt='图片未能加载'},{once:true})
  img.style.width=(rotated?100/ratio:100)+'%';img.style.height=(rotated?ratio*100:100)+'%';img.style.transform='translate(-50%,-50%) rotate('+turn+'deg)'
  frame.append(img);return frame
}
function open(photo){selectedPhoto=photo;
  byId('photo-title').textContent=new Date(photo.capturedAt).toLocaleString()+' · '+photo.width+'×'+photo.height+' · 相机 '+(photo.cameraId||'2')+' · '+stateName(photo.seatState)
  byId('photo-area').classList.remove('zoom');byId('zoom').textContent='放大 2×'
  byId('photo-area').replaceChildren(picture(photo,false));byId('download').href=photo.downloadUrl;byId('download-metadata').href=(photo.metadataUrl||url('/api/photos/'+photo.id+'/metadata'))+'&download=1'
  byId('viewer').showModal()
}
async function refresh(){
  const generation=++refreshGeneration;refreshAbort?.abort();refreshAbort=new AbortController();byId('grid').setAttribute('aria-busy','true');byId('error').textContent=''
  try{
    const response=await fetch('/api/photos?page='+page+'&pageSize='+pageSize+(filterQuery?'&'+filterQuery:''),{headers:{Authorization:'Bearer '+key},cache:'no-store',signal:refreshAbort.signal})
    if(!response.ok)throw Error(response.status===401?'访问码已失效，请重新登录。':'设备暂时无法读取照片。')
    const data=await response.json();if(generation!==refreshGeneration)return;page=data.page;totalPages=data.totalPages;byId('page-count').textContent='第 '+page+' / '+totalPages+' 页 · 共 '+data.total+' 张';byId('page-number').value=String(page);byId('page-number').max=String(totalPages)
    if(activePage==='gallery')byId('summary').textContent=''
    const fragment=document.createDocumentFragment()
    for(const photo of data.items){
      const card=document.createElement('article');card.className='card'
      const button=document.createElement('button');button.className='open';button.ariaLabel='查看照片大图';button.append(picture(photo,true));button.addEventListener('click',()=>open(photo))
      const caption=document.createElement('div');caption.className='caption'
      const date=document.createElement('span');date.textContent=new Date(photo.capturedAt).toLocaleString()
      const download=document.createElement('a');download.href=photo.downloadUrl;download.textContent='下载'
      const tag=document.createElement("span");tag.className="tag "+(photo.seatState||"unknown");tag.textContent=stateName(photo.seatState);caption.append(date,tag,download);card.append(button,caption);fragment.append(card)
    }
    byId('grid').replaceChildren(fragment);byId('grid').setAttribute('aria-busy','false');
    byId('empty').hidden=data.items.length>0;byId('empty').textContent=filterQuery?'这个范围没有符合条件的照片':'还没有观察照片，请开启相机和保存照片。';byId('newer').disabled=page<=1;byId('older').disabled=page>=totalPages
  }catch(e){if(generation!==refreshGeneration||e.name==='AbortError')return;byId('grid').setAttribute('aria-busy','false');byId('error').textContent=e.message}
}
byId('refresh').addEventListener('click',()=>{if(activePage==='gallery')refresh();else if(activePage==='presence')refreshPresence();else if(activePage==='live')cameraStatus();else if(activePage==='settings')loadSettings();else loadMonitor()})
byId('older').addEventListener('click',()=>{page=Math.min(totalPages,page+1);refresh()})
byId('newer').addEventListener('click',()=>{page=Math.max(1,page-1);refresh()})
byId('close').addEventListener('click',()=>byId('viewer').close())
byId('zoom').addEventListener('click',()=>{const enlarged=byId('photo-area').classList.toggle('zoom');byId('zoom').textContent=enlarged?'适合屏幕':'放大 2×';const frame=byId('photo-area').firstElementChild;frame.style.paddingTop=(100/Number(frame.dataset.ratio)*(enlarged?2:1))+'%'})
let region=[.15,.08,.85,.95],regionOrigin=null,previewTimer=null
let streaming=false,streamTimer=null,previousCapture='',capturePending=false,cameraVersion=0
let cameraOperations=Promise.resolve();
const cameraRequest=action=>{const operation=cameraOperations.then(async()=>{
 const r=await fetch('/api/camera/'+action,{method:'POST',headers:{Authorization:'Bearer '+key},cache:'no-store'});
 if(!r.ok)throw Error(r.status===409?(action==='capture'?'先开启照片保存':'请在设置中开启相机'):'相机操作失败，请刷新重试');
});cameraOperations=operation.catch(()=>{});return operation;};
async function applyLiveLayers(){for(const [control,action] of [['show-detections','detection/on'],['show-faces','landmarks/face/on'],['show-hands','landmarks/hand/on'],['show-holistic','landmarks/holistic/on'],['show-yamnet','yamnet/on']])if(byId(control).checked){try{await cameraRequest(action)}catch(e){byId(control).checked=false;byId('capture-status').textContent=control==='show-yamnet'?'在设置中开启麦克风':e.message;}}}

function showStream(){
 byId('live-empty').hidden=true;byId('live-area').hidden=false;byId('live-image').src=url('/api/camera/stream')+'&t='+Date.now()
 clearInterval(streamTimer);streamTimer=setInterval(()=>{if(streaming)byId('live-image').src=url('/api/camera/stream')+'&t='+Date.now()},60000)
}
async function cameraStatus(){
 const version=cameraVersion;try{
  const r=await fetch('/api/camera',{headers:{Authorization:'Bearer '+key},cache:'no-store'});if(!r.ok)return
  const s=await r.json();if(version!==cameraVersion)return;byId('camera-status').textContent='相机 '+(s.cameraId||'—')+' · '+(s.status||'请在设备开启相机')
  byId('start-stream').disabled=!s.available||streaming;byId('capture').disabled=!s.available||!s.saving||capturePending;byId('stop-stream').disabled=!streaming
  if(streaming&&!s.live){streaming=false;clearInterval(streamTimer);byId('live-image').removeAttribute('src');byId('live-area').hidden=true;byId('live-empty').hidden=false}
  if(capturePending&&s.lastCaptureId&&s.lastCaptureId!==previousCapture){capturePending=false;byId('capture-status').textContent='已保存到图库';page=1;refresh()}
 }catch{}
}
byId('start-stream').addEventListener('click',async()=>{
 cameraVersion++;try{await cameraRequest('start');await new Promise(r=>setTimeout(r,300));streaming=true;await applyLiveLayers();showStream();cameraStatus()}catch(e){byId('capture-status').textContent=e.message}
})
async function stopStream(){cameraVersion++;clearInterval(previewTimer);byId('edit-seat').checked=false;byId('roi-editor').toggleAttribute('hidden',true);post('/api/seat/preview',{enabled:false}).catch(()=>{});streaming=false;clearInterval(streamTimer);byId('live-image').removeAttribute('src');byId('live-area').hidden=true;byId('live-empty').hidden=false;try{await cameraRequest('stop')}catch{}cameraStatus()}
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
setInterval(()=>{if(!document.hidden&&activePage==='live')cameraStatus()},2000)

byId('show-detections').addEventListener('change',async()=>{if(!streaming)return;try{await cameraRequest(byId('show-detections').checked?'detection/on':'detection/off')}catch(e){byId('capture-status').textContent=e.message;byId('show-detections').checked=false}})
let presenceData=null,selectedDay='',presenceQuery=''
const presenceDuration=ms=>{const n=Math.floor(ms/60000);return n>=60?Math.floor(n/60)+'小时 '+n%60+'分':ms>0&&n===0?'不到1分钟':n+'分钟'}
const compactDuration=ms=>{const n=Math.floor(ms/60000);return n>=60?Math.floor(n/60)+'h'+n%60+'m':n+'m'}
function presenceClock(at){if(!at)return '—';try{return new Intl.DateTimeFormat('zh-CN',{timeZone:presenceData.timezone,hour:'2-digit',minute:'2-digit',hour12:false}).format(at)}catch{return new Date(at).toLocaleTimeString()}}
function drawPresence(){
 if(!presenceData?.days?.length)return
 const days=presenceData.days,today=days.at(-1),day=days.find(d=>d.date===selectedDay)||today
 byId('presence-status').textContent=presenceData.status;byId('presence-date').textContent=day.date
 byId('presence-total').textContent=(presenceQuery&&!selectedDay?'所选时间':day.date===presenceData.todayDate?'今日':day.date)+'在位 '+presenceDuration(presenceQuery&&!selectedDay?days.reduce((total,d)=>total+d.occupiedMs,0):day.occupiedMs)
 byId('presence-first').textContent=presenceQuery&&!selectedDay?byId('presence-start').value+' 至 '+byId('presence-end').value:'首次在位 '+presenceClock(day.firstOccupiedAt)
 byId('presence-track').replaceChildren()
 for(const part of day.intervals){const span=document.createElement('span');span.className=part.state;span.style.left=Math.max(0,(part.start-day.start)/(day.end-day.start))*100+'%';span.style.width=Math.max(0,(part.end-part.start)/(day.end-day.start))*100+'%';span.title=presenceClock(part.start)+'–'+presenceClock(part.end)+' '+(part.state==='occupied'?'有人':part.state==='empty'?'无人':'待确认');byId('presence-track').append(span)}
 byId('presence-days').replaceChildren();const max=Math.max(3600000,...days.map(d=>d.occupiedMs))
 for(const d of days){const button=document.createElement('button');button.setAttribute('aria-pressed',String(d.date===day.date));button.title=d.date+' '+presenceDuration(d.occupiedMs);const value=document.createElement('small');value.textContent=compactDuration(d.occupiedMs);const bar=document.createElement('div'),fill=document.createElement('i');fill.style.height=Math.max(d.occupiedMs>0?3:0,d.occupiedMs/max*100)+'%';bar.append(fill);const date=document.createElement('span');date.textContent=d.date.slice(5);button.append(value,bar,date);button.addEventListener('click',()=>{selectedDay=d.date;drawPresence()});byId('presence-days').append(button)}
}
async function refreshPresence(){try{const r=await fetch('/api/presence?'+(presenceQuery||'days=7'),{headers:{Authorization:'Bearer '+key},cache:'no-store'});if(!r.ok)throw Error();presenceData=await r.json();drawPresence()}catch{byId('presence-status').textContent='工位记录暂不可用'}}
async function refreshDetections(){if(!streaming||!byId('show-detections').checked){byId('detection-summary').textContent='';return}try{const r=await fetch('/api/detections',{headers:{Authorization:'Bearer '+key},cache:'no-store'});if(!r.ok)return;const d=await r.json();byId('detection-summary').textContent=d.enabled?(d.boxes.length+'个目标'):'检测准备中'}catch{}}
setInterval(()=>{if(!document.hidden&&activePage==='presence')refreshPresence()},10000);setInterval(()=>{if(!document.hidden&&activePage==='live')refreshDetections()},1000)

function stateName(state){return state==='occupied'?'有人':state==='empty'?'无人':'未知'}
async function post(path,body){const r=await fetch(path,{method:'POST',headers:{Authorization:'Bearer '+key,...(body?{'Content-Type':'application/json'}:{})},...(body?{body:JSON.stringify(body)}:{}),cache:'no-store'});const d=await r.json();if(!r.ok)throw Error(d.message||d.error||'操作失败');return d}
function formData(form){const data={};for(const el of form.elements){if(!el.name||el.disabled)continue;data[el.name]=el.type==='checkbox'?el.checked:(el.type==='number'||el.hasAttribute('data-number'))?Number(el.value):el.hasAttribute('data-boolean')?el.value==='true':el.value}return data}
function fillForm(form,data){for(const el of form.elements){if(!el.name||!(el.name in data)||el.type==='password')continue;if(el.type==='checkbox')el.checked=Boolean(data[el.name]);else el.value=String(data[el.name])}}
let activePage='gallery',settingsData=null,monitorData=null;const pageNames={presence:'工位时间',live:'实时观察',gallery:'图库',settings:'设置',monitor:'监控'};
function selectPage(name){if(!pageNames[name])return;if(name!=='monitor'){monitorEntered=false;if(monitorPreviewWanted)stopMonitorPreview();}activePage=name;for(const b of document.querySelectorAll('[data-page]'))b.setAttribute('aria-pressed',String(b.dataset.page===name));for(const key of Object.keys(pageNames))byId(key+'-page').hidden=key!==name;byId('page-title').textContent=pageNames[name];for(const el of document.querySelectorAll('[data-gallery-tool]'))el.hidden=name!=='gallery';for(const el of document.querySelectorAll('[data-settings-tool]'))el.hidden=name!=='settings';for(const el of document.querySelectorAll('[data-monitor-tool]'))el.hidden=name!=='monitor';byId('summary').textContent='';if(name==='settings')loadSettings();else if(name==='monitor')loadMonitor();else if(name==='gallery')refresh();else if(name==='presence')refreshPresence();else cameraStatus();if(name!=='live'&&streaming)stopStream();history.replaceState(null,'','#'+name)}
for(const b of document.querySelectorAll('[data-page]'))b.addEventListener('click',()=>selectPage(b.dataset.page));
byId('page-size').addEventListener('change',()=>{pageSize=Number(byId('page-size').value);try{localStorage.setItem('bloub.gallery.pageSize',pageSize)}catch{}page=1;refresh()});
byId('go-page').addEventListener('click',()=>{page=Math.max(1,Math.min(totalPages,Number(byId('page-number').value)||1));refresh()});
byId('page-number').addEventListener('keydown',e=>{if(e.key==='Enter')byId('go-page').click()});

async function loadSettings(){loadAccessCode();try{const r=await fetch('/api/settings',{headers:{Authorization:'Bearer '+key},cache:'no-store'});settingsData=await r.json();fillForm(byId('settings-form'),settingsData);settingDependencies()}catch{byId('settings-message').textContent='设置读取失败'}}
byId('settings-form').addEventListener('submit',async e=>{e.preventDefault();try{settingsData=await post('/api/settings',formData(e.target));byId('settings-message').textContent='已保存';cameraStatus();refreshPresence()}catch(e){byId('settings-message').textContent=e.message}})
byId('apply-range').addEventListener('click',()=>{const params=new URLSearchParams(),a=byId('range-start').value,b=byId('range-end').value;const start=a?new Date(a).getTime():0,end=b?new Date(b).getTime():Infinity;if(!Number.isFinite(start)||Number.isNaN(end)||end<=start){byId('error').textContent='结束时间需晚于开始时间';return}if(a)params.set('start',start);if(b)params.set('end',end);if(byId('range-state').value!=='all')params.set('state',byId('range-state').value);filterQuery=params.toString();page=1;updateExport();refresh()})
byId('clear-range').addEventListener('click',()=>{byId('range-start').value='';byId('range-end').value='';byId('range-state').value='all';filterQuery='';page=1;updateExport();refresh()})
byId('delete-photo').addEventListener('click',async()=>{if(!selectedPhoto||!confirm('删除这张照片和标签？'))return;try{await post('/api/photos/'+selectedPhoto.id+'/delete');byId('viewer').close();refresh()}catch(e){byId('error').textContent=e.message}})
byId('clear-photos').addEventListener('click',async()=>{if(!confirm('清空全部照片和标签？工位统计不受影响。'))return;try{await post('/api/photos/clear');refresh()}catch(e){byId('settings-message').textContent=e.message}})
async function loadMonitor(fill=true){try{const r=await fetch('/api/monitor',{headers:{Authorization:'Bearer '+key},cache:'no-store'});monitorData=await r.json();byId('monitor-toggle').checked=monitorData.enabled;byId('monitor-status').textContent=monitorData.status+' · '+monitorData.uploadStatus+' · 待上传 '+monitorData.pending+' 段 · '+(monitorData.bytes/1048576).toFixed(1)+' MiB';if(fill){fillMonitor(monitorData.config);}if(activePage==='monitor'&&!monitorEntered){monitorEntered=true;if(monitorData.enabled)startMonitorPreview();}byId('video-list').replaceChildren();byId('video-count').textContent=monitorData.videos.length+' 段';byId('videos-empty').hidden=monitorData.videos.length>0;for(const v of monitorData.videos.slice().reverse()){const row=document.createElement('article');row.className='video-item';const label=document.createElement('span');label.textContent=new Date(v.metadata.startedAt||Number(v.id.slice(0,13))).toLocaleString()+' · '+(v.bytes/1048576).toFixed(1)+' MiB · '+(v.metadata.uploaded?'已上传':'本机保留');const a=document.createElement('a');a.href=url('/api/videos/'+v.id)+'&download=1';a.textContent='下载 MP4';const del=document.createElement('button');del.className='danger';del.textContent='删除';del.addEventListener('click',async()=>{if(confirm('删除这段本机录像？')){await post('/api/videos/'+v.id+'/delete');loadMonitor(false)}});const preview=document.createElement('button');preview.textContent='预览';preview.addEventListener('click',()=>{byId('video-title').textContent=new Date(v.metadata.startedAt||Number(v.id.slice(0,13))).toLocaleString();byId('video-player').src=url('/api/videos/'+v.id);byId('video-viewer').showModal();});row.append(label,preview,a,del);byId('video-list').append(row)}}catch{byId('monitor-status').textContent='监控状态读取失败'}}
byId('monitor-form').addEventListener('submit',async e=>{e.preventDefault();try{const data=monitorDraft();if(data.clearSecret&&data.uploadMode!=='manual')throw Error('清除密钥前先关闭自动上传');await post('/api/monitor/config',data);for(const el of e.target.elements)if(el.type==='password')el.value='';byId('monitor-message').textContent='已保存';await loadMonitor();if(monitorPreviewWanted){await new Promise(r=>setTimeout(r,400));startMonitorPreview();}}catch(e){byId('monitor-message').textContent=e.message}})
byId('monitor-toggle').addEventListener('change',async()=>{try{if(byId('monitor-toggle').checked&&(await(await fetch('/api/settings',{headers:{Authorization:'Bearer '+key}})).json()).gallerySaving&&!confirm('开启监控将暂停照片保存，继续？')){byId('monitor-toggle').checked=false;return;}await post('/api/settings',{monitorEnabled:byId('monitor-toggle').checked,...(byId('monitor-toggle').checked?{camera:true}:{})});await loadMonitor(false);if(byId('monitor-toggle').checked&&!monitorPreviewWanted)startMonitorPreview();else if(monitorPreviewWanted){await new Promise(r=>setTimeout(r,400));startMonitorPreview();}}catch(e){byId('monitor-message').textContent=e.message;loadMonitor(false)}})
byId('retry-upload').addEventListener('click',async()=>{try{if(monitorData?.config.provider!==byId('monitor-form').elements.provider.value)throw Error('先保存上传配置');await post('/api/monitor/retry');byId('monitor-message').textContent='已安排上传'}catch(e){byId('monitor-message').textContent=e.message}})
setInterval(()=>{if(!document.hidden&&activePage==='monitor')loadMonitor(false)},5000)
function drawRegion(){const box=byId('roi-box');for(const [name,value] of Object.entries({x:region[0]*1000,y:region[1]*750,width:(region[2]-region[0])*1000,height:(region[3]-region[1])*750}))box.setAttribute(name,value)}
function roiPoint(e){const b=byId('live-image').getBoundingClientRect();return [Math.max(0,Math.min(1,(e.clientX-b.left)/b.width)),Math.max(0,Math.min(1,(e.clientY-b.top)/b.height))]}
byId('roi-editor').addEventListener('pointerdown',e=>{regionOrigin=roiPoint(e);e.currentTarget.setPointerCapture(e.pointerId)})
byId('roi-editor').addEventListener('pointermove',e=>{if(!regionOrigin)return;const p=roiPoint(e);region=[Math.min(p[0],regionOrigin[0]),Math.min(p[1],regionOrigin[1]),Math.max(p[0],regionOrigin[0]),Math.max(p[1],regionOrigin[1])];drawRegion();byId('save-seat').disabled=false})
for(const name of ['pointerup','pointercancel'])byId('roi-editor').addEventListener(name,()=>regionOrigin=null)
byId('edit-seat').addEventListener('change',async()=>{clearInterval(previewTimer);const on=byId('edit-seat').checked;byId('roi-editor').toggleAttribute('hidden',!on);try{if(on){if(!streaming){cameraVersion++;await cameraRequest('start');await new Promise(r=>setTimeout(r,300));streaming=true;showStream()}byId('roi-editor').toggleAttribute('hidden',false);byId('live-area').hidden=false;byId('live-empty').hidden=true;const r=await(await fetch('/api/presence',{headers:{Authorization:'Bearer '+key}})).json();region=r.roi;drawRegion();await post('/api/seat/preview',{enabled:true});previewTimer=setInterval(()=>post('/api/seat/preview',{enabled:true}).catch(()=>{}),5000)}else await post('/api/seat/preview',{enabled:false})}catch(e){byId('seat-message').textContent=e.message}})
byId('save-seat').addEventListener('click',async()=>{try{if(region[2]-region[0]<.15||region[3]-region[1]<.15)throw Error('框住座位和上半身，区域不能太小');await post('/api/seat/region',{roi:region});byId('save-seat').disabled=true;byId('seat-message').textContent='区域已保存'}catch(e){byId('seat-message').textContent=e.message}})
for(const state of ['empty','occupied'])byId('label-'+state).addEventListener('click',async()=>{try{const d=await post('/api/seat/label',{state});byId('seat-message').textContent=d.message;refreshPresence()}catch(e){byId('seat-message').textContent=e.message}})

const ossRegions={'cn-hangzhou':'杭州','cn-shanghai':'上海','cn-qingdao':'青岛','cn-beijing':'北京','cn-zhangjiakou':'张家口','cn-huhehaote':'呼和浩特','cn-wulanchabu':'乌兰察布','cn-shenzhen':'深圳','cn-heyuan':'河源','cn-guangzhou':'广州','cn-chengdu':'成都','cn-hongkong':'香港','ap-southeast-1':'新加坡','ap-southeast-3':'吉隆坡','ap-southeast-5':'雅加达','ap-northeast-1':'东京','ap-northeast-2':'首尔','us-west-1':'美国西部','us-east-1':'美国东部','eu-central-1':'法兰克福','eu-west-1':'伦敦','eu-west-2':'巴黎'};
const cosRegions={'ap-beijing':'北京','ap-shanghai':'上海','ap-guangzhou':'广州','ap-nanjing':'南京','ap-chengdu':'成都','ap-chongqing':'重庆','ap-hongkong':'香港','ap-singapore':'新加坡','ap-jakarta':'雅加达','ap-seoul':'首尔','ap-bangkok':'曼谷','ap-tokyo':'东京','na-siliconvalley':'硅谷','na-ashburn':'弗吉尼亚','sa-saopaulo':'圣保罗','eu-frankfurt':'法兰克福'};
function storageFields(){const form=byId('monitor-form'),provider=form.elements.provider.value;for(const group of form.querySelectorAll('[data-provider]')){const visible=group.dataset.provider.split(' ').includes(provider);group.hidden=!visible;for(const el of group.querySelectorAll('input,select'))el.disabled=!visible;}
 byId('endpoint-label').textContent=provider==='webdav'?'NAS 地址':'S3 服务地址';form.elements.endpoint.placeholder=provider==='webdav'?'https://nas.example.com:5006/录像':'https://nas.example.com:9000';byId('access-label').textContent=provider==='cos'?'SecretId':'AccessKey ID';byId('secret-label').textContent=provider==='webdav'?'NAS 密码':provider==='cos'?'SecretKey':'AccessKey Secret';byId('clear-secret-label').textContent=provider==='webdav'?'清除已存密码':'清除已存密钥';
 const list=byId('cloud-regions');list.replaceChildren();for(const [value,name] of Object.entries(provider==='oss'?ossRegions:cosRegions)){const option=document.createElement('option');option.value=value;option.label=name;list.append(option)}
 byId('storage-help').textContent=provider==='webdav'?'在 NAS 开启 WebDAV，并授予目录写入权限。':provider==='s3'?'':provider==='oss'?'若存储桶要求自定义域名，在高级配置中填写。':'';byId('storage-help').hidden=!byId('storage-help').textContent;
 storageDestination();}
function storageDestination(){const f=byId('monitor-form').elements;byId('storage-destination').textContent='目标目录：'+(f.bucket.value||'—')+'/'+(f.prefix.value||'');}
function fillMonitor(config){byId('connection-result').textContent='';const f=byId('monitor-form');fillForm(f,config);encodingFields(config);for(const side of ['Before','After']){const unit=config['motion'+side+'Unit']||'minutes',seconds=config['motion'+side+'Seconds']??(config.motionWindowMinutes||1)*60;f.elements['motion'+side+'Unit'].value=unit;f.elements['motion'+side+'Unit'].dataset.previous=unit;f.elements['motion'+side+'Value'].value=String(seconds/(unit==='minutes'?60:1));}f.elements.s3Region.value=config.region||'us-east-1';for(const el of f.elements)if(el.type==='password'||el.name==='clearSecret'||el.name==='clearToken'){if(el.type==='password')el.value='';else el.checked=false;}storageFields();monitorDependencies();byId('credential-status').textContent=(config.secretConfigured?'密钥已保存':'尚未保存密钥')+(config.tokenConfigured?' · 令牌已保存':'');}
byId('monitor-form').elements.provider.addEventListener('change',()=>{const provider=byId('monitor-form').elements.provider.value;const saved=monitorData?.profiles?.[provider];if(saved)fillMonitor(saved);else{byId('credential-status').textContent='配置读取失败，请刷新';}byId('monitor-message').textContent='';if(monitorPreviewWanted)queueMonitorPreview();});
for(const name of ['bucket','prefix'])byId('monitor-form').elements[name].addEventListener('input',storageDestination);
storageFields();

async function loadAccessCode(){try{const r=await fetch('/api/access-code',{headers:{Authorization:'Bearer '+key},cache:'no-store'});if(!r.ok)throw Error();const data=await r.json();byId('access-code').textContent=data.key;}catch{byId('code-message').textContent='访问码读取失败'}}
byId('custom-code').addEventListener('click',()=>{byId('code-editor').hidden=false;byId('new-code').value=key;byId('new-code').focus();});
byId('cancel-code').addEventListener('click',()=>byId('code-editor').hidden=true);
async function changeAccessCode(body){try{if(streaming)await stopStream();if(monitorPreviewWanted)await stopMonitorPreview();const data=await post('/api/access-code',body);key=data.key;const current=new URL(location.href);current.searchParams.set('key',key);history.replaceState(null,'',current);byId('access-code').textContent=key;byId('code-editor').hidden=true;byId('code-message').textContent='访问码已更新';updateExport();byId('viewer').close();selectedPhoto=null;}catch(e){byId('code-message').textContent=e.message}}
byId('refresh-code').addEventListener('click',()=>changeAccessCode({action:'refresh'}));
byId('save-code').addEventListener('click',()=>{const code=byId('new-code').value;if(!/^[0-9]{6}$/.test(code)){byId('code-message').textContent='输入六位数字访问码';return;}changeAccessCode({code});});

document.querySelector('.brand').addEventListener('click',e=>{e.preventDefault();selectPage('gallery');});

function settingDependencies(){const f=byId('settings-form').elements;for(const name of ['cameraId','quietStart','quietEnd'])f[name].disabled=!f.camera.checked;for(const name of ['occupiedMinutes','emptyMinutes'])f[name].disabled=!f.camera.checked||!f.gallerySaving.checked||!f.seatEnabled.checked;f.unknownMinutes.disabled=!f.camera.checked||!f.gallerySaving.checked||!f.unknownSaving.checked;f.occupiedMinutes.disabled=f.occupiedMinutes.disabled||!f.occupiedSaving.checked;f.emptyMinutes.disabled=f.emptyMinutes.disabled||!f.emptySaving.checked;f.observationMinutes.disabled=!f.camera.checked||!f.observationEnabled.checked;for(const name of ['occupiedSaving','emptySaving','unknownSaving','nightSaving'])f[name].disabled=!f.camera.checked||!f.gallerySaving.checked;}
for(const name of ['camera','gallerySaving','seatEnabled','observationEnabled','occupiedSaving','emptySaving','unknownSaving'])byId('settings-form').elements[name].addEventListener('change',settingDependencies);

byId('presence-query').addEventListener('submit',e=>{e.preventDefault();const a=byId('presence-start').value,b=byId('presence-end').value;if(!a||!b){byId('presence-query-message').textContent='选择开始和结束日期';return;}const from=new Date(a+'T00:00:00'),to=new Date(b+'T00:00:00');to.setDate(to.getDate()+1);if(to<=from||to-from>365*86400000){byId('presence-query-message').textContent='日期范围需在 365 天内';return;}presenceQuery=new URLSearchParams({start:String(+from),end:String(+to)}).toString();selectedDay='';byId('presence-query-message').textContent='';refreshPresence();});
byId('reset-presence').addEventListener('click',()=>{presenceQuery='';selectedDay='';byId('presence-start').value='';byId('presence-end').value='';byId('presence-query-message').textContent='';refreshPresence();});

function closeVideo(){const video=byId('video-player');video.pause();video.removeAttribute('src');video.load();}
byId('close-video').addEventListener('click',()=>byId('video-viewer').close());byId('video-viewer').addEventListener('close',closeVideo);

for(const [control,model] of [['show-faces','landmarks/face'],['show-hands','landmarks/hand'],['show-holistic','landmarks/holistic'],['show-yamnet','yamnet']])byId(control).addEventListener('change',async()=>{if(!streaming)return;try{await cameraRequest(model+'/'+(byId(control).checked?'on':'off'));}catch(e){byId(control).checked=false;byId('landmark-status').textContent=model==='yamnet'?'在设置中开启麦克风':'检测切换失败';}});
setInterval(async()=>{if(document.hidden||activePage!=='live'||!streaming)return;try{const d=await(await fetch('/api/landmarks',{headers:{Authorization:'Bearer '+key}})).json();byId('landmark-status').textContent=byId('show-faces').checked||byId('show-hands').checked||byId('show-holistic').checked?(d.status==='检测中'?(d.faces+' 个面部 · '+d.hands+' 个手部'):d.status):'';}catch{}},1000);
byId('live-image').addEventListener('load',()=>{const image=byId('live-image');if(image.naturalWidth&&image.naturalHeight)byId('live-stage').style.aspectRatio=image.naturalWidth+'/'+image.naturalHeight;});
byId('settings-form').elements.gallerySaving.addEventListener('change',()=>{const input=byId('settings-form').elements.gallerySaving;if(input.checked&&settingsData?.monitorEnabled&&!confirm('开启照片保存将关闭监控，继续？')){input.checked=false;settingDependencies();}});
function monitorDraft(){const data=formData(byId('monitor-form'));for(const side of ['Before','After']){const f=byId('monitor-form').elements,unit=f['motion'+side+'Unit'].value;data['motion'+side+'Seconds']=Math.round(Number(f['motion'+side+'Value'].value)*(unit==='minutes'?60:1));data['motion'+side+'Unit']=unit;delete data['motion'+side+'Value'];}delete data.s3Region;if(data.provider==='s3'){data.region=byId('monitor-form').elements.s3Region.value||'us-east-1';}if(data.provider==='oss'||data.provider==='cos'){delete data.endpoint;data.allowHttp=false;}return data;}
byId('test-connection').addEventListener('click',async()=>{const provider=byId('monitor-form').elements.provider.value;const button=byId('test-connection');button.disabled=true;byId('connection-result').textContent='测试中…';try{const result=await post('/api/monitor/test',monitorDraft());if(byId('monitor-form').elements.provider.value===provider)byId('connection-result').textContent=result.message+(result.cleanup===false&&result.testObject?' · '+result.testObject:'');}catch(e){if(byId('monitor-form').elements.provider.value===provider)byId('connection-result').textContent=e.message;}finally{button.disabled=false;}});


function monitorDependencies(){const f=byId('monitor-form').elements;f.rotation.disabled=!f.rotationEnabled.checked;for(const side of ['Before','After']){const unit=f['motion'+side+'Unit'].value,value=f['motion'+side+'Value'];value.disabled=f['motion'+side+'Unit'].disabled=!f.motionOnly.checked;value.max=unit==='minutes'?'30':'1800';value.step=unit==='minutes'?'any':'1';}f.uploadTime.disabled=f.uploadMode.value!=='scheduled';byId('upload-time-row').hidden=f.uploadMode.value!=='scheduled';updateVideoEstimate();}
for(const name of ['rotationEnabled','motionOnly','uploadMode'])byId('monitor-form').elements[name].addEventListener('change',monitorDependencies);
for(const side of ['Before','After']){const f=byId('monitor-form').elements,unit=f['motion'+side+'Unit'],value=f['motion'+side+'Value'];unit.addEventListener('change',()=>{const seconds=Number(value.value)*(unit.dataset.previous==='minutes'?60:1);value.value=String(seconds/(unit.value==='minutes'?60:1));unit.dataset.previous=unit.value;monitorDependencies();});}
function options(select,items,current){select.replaceChildren();for(const [value,label] of items){const option=document.createElement('option');option.value=String(value);option.textContent=label;select.append(option);}if(items.some(([value])=>String(value)===String(current)))select.value=String(current);else if(current!==undefined&&current!==''){const option=document.createElement('option');option.value=String(current);option.textContent=String(current)+'（当前不可用）';select.append(option);select.value=String(current);}}
function encodingFields(config){const f=byId('monitor-form').elements,codecs=monitorData?.capabilities?.codecs||[];const selected=config||{videoCodec:f.videoCodec.value,resolution:f.resolution.value,frameRate:Number(f.frameRate.value)};options(f.videoCodec,codecs.map(c=>[c.id,c.label]),selected.videoCodec);const codec=codecs.find(c=>c.id===f.videoCodec.value);options(f.resolution,codec?[['auto','自动 · '+codec.automaticResolution.replace('x',' × ')],...codec.formats.map(d=>[d.value,d.value.replace('x',' × ')])]:[],selected.resolution);const format=codec?.formats.find(d=>d.value===(f.resolution.value==='auto'?codec.automaticResolution:f.resolution.value));options(f.frameRate,(format?.frameRates||[]).map(rate=>[rate,rate+' fps']),selected.frameRate);f.bitrateKbps.min=String(format?.minBitrateKbps||128);f.bitrateKbps.max=String(format?.maxBitrateKbps||20000);byId('encoding-message').textContent=!codec||!format?'当前录像配置不可用，请选择设备支持的选项':'';updateVideoEstimate();}
for(const name of ['videoCodec','resolution'])byId('monitor-form').elements[name].addEventListener('change',()=>encodingFields());
function updateVideoEstimate(){const f=byId('monitor-form').elements,rate=Number(f.bitrateKbps.value)*1000+(f.recordAudio.checked?64000:0),seconds=Number(f.segmentSeconds.value);const size=(duration)=>{const bytes=Math.ceil(duration*rate/8*1.03)+4096;return bytes>=1073741824?(bytes/1073741824).toFixed(2)+' GiB':(bytes/1048576).toFixed(1)+' MiB';};byId('video-estimate').textContent=rate>0&&seconds>0?'预计每段 '+size(seconds)+' · 每小时 '+size(3600):'';}
for(const name of ['bitrateKbps','segmentSeconds','recordAudio'])byId('monitor-form').elements[name].addEventListener('input',updateVideoEstimate);
const previewNames=['timestamp','mirror','rotationEnabled','rotation','overlayObjects','overlayFaces','overlayHands','overlayHolistic','overlayYamnet','videoCodec','resolution','frameRate','bitrateKbps'];
function monitorPreviewOptions(){const f=byId('monitor-form').elements,data={};for(const name of previewNames){const el=f[name];data[name]=el.type==='checkbox'?el.checked:el.type==='number'||el.hasAttribute('data-number')?Number(el.value):el.value;}return data;}
function previewMessage(text){byId('monitor-preview-status').textContent=text;byId('monitor-preview-empty').hidden=false;byId('monitor-preview-image').hidden=true;}
function previewButtons(){byId('start-monitor-preview').disabled=monitorPreviewWanted;byId('stop-monitor-preview').disabled=!monitorPreviewWanted;}
function showMonitorPreview(){if(!monitorPreviewWanted||activePage!=='monitor')return;byId('monitor-preview-image').src=url('/api/monitor/preview/stream')+'&t='+Date.now();clearTimeout(monitorPreviewLoadTimer);monitorPreviewLoadTimer=setTimeout(()=>{if(monitorPreviewWanted&&byId('monitor-preview-image').hidden)previewMessage('画面未能加载，请重新开启预览');},12000);}
function startMonitorPreview(){monitorPreviewWanted=true;const version=++monitorPreviewVersion;previewButtons();if(byId('monitor-preview-image').hidden)previewMessage('正在开启预览…');const config=monitorPreviewOptions();const task=monitorPreviewQueue.then(async()=>{if(version!==monitorPreviewVersion||!monitorPreviewWanted)return;try{await post('/api/monitor/preview',{enabled:true,options:config});for(let i=0;i<30;i++){if(version!==monitorPreviewVersion||!monitorPreviewWanted)return;const r=await fetch('/api/camera',{headers:{Authorization:'Bearer '+key},cache:'no-store'}),state=await r.json();if(state.monitorPreview)break;if(i===29)throw Error('预览未开启，请重试');await new Promise(resolve=>setTimeout(resolve,100));}if(version!==monitorPreviewVersion)return;showMonitorPreview();clearInterval(monitorPreviewTimer);monitorPreviewTimer=setInterval(showMonitorPreview,60000);}catch(e){if(version!==monitorPreviewVersion)return;monitorPreviewWanted=false;previewButtons();previewMessage(e.message);byId('monitor-preview-image').removeAttribute('src');}});monitorPreviewQueue=task.catch(()=>{});return task;}
function stopMonitorPreview(){monitorPreviewWanted=false;monitorPreviewVersion++;clearTimeout(monitorPreviewDebounce);clearTimeout(monitorPreviewLoadTimer);clearInterval(monitorPreviewTimer);byId('monitor-preview-image').removeAttribute('src');previewMessage('预览已关闭');previewButtons();const task=monitorPreviewQueue.then(()=>post('/api/monitor/preview',{enabled:false}));monitorPreviewQueue=task.catch(()=>{});return task.catch(()=>{});}
function queueMonitorPreview(){if(!monitorPreviewWanted)return;clearTimeout(monitorPreviewDebounce);monitorPreviewDebounce=setTimeout(startMonitorPreview,250);}
for(const name of previewNames)byId('monitor-form').elements[name].addEventListener('change',queueMonitorPreview);
byId('start-monitor-preview').addEventListener('click',()=>startMonitorPreview());byId('stop-monitor-preview').addEventListener('click',stopMonitorPreview);
byId('monitor-preview-image').addEventListener('load',()=>{if(!monitorPreviewWanted)return;clearTimeout(monitorPreviewLoadTimer);byId('monitor-preview-image').hidden=false;byId('monitor-preview-empty').hidden=true;});byId('monitor-preview-image').addEventListener('error',()=>{if(monitorPreviewWanted)previewMessage('画面未能加载，请重新开启预览');});
document.addEventListener('visibilitychange',()=>{if(document.hidden&&monitorPreviewWanted)stopMonitorPreview();});window.addEventListener('pagehide',()=>{if(monitorPreviewWanted)fetch('/api/monitor/preview',{method:'POST',headers:{Authorization:'Bearer '+key,'Content-Type':'application/json'},body:'{"enabled":false}',keepalive:true}).catch(()=>{});});
selectPage(location.hash.slice(1)||'gallery');
