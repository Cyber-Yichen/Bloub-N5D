<script setup lang="ts">
import { onMounted,onBeforeUnmount,ref } from 'vue'
import PresenceChart from './PresenceChart.vue'
import SeatCalibration from './SeatCalibration.vue'
import type { PresenceInfo } from './presence'
const props=defineProps<{camera:boolean;native:boolean}>()
const info=ref<PresenceInfo|null>(null),calibrate=ref(false),error=ref('')
let timer:ReturnType<typeof setInterval>
function refresh(){try{if(window.N5D)info.value=JSON.parse(window.N5D.seatInfo())}catch{error.value='暂时读不到工位记录'}}
function toggle(){if(!props.camera){error.value='先在感知页开启相机';return}window.N5D?.seatEnabled(!info.value?.enabled);setTimeout(refresh,200)}
function configure(){if(!props.camera){error.value='先在感知页开启相机';return}error.value='';calibrate.value=true}
onMounted(()=>{refresh();timer=setInterval(refresh,2000)})
onBeforeUnmount(()=>clearInterval(timer))
</script>
<template><div class="seat-dock">
  <div class="seat-tools"><button :disabled="!native" @click="toggle" :aria-pressed="info?.enabled"><span>工位观察</span><i class="switch" :class="{on:info?.enabled}"/></button><button :disabled="!native" class="seat-configure" @click="configure">选择区域 / 纠正结果</button></div>
  <div class="seat-summary"><svg viewBox="0 0 140 100" aria-hidden="true"><path d="M40 50 V24 Q40 12 54 12 H89 Q101 12 101 24 V50 M32 52 H108 V72 H32 Z M43 72 V90 M97 72 V90" fill="none" stroke="#667587" stroke-width="5" stroke-linecap="round"/><circle v-if="info?.state==='occupied'" cx="70" cy="33" r="17" fill="#3976cb"/><circle v-else cx="70" cy="33" r="11" fill="none" stroke="#becad6" stroke-width="3" stroke-dasharray="4 5"/></svg><div><strong>{{ info?.status??'在设备上开启工位观察' }}</strong><p>{{ error||(!info?.ready?'选择座位区域，分别确认一次空位和在位。':info?.message) }}</p></div><span v-if="info?.ready">已校准</span></div>
  <PresenceChart v-if="info" :days="info.days" :timezone="info.timezone"/>
  <Teleport to="body"><SeatCalibration v-if="calibrate&&info" :info="info" @close="calibrate=false;refresh()"/></Teleport>
</div></template>
