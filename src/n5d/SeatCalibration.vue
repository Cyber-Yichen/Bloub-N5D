<script setup lang="ts">
import { computed,onMounted,onBeforeUnmount,ref } from 'vue'
import type { PresenceInfo } from './presence'
const props=defineProps<{info:PresenceInfo}>(),emit=defineEmits<{close:[]}>()
const host=ref<HTMLDivElement|null>(null),image=ref(''),data=ref(props.info),region=ref([...props.info.roi]),dirty=ref(false),message=ref('')
const box=ref({width:600,height:400}),size=computed(()=>Math.min(box.value.width,box.value.height*4/3))
const selection=computed(()=>({x:region.value[0]! *1000,y:region.value[1]! *750,width:(region.value[2]! -region.value[0]!)*1000,height:(region.value[3]! -region.value[1]!)*750}))
let observer:ResizeObserver,timer:ReturnType<typeof setInterval>,origin:{x:number;y:number}|null=null
function refresh(){window.N5D?.seatPreview(true);try{if(window.N5D){data.value=JSON.parse(window.N5D.seatInfo());image.value=window.N5D.seatFrame()}}catch{message.value='暂时没有画面'}}
function point(event:PointerEvent){const r=(event.currentTarget as SVGElement).getBoundingClientRect();return {x:Math.max(0,Math.min(1,(event.clientX-r.left)/r.width)),y:Math.max(0,Math.min(1,(event.clientY-r.top)/r.height))}}
function down(event:PointerEvent){origin=point(event);(event.currentTarget as SVGElement).setPointerCapture(event.pointerId)}
function move(event:PointerEvent){if(!origin)return;const p=point(event);region.value=[Math.min(origin.x,p.x),Math.min(origin.y,p.y),Math.max(origin.x,p.x),Math.max(origin.y,p.y)];dirty.value=true}
function up(){origin=null}
function save(){const r=region.value;if(r[2]! -r[0]!<.15||r[3]! -r[1]!<.15){message.value='区域太小，请框住座位和上半身';return}window.N5D?.seatRegion(r[0]!,r[1]!,r[2]!,r[3]!);dirty.value=false;message.value='区域已保存，等新画面出现后确认';refresh()}
function label(kind:'empty'|'occupied'){try{const result=JSON.parse(window.N5D?.seatLabel(kind)??'{}');message.value=result.message??'未完成确认';refresh()}catch{message.value='确认失败，请重试'}}
onMounted(()=>{const el=host.value!;const resize=()=>{box.value={width:el.clientWidth,height:el.clientHeight}};resize();observer=new ResizeObserver(resize);observer.observe(el);refresh();timer=setInterval(refresh,2000)})
onBeforeUnmount(()=>{observer?.disconnect();clearInterval(timer);window.N5D?.seatPreview(false)})
</script>
<template><section class="seat-calibrator" role="dialog" aria-modal="true" aria-label="选择工位区域">
  <div ref="host" class="seat-cal-host"><div class="seat-selection" :style="{width:size+'px',height:size*.75+'px'}"><img v-if="image" :src="image" alt="相机实时校准画面" draggable="false"/><p v-else>等待相机画面…</p><svg viewBox="0 0 1000 750" @pointerdown="down" @pointermove="move" @pointerup="up" @pointercancel="up"><rect :x="selection.x" :y="selection.y" :width="selection.width" :height="selection.height" fill="#3976cb20" stroke="#62b7ff" stroke-width="5"/><path :d="`M ${selection.x} ${selection.y+24} v-24 h24 M ${selection.x+selection.width-24} ${selection.y} h24 v24 M ${selection.x} ${selection.y+selection.height-24} v24 h24 M ${selection.x+selection.width-24} ${selection.y+selection.height} h24 v-24`" fill="none" stroke="white" stroke-width="8"/></svg></div></div>
  <aside><h2>座位区域</h2><p>拖动框住座位和坐下后的上半身。</p><button :disabled="!dirty" @click="save">保存区域</button><button :disabled="dirty||!image" @click="label('empty')">{{ data.emptyConfirmed?'空位已确认 ✓':'记为空位' }}</button><button :disabled="dirty||!image" @click="label('occupied')">{{ data.occupiedConfirmed?'在位已确认 ✓':'记为在位' }}</button><small role="status">{{ message||data.message }}</small><button class="seat-done" @click="emit('close')">返回工位</button></aside>
</section></template>
