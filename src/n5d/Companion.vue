<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { PetDirector } from './director'
import Avatar from './Avatar.vue'
import { stagePoint,TouchAttention,type Point } from './interaction'
import RingCalibration from './RingCalibration.vue'
import SettingsDock from './SettingsDock.vue'
import { PROJECT } from './project'
import Atmosphere from './Atmosphere.vue'
import { sampleLights } from './lighting'
import { reactScene,emptySensors,type SensorData } from './reactions'
import { GEOMETRY, sampleScene, externalPose, smooth, type Scene, type CompanionState } from './scene'
import { paletteAt, blendPalette, dvdPalette, type ThemeId, type Palette } from './themes'

interface NativeBridge { seatInfo():string;seatFrame():string;seatEnabled(value:boolean):void;seatPreview(value:boolean):void;seatRegion(left:number,top:number,right:number,bottom:number):void;seatLabel(kind:string):string; frame(json: string): void; sensors(mic:boolean,camera:boolean,tof:boolean):void; enableLights(on: boolean): void; brightness(value: number): void; ready(): void; galleryList(before:number):string;galleryServerInfo():string;galleryServerEnabled(value:boolean):void;copyGalleryAddress():void;cameraInfo():string;cameraSource(id:string):void;quietHours(start:number,end:number):void;galleryEnabled(value:boolean):void;galleryDelete(id:string):boolean;galleryClear():void;openRingStudio(): void; openProject():void }
declare global { interface Window {
  N5D?: NativeBridge;
  n5dStatus?: (code:string) => void;
  n5dSensors?:(data:SensorData)=>void;
  companion?: { setState(state:CompanionState,duration?:number):void; reset():void };
  n5dDebug?: { seek(t:number):void; random(t:number):void; resume():void; snapshot():unknown };
} }
const load = (key:string,fallback:string) => { try{return localStorage.getItem(key)??fallback}catch{return fallback} }
const save = (key:string,value:string) => { try{localStorage.setItem(key,value)}catch{/* Private browser preview. */} }
const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
const native = window.N5D
const ringOnline=ref(false),ringBlend=ref(0)
const calm = ref(load('n5d.calm',String(reduced))==='true')
const lights = ref(load('n5d.lights','true')==='true')
const mic=ref(load('n5d.mic','false')==='true'),camera=ref(load('n5d.camera','false')==='true'),tof=ref(load('n5d.tof','false')==='true')
const cameraId=ref(load('n5d.cameraId','2'))
const quietStart=ref(load('n5d.quietStart','23:00')),quietEnd=ref(load('n5d.quietEnd','08:00'))
const minutes=(time:string)=>Number(time.slice(0,2))*60+Number(time.slice(3,5))
function changeQuietHours(start:string,end:string){if(!/^([01]\d|2[0-3]):[0-5]\d$/.test(start)||!/^([01]\d|2[0-3]):[0-5]\d$/.test(end))return;quietStart.value=start;quietEnd.value=end;save('n5d.quietStart',start);save('n5d.quietEnd',end);window.N5D?.quietHours(minutes(start),minutes(end))}
function changeCamera(id:string){if(id!=='2'&&id!=='3')return;cameraId.value=id;save('n5d.cameraId',id);window.N5D?.cameraSource(id)}
const sensorData=ref<SensorData>({...emptySensors}),musicStrength=ref(0),nearStrength=ref(0)
let sensorAt=-10000,lastHello=-10000
function configureSensors(){window.N5D?.quietHours(minutes(quietStart.value),minutes(quietEnd.value));window.N5D?.cameraSource(cameraId.value);for(const [key,value] of [['mic',mic.value],['camera',camera.value],['tof',tof.value]] as const)save('n5d.'+key,String(value));window.N5D?.sensors(mic.value,camera.value,tof.value)}
function toggleSensor(kind:'mic'|'camera'|'tof'){const option={mic,camera,tof}[kind];option.value=!option.value;configureSensors()}

