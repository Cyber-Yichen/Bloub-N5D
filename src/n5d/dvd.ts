import { smooth,hermite } from './motion'

// Display-pixel geometry, conservative circular body envelope; no frame-dependent physics.
export const DVD={start:278,float:280,return:297,end:301,scale:.78,radius:136,
  home:{x:820,y:350},launch:{x:980,y:270},velocity:{x:170,y:-96},
  bounds:{left:136,right:1464,top:136,bottom:584}} as const
interface Segment {at:number;x:number;y:number;vx:number;vy:number}
export interface DvdHit {at:number;x:number;y:number;vertical:boolean;horizontal:boolean}
const segments:Segment[]=[{at:DVD.float,...DVD.launch,vx:DVD.velocity.x,vy:DVD.velocity.y}]
export const DVD_HITS:DvdHit[]=[]
let cursor=segments[0]!
while(cursor.at<DVD.return){
  const {left,right,top,bottom}=DVD.bounds
  const tx=((cursor.vx>0?right:left)-cursor.x)/cursor.vx
  const ty=((cursor.vy>0?bottom:top)-cursor.y)/cursor.vy
  const dt=Math.min(tx,ty),at=cursor.at+dt
  if(at>DVD.return)break
  const vertical=Math.abs(tx-dt)<1e-8,horizontal=Math.abs(ty-dt)<1e-8
  const x=cursor.x+cursor.vx*dt,y=cursor.y+cursor.vy*dt
  DVD_HITS.push({at,x,y,vertical,horizontal})
  cursor={at,x,y,vx:cursor.vx*(vertical?-1:1),vy:cursor.vy*(horizontal?-1:1)}
  segments.push(cursor)
}
export function dvdLinearAt(t:number){
  let s=segments[0]!
  for(const next of segments){if(next.at>t)break;s=next}
  return {x:s.x+s.vx*(t-s.at),y:s.y+s.vy*(t-s.at),vx:s.vx,vy:s.vy}
}
export function dvdWeight(t:number){
  return smooth((t-DVD.start)/(DVD.float-DVD.start))*(1-smooth((t-DVD.return)/(DVD.end-DVD.return)))
}
export function dvdAt(t:number){
  const weight=dvdWeight(t),scale=1+(DVD.scale-1)*weight
  if(t<=DVD.start||t>=DVD.end)return {...DVD.home,scale:1,weight:0}
  if(t<DVD.float)return {
    x:hermite(t-DVD.start,DVD.float-DVD.start,DVD.home.x,DVD.launch.x,0,DVD.velocity.x),
    y:hermite(t-DVD.start,DVD.float-DVD.start,DVD.home.y,DVD.launch.y,0,DVD.velocity.y),scale,weight}
  if(t<=DVD.return)return {...dvdLinearAt(t),scale,weight}
  const finish=dvdLinearAt(DVD.return),age=t-DVD.return,duration=DVD.end-DVD.return
  return {x:hermite(age,duration,finish.x,DVD.home.x,finish.vx,0),
    y:hermite(age,duration,finish.y,DVD.home.y,finish.vy,0),scale,weight}
}

export interface DvdPlan {seed:number;home:{x:number;y:number};launch:{x:number;y:number};velocity:{x:number;y:number};hits:DvdHit[];segments:Segment[]}
const cache=new Map<string,DvdPlan>()
export function dvdPlan(seed=0,home:{x:number;y:number}=DVD.home):DvdPlan{
  const key=seed+':'+home.x+':'+home.y,found=cache.get(key)
  if(found)return found
  let state=seed|0
  const random=()=>{state=(Math.imul(state,1664525)+1013904223)|0;return (state>>>0)/4294967296}
  for(let attempt=0;attempt<256;attempt++){
    const standard=seed===0&&home.x===820&&home.y===350&&attempt===0
    const launch=standard?DVD.launch:{x:home.x+(random()-.5)*240,y:home.y+(random()-.5)*100}
    const velocity=standard?DVD.velocity:{x:(random()<.5?-1:1)*(145+random()*55),y:(random()<.5?-1:1)*(85+random()*40)}
    const parts:Segment[]=[{at:DVD.float,...launch,vx:velocity.x,vy:velocity.y}],hits:DvdHit[]=[]
    let current=parts[0]!
    while(current.at<DVD.return){
      const b=DVD.bounds,tx=((current.vx>0?b.right:b.left)-current.x)/current.vx,ty=((current.vy>0?b.bottom:b.top)-current.y)/current.vy
      const dt=Math.min(tx,ty),at=current.at+dt
      if(at>DVD.return)break
      const vertical=Math.abs(tx-dt)<1e-8,horizontal=Math.abs(ty-dt)<1e-8,x=current.x+current.vx*dt,y=current.y+current.vy*dt
      hits.push({at,x,y,vertical,horizontal});current={at,x,y,vx:current.vx*(vertical?-1:1),vy:current.vy*(horizontal?-1:1)};parts.push(current)
    }
    const plan={seed,home,launch,velocity,hits,segments:parts}
    let valid=true
    for(let t=DVD.start;t<=DVD.end;t+=1/30){
      const q=dvdPose(t,plan),r=170*q.scale*1.008
      if(q.x-r<=0||q.x+r>=1600||q.y-r<=0||q.y+r>=720||Math.hypot(q.x-44,q.y-360)<=r+25){valid=false;break}
    }
    if(valid){cache.set(key,plan);if(cache.size>48)cache.delete(cache.keys().next().value!);return plan}
  }
  throw Error('No safe DVD trajectory for this home position')
}
function linear(t:number,plan:DvdPlan){
  let s=plan.segments[0]!
  for(const next of plan.segments){if(next.at>t)break;s=next}
  return {x:s.x+s.vx*(t-s.at),y:s.y+s.vy*(t-s.at),vx:s.vx,vy:s.vy}
}
export function dvdPose(t:number,plan:DvdPlan){
  const weight=dvdWeight(t),scale=1+(DVD.scale-1)*weight
  if(t<=DVD.start||t>=DVD.end)return {...plan.home,scale:1,weight:0}
  if(t<DVD.float)return {x:hermite(t-DVD.start,2,plan.home.x,plan.launch.x,0,plan.velocity.x),
    y:hermite(t-DVD.start,2,plan.home.y,plan.launch.y,0,plan.velocity.y),scale,weight}
  if(t<=DVD.return)return {...linear(t,plan),scale,weight}
  const last=linear(DVD.return,plan)
  return {x:hermite(t-DVD.return,4,last.x,plan.home.x,last.vx),y:hermite(t-DVD.return,4,last.y,plan.home.y,last.vy),scale,weight}
}
