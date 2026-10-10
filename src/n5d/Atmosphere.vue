<script setup lang="ts">
import { computed } from 'vue'
import { bubblesAt,pathAt,type Scene } from './scene'
import type { Palette } from './themes'
const props=defineProps<{scene:Scene;palette:Palette;music?:number;bubblesEnabled?:boolean}>()
const bubbles=computed(()=>props.bubblesEnabled===false?[]:bubblesAt(props.scene.t,{x:props.scene.homeX,y:props.scene.homeY}))
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
    <g :fill="palette.body" :stroke="palette.body" :opacity="music??0" stroke-linecap="round" stroke-linejoin="round" class="music-notes">
      <g v-for="i in 5" :key="i" :transform="'translate('+(scene.x+(i%2?1:-1)*(175+i*18)+Math.sin(scene.t+i)*10)+' '+(scene.y-35-((scene.t/5+i*.21)%1)*160)+') rotate('+(Math.sin(scene.t+i)*9)+') scale('+(.8+(music??0)*.25)+')'" :opacity="Math.sin(((scene.t/5+i*.21)%1)*Math.PI)">
        <ellipse cx="-6" cy="8" rx="8" ry="6" stroke="none" transform="rotate(-18)"/>
        <path d="M 0 7 V -16 Q 10 -15 12 -7" fill="none" stroke-width="5.5"/>
        <g v-if="i%2===0"><ellipse cx="18" cy="4" rx="8" ry="6" stroke="none" transform="rotate(-18 18 4)"/><path d="M 24 3 V -20 M 0 -16 Q 12 -22 24 -20" fill="none" stroke-width="5.5"/></g>
      </g>
    </g>
    <g :fill="palette.quiet"  :opacity="scene.sleep*.38">
      <circle v-for="i in 3" :key="i" :cx="scene.x+155+i*24" :cy="scene.y-110-i*39-scene.breath*7" :r="8-i*1.4"/>
    </g>
  </svg>
</template>