const savedTheme=load('n5d.theme','paper')
const theme=ref<ThemeId>(['paper','lagoon','sand','night','auto'].includes(savedTheme)?savedTheme as ThemeId:'paper')
const themeChange=ref<{at:number;from:Palette}|null>(null)
const brightness = ref(Number(load('n5d.brightness','42')))
const calibration=ref(false),testUntil=ref(-1)
const panel = ref(false), hint = ref(true), status = ref(window.N5D ? '正在连接灯环' : '浏览器预览 · 灯光在 N5D 上运行')
const seed=Math.floor(Math.random()*0x7fffffff)
const director=new PetDirector(seed),canonical=ref(false)
const ringOffset=ref(Number(load("n5d.ringOffset","0"))||0)
const stage=ref<HTMLDivElement|null>(null),avatar=ref<InstanceType<typeof Avatar>|null>(null)
const attention=new TouchAttention(),look=ref({yaw:12,pitch:8,weight:0})
const taps=ref<(Point&{at:number;id:number})[]>([]),pokeAt=ref(-100)
let tapId=0
let gesture:{id:number;body:boolean;moved:boolean}|null=null
const paletteClock=ref(0)
const clock = ref(0), zoom = ref(1), interactionUntil = ref(-1), userState = ref<CompanionState|null>(null)
const palette=computed(()=>{
  const target=paletteAt(theme.value,paletteClock.value),transition=themeChange.value
  return transition?blendPalette(transition.from,target,smooth((paletteClock.value-transition.at)/3)):target
})
let userUntil=0, raf=0, previous=0, rendered=0, lastLight=0, paused=false, hold=0, hintTimer=0, downX=0, downY=0, longPressed=false
const modeChange=ref<{at:number;from:Scene}|null>(null)
const story=computed(()=>{
  const target=canonical.value?sampleScene(clock.value,calm.value,0):director.sample(clock.value,calm.value), transition=modeChange.value
  if(!transition)return target
  const u=smooth((clock.value-transition.at)/3)
  for(const key of ['x','y','scale','portal','sleep','yaw','pitch','rotation','squash','trail','buddy','growth','dvd'] as const)target[key]=transition.from[key]+(target[key]-transition.from[key])*u
  return target
})
const scene=computed(()=>reactScene(story.value,clock.value,musicStrength.value,nearStrength.value,sensorData.value,calm.value))
const bodyPalette=computed(()=>dvdPalette(palette.value,scene.value.t,scene.value.dvd,scene.value.variant,{x:scene.value.homeX,y:scene.value.homeY}))
const musicShown=computed(()=>!story.value.dvd&&!calm.value&&!story.value.lightActive&&story.value.portal<.05&&story.value.sleep<.5&&story.value.x>500&&story.value.x<1250?musicStrength.value:0)
const pose=computed(()=>{
  if(userState.value)return externalPose(userState.value)
  // Finish one-shot transformations before accepting a new greeting.
  if(scene.value.state==='idle'&&clock.value<interactionUntil.value)return {state:'wink' as const,expression:'heureux' as const}
  return look.value.weight>.15&&scene.value.state==='idle'?{...scene.value,expression:'attentif' as const}:scene.value
})
const gaze=computed(()=>look.value.weight>0?look.value:{yaw:scene.value.yaw,pitch:scene.value.pitch,weight:0})
const poke=computed(()=>{
  const age=(paletteClock.value-pokeAt.value)/.75
  return !reduced&&!calm.value&&age>0&&age<1?1+.065*Math.sin(age*Math.PI)*Math.sin(age*Math.PI):1
})
const portalOpacity=computed(()=>.2+.7*scene.value.portal)
// Without a working light session the excursion stays visible near the right edge.
const displayPosition=computed(()=>{
  const s=scene.value
  if(s.dvd>0||!s.lightActive||s.x<=1230)return {x:s.x,y:s.y}
  const u=smooth((s.x-1230)/400)
  const x=1230+(s.x-1230)/(1+((s.x-1230)/160)**3)**(1/3),y=360+(s.y-360)*(1-.6*u)
  return {x:x+(s.x-x)*ringBlend.value,y:y+(s.y-y)*ringBlend.value}
})
const bodyStyle=computed(()=>({transform:`translate(${displayPosition.value.x}px,${displayPosition.value.y}px) rotate(${scene.value.rotation}deg) scale(${scene.value.scale*scene.value.squash*poke.value},${scene.value.scale/scene.value.squash/poke.value})`}))

