// Export app-private observations through ADB for local research. No uploads.
import { execFileSync } from 'node:child_process'
import { mkdirSync,writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
const adb=process.env.ADB||'adb',app='com.cyberyichen.bloub'
const connected=execFileSync(adb,['devices'],{encoding:'utf8'}).split(/\r?\n/).filter(x=>/\tdevice$/.test(x)).map(x=>x.split('\t')[0])
const serial=process.env.N5D_SERIAL||(connected.length===1?connected[0]:null)
if(!serial)throw Error('Set N5D_SERIAL when zero or multiple devices are connected')
const run=(...args)=>execFileSync(adb,['-s',serial,...args],{timeout:15000,maxBuffer:8*1024*1024})
const limitArg=process.argv.find(x=>x.startsWith('--limit='))
const limit=limitArg?Number(limitArg.slice(8)):Infinity
if(!(limit>0)||limit!==Infinity&&!Number.isInteger(limit))throw Error('Use --limit=<positive integer>')
// Use the device clock, which also defines the gallery retention window.
const now=Number(run('shell','date','+%s').toString().trim())*1000
if(!Number.isFinite(now)||now<=0)throw Error('Invalid device clock')
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..')
const output=path.join(root,'n5d-reports','gallery',new Date().toISOString().replace(/[:.]/g,'-'))
mkdirSync(output,{recursive:true})
const names=run('shell','run-as',app,'ls','-1','files/observations').toString().split(/\r?\n/)
const ids=names.filter(n=>/^[0-9]{10,17}\.json$/.test(n)).map(n=>n.slice(0,-5)).filter(id=>Number(id)>now-7*86400000&&Number(id)<=now).sort().reverse().slice(0,limit)
const items=[]
for(const id of ids){
  try{
    const meta=JSON.parse(run('exec-out','run-as',app,'cat','files/observations/'+id+'.json').toString())
    if(meta.id!==id||meta.capturedAt!==Number(id))throw Error('Invalid metadata')
    const jpg=run('exec-out','run-as',app,'cat','files/observations/'+id+'.jpg')
    if(jpg[0]!==255||jpg[1]!==216)throw Error('Invalid JPEG')
    writeFileSync(path.join(output,id+'.jpg'),jpg)
    items.push({...meta,file:id+'.jpg'})
  }catch(e){console.warn('Skipped observation '+id+': '+e.message)}
}
writeFileSync(path.join(output,'manifest.json'),JSON.stringify({exportedAt:new Date().toISOString(),deviceTime:now,retentionDays:7,items},null,2))
console.log(JSON.stringify({output,exported:items.length,notice:'Local copies remain until you delete them; device cleanup does not delete exports.'},null,2))
