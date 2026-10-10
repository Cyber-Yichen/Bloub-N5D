import { describe,it,expect } from 'vitest'
import { DVD,DVD_HITS,dvdAt,dvdLinearAt } from './dvd'
import { GEOMETRY,sampleScene } from './scene'
import { dvdPalette,THEMES } from './themes'
import { reactScene,emptySensors } from './reactions'

describe('DVD desk excursion',()=>{
  it('starts and returns at home with matched position, velocity and acceleration',()=>{
    const h=.001
    for(const t of [DVD.start,DVD.float,DVD.return,DVD.end]){
      const a=dvdAt(t-h),b=dvdAt(t),c=dvdAt(t+h),prev=dvdAt(t-2*h),next=dvdAt(t+2*h)
      for(const k of ['x','y'] as const){
        expect(Math.abs(a[k]-c[k])).toBeLessThan(.5)
        expect(Math.abs((b[k]-a[k])/h-(c[k]-b[k])/h)).toBeLessThan(.3)
        expect(Math.abs((b[k]-2*a[k]+prev[k])/h**2-(next[k]-2*c[k]+b[k])/h**2)).toBeLessThan(1)
      }
    }
    expect(dvdAt(DVD.start)).toEqual({...DVD.home,scale:1,weight:0})
    expect(dvdAt(DVD.end)).toEqual({...DVD.home,scale:1,weight:0})
  })
  it('keeps the whole body within the display and away from the physical hole',()=>{
    for(let t=DVD.start;t<=DVD.end;t+=1/120){
      const s=sampleScene(t),radius=170*s.scale
      expect(s.x-radius).toBeGreaterThan(0);expect(s.x+radius).toBeLessThan(GEOMETRY.width)
      expect(s.y-radius).toBeGreaterThan(0);expect(s.y+radius).toBeLessThan(GEOMETRY.height)
      expect(Math.hypot(s.x-GEOMETRY.holeX,s.y-GEOMETRY.holeY)).toBeGreaterThan(radius+GEOMETRY.holeRadius)
      expect(s.lightActive).toBe(false)
    }
  })
  it('reflects at all four walls without changing speed or teleporting',()=>{
    expect(DVD_HITS.length).toBeGreaterThanOrEqual(6)
    const walls=new Set<string>(),speed=Math.hypot(DVD.velocity.x,DVD.velocity.y)
    for(const hit of DVD_HITS){
      const a=dvdLinearAt(hit.at-.00001),b=dvdLinearAt(hit.at+.00001)
      expect(Math.hypot(a.x-b.x,a.y-b.y)).toBeLessThan(.01)
      expect(Math.hypot(b.vx,b.vy)).toBeCloseTo(speed,8)
      expect(b.vx).toBe(a.vx*(hit.vertical?-1:1))
      expect(b.vy).toBe(a.vy*(hit.horizontal?-1:1))
      if(hit.vertical)walls.add(hit.x===DVD.bounds.left?'left':'right')
      if(hit.horizontal)walls.add(hit.y===DVD.bounds.top?'top':'bottom')
    }
    expect([...walls].sort()).toEqual(['bottom','left','right','top'])
  })
  it('changes only Bot tint on impact and restores every original palette exactly',()=>{
    for(const base of THEMES){
      const original={...base}
      expect(dvdPalette(base,DVD.float,1)).toEqual(base)
      let previous:string=base.body
      for(const hit of DVD_HITS){
        const at=dvdPalette(base,hit.at,1),before=dvdPalette(base,hit.at-.00001,1)
        expect(at.body).toBe(before.body)
        const color=dvdPalette(base,hit.at+.2,1)
        expect(color.body).not.toBe(previous);previous=color.body
        expect({...color,body:base.body}).toEqual(base)
      }
      expect(dvdPalette(base,DVD.end,0)).toBe(base)
      expect(base).toEqual(original)
    }
  })
  it('respects calm mode and prevents sensor movement from breaking wall collisions',()=>{
    for(const t of [278.5,281.4,290.7,299]){
      const base=sampleScene(t),calm=sampleScene(t,true)
      expect(base.dvd).toBeGreaterThan(0)
      expect(reactScene(base,t,1,1,{...emptySensors,level:1,faces:1})).toEqual(base)
      expect(calm.dvd).toBe(0);expect(calm.x).toBe(820);expect(calm.y).toBe(350)
      expect(sampleScene(t+360).x).toBeCloseTo(base.x,6)
    }
  })
})
