package com.cyberyichen.bloub;
public final class CameraScheduleTest {
  private static int count;
  private static void check(boolean actual){count++;if(!actual)throw new AssertionError("check "+count);}
  public static void main(String[] args){
    check(CameraSchedule.quietMinute(1380,1380,480));check(CameraSchedule.quietMinute(0,1380,480));check(CameraSchedule.quietMinute(479,1380,480));
    check(!CameraSchedule.quietMinute(480,1380,480));check(!CameraSchedule.quietMinute(1379,1380,480));
    check(CameraSchedule.quietMinute(600,540,660));check(!CameraSchedule.quietMinute(660,540,660));check(!CameraSchedule.quietMinute(600,600,600));
    check(!CameraSchedule.allows(1380,1380,480,100000,0,true));check(!CameraSchedule.allows(0,1380,480,100000,99000,false));
    check(CameraSchedule.allows(1380,1380,480,100000,99000,true));check(CameraSchedule.allows(0,1380,480,100000,70000,true));
    check(!CameraSchedule.allows(0,1380,480,100000,69999,true));check(!CameraSchedule.allows(0,1380,480,100000,100001,true));
    check(CameraSchedule.allows(480,1380,480,100000,0,false));check(CameraSchedule.due(1000,0));
    check(!CameraSchedule.due(120999,1000));check(CameraSchedule.due(121000,1000));
    System.out.println("Camera schedule: "+count+" boundary/freshness/interval checks passed");
  }
}
