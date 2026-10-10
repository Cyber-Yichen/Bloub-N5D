<script setup lang="ts">
import GalleryDock from './GalleryDock.vue'
import SeatDock from './SeatDock.vue'
import { ref } from 'vue'
import { THEMES,type ThemeId } from './themes'
import type { SensorData } from './reactions'
import { PROJECT } from './project'
defineProps<{calm:boolean;lights:boolean;brightness:number;theme:ThemeId;mic:boolean;camera:boolean;cameraId:string;quietStart:string;quietEnd:string;tof:boolean;sensors:SensorData;status:string;native:boolean}>()
const emit=defineEmits<{quietHours:[start:string,end:string];cameraSource:[id:string];calibrate:[];close:[];calm:[];lights:[];brightness:[value:number];theme:[id:ThemeId];sensor:[id:'mic'|'camera'|'tof'];ring:[];project:[]}>()
const tab=ref<'companion'|'senses'|'gallery'|'seat'|'about'>('companion')
</script>
<template>
  <section class="controls" role="dialog" aria-modal="true" aria-label="Bloub 桌边设置" @pointerdown.stop @pointerup.stop>
    <aside class="dock-rail">
      <div class="wordmark">bloub<span>桌边</span></div>
      <svg class="desk-signature" viewBox="0 0 220 165" aria-hidden="true">
        <rect x="8" y="52" width="204" height="65" rx="10" fill="#dce5ec"/>
        <rect x="16" y="60" width="132" height="49" rx="6" fill="#fff"/>
        <circle cx="20" cy="84" r="2.2" fill="#151d28"/>
        <path d="M 66 90 C 52 66 91 63 95 84 C 99 104 73 108 66 90" fill="#151d28"/>
        <path d="M 76 82 v5 M 85 82 v5" stroke="white" stroke-width="2.5" stroke-linecap="round"/>
        <circle cx="178" cy="84" r="25" fill="none" stroke="#3976cb" stroke-width="8"/>
        <circle class="signature-shadow" cx="178" cy="84" r="25" fill="none" stroke="#151d28" stroke-width="9" stroke-dasharray="23 134" stroke-linecap="round"/>
      </svg>

      <nav aria-label="设置分类">
        <button :aria-current="tab==='companion'?'page':undefined" @click="tab='companion'"><span>陪伴</span></button>
        <button :aria-current="tab==='senses'?'page':undefined" @click="tab='senses'"><span>感知</span></button>
        <button :aria-current="tab==='gallery'?'page':undefined" @click="tab='gallery'"><span>图库</span></button>
        <button :aria-current="tab==='seat'?'page':undefined" @click="tab='seat'"><span>工位</span></button>
        <button :aria-current="tab==='about'?'page':undefined" @click="tab='about'"><span>关于</span></button>
      </nav>
      <span class="rail-version">N5D · v{{ PROJECT.version }}</span>
    </aside>
    <div class="dock-content">
      <header class="dock-head">
        <div><span class="eyebrow">{{ tab==='about'?'BLOUB / OPEN SOURCE':tab==='gallery'?'BLOUB / GALLERY':tab==='senses'?'BLOUB / SENSES':'BLOUB / COMPANION' }}</span>
          <h1>{{ tab==='about'?'一个会探索的桌边伙伴':tab==='gallery'?'图库':tab==='seat'?'工位':tab==='senses'?'让它感受桌边的动静':'按你的节奏陪伴' }}</h1>
        </div>
        <button class="close" @click="emit('close')" aria-label="收起设置">×</button>
      </header>
      <div v-if="tab==='companion'" class="dock-body">
        <div class="settings-row">
          <button class="setting-card" :aria-pressed="!calm" @click="emit('calm')">
            <span>陪伴方式</span><strong>{{ calm?'安静陪伴':'自由探索' }}</strong>

          </button>
          <button class="setting-card" :aria-pressed="lights" @click="emit('lights')">
            <span>灯环互动 <i class="switch" :class="{on:lights}"/></span><strong>{{ lights?'让小影子去转两圈':'只在屏幕里玩' }}</strong>

          </button>
        </div>
        <div class="section-title"><h2>背景与伙伴</h2></div>
        <div class="theme-row" aria-label="背景与 Bot 配色">
          <button v-for="option in THEMES" :key="option.id" :aria-pressed="theme===option.id" @click="emit('theme',option.id)">
            <i :style="{background:option.paper}"><b :style="{background:option.body}"/></i><span>{{ option.name }}</span>
          </button>
          <button :aria-pressed="theme==='auto'" @click="emit('theme','auto')"><i class="auto-swatch">↻</i><span>自动轮换</span></button>
        </div>
        <label class="brightness"><span>屏幕亮度</span><input :value="brightness" type="range" min="15" max="100" aria-label="屏幕亮度" @input="emit('brightness',Number(($event.target as HTMLInputElement).value))"/><b>{{ brightness }}%</b></label>
      </div>
      <div v-else-if="tab==='senses'" class="dock-body">
        <div class="sense-list">
          <button :disabled="!native" :aria-pressed="mic" @click="emit('sensor','mic')"><div><strong>音乐耳朵</strong><span>听到音乐，左右摇摆，冒出音符。</span><small>{{ mic?(native?sensors.micStatus:'设备上开启'):'已关闭' }}</small></div><i class="switch" :class="{on:mic}"/></button>
          <div class="camera-card"><button :disabled="!native" :aria-pressed="camera" @click="emit('sensor','camera')"><div><strong>偶尔看看</strong><span>看看桌边，记录周围的画面。</span><small>{{ camera?sensors.cameraStatus:'已关闭' }}</small></div><i class="switch" :class="{on:camera}"/></button><select class="camera-choice" :disabled="!native" :value="cameraId" aria-label="相机通道" @change="emit('cameraSource',($event.target as HTMLSelectElement).value)"><option value="2">相机 2</option><option value="3">相机 3</option></select></div>
          <button :disabled="!native" :aria-pressed="tof" @click="emit('sensor','tof')"><div><strong>靠近感应</strong><span>靠近灯环中间，它会探头打招呼。</span><small>{{ tof?(sensors.distance>=0?sensors.distance+' mm':sensors.tofStatus):'已关闭' }}</small></div><i class="switch" :class="{on:tof}"/></button>
          <div class="night-card"><strong>夜间休息</strong><div class="quiet-times"><label>开始<input type="time" :value="quietStart" aria-label="夜间休息开始" @change="emit('quietHours',($event.target as HTMLInputElement).value,quietEnd)"/></label><label>结束<input type="time" :value="quietEnd" aria-label="夜间休息结束" @change="emit('quietHours',quietStart,($event.target as HTMLInputElement).value)"/></label></div><small>听到声音，短时观察。</small></div>
        </div>

      </div>
      <div v-else-if="tab==='gallery'" class="dock-body"><GalleryDock/></div>
      <div v-else-if="tab==='seat'" class="dock-body"><SeatDock :camera="camera" :native="native"/></div>
      <div v-else class="dock-body about-body">
        <div class="about-intro"><span class="version-pill">v{{ PROJECT.version }} · MIT</span><p>屏幕里的伙伴，灯环上的小影子。</p></div>
        <button class="github-card" @click="emit('project')" aria-label="打开 Bloub-N5D GitHub 项目"><span><strong>GitHub 开源项目</strong><small>Cyber-Yichen / Bloub-N5D</small></span><b>↗</b></button>
        <p class="project-url">{{ PROJECT.github }}</p>
        <div class="credits"><div><span>动画核心</span><strong>Jérémy Perret / Bloub</strong><small>MIT · github.com/jeremy-prt/bloub</small></div><div><span>实体灯环</span><strong>N5D RingStudio</strong><small>公共控制接口 · 独立管理灯光</small></div><div><span>音乐感知</span><strong>YAMNet / TensorFlow Lite</strong><small>Apache 2.0 · 本机处理</small></div></div>

      </div>
      <footer class="panel-foot"><div class="panel-tools"><button v-if="tab==='companion'" class="link" @click="emit('calibrate')">灯环位置校准 ↗</button><button v-if="native" class="link" @click="emit('ring')">灯环工坊 ↗</button><span v-else>1600 × 720</span></div></footer>
    </div>
  </section>
</template>
