import { describe,it,expect } from 'vitest'
import { ACTS,GEOMETRY,PERIOD,WAYPOINTS,sampleScene,shapeAt,ringPoint,bubblesAt,pathAt,RING_TRIP } from './scene'
import { sampleLights } from './lighting'
import { reactScene,emptySensors } from './reactions'
import { THEMES } from './themes'
import { SHAPES } from '../bot/skins'
import { STATE_BY_ID } from '../bot/states'
import { BotEngine } from '../bot/engine'
import { EXPRESSION_BY_ID } from '../bot/expressions'

describe('N5D physical choreography',()=>{
  it('closes the six minute loop without a spatial, velocity or breathing jump',()=>{
    const a=sampleScene(PERIOD-1e-5),b=sampleScene(PERIOD+1e-5)
    for(const k of ['x','y','scale','yaw','pitch','breath','portal','sleep','rotation','squash','buddy','growth'] as const)expect(Math.abs(a[k]-b[k])).toBeLessThan(.001)
    expect(a.state).toBe(b.state);expect(a.expression).toBe(b.expression)
  })
  it('has continuous travel and acceleration at every waypoint including physical handoff',()=>{
    for(const key of WAYPOINTS){
      const t=key[0],a=sampleScene(t-.001),b=sampleScene(t+.001),c=sampleScene(t)
      expect(Math.hypot(a.x-b.x,a.y-b.y)).toBeLessThan(.8)
      const prev=pathAt(t-.002),next=pathAt(t+.002)
      for(const k of ['x','y'] as const){
        expect(Math.abs((c[k]-a[k])/.001-(b[k]-c[k])/.001)).toBeLessThan(.3)
        expect(Math.abs((c[k]-2*a[k]+prev[k])/1e-6-(next[k]-2*b[k]+c[k])/1e-6)).toBeLessThan(1)
      }
    }
  })
  it('fully hides behind the measured hole in both hide-and-seek pauses',()=>{
    for(const [start,end] of [[52,57],[67,69]])for(let t=start!;t<=end!;t+=.1){
      const s=sampleScene(t);expect(Math.hypot(s.x-GEOMETRY.holeX,s.y-GEOMETRY.holeY)+145*s.scale).toBeLessThan(GEOMETRY.holeRadius)
      expect(s.lightActive).toBe(false)
    }
  })
  it('uses the ring to the right and offsets white LEDs by 7.5 degrees',()=>{
    expect((ringPoint(18).x-1600)*GEOMETRY.mmPerPixel).toBeCloseTo(19.01,1)
    expect(ringPoint(0).y).toBeCloseTo(360-GEOMETRY.ringRadius)
    expect(ringPoint(6).x).toBeCloseTo(2150+GEOMETRY.ringRadius)
    const w=ringPoint(0,true);expect(Math.atan2(w.x-2150,360-w.y)*180/Math.PI).toBeCloseTo(7.5)
    for(let t=156;t<=176;t+=.1){const s=sampleScene(t);expect(Math.hypot(s.x-2150,s.y-360)).toBeCloseTo(GEOMETRY.ringRadius,5)}
  })
  it('keeps the ring lit with a moving black body in BOTH staggered LED banks',()=>{
    for(let t=156;t<=176;t+=.05){
      const s=sampleScene(t),frame=sampleLights(s,THEMES[0])
      for(const white of [false,true]){
        const nearest=Array.from({length:24},(_,i)=>({i,d:Math.hypot(ringPoint(i,white).x-s.x,ringPoint(i,white).y-s.y)})).sort((a,b)=>a.d-b.d)[0]!.i
        if(white)expect(frame.white[nearest]).toBe(0)
        else expect(frame.rgb.slice(nearest*3,nearest*3+3)).toEqual([0,0,0])
      }
      expect(frame.white.filter(v=>v===255).length).toBeGreaterThan(18)
    }
    // No dark gap anticipates the body crossing the physical gap.
    for(const t of [149,150,151])expect(Math.min(...sampleLights(sampleScene(t),THEMES[0]).white)).toBe(255)

    // From x=980 the outgoing wave reaches the near side first, then the far side.
    const energy=(t:number,i:number)=>sampleLights(sampleScene(t),THEMES[0]).rgb.slice(i*3,i*3+3).reduce((a,b)=>a+b,0)
    expect(energy(222.4,18)).toBeGreaterThan(energy(222.4,6)+10)
    expect(energy(227.2,6)).toBeGreaterThan(energy(227.2,18)+10)
  })
  it('enters at seven, turns counterclockwise and leaves at eleven with tangent handoff',()=>{
    let angle=0,prev=pathAt(156)
    for(let t=156.01;t<=176.001;t+=.01){
      const p=pathAt(t),u={x:prev.x-2150,y:prev.y-360},w={x:p.x-2150,y:p.y-360}
      angle+=Math.atan2(u.x*w.y-u.y*w.x,u.x*w.x+u.y*w.y)
      expect(Math.hypot(p.x-prev.x,p.y-prev.y)/.01*.0942).toBeCloseTo(RING_TRIP.speed*.0942,3)
      prev=p
    }
    expect(angle).toBeCloseTo(-16*Math.PI/3,6)
    expect(Math.atan2(pathAt(156).x-2150,360-pathAt(156).y)).toBeCloseTo(-5*Math.PI/6,6);expect(Math.atan2(pathAt(176).x-2150,360-pathAt(176).y)).toBeCloseTo(-Math.PI/6,6)
    for(const t of [156,176]){const a=pathAt(t-.0001),c=pathAt(t),b=pathAt(t+.0001);for(const key of ['x','y'] as const)expect((c[key]-a[key])/.0001).toBeCloseTo((b[key]-c[key])/.0001,1);}

  })
  it('releases on ordinary, hole, nap and calm scenes; ends each ownership blend at zero',()=>{
    for(let t=0;t<PERIOD;t+=.2){
      expect(sampleScene(t).lightActive).toBe((t>=149&&t<187)||(t>=219&&t<240))
      expect(sampleScene(t,true).lightActive).toBe(false)
    }
    for(const t of [149,187,219,240])expect(sampleScene(t).lightMix).toBe(0)
  })
  it('lets each upstream one-shot finish before recovering',()=>{
    for(const [start,end,state] of ACTS)expect(end-start).toBeGreaterThanOrEqual(STATE_BY_ID.get(state)?.minDuration??1.6)
  })
  it('shuffles all eight shapes deterministically instead of choosing per frame',()=>{
    const found=new Set<string>()
    for(let t=0;t<PERIOD*8;t+=.2)found.add(shapeAt(t,1234))
    expect([...found].sort()).toEqual(SHAPES.map(s=>s.id).sort())
    for(let t=0;t<360;t+=.1)expect(shapeAt(t,1234)).toBe(shapeAt(t,1234))
    expect(sampleScene(58,false,1234).shape).toBe('cercle')
  })
  it('grows while swallowing bubbles, then settles back to resting size',()=>{
    expect(bubblesAt(88).length).toBeGreaterThan(0)
    expect(sampleScene(102).scale).toBeGreaterThan(1.3)
    expect(sampleScene(116).growth).toBe(0)
    expect(sampleScene(41).buddy).toBe(1)
  })
  it('runs two full cycles with all shapes, bounded movement and finite upstream morphs',()=>{
    const engine=new BotEngine(100,'idle',SHAPES[0]!.radii,EXPRESSION_BY_ID.get('neutre')!)
    let previous=sampleScene(0)
    for(let n=0;n<=PERIOD*2*30;n++){
      const t=n/30,s=sampleScene(t,false,1234)
      engine.setShape(SHAPES.find(shape=>shape.id===s.shape)!.radii,t)
      engine.setState(s.state,t);engine.setExpression(EXPRESSION_BY_ID.get(s.expression)!,t)
      const f=engine.sample(t)
      expect(f.bodyPath).not.toMatch(/NaN|Infinity/)
      expect(Math.hypot(s.x-previous.x,s.y-previous.y)).toBeLessThan(15)
      if(s.x<1600){expect(s.y-170*s.scale).toBeGreaterThan(0);expect(s.y+170*s.scale).toBeLessThan(720)}
      if(n%3===0){const lights=sampleLights(s,THEMES[0]);expect(lights.rgb.length).toBe(72);expect(lights.white.length).toBe(24);expect([...lights.rgb,...lights.white].every(v=>Number.isInteger(v)&&v>=0&&v<=255)).toBe(true)}
      previous=s
    }
  })
  it('keeps sensor reactions out of portal/ring motion and rejects invalid distances',()=>{
    const sensors={...emptySensors,music:.9,distance:-1,faces:0}
    const quiet=sampleScene(130),party=reactScene(quiet,130,1,0,sensors)
    expect(party.rotation).not.toBe(quiet.rotation);expect(party.expression).toBe('heureux')
    for(const t of [42,57,160,230,320]){
      const base=sampleScene(t);expect(reactScene(base,t,1,1,sensors)).toEqual(base)
    }
  })
})
