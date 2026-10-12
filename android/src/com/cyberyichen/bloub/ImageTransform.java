package com.cyberyichen.bloub;
/** Normalized clockwise rotation, followed by optional horizontal mirroring. */
final class ImageTransform {
 static float[] point(float x,float y,int turn,boolean mirror){float a=x,b=y;if(turn==90){a=1-y;b=x;}else if(turn==180){a=1-x;b=1-y;}else if(turn==270){a=y;b=1-x;}return new float[]{mirror?1-a:a,b};}
 static float[] bounds(float left,float top,float right,float bottom,int turn,boolean mirror){float[] a=point(left,top,turn,mirror),b=point(right,bottom,turn,mirror);return new float[]{Math.min(a[0],b[0]),Math.min(a[1],b[1]),Math.max(a[0],b[0]),Math.max(a[1],b[1])};}
}
