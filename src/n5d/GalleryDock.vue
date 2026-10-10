<script setup lang="ts">
import { ref,onMounted,onBeforeUnmount } from 'vue'
interface Photo {id:string;capturedAt:number;faces:number;motion:number;url:string}
const native=!!window.N5D
const photos=ref<Photo[]>([]),selected=ref<Photo|null>(null),enabled=ref(true),total=ref(0),next=ref(0),cursor=ref(0),history=ref<number[]>([]),error=ref('')
const date=(t:number)=>new Date(t).toLocaleString('zh-CN',{month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit'})
function refresh(){
  try{const data=JSON.parse(window.N5D?.galleryList(cursor.value)??'{"items":[],"total":0,"enabled":false}');photos.value=data.items??[];enabled.value=data.enabled;total.value=data.total;next.value=data.nextBefore??0;error.value=''}catch{error.value='图库暂时无法读取'}
}
function toggle(){enabled.value=!enabled.value;window.N5D?.galleryEnabled(enabled.value)}
function remove(){if(selected.value){window.N5D?.galleryDelete(selected.value.id);selected.value=null;refresh()}}
function clear(){if(confirm('删除图库中的全部观察照片？')){window.N5D?.galleryClear();cursor.value=0;history.value=[];refresh()}}
function page(forward:boolean){if(forward){history.value.push(cursor.value);cursor.value=next.value}else cursor.value=history.value.pop()??0;refresh()}
let timer=0
onMounted(()=>{refresh();timer=window.setInterval(refresh,5000)})
onBeforeUnmount(()=>clearInterval(timer))
</script>
<template>
  <div class="gallery-dock">
    <div class="gallery-toolbar"><span>本机照片 · 保留 7 天 · {{ total }} 张</span><button :disabled="!native" :aria-pressed="enabled" @click="toggle">保存观察 <i class="switch" :class="{on:enabled}"/></button><button :disabled="!total" @click="clear">清空图库</button></div>
    <p v-if="error">{{ error }}</p>
    <div v-else-if="!photos.length" class="gallery-empty"><span>◌</span><strong>等它下次看看周围</strong><p>开启相机和“保存观察”，每次短时观察留一张照片。<br/>照片只保存在这台设备，不上传；到期自动清理。</p></div>
    <div v-else class="gallery-grid"><button v-for="photo in photos" :key="photo.id" @click="selected=photo"><img :src="photo.url" :alt="'观察照片 '+date(photo.capturedAt)" loading="lazy"/><span>{{ date(photo.capturedAt) }}</span></button></div>
    <div v-if="next||history.length" class="gallery-pages"><button :disabled="!history.length" @click="page(false)">较新照片</button><button :disabled="!next" @click="page(true)">较早照片</button></div>
    <div v-if="selected" class="photo-viewer" role="dialog" aria-label="观察照片"><img :src="selected.url" alt="相机观察照片"/><div><span>{{ date(selected.capturedAt) }} · 检测到 {{ selected.faces }} 张脸 · 7 天后删除</span><button @click="remove">删除这张</button><button @click="selected=null">返回图库</button></div></div>
  </div>
</template>
