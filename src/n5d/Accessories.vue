<script setup lang="ts">
import type { Scene } from './scene'
import type { Palette } from './themes'
defineProps<{scene:Scene;palette:Palette}>()
</script>
<template>
  <svg class="atmosphere" viewBox="0 0 1600 720" aria-hidden="true">
    <g v-if="scene.accessory" :transform="`translate(${scene.x} ${scene.y})`" :stroke="palette.body" :fill="palette.body" stroke-linecap="round" stroke-linejoin="round">
      <g v-if="scene.accessory==='camera'" :transform="`translate(110 60) rotate(${Math.sin(scene.t*2)*4})`"><path d="M-38-16h17l6-13h25l6 13h22v50h-76z" :fill="palette.paper" stroke-width="8"/><circle cx="0" cy="10" r="17" :fill="palette.paper" stroke-width="7"/><circle cx="27" cy="-5" r="4" stroke="none"/><path d="M-65 25q12-12 25-9" fill="none" stroke-width="9"/><path d="M46-33l8-16m2 28 17-7" fill="none" stroke-width="5" :opacity="Math.max(0,Math.sin(scene.t*4))"/></g>
      <g v-else-if="scene.accessory==='hello'" :transform="`translate(150 -55) rotate(${Math.sin(scene.t*5)*16})`"><path d="M0 48V4q0-10 8-10t8 10v17-39q0-10 8-10t8 10v38-30q0-10 8-10t8 10v30-20q0-10 8-10t8 10v41q-4 27-31 27h-5Q5 70 0 48Z" :fill="palette.paper" stroke-width="7"/></g>
      <g v-else :transform="`translate(165 -140) rotate(${Math.sin(scene.t*2)*7})`"><path v-if="scene.accessory==='surprise'" d="M0-26v32" fill="none" stroke-width="11"/><path v-else d="M-9-22q0-17 17-17t17 15q0 11-17 18v12" fill="none" stroke-width="8"/><circle :cx="scene.accessory==='surprise'?0:8" cy="23" r="6" stroke="none"/></g>
    </g>
  </svg>
</template>
