<script setup lang="ts">
import { computed, ref } from 'vue'
import { clock,duration,intervalStyle,type PresenceDay } from './presence'
const props=defineProps<{days:PresenceDay[];timezone:string}>()
const selected=ref('')
const day=computed(()=>props.days.find(d=>d.date===selected.value)??props.days.at(-1))
const max=computed(()=>Math.max(3600000,...props.days.map(d=>d.occupiedMs)))
</script>
<template><div class="presence-chart" v-if="day">
  <div class="presence-day-title"><strong>{{ day.date===days.at(-1)?.date?'今日':day.date }}在位 <b>{{ duration(day.occupiedMs) }}</b></strong><span>首次 {{ clock(day.firstOccupiedAt,timezone) }}</span></div>
  <div class="presence-track" role="img" :aria-label="`${day.date}座位状态时间轴`"><span v-for="(part,i) in day.intervals" :key="i" :class="part.state" :style="intervalStyle(part,day)" :title="`${clock(part.start,timezone)}–${clock(part.end,timezone)} ${part.state==='occupied'?'有人':part.state==='empty'?'无人':'待确认'}`"/></div>
  <div class="presence-hours"><span v-for="hour in [0,4,8,12,16,20,24]" :key="hour">{{ hour.toString().padStart(2,'0') }}</span></div>
  <div class="presence-legend"><span><i class="occupied"/>有人</span><span><i class="empty"/>无人</span><span><i/>未观察 / 待确认</span><span class="presence-estimate">估算时间</span></div>
  <div class="presence-week" aria-label="最近七天在位时间"><button v-for="d in days" :key="d.date" :aria-pressed="day.date===d.date" :title="`${d.date} ${duration(d.occupiedMs)}`" @click="selected=d.date"><small>{{ duration(d.occupiedMs) }}</small><div><i :style="{height:Math.max(d.occupiedMs>0?3:0,d.occupiedMs/max*100)+'%'}"/></div><span>{{ d.date.slice(5) }}</span></button></div>
</div></template>
