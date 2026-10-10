package com.cyberyichen.bloub;
import java.util.*;
public final class SeatCoreTest {
  static int n;static void check(boolean x,String why){n++;if(!x)throw new AssertionError(why);}
  static float[] f(float v){float[] a=new float[432];Arrays.fill(a,v);return a;}
  public static void main(String[] args){
    SeatLearner l=new SeatLearner();check(l.predict(f(0),.9)==-1,"no unlabeled guessing");l.label(0,f(0));check(!l.ready(),"need occupied anchor");l.label(1,f(.3f));check(l.ready(),"two distinct anchors");
    check(l.predict(f(0),.02)==0,"empty anchor");check(l.predict(f(.3f),.9)==1,"occupied person");check(l.predict(f(.15f),.2)==-1,"ambiguous stays unknown");check(!l.adapt(1,f(.3f),.2),"no self-label from weak person evidence");check(!l.adapt(0,f(.05f),.8),"no empty training while detector sees person");
    check(l.predict(f(.7f),.9)==1,"new appearance detected without training drift");check(!l.adapt(1,f(.7f),.9),"new appearance cannot overwrite anchor");
    for(int i=0;i<2000;i++)l.adapt(0,f(.07f),.02);check(SeatLearner.distance(l.emptyMean,l.emptyAnchor)<=.08,"bounded long-run drift");l.reset();check(!l.ready()&&l.adaptations==0,"reset calibration");l.label(0,f(0));l.label(1,f(.01f));check(!l.ready(),"anchors too similar");
    SeatLearner narrow=new SeatLearner();narrow.label(0,f(0));narrow.label(1,f(.075f));check(narrow.ready()&&narrow.predict(f(.075f),.8)==1,"distinct narrow-region anchors classify occupied");check(narrow.predict(f(.005f),.85)==0,"person outside empty seat cannot override empty anchor");check(narrow.predict(f(.055f),.85)==1,"small pose changes remain occupied with person evidence");
    PresenceTimeline t=new PresenceTimeline();long wall=1000000;
    t.sample(1,0,wall);check(t.current(0)==-1,"first frame not confirmed");t.sample(1,500,wall+500);t.sample(1,1000,wall+1000);check(t.current(1000)==-1,"fast frames cannot speed confirmation");
    t.sample(1,20000,wall+20000);t.sample(1,40000,wall+40000);check(t.current(40000)==1,"three spaced confirmations");t.sample(1,60000,wall+60000);check(t.totals(wall,wall+60000)[2]==20000,"only confirmed consecutive interval counts");check(t.current(120001)==-1,"stale is unknown");
    t.sample(0,80000,wall+80000);check(t.current(80000)==-1,"transition uncertain");t.sample(0,100000,wall+100000);t.sample(0,120000,wall+120000);t.sample(0,140000,wall+140000);check(t.totals(wall,wall+140000)[1]==20000,"empty credited after confirmation");check(t.totals(wall,wall+140000)[2]==20000,"departure pending not occupied");
    long before=t.totals(wall,wall+10000000)[2];t.sample(1,160000,wall+1000000);check(t.current(160000)==-1&&t.totals(wall,wall+10000000)[2]==before,"wall clock jump not credited");
    t.pause();t.sample(1,1000000,wall+2000000);check(t.current(1000000)==-1,"resume no bridge over pause");
    PresenceTimeline gap=new PresenceTimeline();gap.sample(1,0,wall);gap.sample(1,20000,wall+20000);gap.sample(1,40000,wall+40000);gap.sample(1,200000,wall+200000);check(gap.totals(wall,wall+200000)[2]==0,"sampling gap never becomes occupied time");
    PresenceTimeline midnight=new PresenceTimeline();midnight.add(900,1100,1);check(midnight.totals(0,1000)[2]==100&&midnight.totals(1000,2000)[2]==100,"day boundary splits exactly");midnight.add(1100,1200,1);check(midnight.segments.size()==1,"merge consecutive intervals");midnight.prune(1000);check(midnight.segments.get(0).start==1000,"clip partial retention boundary");midnight.prune(1300);check(midnight.segments.isEmpty(),"retention");
    System.out.println("Seat core: "+n+" calibration, drift, confidence, timing, clock and retention checks passed");
  }
}
