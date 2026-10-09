<script setup lang="ts">
import { ref } from 'vue'
import { THEMES,type ThemeId } from './themes'
import type { SensorData } from './reactions'
import { PROJECT } from './project'
defineProps<{calm:boolean;lights:boolean;brightness:number;theme:ThemeId;mic:boolean;camera:boolean;tof:boolean;sensors:SensorData;status:string;native:boolean}>()
const emit=defineEmits<{close:[];calm:[];lights:[];brightness:[value:number];theme:[id:ThemeId];sensor:[id:'mic'|'camera'|'tof'];ring:[];project:[]}>()
const tab=ref<'companion'|'senses'|'about'>('companion')
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
      <p class="rail-note">屏幕里的伙伴<br/>灯环上的小影子</p>
      <nav aria-label="设置分类">
        <button :aria-current="tab==='companion'?'page':undefined" @click="tab='companion'"><span>陪伴</span><small>动作与配色</small></button>
        <button :aria-current="tab==='senses'?'page':undefined" @click="tab='senses'"><span>感知</span><small>听听 · 看看 · 靠近</small></button>
        <button :aria-current="tab==='about'?'page':undefined" @click="tab='about'"><span>关于</span><small>版本与开源项目</small></button>
      </nav>
      <span class="rail-version">N5D · v{{ PROJECT.version }}</span>
    </aside>
    <div class="dock-content">
      <header class="dock-head">
        <div><span class="eyebrow">{{ tab==='about'?'BLOUB / OPEN SOURCE':tab==='senses'?'BLOUB / SENSES':'BLOUB / COMPANION' }}</span>
          <h1>{{ tab==='about'?'一个会探索的桌边伙伴':tab==='senses'?'让它感受桌边的动静':'按你的节奏陪伴' }}</h1>
        </div>
        <button class="close" @click="emit('close')" aria-label="收起设置">×</button>
      </header>
      <div v-if="tab==='companion'" class="dock-body">
        <div class="settings-row">
          <button class="setting-card" :aria-pressed="!calm" @click="emit('calm')">
            <span>陪伴方式</span><strong>{{ calm?'安静陪伴':'自由探索' }}</strong>
            <small>{{ calm?'留在身边，轻轻呼吸':'变形、钻洞，偶尔去灯环玩' }}</small>
          </button>
          <button class="setting-card" :aria-pressed="lights" @click="emit('lights')">
            <span>灯环互动 <i class="switch" :class="{on:lights}"/></span><strong>{{ lights?'让小影子去转两圈':'只在屏幕里玩' }}</strong>
            <small>互动结束，灯环继续原来的灯效</small>
          </button>
        </div>
        <div class="section-title"><h2>背景与伙伴</h2><span>换色会慢慢过渡</span></div>
        <div class="theme-row" aria-label="背景与 Bot 配色">
          <button v-for="option in THEMES" :key="option.id" :aria-pressed="theme===option.id" @click="emit('theme',option.id)">
            <i :style="{background:option.paper}"><b :style="{background:option.body}"/></i><span>{{ option.name }}</span>
          </button>
          <button :aria-pressed="theme==='auto'" @click="emit('theme','auto')"><i class="auto-swatch">↻</i><span>自动轮换</span></button>
        </div>
        <label class="brightness"><span>屏幕亮度</span><input :value="brightness" type="range" min="15" max="75" aria-label="屏幕亮度" @input="emit('brightness',Number(($event.target as HTMLInputElement).value))"/><b>{{ brightness }}%</b></label>
      </div>
      <div v-else-if="tab==='senses'" class="dock-body">
        <p class="section-description">需要时打开，用完随时关闭。</p>
        <div class="sense-list">
          <button :disabled="!native" :aria-pressed="mic" @click="emit('sensor','mic')"><div><strong>音乐耳朵</strong><span>听到音乐，左右摇摆，冒出音符。</span><small>{{ native?sensors.micStatus:'在 N5D 上开启麦克风' }}</small></div><i class="switch" :class="{on:mic}"/></button>
          <button :disabled="!native" :aria-pressed="camera" @click="emit('sensor','camera')"><div><strong>偶尔看看</strong><span>每两分钟看看周围五秒，跟随动静和脸的方向。</span><small>{{ native?sensors.cameraStatus:'在 N5D 上开启相机' }}</small></div><i class="switch" :class="{on:camera}"/></button>
          <button :disabled="!native" :aria-pressed="tof" @click="emit('sensor','tof')"><div><strong>靠近感应</strong><span>把手靠近灯环中间，它会探头打个招呼。</span><small>{{ sensors.distance>=0?sensors.distance+' mm':native?sensors.tofStatus:'在 N5D 上连接距离传感器' }}</small></div><i class="switch" :class="{on:tof}"/></button>
        </div>
        <p class="privacy-note">声音和画面只在本机内存分析，不保存、不上传。切到后台即停止感知。</p>
      </div>
      <div v-else class="dock-body about-body">
        <div class="about-intro"><span class="version-pill">v{{ PROJECT.version }} · MIT</span><p>Bloub 会变形、躲进小窝、吞下泡泡，<br/>也会化成灯环上的黑色影子，转两圈再回来。</p></div>
        <button class="github-card" @click="emit('project')" aria-label="打开 Bloub-N5D GitHub 项目"><span><strong>GitHub 开源项目</strong><small>Cyber-Yichen / Bloub-N5D</small></span><b>↗</b></button>
        <p class="project-url">{{ PROJECT.github }}</p>
        <div class="credits"><div><span>动画核心</span><strong>Jérémy Perret / Bloub</strong><small>MIT · github.com/jeremy-prt/bloub</small></div><div><span>实体灯环</span><strong>N5D RingStudio</strong><small>公共控制接口 · 独立管理灯光</small></div><div><span>音乐感知</span><strong>YAMNet / TensorFlow Lite</strong><small>Apache 2.0 · 本机处理</small></div></div>
        <p class="about-note">按 N5D 的屏幕、挖孔和灯环尺寸制作。轻触打招呼，长按打开设置。</p>
      </div>
      <footer class="panel-foot"><p role="status">{{ status }}</p><button v-if="native" class="link" @click="emit('ring')">灯环工坊 ↗</button><span v-else>1600 × 720</span></footer>
    </div>
  </section>
</template>
