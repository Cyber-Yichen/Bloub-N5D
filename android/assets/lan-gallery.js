'use strict'
const key=new URL(location.href).searchParams.get('key')||''
const url=(path)=>path+(path.includes('?')?'&':'?')+'key='+encodeURIComponent(key)
const byId=id=>document.getElementById(id)
let page=1,totalPages=1,pageSize=24,selectedPhoto=null;try{pageSize=Number(localStorage.getItem('bloub.gallery.pageSize'))||24}catch{}
if(![12,24,48,96].includes(pageSize))pageSize=24;byId('page-size').value=String(pageSize);let refreshGeneration=0
let filterQuery=""
function updateExport(){byId('archive').href=url('/api/archive.zip'+(filterQuery?'?'+filterQuery:''));byId('api').href=url('/api/photos'+(filterQuery?'?'+filterQuery:''))}updateExport()
function picture(photo,thumbnail){
  const turn=photo.rotationClockwise??90,w=photo.width||240,h=photo.height||320,rotated=Math.abs(turn%180)===90
  const ratio=rotated?h/w:w/h
  const frame=document.createElement('div');frame.className='picture';frame.dataset.ratio=String(ratio);frame.style.paddingTop=(100/ratio)+'%'
  const img=document.createElement('img');img.src=thumbnail?photo.thumbnailUrl:photo.url;img.alt='观察照片';img.loading=thumbnail?'lazy':'eager'
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
  const generation=++refreshGeneration;byId('error').textContent=''
  try{
    const response=await fetch('/api/photos?page='+page+'&pageSize='+pageSize+(filterQuery?'&'+filterQuery:''),{headers:{Authorization:'Bearer '+key},cache:'no-store'})
    if(!response.ok)throw Error(response.status===401?'访问密钥无效，请从设备重新复制网址。':'设备暂时无法读取照片。')
    const data=await response.json();if(generation!==refreshGeneration)return;page=data.page;totalPages=data.totalPages;byId('page-count').textContent='第 '+page+' / '+totalPages+' 页 · 共 '+data.total+' 张';byId('page-number').value=String(page);byId('page-number').max=String(totalPages)
    if(activePage==='gallery')byId('summary').textContent=''
    byId('grid').replaceChildren()
    for(const photo of data.items){
      const card=document.createElement('article');card.className='card'
      const button=document.createElement('button');button.className='open';button.ariaLabel='查看照片大图';button.append(picture(photo,true));button.addEventListener('click',()=>open(photo))
      const caption=document.createElement('div');caption.className='caption'
      const date=document.createElement('span');date.textContent=new Date(photo.capturedAt).toLocaleString()
      const download=document.createElement('a');download.href=photo.downloadUrl;download.textContent='下载'
      const tag=document.createElement("span");tag.className="tag "+(photo.seatState||"unknown");tag.textContent=stateName(photo.seatState);caption.append(date,tag,download);card.append(button,caption);byId('grid').append(card)
    }
    byId('empty').hidden=data.items.length>0;byId('empty').textContent=filterQuery?'这个范围没有符合条件的照片':'还没有观察照片，请开启相机和保存照片。';byId('newer').disabled=page<=1;byId('older').disabled=page>=totalPages
  }catch(e){byId('error').textContent=e.message}
}
byId('refresh').addEventListener('click',()=>{if(activePage==='gallery')refresh();else if(activePage==='presence')refreshPresence();else if(activePage==='live')cameraStatus();else if(activePage==='settings')loadSettings();else loadMonitor()})
byId('older').addEventListener('click',()=>{page=Math.min(totalPages,page+1);refresh()})
byId('newer').addEventListener('click',()=>{page=Math.max(1,page-1);refresh()})
byId('close').addEventListener('click',()=>byId('viewer').close())
byId('zoom').addEventListener('click',()=>{const enlarged=byId('photo-area').classList.toggle('zoom');byId('zoom').textContent=enlarged?'适合屏幕':'放大 2×';const frame=byId('photo-area').firstElementChild;frame.style.paddingTop=(100/Number(frame.dataset.ratio)*(enlarged?2:1))+'%'})
let region=[.15,.08,.85,.95],regionOrigin=null,previewTimer=null
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
  if(capturePending&&s.lastCaptureId&&s.lastCaptureId!==previousCapture){capturePending=false;byId('capture-status').textContent='已保存到图库';page=1;refresh()}
 }catch{}
}
byId('start-stream').addEventListener('click',async()=>{
 try{await cameraRequest('start');await new Promise(r=>setTimeout(r,300));streaming=true;if(byId("show-detections").checked)await cameraRequest("detection/on");showStream();cameraStatus()}catch(e){byId('capture-status').textContent=e.message}
})
async function stopStream(){clearInterval(previewTimer);byId('edit-seat').checked=false;byId('roi-editor').hidden=true;post('/api/seat/preview',{enabled:false}).catch(()=>{});streaming=false;clearInterval(streamTimer);byId('live-image').removeAttribute('src');byId('live-area').hidden=true;try{await cameraRequest('stop')}catch{}cameraStatus()}
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
 byId('presence-status').textContent=presenceData.status
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

