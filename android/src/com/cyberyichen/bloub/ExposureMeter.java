package com.cyberyichen.bloub;
import java.util.Arrays;
/** Bounded highlight-aware correction around the HAL's automatic exposure. */
final class ExposureMeter {
  static long nextGain(long current,int correction,long minimum,long maximum){return Math.max(minimum,Math.min(maximum,Math.round(current*Math.pow(2,correction*.35))));}
  static int correction(int[] luminance){
    if(luminance==null||luminance.length<32)return 0;int[] sorted=luminance.clone();Arrays.sort(sorted);int bright=0;for(int v:sorted)if(v>=245)bright++;
    int mid=sorted[(int)((sorted.length-1)*.6)];
    if(bright>sorted.length*.12||mid>135)return -1;
    if(mid<88)return 1;
    return 0;
  }
}
