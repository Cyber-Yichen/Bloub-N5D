<script setup lang="ts">
defineProps<{offset:number}>()
const emit=defineEmits<{offset:[value:number];test:[];close:[]}>()
</script>
<template>
  <section class="ring-calibration" role="dialog" aria-label="灯环位置校准">
    <header class="dock-head"><div><span class="eyebrow">BLOUB / ALIGNMENT</span><h1>让黑影对准灯环左侧</h1></div><button class="close" @click="emit('close')" aria-label="关闭校准">×</button></header>
    <p>面对设备正面，点击测试；观察实体灯环的黑影是否位于 9 点方向。</p>
    <svg viewBox="0 0 300 180" aria-hidden="true"><circle cx="150" cy="90" r="65" fill="none" stroke="#3976cb" stroke-width="18"/><circle cx="150" cy="90" r="65" fill="none" stroke="#151d28" stroke-width="19" stroke-dasharray="28 381" transform="rotate(168 150 90)" stroke-linecap="round"/><path d="M 28 90 H 70" stroke="#667587" stroke-width="2"/><text x="12" y="120" fill="#667587" font-size="14">9 点</text></svg>
    <label class="brightness"><span>黑影角度偏移</span><input type="range" min="-180" max="180" step="1" :value="offset" aria-label="黑影角度偏移" @input="emit('offset',Number(($event.target as HTMLInputElement).value))"/><b>{{ offset }}°</b></label>
    <div class="calibration-actions"><button @click="emit('test')">测试 6 秒</button><button @click="emit('offset',0)">恢复测量值</button><button @click="emit('close')">保存并返回</button></div>
    <p class="privacy-note">测试时临时接管灯环；六秒后自动归还。角度会保存到本机，后续转圈沿用此校准。</p>
  </section>
</template>
