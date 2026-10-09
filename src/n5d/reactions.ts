import { clamp01,type Scene } from './scene'
export interface SensorData {
  level:number;music:number;speech:number;distance:number;motion:number;faces:number;faceX:number;faceY:number;
  micStatus:string;cameraStatus:string;tofStatus:string;cameraOn:boolean;cameraFrames:number;
}
export const emptySensors:SensorData={level:0,music:0,speech:0,distance:-1,motion:0,faces:0,faceX:0,faceY:0,
  micStatus:'关闭',cameraStatus:'关闭',tofStatus:'关闭',cameraOn:false,cameraFrames:0}
export function reactScene(base:Scene,time:number,music:number,near:number,data:SensorData,calm=false):Scene{
  const s={...base}
  // Small additive reactions return gently; the portal/ring storyline keeps its trajectory.
  const free=!calm&&!base.lightActive&&base.portal<.05&&base.sleep<.5&&base.x>500&&base.x<1250
  if(!free)return s
  s.rotation+=Math.sin(time*Math.PI*1.6)*10*music
  s.x+=Math.sin(time*Math.PI*1.6)*18*music
  s.y-=Math.abs(Math.sin(time*Math.PI*1.6))*(8+data.level*12)*music
  s.squash*=1+.035*music*Math.sin(time*Math.PI*3.2)
  s.scale*=1+.055*near
  if(base.state==='idle'){
    if(music>.3)s.expression='heureux'
    else if(data.faces>0||data.speech>.3||near>.3||data.motion>.25)s.expression='attentif'
  }
  s.yaw+=(35-s.yaw)*near
  if(data.faces>0){s.yaw+=(clamp01(data.faceX+.5)*50-25-s.yaw)*.35;s.pitch+=(data.faceY*30-s.pitch)*.35}
  return s
}
