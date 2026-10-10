package com.cyberyichen.bloub;
/** Automatic camera policy, evaluated against the device's local hour and monotonic audio time. */
final class CameraSchedule {
  static boolean quietMinute(int minute,int start,int end){return start==end?false:start>end?minute>=start||minute<end:minute>=start&&minute<end;}
  static boolean allows(int minute,int start,int end,long now,long lastSound,boolean microphone){
    return !quietMinute(minute,start,end)||(microphone&&lastSound>0&&now>=lastSound&&now-lastSound<=30000);
  }
  static boolean due(long now,long lastAttempt){return lastAttempt==0||now-lastAttempt>=120000;}
}
