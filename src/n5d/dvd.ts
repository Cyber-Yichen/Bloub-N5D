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