const resize=()=>{zoom.value=Math.min(innerWidth/GEOMETRY.width,innerHeight/GEOMETRY.height)}
function toggleCalm(){modeChange.value={at:clock.value,from:{...scene.value}};calm.value=!calm.value;save('n5d.calm',String(calm.value))}
function toggleLights(){lights.value=!lights.value;save('n5d.lights',String(lights.value));window.N5D?.enableLights(lights.value)}
function changeBrightness(value:number){brightness.value=Math.max(15,Math.min(100,value));setBrightness()}
function openProject(){if(native)native.openProject();else window.open(PROJECT.github,'_blank','noopener,noreferrer')}
function setBrightness(){save('n5d.brightness',String(brightness.value));window.N5D?.brightness(brightness.value/100)}
function setOffset(value:number){ringOffset.value=Math.max(-180,Math.min(180,value));save("n5d.ringOffset",String(ringOffset.value))}
function finishTest(){testUntil.value=-1;window.N5D?.enableLights(lights.value);window.N5D?.frame(JSON.stringify(sampleLights({...scene.value,lightActive:false,lightMix:0},palette.value,ringOffset.value)))}
function closeCalibration(){calibration.value=false;finishTest()}
function testRing(){window.N5D?.enableLights(true);testUntil.value=paletteClock.value+6}
function setTheme(id:ThemeId){themeChange.value={at:paletteClock.value,from:{...palette.value}};theme.value=id;save('n5d.theme',id)}
function touchPoint(e:PointerEvent){return stage.value?stagePoint({x:e.clientX,y:e.clientY},stage.value.getBoundingClientRect()):null}
function ripple(point:Point){taps.value=[...taps.value.slice(-3),{...point,at:paletteClock.value,id:++tapId}]}
function pointerDown(e:PointerEvent){
  if(panel.value||gesture||!e.isPrimary||e.button!==0)return
  const point=touchPoint(e);if(!point)return
  downX=e.clientX;downY=e.clientY;longPressed=false
  gesture={id:e.pointerId,body:avatar.value?.hitTest(e.clientX,e.clientY)??false,moved:false}
  ;(e.currentTarget as HTMLElement).setPointerCapture?.(e.pointerId)
  if(!gesture.body){attention.aim(point,paletteClock.value,true);ripple(point)}
  hold=window.setTimeout(()=>{
    longPressed=true;panel.value=true;hint.value=false
    attention.cancel(paletteClock.value);gesture=null;taps.value=[]
  },650)
}
function pointerMove(e:PointerEvent){
  if(!gesture||gesture.id!==e.pointerId||panel.value)return
  if(Math.hypot(e.clientX-downX,e.clientY-downY)>20){gesture.moved=true;clearTimeout(hold)}
  if(gesture.moved){const point=touchPoint(e);if(point)attention.aim(point,paletteClock.value,true)}
}
function cancelHold(){clearTimeout(hold)}
function cancelGesture(){cancelHold();if(gesture){attention.cancel(paletteClock.value);gesture=null}}
function pointerUp(e:PointerEvent){
  cancelHold();if(!gesture||gesture.id!==e.pointerId)return
  const ended=gesture;gesture=null
  if(longPressed||panel.value)return
  if(ended.body&&!ended.moved){
    attention.cancel(paletteClock.value)
    interactionUntil.value=clock.value+2.2
    if(!scene.value.lightActive&&scene.value.portal<.05&&scene.value.state==='idle')pokeAt.value=paletteClock.value
  }else attention.release(paletteClock.value)
}
function key(e:KeyboardEvent){if(e.key==='Escape'){panel.value=!panel.value;if(!panel.value)closeCalibration();cancelGesture();attention.cancel(paletteClock.value);taps.value=[]}else if(e.key===' '&&!panel.value){e.preventDefault();interactionUntil.value=clock.value+2.2}}
function tick(ms:number){
  raf=requestAnimationFrame(tick)
  if(document.hidden){previous=ms;return}
  // 30 fps drawing is ample for a quiet desk companion; no catch-up after suspension.
  if(ms-rendered<1000/30-1)return
  const dt=previous?Math.min((ms-previous)/1000,.1):0
  previous=ms;rendered=ms
  paletteClock.value+=dt
  if(testUntil.value>0&&testUntil.value<=paletteClock.value)finishTest()
  if(!paused&&!panel.value)clock.value+=dt
  ringBlend.value+=((ringOnline.value&&lights.value?1:0)-ringBlend.value)*(1-Math.exp(-dt/.8))
  const fresh=ms-sensorAt<2200,data=sensorData.value
  const musicTarget=fresh&&mic.value?Math.max(0,Math.min(1,(data.music-.14)/.42)):0
  const nearTarget=fresh&&tof.value&&data.distance>=0?Math.max(0,Math.min(1,(300-data.distance)/230)):0
  musicStrength.value+=(musicTarget-musicStrength.value)*(1-Math.exp(-dt/(musicTarget>musicStrength.value?1.2:3)))
  nearStrength.value+=(nearTarget-nearStrength.value)*(1-Math.exp(-dt/.9))
  if(!calm.value&&nearStrength.value>.65&&clock.value-lastHello>18&&story.value.x<1300&&story.value.x>500&&!story.value.lightActive){interactionUntil.value=clock.value+2.2;lastHello=clock.value}

  if(panel.value)attention.cancel(paletteClock.value)
  look.value=attention.update(dt,paletteClock.value,{...scene.value,...displayPosition.value})
  if(taps.value.length)taps.value=taps.value.filter(t=>paletteClock.value-t.at<.9)
  if(userState.value&&clock.value>=userUntil)userState.value=null
  if(ms-lastLight>=100){
    lastLight=ms
    const s=testUntil.value>paletteClock.value?sampleScene(156):scene.value
    if(lights.value||testUntil.value>paletteClock.value)window.N5D?.frame(JSON.stringify(sampleLights(panel.value&&testUntil.value<=paletteClock.value?{...s,lightActive:false,lightMix:0}:s,palette.value,ringOffset.value)))
  }
}
const codes:Record<string,string>={CONNECTED:'剧情灯光互动中 · 结束后自动归还',IDLE:'灯光自由播放 · 仅在灯环互动时临时接管',DISABLED:'请在灯环工坊「关于」开启其他应用控制',UNAVAILABLE:'未找到灯环工坊 1.1',BUSY:'灯环正在由其他应用控制',OFF:'灯光互动已关闭 · 已归还灯环',DISCONNECTED:'灯环连接已断开',NOT_OWNER:'灯环控制已归还；下次剧情再尝试',DRIVER_ERROR:'灯光驱动异常，请在灯环工坊检查',UNSUPPORTED_VERSION:'灯环接口或设备映射不兼容'}
onMounted(()=>{
  resize();window.addEventListener('resize',resize);window.addEventListener('keydown',key)
  window.n5dSensors=(data)=>{sensorData.value=data;sensorAt=performance.now()}
  window.n5dStatus=(code)=>{ringOnline.value=code==='CONNECTED';status.value=codes[code]??`灯环状态：${code}`}
  window.companion={setState(state,duration=8){
    if(!['idle','thinking','success','attention','sleep'].includes(state))return
    userState.value=state;userUntil=clock.value+Math.max(.8,Math.min(120,Number.isFinite(duration)?duration:8))
  },reset(){userState.value=null}}
  // Explicit diagnostic URL only; the normal appliance has no seek or speed control.
  if(new URLSearchParams(location.search).has('debug'))window.n5dDebug={seek(t){if(Number.isFinite(t)){canonical.value=true;clock.value=Math.max(0,t);paused=true}},random(t){canonical.value=false;clock.value=Math.max(0,t);paused=false;previous=0},resume(){paused=false;previous=0},snapshot(){return {clock:clock.value,...scene.value,lights:lights.value,status:status.value,sensors:{...sensorData.value},musicStrength:musicStrength.value,nearStrength:nearStrength.value,look:{...gaze.value},renderState:pose.value.state,touchTarget:attention.target,touchRipples:taps.value.length,bodyColor:bodyPalette.value.body,paperColor:palette.value.paper,ringOffset:ringOffset.value,random:!canonical.value}}}
  window.N5D?.ready();window.N5D?.brightness(brightness.value/100);window.N5D?.enableLights(lights.value);configureSensors()
  raf=requestAnimationFrame(tick);hintTimer=window.setTimeout(()=>hint.value=false,7000)
})
onBeforeUnmount(()=>{cancelAnimationFrame(raf);clearTimeout(hold);clearTimeout(hintTimer);window.removeEventListener('resize',resize);window.removeEventListener('keydown',key);delete window.companion;delete window.n5dDebug;delete window.n5dStatus;delete window.n5dSensors})
</script>

