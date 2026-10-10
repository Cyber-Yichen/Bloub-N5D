<script setup lang="ts">
import { ref,onMounted,onBeforeUnmount } from 'vue'
import PhotoView from './PhotoView.vue'
interface Photo {id:string;capturedAt:number;faces:number;motion:number;url:string;thumbnailUrl?:string;cameraId?:string;width?:number;height?:number;rotationClockwise?:number}
interface Lan {enabled:boolean;running:boolean;url:string;key:string;error:string}
const native=!!window.N5D
const photos=ref<Photo[]>([]),selected=ref<Photo|null>(null),photoZoom=ref(1),mode=ref<'photos'|'lan'>('photos')
const enabled=ref(true),total=ref(0),next=ref(0),cursor=ref(0),history=ref<number[]>([]),error=ref('')
const lan=ref<Lan>({enabled:false,running:false,url:'',key:'',error:''})
const date=(t:number)=>new Date(t).toLocaleString('zh-CN',{month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit'})
function refresh(){
  try{const data=JSON.parse(window.N5D?.galleryList(cursor.value)??'{"items":[],"total":0,"enabled":false}');photos.value=data.items??[];enabled.value=data.enabled;total.value=data.total;next.value=data.nextBefore??0;error.value='';if(native)lan.value=JSON.parse(window.N5D!.galleryServerInfo())}catch{error.value='图库暂时无法读取'}
}
function toggle(){enabled.value=!enabled.value;window.N5D?.galleryEnabled(enabled.value)}
function open(photo:Photo){photoZoom.value=1;selected.value=photo}
function remove(){if(selected.value){window.N5D?.galleryDelete(selected.value.id);selected.value=null;refresh()}}
function clear(){if(confirm('删除图库中的全部观察照片？')){window.N5D?.galleryClear();cursor.value=0;history.value=[];refresh()}}
function page(forward:boolean){if(forward){history.value.push(cursor.value);cursor.value=next.value}else cursor.value=history.value.pop()??0;refresh()}
function copyAddress(){window.N5D?.copyGalleryAddress()}
function toggleLan(){window.N5D?.galleryServerEnabled(!lan.value.enabled);setTimeout(refresh,300)}
function key(e:KeyboardEvent){if(selected.value&&e.key==='Escape'){selected.value=null;e.stopImmediatePropagation();e.preventDefault()}}
let timer=0
onMounted(()=>{refresh();timer=window.setInterval(refresh,5000);window.addEventListener('keydown',key,true)})
onBeforeUnmount(()=>{clearInterval(timer);window.removeEventListener('keydown',key,true)})
</script>
<template>
  <div class="gallery-dock">
    <div class="gallery-toolbar"><span>{{ total }} 张</span><button :disabled="!native" :aria-pressed="enabled" @click="toggle">保存观察 <i class="switch" :class="{on:enabled}"/></button><button class="danger" :disabled="!total" @click="clear">清空图库</button></div>
    <nav class="gallery-tabs" aria-label="图库功能"><button :aria-pressed="mode==='photos'" @click="mode='photos'">照片</button><button :aria-pressed="mode==='lan'" @click="mode='lan'">电脑下载</button></nav>
    <p v-if="error">{{ error }}</p>
    <template v-else-if="mode==='photos'">
      <div v-if="!photos.length" class="gallery-empty"><span>◌</span><strong>等它下次看看周围</strong><p>开启相机和“保存观察”，每次短时观察留一张照片。<br/>照片保存在设备，到期清理。</p></div>
      <div v-else class="gallery-grid"><button v-for="photo in photos" :key="photo.id" @click="open(photo)" :aria-label="'查看大图 '+date(photo.capturedAt)"><div class="gallery-thumbnail"><PhotoView :photo="photo" thumbnail/></div><span>{{ date(photo.capturedAt) }}</span></button></div>
      <div v-if="next||history.length" class="gallery-pages"><button :disabled="!history.length" @click="page(false)">较新照片</button><button :disabled="!next" @click="page(true)">较早照片</button></div>
    </template>
    <section v-else class="lan-dock">
      <button class="lan-switch" :disabled="!native" :aria-pressed="lan.enabled" @click="toggleLan"><div><strong>局域网图库</strong><small>{{ lan.running?'已启动':lan.error||'已关闭' }}</small></div><i class="switch" :class="{on:lan.enabled}"/></button>
      <div class="lan-address"><label>电脑网址<input readonly :value="lan.url?.split('?')[0]||'连接局域网后显示'" aria-label="局域网图库网址"/></label><div class="lan-code">六位访问码<b>{{ lan.key||'------' }}</b></div></div>
      <button class="link" :disabled="!lan.url" @click="copyAddress">复制访问链接 ↗</button>
      <div class="lan-api"><strong>AI 接口</strong><code>GET /api/photos</code><code>GET /api/camera</code></div>

    </section>
    <Teleport to="body"><section v-if="selected" class="photo-viewer" role="dialog" aria-modal="true" aria-label="观察照片大图" @pointerdown.stop @pointerup.stop>
      <div class="photo-viewport"><PhotoView :photo="selected" :zoom="photoZoom"/></div>
      <div class="photo-details"><strong>照片</strong><span>{{ date(selected.capturedAt) }}</span><small>{{ selected.width }} × {{ selected.height }} · 相机 {{ selected.cameraId??'2' }}</small><button @click="photoZoom=photoZoom===1?2:1">{{ photoZoom===1?'放大 2×':'适合屏幕' }}</button><small v-if="photoZoom>1">滑动查看照片</small><button @click="selected=null">返回图库</button><button class="photo-delete danger" @click="remove">删除这张</button></div>
    </section></Teleport>
  </div>
</template>
