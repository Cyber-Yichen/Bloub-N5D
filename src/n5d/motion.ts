export const clamp01=(v:number)=>Math.max(0,Math.min(1,v))
export const smooth=(v:number)=>{const t=clamp01(v);return t*t*t*(10+t*(-15+6*t))}
export function hermite(t:number,d:number,p0:number,p1:number,v0=0,v1=0,a0=0,a1=0){
  const u=clamp01(t/d),v=v0*d,a=a0*d*d/2
  const q=p1-p0-v-a,r=v1*d-v-2*a,s=a1*d*d-2*a
  return p0+v*u+a*u*u+(10*q-4*r+s/2)*u**3+(-15*q+7*r-s)*u**4+(6*q-3*r+s/2)*u**5
}
