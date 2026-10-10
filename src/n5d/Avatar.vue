<script setup lang="ts">
import { computed, ref, shallowRef, triggerRef, watch } from 'vue'
import { BotEngine, type BotFrame } from '../bot/engine'
import { EXPRESSION_BY_ID, type ExpressionId } from '../bot/expressions'
import { STATE_BY_ID, type StateId } from '../bot/states'
import { SHAPE_BY_ID,type ShapeId } from '../bot/skins'
import { mixHex } from '../bot/skins'
import { luminance, type Palette } from './themes'

const props = defineProps<{ time: number; local:number; shape:ShapeId; state: StateId; expression: ExpressionId; yaw: number; pitch: number; palette:Palette; lockLook?:boolean }>()
const silhouette=ref<SVGPathElement|null>(null)
let hitCanvas:CanvasRenderingContext2D|null=null
function hitTest(clientX:number,clientY:number){
  const path=silhouette.value,matrix=path?.getScreenCTM(),svg=path?.ownerSVGElement
  if(!path||!matrix||!svg||frame.value.bodyAlpha<.2)return false
  try{
    const point=svg.createSVGPoint();point.x=clientX;point.y=clientY
    const local=point.matrixTransform(matrix.inverse())
    if(typeof path.isPointInFill==='function')return path.isPointInFill(local)
    // Older device WebViews have Path2D but not SVGGeometryElement.isPointInFill.
    hitCanvas??=document.createElement('canvas').getContext('2d')
    return hitCanvas?.isPointInPath(new Path2D(frame.value.bodyPath),local.x,local.y)??false
  }catch{return false}
}
defineExpose({hitTest})
const R = 100
const engine = new BotEngine(R,'idle',null,EXPRESSION_BY_ID.get('neutre')!)
const frame = shallowRef<BotFrame>(engine.sample(0))
let previous=-1
watch(() => props.time, (t) => {
  // Diagnostic seeking resets local state time; ordinary playback retains its morph.
  if(previous>=0&&(t<previous||t-previous>1))engine.reset(props.state,t-props.local)
  previous=t
  engine.setShape(SHAPE_BY_ID.get(props.shape)!.radii,t)
  engine.setState(props.state,t)
  engine.setExpression(EXPRESSION_BY_ID.get(props.expression)!,t)
  engine.setLook((props.lockLook||STATE_BY_ID.get(props.state)?.baseFace)?{yaw:props.yaw,pitch:props.pitch,mix:1,spin:0,wander:.5}:null,t,.35)
  frame.value=engine.sample(t)
  triggerRef(frame)
}, { immediate: true })
const dotFill = (dot: BotFrame['dots'][number]) => dot.color ?? (dot.depth===undefined ? props.palette.body : mixHex(props.palette.paper,props.palette.body,dot.depth))
// During a light/dark inversion, a fine contour keeps the silhouette visible.
const contour=computed(()=>({color:luminance(props.palette.paper)>.5?'#26343b':'#d9e4e2',width:Math.max(0,1-Math.abs(luminance(props.palette.paper)-luminance(props.palette.body))/.24)*1.2}))
</script>

<template>
  <svg class="avatar" viewBox="-180 -180 360 360" role="img" aria-label="Bloub 桌面小伙伴">
    <defs>
      <mask id="body-mask" maskUnits="userSpaceOnUse" x="-180" y="-180" width="360" height="360">
        <path :d="frame.bodyPath" fill="white"/>
        <path v-for="(eye,i) in frame.eyes" :key="i" :d="eye.d" :transform="eye.matrix" :opacity="eye.alpha" fill="black"/>
        <circle v-if="frame.notch" :cx="frame.notch.x" :cy="frame.notch.y" :r="frame.notch.r" fill="black"/>
      </mask>
      <linearGradient v-for="arc in frame.arcs" :id="`ring-${arc.id}`" :key="arc.id" gradientUnits="userSpaceOnUse"
        :x1="arc.grad.x1" :y1="arc.grad.y1" :x2="arc.grad.x2" :y2="arc.grad.y2">
        <stop v-for="(c,i) in arc.grad.stops" :key="i" :offset="i/(arc.grad.stops.length-1)" :stop-color="c"/>
      </linearGradient>
    </defs>
    <g fill="none" stroke-linecap="round">
      <path v-for="arc in frame.arcs" :key="arc.id" :d="arc.back" :stroke="`url(#ring-${arc.id})`" :stroke-width="arc.width" :opacity="arc.opacity"/>
    </g>
    <g v-if="frame.dotsBehind">
      <template v-for="(dot,i) in frame.dots" :key="i">
        <path v-if="dot.d" :d="dot.d" :transform="`translate(${dot.x} ${dot.y}) rotate(${dot.rot??0}) scale(${R})`" :fill="dotFill(dot)" :opacity="dot.opacity"/>
        <circle v-else :cx="dot.x" :cy="dot.y" :r="dot.r" :fill="dotFill(dot)" :opacity="dot.opacity"/>
      </template>
    </g>
    <g :opacity="frame.bodyAlpha">
      <path ref="silhouette" :d="frame.bodyPath" :fill="props.palette.paper"/>
      <path :d="frame.bodyPath" :fill="props.palette.body" mask="url(#body-mask)"/>
      <path v-if="contour.width>.01" :d="frame.bodyPath" fill="none" :stroke="contour.color" :stroke-width="contour.width"/>
    </g>
    <g v-if="!frame.dotsBehind">
      <template v-for="(dot,i) in frame.dots" :key="i">
        <path v-if="dot.d" :d="dot.d" :transform="`translate(${dot.x} ${dot.y}) rotate(${dot.rot??0}) scale(${R})`" :fill="dotFill(dot)" :opacity="dot.opacity"/>
        <circle v-else :cx="dot.x" :cy="dot.y" :r="dot.r" :fill="dotFill(dot)" :opacity="dot.opacity"/>
      </template>
    </g>
    <circle v-if="frame.notif" :cx="frame.notif.x" :cy="frame.notif.y" :r="frame.notif.r" :fill="props.palette.amber"/>
    <g fill="none" stroke-linecap="round">
      <path v-for="arc in frame.arcs" :key="arc.id" :d="arc.front" :stroke="`url(#ring-${arc.id})`" :stroke-width="arc.width" :opacity="arc.opacity"/>
    </g>
  </svg>
</template>
