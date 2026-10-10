import { sampleScene,shapeAt,smooth,type Scene } from './scene'
export const CLIPS={hole:[25,84],bubbles:[84,137],play:[116,137],ring:[137,205],wave:[205,256],acrobat:[256,278],dvd:[278,301],nap:[301,346]} as const
export type Clip=keyof typeof CLIPS
interface Job {start:number;end:number;kind:Clip|'rest';home:{x:number;y:number};from:{x:number;y:number};variant:number}
export class PetDirector{
  private state=0;private bag:Clip[]=[];private jobs:Job[]=[];private home={x:820,y:350};private last:Clip|null=null;private lightAt=-1000
  constructor(private seed:number){this.reset()}
  private reset(){this.state=this.seed|0;this.bag=[];this.jobs=[{start:0,end:8,kind:'rest',home:{x:820,y:350},from:{x:820,y:350},variant:0}];this.home={x:820,y:350};this.last=null;this.lightAt=-1000}
  private random(){this.state=(Math.imul(this.state,1664525)+1013904223)|0;return (this.state>>>0)/4294967296}
  private append(){
    if(!this.bag.length)this.bag=Object.keys(CLIPS) as Clip[]
    const time=this.jobs[this.jobs.length-1]!.end
    let eligible=this.bag.filter(k=>k!==this.last&&(!(k==='ring'||k==='wave')||time-this.lightAt>35))
    if(!eligible.length)eligible=this.bag.filter(k=>k!==this.last)
    if(!eligible.length)eligible=this.bag
    const kind=eligible[Math.floor(this.random()*eligible.length)]!
    this.bag.splice(this.bag.indexOf(kind),1)
    const home=kind==='wave'?{x:820,y:350}:{x:Math.round(640+this.random()*430),y:Math.round(310+this.random()*80)}
    const rest=8+this.random()*16+((kind==='ring'||kind==='wave')?Math.max(0,35-(time-this.lightAt)):0)
    const variant=(this.random()*0x7fffffff)|0,[a,b]=CLIPS[kind]
    this.jobs.push({start:time,end:time+rest,kind:'rest',home,from:this.home,variant},
      {start:time+rest,end:time+rest+b-a,kind,home,from:home,variant})
    if(kind==='ring'||kind==='wave')this.lightAt=time+rest+b-a
    this.home=home;this.last=kind
  }
  sample(seconds:number,calm=false):Scene{
    if(seconds<this.jobs[0]!.start)this.reset()
    while(this.jobs[this.jobs.length-1]!.end<=seconds)this.append()
    while(this.jobs.length>1&&this.jobs[0]!.end<=seconds)this.jobs.shift()
    const job=this.jobs[0]!
    if(calm)return {...sampleScene(seconds,true,this.seed),episode:'rest'}
    if(job.kind==='rest'){
      const base=sampleScene(0,false,job.variant,job.home,seconds),u=smooth((seconds-job.start)/4)
      return {...base,x:job.from.x+(job.home.x-job.from.x)*u,y:job.from.y+(job.home.y-job.from.y)*u,shape:shapeAt(seconds,this.seed),episode:'rest'}
    }
    return {...sampleScene(CLIPS[job.kind][0]+seconds-job.start,false,job.variant,job.home,seconds),episode:job.kind}
  }
  get bufferedJobs(){return this.jobs.length}
}
