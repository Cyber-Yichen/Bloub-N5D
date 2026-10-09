import { mixHex } from '../bot/skins'
import { smooth } from './scene'

export interface Palette { paper:string; body:string; rim:string; amber:string; quiet:string }
export const THEMES = [
  {id:'paper',name:'白底黑色',paper:'#ffffff',body:'#0a0a0c',rim:'#899c9c',amber:'#b37c39',quiet:'#87918d'},
  {id:'lagoon',name:'深海薄荷',paper:'#101d26',body:'#a5ccc4',rim:'#48717a',amber:'#d7ad7a',quiet:'#70918f'},
  {id:'sand',name:'暖沙棕色',paper:'#eadfd3',body:'#4b3934',rim:'#a89482',amber:'#ab6942',quiet:'#9c8877'},
  {id:'night',name:'黑底银白',paper:'#080b10',body:'#d4dadd',rim:'#424e62',amber:'#adb6d2',quiet:'#69788f'}
] as const
export type ThemeId = typeof THEMES[number]['id'] | 'auto'
export function blendPalette(a:Palette,b:Palette,t:number):Palette {
  return {paper:mixHex(a.paper,b.paper,t),body:mixHex(a.body,b.body,t),rim:mixHex(a.rim,b.rim,t),amber:mixHex(a.amber,b.amber,t),quiet:mixHex(a.quiet,b.quiet,t)}
}
export function paletteAt(id:ThemeId,t:number):Palette {
  if(id!=='auto')return THEMES.find(theme=>theme.id===id)??THEMES[0]
  const cycle=Math.floor(t/180),next=THEMES[cycle%THEMES.length]!
  if(cycle===0)return next
  return blendPalette(THEMES[(cycle-1)%THEMES.length]!,next,smooth((t%180)/12))
}
export function luminance(hex:string):number {
  const n=parseInt(hex.slice(1),16)
  return (((n>>16)&255)*.2126+((n>>8)&255)*.7152+(n&255)*.0722)/255
}
