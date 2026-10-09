import { SHAPES,type ShapeId } from '../bot/skins'
import type { StateId } from '../bot/states'
import type { ExpressionId } from '../bot/expressions'

// Front-view coordinates in display pixels. The physical ring is to the RIGHT.
export const GEOMETRY={width:1600,height:720,holeX:44,holeY:360,holeRadius:24,cornerRadius:60,
  mmPerPixel:.0942,ringX:2150,ringY:360,ringRadius:32.8/.0942,ringBand:4.7/.0942}
export const PERIOD=360
export const clamp01=(v:number)=>Math.max(0,Math.min(1,v))
export const smooth=(v:number)=>{const t=clamp01(v);return t*t*t*(10+t*(-15+6*t))}
const mix=(a:number,b:number,t:number)=>a+(b-a)*t
export const envelope=(t:number,a:number,b:number,c:number,d:number)=>smooth((t-a)/(b-a))*(1-smooth((t-c)/(d-c)))
type Key=readonly [number,number,number,number]
const left=GEOMETRY.ringX-GEOMETRY.ringRadius
export const WAYPOINTS:readonly Key[]=[
  [0,820,350,1],[25,820,350,1],[37,172,355,.43],[48,172,355,.43],
  [52,44,360,.095],[57,44,360,.095],[61,103,360,.25],[63,103,360,.25],
  [67,44,360,.095],[69,44,360,.095],[75,200,350,.46],[84,820,350,1],
  [140,820,350,1],[146,820,350,1],[150,1230,410,70/145],[156,left,360,70/145],
  [176,left,360,70/145],[182,1230,220,70/145],[188,820,350,1],
  [205,820,350,1],[212,980,360,.86],[242,980,360,.86],[252,820,350,1],
  [256,820,350,1],[262,1120,255,.82],[268,720,425,.92],[277,820,350,1],
  [302,820,350,1],[310,1110,415,.82],[330,1110,415,.82],[346,820,350,1],[360,820,350,1],
]
export function ringPoint(index:number,white=false){
  const a=(index+(white?.5:0))*Math.PI/12
  return {x:GEOMETRY.ringX+GEOMETRY.ringRadius*Math.sin(a),y:GEOMETRY.ringY-GEOMETRY.ringRadius*Math.cos(a)}
}
// One clock, physical positions, and matching position/velocity/acceleration at handoff.
export const RING_TRIP={enter:156,exit:176,turns:2,angularSpeed:Math.PI/5,
  speed:GEOMETRY.ringRadius*Math.PI/5,bodyRadius:70}
