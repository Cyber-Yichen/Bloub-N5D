import { GEOMETRY } from './scene'
export interface Point {x:number;y:number}
export interface BodyPose extends Point {scale:number;rotation:number;squash:number;yaw:number;pitch:number}
const clamp=(v:number,min:number,max:number)=>Math.max(min,Math.min(max,v))
/** The stage is letterboxed and scaled; CSS viewport coordinates are not device pixels. */
export function stagePoint(client:Point,box:{left:number;top:number;width:number;height:number}):Point|null{
  if(!Number.isFinite(client.x+client.y+box.left+box.top+box.width+box.height)||box.width<=0||box.height<=0)return null
  const p={x:(client.x-box.left)*GEOMETRY.width/box.width,y:(client.y-box.top)*GEOMETRY.height/box.height}
  if(p.x<0||p.x>GEOMETRY.width||p.y<0||p.y>GEOMETRY.height)return null
  if(Math.hypot(p.x-GEOMETRY.holeX,p.y-GEOMETRY.holeY)<=GEOMETRY.holeRadius+1)return null
  return p
}
/** Undo the body rotation/squash so dancing eyes still aim at a fixed screen point.
 * Upstream positive pitch points UP, while screen y points down. */
export function gazeToward(point:Point,body:BodyPose){
  const a=body.rotation*Math.PI/180,dx=point.x-body.x,dy=point.y-body.y
  const squash=Math.max(.1,body.squash),depth=420*Math.max(.25,body.scale)
  const x=(Math.cos(a)*dx+Math.sin(a)*dy)/squash
  const y=(-Math.sin(a)*dx+Math.cos(a)*dy)*squash
  return {yaw:clamp(Math.atan2(x,depth)*180/Math.PI,-38,38),pitch:clamp(-Math.atan2(y,depth)*180/Math.PI,-24,24)}
}
/** Bounded temporary attention; retargeting never resets the displayed direction. */
export class TouchAttention {
  target:Point|null=null
  private held=false
  private until=0
  private weight=0
  private yaw=0
  private pitch=0
  aim(point:Point,now:number,held=false){
    if(!Number.isFinite(point.x+point.y+now))return
    this.target={...point};this.held=held;this.until=now+3.2
  }
  release(now:number){this.held=false;this.until=now+3.2}
  cancel(now:number){this.held=false;this.until=now;this.target=null}
  update(dt:number,now:number,body:BodyPose){
    const step=Number.isFinite(dt)?clamp(dt,0,.1):0
    const active=this.target!==null&&(this.held||now<this.until)
    if(this.weight<.0001){this.yaw=body.yaw;this.pitch=body.pitch}
    if(this.target){
      const goal=gazeToward(this.target,body),u=1-Math.exp(-step/.22)
      this.yaw+=(goal.yaw-this.yaw)*u;this.pitch+=(goal.pitch-this.pitch)*u
    }
    this.weight+=((active?1:0)-this.weight)*(1-Math.exp(-step/(active?.16:.55)))
    if(!active&&this.weight<.001){this.weight=0;this.target=null}
    return {yaw:body.yaw+(this.yaw-body.yaw)*this.weight,pitch:body.pitch+(this.pitch-body.pitch)*this.weight,weight:this.weight}
  }
}