function stateName(state){return state==='occupied'?'有人':state==='empty'?'无人':'未知'}
async function post(path,body){const r=await fetch(path,{method:'POST',headers:{Authorization:'Bearer '+key,...(body?{'Content-Type':'application/json'}:{})},...(body?{body:JSON.stringify(body)}:{}),cache:'no-store'});const d=await r.json();if(!r.ok)throw Error(d.message||d.error||'操作失败');return d}
function formData(form){const data={};for(const el of form.elements){if(!el.name||el.disabled)continue;data[el.name]=el.type==='checkbox'?el.checked:el.type==='number'?Number(el.value):el.hasAttribute('data-boolean')?el.value==='true':el.value}return data}
function fillForm(form,data){for(const el of form.elements){if(!el.name||!(el.name in data)||el.type==='password')continue;if(el.type==='checkbox')el.checked=Boolean(data[el.name]);else el.value=String(data[el.name])}}
let activePage='gallery',settingsData=null,monitorData=null;const pageNames={presence:'工位时间',live:'实时观察',gallery:'图库',settings:'设置',monitor:'监控'};refresh()
function selectPage(name){if(!pageNames[name])return;activePage=name;for(const b of document.querySelectorAll('[data-page]'))b.setAttribute('aria-pressed',String(b.dataset.page===name));for(const key of Object.keys(pageNames))byId(key+'-page').hidden=key!==name;byId('page-title').textContent=pageNames[name];for(const el of document.querySelectorAll('[data-gallery-tool]'))el.hidden=name!=='gallery';byId('summary').textContent='';if(name==='settings')loadSettings();else if(name==='monitor')loadMonitor();else if(name==='gallery')refresh();else if(name==='presence')refreshPresence();else cameraStatus();if(name!=='live'&&streaming)stopStream();history.replaceState(null,'','#'+name)}
for(const b of document.querySelectorAll('[data-page]'))b.addEventListener('click',()=>selectPage(b.dataset.page));selectPage(location.hash.slice(1)||'gallery');
byId('page-size').addEventListener('change',()=>{pageSize=Number(byId('page-size').value);try{localStorage.setItem('bloub.gallery.pageSize',pageSize)}catch{}page=1;refresh()});
byId('go-page').addEventListener('click',()=>{page=Math.max(1,Math.min(totalPages,Number(byId('page-number').value)||1));refresh()});
byId('page-number').addEventListener('keydown',e=>{if(e.key==='Enter')byId('go-page').click()});

