<script setup lang="ts">
import { computed,onMounted,onBeforeUnmount,ref } from 'vue'
import Avatar from './Avatar.vue'
import Atmosphere from './Atmosphere.vue'
import { sampleScene,ringPoint,PERIOD } from './scene'
import { sampleLights } from './lighting'
import { THEMES } from './themes'
const time=ref(0),playing=ref(false),zoom=ref(1)
const palette=THEMES[0],scene=computed(()=>sampleScene(time.value))
const leds=computed(()=>sampleLights(scene.value,palette))
const rgb=(i:number)=>'rgb('+leds.value.rgb.slice(i*3,i*3+3).map(v=>v).join(',')+')'
const white=(i:number)=>'rgb('+[1,1,1].map(()=>leds.value.white[i]!).join(',')+')'
let raf=0,previous=0
function tick(ms:number){raf=requestAnimationFrame(tick);if(playing.value&&previous)time.value=(time.value+Math.min((ms-previous)/1000,.1))%PERIOD;previous=ms}
const resize=()=>zoom.value=Math.min(innerWidth/2600,(innerHeight-60)/840)
onMounted(()=>{resize();window.addEventListener('resize',resize);window.N5D?.enableLights(false);raf=requestAnimationFrame(tick);
  window.n5dRig={seek(t:number){time.value=t;playing.value=false},snapshot(){return {scene:scene.value,leds:leds.value}}}})
onBeforeUnmount(()=>{cancelAnimationFrame(raf);window.removeEventListener('resize',resize);delete window.n5dRig})
declare global{interface Window{n5dRig?:{seek(t:number):void;snapshot():unknown}}}
</script>
<template>
  <div class="rig" :style="{transform:'translate(-50%,-50%) scale('+zoom+')'}">
    <svg width="2600" height="840" class="rig-body">
      <rect x="30" y="36" width="2569" height="791" rx="55" fill="#c8c8c4"/>
      <circle cx="2210" cy="430" r="395" fill="#151b1c"/>
      <circle cx="2210" cy="430" r="325" fill="#242d2e"/>
      <g v-for="i in 24" :key="i">
        <circle :cx="60+ringPoint(i-1).x" :cy="70+ringPoint(i-1).y" r="27" :fill="rgb(i-1)" opacity=".45"/>
        <circle :cx="60+ringPoint(i-1).x" :cy="70+ringPoint(i-1).y" r="12" :fill="rgb(i-1)"/>
        <circle :cx="60+ringPoint(i-1,true).x" :cy="70+ringPoint(i-1,true).y" r="9" :fill="white(i-1)"/>
      </g>
      <text x="2120" y="440" fill="#758586" font-size="26">ToF / NFC</text>
      <text x="70" y="817" fill="#485556" font-size="23">1600 × 720 · 挖孔 (44,360)</text>
      <text x="1840" y="817" fill="#485556" font-size="23">环心 (2150,360) · 24 RGB + 24 W</text>
    </svg>
    <div class="rig-screen" :style="{background:palette.paper}">
      <Atmosphere :scene="scene" :palette="palette"/>
      <div class="companion-position" :style="{transform:'translate('+scene.x+'px,'+scene.y+'px) rotate('+scene.rotation+'deg) scale('+scene.scale*scene.squash+','+scene.scale/scene.squash+')'}">
        <Avatar :time="time" :local="scene.local" :shape="scene.shape" :state="scene.state" :expression="scene.expression" :yaw="scene.yaw" :pitch="scene.pitch" :palette="palette"/>
      </div>
      <svg class="aperture" viewBox="0 0 1600 720">
        <circle cx="44" cy="360" :r="25+58*scene.buddy" fill="#000"/>
        <clipPath id="buddy-clip"><circle cx="44" cy="360" :r="25+scene.buddy*58"/></clipPath>
        <g clip-path="url(#buddy-clip)" :opacity="scene.buddy" fill="white"><ellipse cx="83" cy="354" rx="5.5" ry="10"/><ellipse cx="105" cy="354" rx="5.5" ry="10"/></g>
        <circle cx="44" cy="360" r="25" fill="#000"/>
      </svg>
    </div>
  </div>
  <div class="rig-controls"><button @click="playing=!playing">{{ playing?'暂停':'播放' }}</button><input v-model.number="time" type="range" min="0" :max="PERIOD" step=".1"/><span>{{ time.toFixed(1) }}s · {{ scene.label }}</span></div>
</template>
<style>
.rig{position:absolute;width:2600px;height:840px;left:50%;top:48%;transform-origin:center}.rig-body{position:absolute;inset:0}.rig-screen{position:absolute;left:60px;top:70px;width:1600px;height:720px;overflow:hidden;border-radius:60px}.rig-controls{position:absolute;bottom:0;left:0;width:100%;display:flex;align-items:center;gap:16px;padding:8px 20px;background:#1b3039;color:#c2d8d4}.rig-controls button{padding:8px 16px;min-height:40px}.rig-controls input{flex:1}.rig-controls span{min-width:180px;font-size:14px}
</style>
