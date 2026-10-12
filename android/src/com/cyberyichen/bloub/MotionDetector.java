package com.cyberyichen.bloub;
/** Exposure-compensated luma differences, evaluated on the clean camera frame before overlays. */
final class MotionDetector {
  private int[] previous;private long last;
  boolean changed(int[] pixels,long now){if(pixels==null||pixels.length<32)return false;if(previous==null||previous.length!=pixels.length||now-last>5000){previous=pixels.clone();last=now;return false;}double mean=0;for(int i=0;i<pixels.length;i++)mean+=pixels[i]-previous[i];mean/=pixels.length;int changed=0;for(int i=0;i<pixels.length;i++)if(Math.abs(pixels[i]-previous[i]-mean)>18)changed++;previous=pixels.clone();last=now;return changed>pixels.length*.07;}
}
