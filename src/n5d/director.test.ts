import { describe,it,expect } from 'vitest'
import { PetDirector,CLIPS } from './director'
import { dvdPlan,dvdPose,DVD } from './dvd'
import { idleYaw,sampleScene,GEOMETRY } from './scene'
import { sampleLights } from './lighting'
import { THEMES } from './themes'
describe('desktop pet director',()=>{
  it('shuffles complete stories, varies homes, avoids repeats and keeps bounded history',()=>{
    const a=new PetDirector(17),b=new PetDirector(99),seen=new Set<string>(),order:string[]=[],homes=new Set<string>()
    let last='',different=false,previous=a.sample(0)
    for(let t=.2;t<3600;t+=.2){
      const s=a.sample(t),other=b.sample(t)
      if(s.episode!==other.episode)different=true
      if(s.episode!=='rest'&&s.episode!==last){seen.add(s.episode!);order.push(s.episode!);homes.add(s.homeX+','+s.homeY)}
      expect(Math.hypot(s.x-previous.x,s.y-previous.y)).toBeLessThan(110)
      expect(Number.isFinite(s.scale+s.x+s.y)).toBe(true)
      if(s.x<1600){expect(s.y-170*s.scale).toBeGreaterThan(0);expect(s.y+170*s.scale).toBeLessThan(720)}
      expect(a.bufferedJobs).toBeLessThan(4)
      previous=s;last=s.episode!
    }
    expect([...seen].sort()).toEqual(Object.keys(CLIPS).sort());expect(homes.size).toBeGreaterThan(20);expect(different).toBe(true)
    for(let i=1;i<order.length;i++)expect(order[i]).not.toBe(order[i-1])
  })
  it('produces reproducible safe DVD variants that restore their own homes',()=>{
    const signatures=new Set<string>()
    for(let seed=1;seed<=25;seed++){
      const home={x:650+seed*15,y:320+(seed%5)*14},plan=dvdPlan(seed,home)
      signatures.add(JSON.stringify(plan.velocity))
      expect(dvdPlan(seed,home)).toBe(plan)
      for(let t=DVD.start;t<=DVD.end;t+=.1){
        const s=dvdPose(t,plan),r=170*s.scale*1.008
        expect(s.x-r).toBeGreaterThan(0);expect(s.x+r).toBeLessThan(1600)
        expect(s.y-r).toBeGreaterThan(0);expect(s.y+r).toBeLessThan(720)
        expect(Math.hypot(s.x-44,s.y-360)).toBeGreaterThan(r+25)
      }
      expect(dvdPose(DVD.end,plan)).toEqual({...home,scale:1,weight:0})
    }
    expect(signatures.size).toBeGreaterThan(20)
  })
  it('separates the dialogue silhouettes and lets idle eyes look in both directions smoothly',()=>{
    const s=sampleScene(41)
    expect(s.x-145*s.scale).toBeGreaterThan(GEOMETRY.holeX+25+58+35)
    const looks=Array.from({length:1600},(_,i)=>idleYaw(i/10,19))
    expect(Math.max(...looks)).toBeGreaterThan(25);expect(Math.min(...looks)).toBeLessThan(-10)
    for(let i=1;i<looks.length;i++)expect(Math.abs(looks[i]!-looks[i-1]!)).toBeLessThan(8)
  })
  it('moves the RGB and staggered white dark gap together and uses full channel peaks',()=>{
    const base=sampleScene(156),frame=sampleLights(base,THEMES[0],90)
    expect(frame.rgb.slice(0,3)).toEqual([0,0,0]);expect(frame.white[0]).toBe(0)
    expect(Math.max(...frame.rgb)).toBe(255);expect(Math.max(...frame.white)).toBe(255)
    expect(sampleLights(sampleScene(130),THEMES[0]).active).toBe(false)
  })
})
