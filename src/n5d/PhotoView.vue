<script setup lang="ts">
import { ref,computed,onMounted,onBeforeUnmount } from 'vue'
const props=defineProps<{photo:{url:string;thumbnailUrl?:string;width?:number;height?:number;rotationClockwise?:number};thumbnail?:boolean;zoom?:number}>()
const host=ref<HTMLDivElement|null>(null),box=ref({width:1,height:1})
const turn=computed(()=>props.photo.rotationClockwise??90)
const raw=computed(()=>({width:props.photo.width||240,height:props.photo.height||320}))
const rotated=computed(()=>Math.abs(turn.value%180)===90?{width:raw.value.height,height:raw.value.width}:raw.value)
const scale=computed(()=>Math.min(box.value.width/rotated.value.width,box.value.height/rotated.value.height)*(props.zoom??1))
const size=computed(()=>({width:rotated.value.width*scale.value,height:rotated.value.height*scale.value}))
const space=computed(()=>({width:Math.max(box.value.width,size.value.width)+'px',height:Math.max(box.value.height,size.value.height)+'px'}))
const image=computed(()=>({width:raw.value.width*scale.value+'px',height:raw.value.height*scale.value+'px',transform:'translate(-50%,-50%) rotate('+turn.value+'deg)'}))
let observer:ResizeObserver|undefined
onMounted(()=>{const el=host.value!;const update=()=>box.value={width:el.clientWidth,height:el.clientHeight};update();observer=new ResizeObserver(update);observer.observe(el)})
onBeforeUnmount(()=>observer?.disconnect())
</script>
<template><div ref="host" class="observation-photo" :class="{thumbnail}"><div class="photo-space" :style="space"><div class="photo-stage" :style="{width:size.width+'px',height:size.height+'px'}"><img :src="thumbnail?(photo.thumbnailUrl??photo.url):photo.url" :style="image" :alt="thumbnail?'观察照片，点击看大图':'相机观察照片'" :loading="thumbnail?'lazy':'eager'" draggable="false"/></div></div></div></template>
