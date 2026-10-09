import { describe,it,expect } from 'vitest'
import { stagePoint,gazeToward,TouchAttention,type BodyPose } from './interaction'
const body:BodyPose={x:820,y:350,scale:1,rotation:0,squash:1,yaw:12,pitch:8}
describe('screen attention',()=>{
  it('maps a scaled, offset stage exactly and ignores letterbox/physical hole/invalid geometry',()=>{
    const box={left:80,top:110,width:800,height:360}
    expect(stagePoint({x:680,y:210},box)).toEqual({x:1200,y:200})
    expect(stagePoint({x:79,y:210},box)).toBeNull()
    expect(stagePoint({x:102,y:290},box)).toBeNull()
    expect(stagePoint({x:400,y:100},{...box,width:0})).toBeNull()
    expect(stagePoint({x:NaN,y:200},box)).toBeNull()
  })
  it('looks left/right/up/down using the upstream pitch direction and bounds far targets',()=>{
    expect(gazeToward({x:300,y:350},body).yaw).toBeLessThan(-30)
    expect(gazeToward({x:1300,y:350},body).yaw).toBeGreaterThan(30)
    expect(gazeToward({x:820,y:80},body).pitch).toBeGreaterThan(18)
    expect(gazeToward({x:820,y:650},body).pitch).toBeLessThan(-18)
    expect(gazeToward({x:1e9,y:-1e9},body)).toEqual({yaw:38,pitch:24})
    expect(gazeToward({x:820,y:200},{...body,rotation:90}).yaw).toBeLessThan(0)
  })
  it('retargets smoothly, follows a moving body, holds briefly and returns without retaining old touches',()=>{
    const look=new TouchAttention();look.aim({x:1300,y:350},0)
    let previous=look.update(0,0,body)
    expect(previous.yaw).toBe(body.yaw)
    for(let i=1;i<=30;i++){const next=look.update(1/30,i/30,body);expect(Math.abs(next.yaw-previous.yaw)).toBeLessThan(7);previous=next}
    expect(previous.yaw).toBeGreaterThan(35)
    look.aim({x:300,y:350},1)
    const retarget=look.update(1/30,1+1/30,body)
    expect(Math.abs(retarget.yaw-previous.yaw)).toBeLessThan(20)
    for(let i=32;i<400;i++)previous=look.update(1/30,i/30,body)
    expect(look.target).toBeNull();expect(previous).toEqual({yaw:body.yaw,pitch:body.pitch,weight:0})
  })
  it('keeps following during a long drag, then fades after cancellation',()=>{
    const look=new TouchAttention();look.aim({x:1300,y:100},0,true)
    for(let i=0;i<300;i++)look.update(1/30,i/30,body)
    expect(look.update(1/30,10,body).weight).toBeGreaterThan(.99)
    look.cancel(10)
    for(let i=301;i<480;i++)look.update(1/30,i/30,body)
    expect(look.target).toBeNull()
  })
})
