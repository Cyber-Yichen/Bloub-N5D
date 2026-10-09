<script setup lang="ts">
import { computed } from 'vue'
import { bubblesAt,pathAt,type Scene } from './scene'
import type { Palette } from './themes'
const props=defineProps<{scene:Scene;palette:Palette;music?:number;bubblesEnabled?:boolean}>()
const bubbles=computed(()=>props.bubblesEnabled===false?[]:bubblesAt(props.scene.t))
const trail=computed(()=>Array.from({length:8},(_,i)=>({...pathAt(props.scene.t-(i+1)*.18),i})))
</script>
<template>
  <svg class="atmosphere" viewBox="0 0 1600 720" aria-hidden="true">
    <circle v-for="(w,i) in scene.waves" :key="i" :cx="w.x" :cy="w.y" :r="w.radius"
      fill="none" :stroke="w.returning?palette.rim:palette.amber" stroke-width="3" :opacity="w.opacity*.5"/>
    <circle v-for="(b,i) in bubbles" :key="i" :cx="b.x" :cy="b.y" :r="b.r" :opacity="b.opacity" fill="#000" :stroke="palette.rim" stroke-width=".8"/>
    <g :fill="palette.amber" :opacity="scene.trail">
      <circle v-for="p in trail" :key="p.i" :cx="p.x" :cy="p.y" :r="7-p.i*.65" :opacity="(1-p.i/8)*.55"/>
    </g>
    <g :fill="palette.amber" :opacity="music??0" font-family="serif" font-size="34">
      <text v-for="i in 6" :key="i" :x="scene.x+(i%2?1:-1)*(170+i*12)+Math.sin(scene.t+i)*12"
        :y="scene.y-55-((scene.t/4+i*.17)%1)*170" :opacity="Math.sin(((scene.t/4+i*.17)%1)*Math.PI)">{{ i%2?'♪':'♫' }}</text>
    </g>
    <g :fill="palette.quiet"  :opacity="scene.sleep*.38">
      <circle v-for="i in 3" :key="i" :cx="scene.x+155+i*24" :cy="scene.y-110-i*39-scene.breath*7" :r="8-i*1.4"/>
    </g>
  </svg>
</template>