<template>
  <main @pointerdown="pointerDown" @pointermove="pointerMove" @pointerup="pointerUp" @pointercancel="cancelGesture" @lostpointercapture="cancelGesture" :style="{background:palette.paper}">
    <div ref="stage" class="stage" :style="{transform:`translate(-50%,-50%) scale(${zoom})`,background:palette.paper}">
      <Atmosphere :scene="scene" :palette="palette" :music="musicShown" :bubbles-enabled="!calm"/>
      <svg v-if="!panel&&taps.length" class="touch-feedback" viewBox="0 0 1600 720" aria-hidden="true">
        <g v-for="tap in taps" :key="tap.id" :opacity="Math.max(0,1-(paletteClock-tap.at)/.9)" :stroke="palette.rim" fill="none">
          <circle :cx="tap.x" :cy="tap.y" :r="reduced?9:9+36*smooth((paletteClock-tap.at)/.9)" stroke-width="1.6"/>
          <circle :cx="tap.x" :cy="tap.y" r="3" :fill="palette.rim" stroke="none"/>
        </g>
      </svg>
      <div class="companion-position" :style="bodyStyle">
        <Avatar ref="avatar" :time="clock" :local="scene.local" :shape="scene.shape" :state="pose.state" :expression="pose.expression" :yaw="gaze.yaw"  :pitch="gaze.pitch" :palette="bodyPalette" :lock-look="scene.portal>.05"/>
      </div>
      <svg class="aperture" viewBox="0 0 1600 720" aria-hidden="true">
        <g :opacity="portalOpacity" fill="none" :stroke="palette.rim">
          <circle cx="44" cy="360" :r="31+scene.portal*2" stroke-width="1.5"/>
          <circle cx="44" cy="360" :r="38+scene.breath*8" :opacity="scene.portal*.26" stroke-width="1"/>
          <path d="M 44 315 A 45 45 0 0 1 44 405" :stroke="palette.amber" :opacity="scene.portal*.6" stroke-width="2" stroke-linecap="round"/>
        </g>
        <!-- A measured, fixed physical occluder. The character actually passes behind it. -->
        <circle cx="44" cy="360" :r="25+scene.buddy*58" fill="#000"/>
        <clipPath id="buddy-clip"><circle cx="44" cy="360" :r="25+scene.buddy*58"/></clipPath>
        <g clip-path="url(#buddy-clip)" :opacity="scene.buddy" fill="#fff">
          <ellipse cx="83" :cy="354+scene.breath*2" rx="5.5" :ry="12-scene.breath*4"/>
          <ellipse cx="105" :cy="354+scene.breath*2" rx="5.5" :ry="12-scene.breath*4"/>
        </g>
        <circle cx="44" cy="360" r="25" fill="#000"/>
      </svg>
      <Transition name="fade"><p v-if="sensorData.cameraOn" class="camera-observation" :style="{color:palette.quiet}">◉ 相机观察中</p></Transition>
      <Transition name="fade"><p v-if="hint&&!panel" class="hint" :style="{color:palette.quiet}">点空白看过去 · 轻触打招呼 · 长按设置</p></Transition>
      <Transition name="panel">
        <SettingsDock v-if="panel" :calm="calm" :lights="lights" :brightness="brightness" :theme="theme"
          :quiet-start="quietStart" :quiet-end="quietEnd" :mic="mic" :camera="camera" :camera-id="cameraId" :tof="tof" :sensors="sensorData" :status="status" :native="!!native"
          @close="panel=false;closeCalibration()" @calibrate="calibration=true" @calm="toggleCalm" @lights="toggleLights" @brightness="changeBrightness"
          @theme="setTheme" @sensor="toggleSensor" @quiet-hours="changeQuietHours" @camera-source="changeCamera" @ring="native?.openRingStudio()" @project="openProject"/>
      </Transition>
      <RingCalibration v-if="panel&&calibration" :offset="ringOffset" @offset="setOffset" @test="testRing" @close="closeCalibration"/>
    </div>
  </main>
</template>
