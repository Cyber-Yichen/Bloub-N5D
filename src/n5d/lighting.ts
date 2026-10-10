import { pathAt,ringPoint,smooth,RING_TRIP,type Scene } from './scene'
import type { Palette } from './themes'
const channels=(hex:string)=>[1,3,5].map(i=>parseInt(hex.slice(i,i+2),16))
const gaussian=(distance:number,width:number)=>Math.exp(-.5*(distance/width)**2)
/** Trails, waves and LEDs share world coordinates. RGB order is physical;
 * RingStudio handles BGR wiring. Bytes already contain effective intensity. */
export function sampleLights(scene:Scene,palette:Palette,offset=0){
  const rgb:number[]=[],white:number[]=[]
  const peak=(hex:string)=>{const c=channels(hex),m=Math.max(...c);return c.map(v=>m?v*255/m:255)}
  const color=peak(palette.amber),cool=peak(palette.rim)
  const energyAt=(x:number,y:number)=>{
    let warm=0,cold=0
    if(scene.trail>0){
      for(let k=0;k<8;k++){
        const p=pathAt(scene.t-k*.18)
        warm+=gaussian(Math.hypot(x-p.x,y-p.y),44)*(1-k/8)*.36*scene.trail
      }
    }
    for(const wave of scene.waves){
      const e=gaussian(Math.hypot(x-wave.x,y-wave.y)-wave.radius,48)*wave.opacity
      if(wave.returning)cold+=e;else warm+=e
    }
    return [Math.min(1,warm),Math.min(1,cold)] as const
  }
  const trip=scene.t>=149&&scene.t<187&&scene.lightActive
  // Both interleaved LED banks must go dark. A flat core spans the nearest RGB
  // and white LEDs; feathered edges make the dark body travel without stepping.
  const shadowAt=(x:number,y:number)=>smooth((Math.hypot(x-scene.x,y-scene.y)-RING_TRIP.bodyRadius)/RING_TRIP.bodyRadius)
  for(let i=0;i<24;i++){
    const p=ringPoint(i-offset/15),[a,b]=energyAt(p.x,p.y)
    for(let j=0;j<3;j++)rgb.push(trip?Math.round(color[j]!*shadowAt(p.x,p.y)):Math.round(Math.min(255,(color[j]!/255*a+cool[j]!/255*b)*255)))
    const w=ringPoint(i-offset/15,true),[wa,wb]=energyAt(w.x,w.y)
    white.push(trip?Math.round(255*shadowAt(w.x,w.y)):Math.round(Math.min(255,(wa+wb)*255)))
  }
  return {active:scene.lightActive,mix:scene.lightMix,rgb,white}
}