function hermite(t:number,d:number,p0:number,p1:number,v0=0,v1=0,a0=0,a1=0){
  const u=clamp01(t/d),v=v0*d,a=a0*d*d/2
  const q=p1-p0-v-a,r=v1*d-v-2*a,s=a1*d*d-2*a
  return p0+v*u+a*u*u+(10*q-4*r+s/2)*u**3+(-15*q+7*r-s)*u**4+(6*q-3*r+s/2)*u**5
}
export function pathAt(t:number){
  let x=820,y=350,scale=1
  for(let i=1;i<WAYPOINTS.length;i++){
    const a=WAYPOINTS[i-1]!,b=WAYPOINTS[i]!
    if(t<=b[0]){const u=smooth((t-a[0])/(b[0]-a[0]));x=mix(a[1],b[1],u);y=mix(a[2],b[2],u);scale=mix(a[3],b[3],u);break}
  }
  const v=RING_TRIP.speed,a=v*RING_TRIP.angularSpeed
  if(t>=146&&t<150){
    x=hermite(t-146,4,820,1230,0,120);y=hermite(t-146,4,350,410)
  }else if(t>=150&&t<156){
    x=hermite(t-150,6,1230,left,120,0,0,a);y=hermite(t-150,6,410,360,0,-v)
  }else if(t>=156&&t<=176){
    const angle=1.5*Math.PI+RING_TRIP.angularSpeed*(t-156)
    x=GEOMETRY.ringX+GEOMETRY.ringRadius*Math.sin(angle)
    y=GEOMETRY.ringY-GEOMETRY.ringRadius*Math.cos(angle)
  }else if(t>176&&t<182){
    x=hermite(t-176,6,left,1230,0,-120,a,0);y=hermite(t-176,6,360,220,-v,0)
  }else if(t>=182&&t<188){
    x=hermite(t-182,6,1230,820,-120,0);y=hermite(t-182,6,220,350)
  }
  return {x,y,scale}
}
// One-shot morphs finish before the next action; breathing intervals reconnect them.
export const ACTS:readonly (readonly [number,number,StateId,string])[]=[
  [10,12.2,'wink','打个招呼'],[40,43,'egg','钻洞前收一收'],[44,47,'thinking','里面有什么'],
  [54,56.6,'sleep','躲起来'],[102,105,'hexagon','变个形'],[111,114,'egg','再变一次'],
  [119,122,'play','小小练习'],[142,144.8,'notify','发现右边的灯环'],
  [191,193.4,'wink','回来啦'],[197,199.8,'burst','撒一把星星'],
  [206,209,'thinking','想个游戏'],[216,218.8,'burst','送出一圈光'],
  [235,237.7,'notify','收到回信'],[239,241.2,'wink','谢谢你'],
  [263,265.8,'comet','变成小彗星'],[270,273.6,'orbit','转个圈'],
  [334,336.4,'wide','醒来看看'],
]
export interface Wave {x:number;y:number;radius:number;opacity:number;returning:boolean}
export function wavesAt(t:number):Wave[]{
  return [
    {x:980,y:360,radius:Math.max(0,(t-216.7)*145),opacity:envelope(t,216.7,217.1,227,228),returning:false},
    {x:GEOMETRY.ringX,y:GEOMETRY.ringY,radius:Math.max(0,(t-228)*145),opacity:envelope(t,228,228.4,238,240),returning:true},
  ].filter(w=>w.opacity>.0001)
}
export type CompanionState='idle'|'thinking'|'success'|'attention'|'sleep'
export interface Scene {
  t:number;x:number;y:number;scale:number;shape:ShapeId;buddy:number;growth:number;state:StateId;local:number;expression:ExpressionId;
  portal:number;sleep:number;yaw:number;pitch:number;breath:number;label:string;
  lightActive:boolean;lightMix:number;orbit:number;rotation:number;squash:number;trail:number;waves:Wave[];
}
export function shapeAt(seconds:number,seed=0):ShapeId{
  const t=((seconds%PERIOD)+PERIOD)%PERIOD
  if((t>=24&&t<115)||(t>=139&&t<188)||(t>=205&&t<242)||(t>=302&&t<346))return 'cercle'
  // Shuffle all eight shapes once per bag; never pick a new random value per frame.
  const slot=Math.floor(seconds/12),bag=Math.floor(slot/SHAPES.length)
  const order=SHAPES.map((s,i)=>({id:s.id,key:hash(seed+bag*7919+i*104729)})).sort((a,b)=>a.key-b.key)
  return order[((slot%order.length)+order.length)%order.length]!.id
}
function hash(n:number){let x=(n|0)^0x9e3779b9;x=Math.imul(x^(x>>>16),0x21f0aaad);x=Math.imul(x^(x>>>15),0x735a2d97);return (x^(x>>>15))>>>0}
export interface Bubble {x:number;y:number;r:number;opacity:number}
export function bubblesAt(t:number):Bubble[]{
  return Array.from({length:8},(_,i)=>{
    const age=(t-84-i*1.6)/3.6,u=clamp01(age),travel=smooth(u)
    return {x:mix(44,820,travel),y:mix(360,350,travel)-Math.sin(u*Math.PI)*(45+i%3*27),
      r:(12+i%3*5)*(1-smooth((u-.82)/.18)),opacity:age>=0&&age<=1?smooth(u/.13):0}
  }).filter(b=>b.opacity>.001&&b.r>.01)
}
export function sampleScene(seconds:number,calm=false,seed=0):Scene{
  const t=((seconds%PERIOD)+PERIOD)%PERIOD
  const p=calm?{x:820,y:350,scale:1}:pathAt(t)
  const portal=calm?0:envelope(t,25,36,75,84)
  const sleep=calm?.35:envelope(t,302,314,327,340)
  const breath=.5-.5*Math.cos(seconds*Math.PI/3)
  const buddy=calm?0:envelope(t,35,38,47,50)
  const growth=calm?0:envelope(t,87.6,100,105,115)
  let state:StateId='idle',expression:ExpressionId='neutre',label='陪着你',local=t
  if(!calm){
    if(t>=25&&t<84){expression='curieux';label=t>=52&&t<75?'捉迷藏':'去小窝看看'}
    if(t>=140&&t<188){expression='curieux';label=t>=156&&t<176?'变成黑色小影子 · 绕两圈':'去灯环玩'}
    if(t>=188&&t<205)expression='heureux'
    if(t>=205&&t<242){expression='attentif';label='和灯环交换光波'}
    if(t>=256&&t<277){expression='heureux';label='舒展一下'}
    if(sleep>.5){expression='somnolent';label='打个盹'}
    const act=ACTS.find(a=>t>=a[0]&&t<a[1]);if(act){state=act[2];local=t-act[0];label=act[3]}
  }
  const right=calm?0:envelope(t,137,147,184,194)+envelope(t,205,212,240,250)
  const yaw=mix(mix(12+Math.sin(seconds*Math.PI/15)*7,-45,portal),43,clamp01(right))
  const pitch=mix(mix(8,-3,portal),22,sleep)
  const acrobat=calm?0:envelope(t,256,259,273,277)
  const stretch=calm?0:envelope(t,332,334,335,339)
  const rotation=acrobat*12*Math.sin((t-256)*Math.PI/7)
  const squash=1+acrobat*.07*Math.sin((t-256)*Math.PI/3)-stretch*.09
  const lightActive=!calm&&((t>=149&&t<187)||(t>=219&&t<240))
  const lightMix=calm?0:envelope(t,149,152,184,187)+envelope(t,219,221,238,240)
  return {...p,t,shape:shapeAt(seconds,seed),buddy,growth,scale:p.scale*(1+.008*Math.sin(seconds*Math.PI/3))*(1+.32*growth),state,local,expression,
    portal,sleep,yaw,pitch,breath,label,lightActive,lightMix,rotation,squash,
    orbit:calm?0:envelope(t,270,271,273,275),trail:calm?0:envelope(t,150,152,180,184),
    waves:calm?[]:wavesAt(t)}
}
/** Future local adapter, with no network listener. */
export function externalPose(state:CompanionState):{state:StateId;expression:ExpressionId}{
  switch(state){
    case 'thinking':return {state:'thinking',expression:'attentif'}
    case 'success':return {state:'wink',expression:'heureux'}
    case 'attention':return {state:'wide',expression:'attentif'}
    case 'sleep':return {state:'idle',expression:'somnolent'}
    default:return {state:'idle',expression:'neutre'}
  }
}
