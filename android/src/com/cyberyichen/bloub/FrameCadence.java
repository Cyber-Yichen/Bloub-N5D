package com.cyberyichen.bloub;
/** Retains source timestamps while evenly dropping excess camera frames. */
final class FrameCadence {
 private final long step;private long next=-1;
 FrameCadence(int fps){if(fps<1||fps>60)throw new IllegalArgumentException();step=1000000000L/fps;}
 boolean accept(long at){if(next<0)next=at;if(at<next)return false;next+=step;if(at-next>step)next=at+step;return true;}
}