async function loadSettings(){try{const r=await fetch('/api/settings',{headers:{Authorization:'Bearer '+key},cache:'no-store'});settingsData=await r.json();fillForm(byId('settings-form'),settingsData)}catch{byId('settings-message').textContent='设置读取失败'}}
byId('settings-form').addEventListener('submit',async e=>{e.preventDefault();try{settingsData=await post('/api/settings',formData(e.target));byId('settings-message').textContent='已保存';cameraStatus();refreshPresence()}catch(e){byId('settings-message').textContent=e.message}})
byId('apply-range').addEventListener('click',()=>{const params=new URLSearchParams(),a=byId('range-start').value,b=byId('range-end').value;const start=a?new Date(a).getTime():0,end=b?new Date(b).getTime():Infinity;if(!Number.isFinite(start)||Number.isNaN(end)||end<=start){byId('error').textContent='结束时间需晚于开始时间';return}if(a)params.set('start',start);if(b)params.set('end',end);if(byId('range-state').value!=='all')params.set('state',byId('range-state').value);filterQuery=params.toString();page=1;updateExport();refresh()})
byId('clear-range').addEventListener('click',()=>{byId('range-start').value='';byId('range-end').value='';byId('range-state').value='all';filterQuery='';page=1;updateExport();refresh()})
byId('delete-photo').addEventListener('click',async()=>{if(!selectedPhoto||!confirm('删除这张照片和标签？'))return;try{await post('/api/photos/'+selectedPhoto.id+'/delete');byId('viewer').close();refresh()}catch(e){byId('error').textContent=e.message}})
byId('clear-photos').addEventListener('click',async()=>{if(!confirm('清空全部照片和标签？工位统计不受影响。'))return;try{await post('/api/photos/clear');refresh()}catch(e){byId('settings-message').textContent=e.message}})
async function loadMonitor(fill=true){try{const r=await fetch('/api/monitor',{headers:{Authorization:'Bearer '+key},cache:'no-store'});monitorData=await r.json();byId('monitor-toggle').checked=monitorData.enabled;byId('monitor-status').textContent=monitorData.status+' · '+monitorData.uploadStatus+' · 待上传 '+monitorData.pending+' 段 · '+(monitorData.bytes/1048576).toFixed(1)+' MiB';if(fill){fillForm(byId('monitor-form'),monitorData.config);byId('monitor-form').elements.s3Region.value=monitorData.config.region||'us-east-1';storageFields();}byId('credential-status').textContent=(monitorData.config.secretConfigured?'密钥已保存':'尚未保存密钥')+(monitorData.config.tokenConfigured?' · 令牌已保存':'');byId('video-list').replaceChildren();for(const v of monitorData.videos.slice().reverse()){const row=document.createElement('article');row.className='video-item';const label=document.createElement('span');label.textContent=new Date(v.metadata.startedAt||Number(v.id.slice(0,13))).toLocaleString()+' · '+(v.bytes/1048576).toFixed(1)+' MiB · '+(v.metadata.uploaded?'已上传':'本机保留');const a=document.createElement('a');a.href=url('/api/videos/'+v.id);a.textContent='下载 MP4';const del=document.createElement('button');del.className='danger';del.textContent='删除';del.addEventListener('click',async()=>{if(confirm('删除这段本机录像？')){await post('/api/videos/'+v.id+'/delete');loadMonitor(false)}});row.append(label,a,del);byId('video-list').append(row)}}catch{byId('monitor-status').textContent='监控状态读取失败'}}
byId('monitor-form').addEventListener('submit',async e=>{e.preventDefault();try{const data=formData(e.target);if(data.provider==='s3'){data.region=data.s3Region||'us-east-1';delete data.s3Region;}if(data.provider==='oss'||data.provider==='cos'){delete data.endpoint;data.allowHttp=false;}if(data.clearSecret&&data.autoUpload)throw Error('清除密钥前先关闭自动上传');await post('/api/monitor/config',data);for(const el of e.target.elements)if(el.type==='password')el.value='';byId('monitor-message').textContent='已保存';loadMonitor()}catch(e){byId('monitor-message').textContent=e.message}})
byId('monitor-toggle').addEventListener('change',async()=>{try{await post('/api/settings',{monitorEnabled:byId('monitor-toggle').checked,...(byId('monitor-toggle').checked?{camera:true}:{})});loadMonitor(false)}catch(e){byId('monitor-message').textContent=e.message;loadMonitor(false)}})
byId('retry-upload').addEventListener('click',async()=>{await post('/api/monitor/retry');byId('monitor-message').textContent='已安排重试'})
setInterval(()=>{if(activePage==='monitor')loadMonitor(false)},5000)
function drawRegion(){const box=byId('roi-box');for(const [name,value] of Object.entries({x:region[0]*1000,y:region[1]*750,width:(region[2]-region[0])*1000,height:(region[3]-region[1])*750}))box.setAttribute(name,value)}
function roiPoint(e){const b=byId('roi-editor').getBoundingClientRect();return [Math.max(0,Math.min(1,(e.clientX-b.left)/b.width)),Math.max(0,Math.min(1,(e.clientY-b.top)/b.height))]}
byId('roi-editor').addEventListener('pointerdown',e=>{regionOrigin=roiPoint(e);e.currentTarget.setPointerCapture(e.pointerId)})
byId('roi-editor').addEventListener('pointermove',e=>{if(!regionOrigin)return;const p=roiPoint(e);region=[Math.min(p[0],regionOrigin[0]),Math.min(p[1],regionOrigin[1]),Math.max(p[0],regionOrigin[0]),Math.max(p[1],regionOrigin[1])];drawRegion();byId('save-seat').disabled=false})
for(const name of ['pointerup','pointercancel'])byId('roi-editor').addEventListener(name,()=>regionOrigin=null)
byId('edit-seat').addEventListener('change',async()=>{clearInterval(previewTimer);const on=byId('edit-seat').checked;byId('roi-editor').hidden=!on;try{if(on){if(!streaming){await cameraRequest('start');streaming=true;showStream()}const r=await(await fetch('/api/presence',{headers:{Authorization:'Bearer '+key}})).json();region=r.roi;drawRegion();await post('/api/seat/preview',{enabled:true});previewTimer=setInterval(()=>post('/api/seat/preview',{enabled:true}).catch(()=>{}),5000)}else await post('/api/seat/preview',{enabled:false})}catch(e){byId('seat-message').textContent=e.message}})
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
byId('monitor-form').elements.provider.addEventListener('change',()=>{const f=byId('monitor-form').elements;f.autoUpload.checked=false;f.secret.value='';f.token.value='';f.clearSecret.checked=false;f.clearToken.checked=false;f.endpoint.value='';f.endpointOverride.value='';f.bucket.value='';f.accessId.value='';f.username.value='';f.region.value=f.provider.value==='oss'?'cn-hangzhou':f.provider.value==='cos'?'ap-guangzhou':'';byId('credential-status').textContent='填写此存储位置的凭证';storageFields()});
for(const name of ['bucket','prefix'])byId('monitor-form').elements[name].addEventListener('input',storageDestination);
storageFields();
