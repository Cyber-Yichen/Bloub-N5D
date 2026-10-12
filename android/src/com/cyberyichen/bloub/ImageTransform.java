package com.cyberyichen.bloub;
/** Normalized clockwise rotation, followed by optional horizontal mirroring. */
final class ImageTransform {
 static float[] point(float x,float y,int turn,boolean mirror){float a=x,b=y;if(turn==90){a=1-y;b=x;}else if(turn==180){a=1-x;b=1-y;}else if(turn==270){a=y;b=1-x;}return new float[]{mirror?1-a:a,b};}
 static float[] bounds(float left,float top,float right,float bottom,int turn,boolean mirror){float[] a=point(left,top,turn,mirror),b=point(right,bottom,turn,mirror);return new float[]{Math.min(a[0],b[0]),Math.min(a[1],b[1]),Math.max(a[0],b[0]),Math.max(a[1],b[1])};}
 static float[] crop(float sourceAspect,float targetAspect){if(sourceAspect>targetAspect){float width=targetAspect/sourceAspect;return new float[]{(1-width)/2,0,(1+width)/2,1};}float height=sourceAspect/targetAspect;return new float[]{0,(1-height)/2,1,(1+height)/2};}
 static float[] bounds(float l,float t,float r,float b,int turn,boolean mirror,float[] crop){if(crop==null)return bounds(l,t,r,b,turn,mirror);l=Math.max(l,crop[0]);t=Math.max(t,crop[1]);r=Math.min(r,crop[2]);b=Math.min(b,crop[3]);if(r<=l||b<=t)return null;return bounds((l-crop[0])/(crop[2]-crop[0]),(t-crop[1])/(crop[3]-crop[1]),(r-crop[0])/(crop[2]-crop[0]),(b-crop[1])/(crop[3]-crop[1]),turn,mirror);}
}
