package com.cyberyichen.bloub;
import java.util.*;
/** Count only consecutive, fresh and confirmed observations, using a monotonic clock. */
final class PresenceTimeline {
  static final long STALE_MS=60000,CONFIRM_SPACING_MS=10000;
  static final class Segment {long start,end;final int state;Segment(long a,long b,int s){start=a;end=b;state=s;}}
  final ArrayList<Segment> segments=new ArrayList<>();
  int state=-1,candidate=-1,hits;long lastMono=-1,lastWall,lastCandidateMono=-1,firstArrival;
  void sample(int raw,long mono,long wall){
    if(raw<-1||raw>1)raw=-1;
    long delta=lastMono<0?0:mono-lastMono;
    boolean continuous=lastMono>=0&&delta>=0&&delta<=STALE_MS&&Math.abs((wall-lastWall)-delta)<=5000;
    if(!continuous){state=candidate=-1;hits=0;lastCandidateMono=-1;}
    int previous=current(mono);
    if(raw<0){state=candidate=-1;hits=0;lastCandidateMono=-1;}
    else if(raw!=candidate){candidate=raw;hits=1;lastCandidateMono=mono;}
    else if(mono-lastCandidateMono>=CONFIRM_SPACING_MS){hits++;lastCandidateMono=mono;}
    if(raw>=0&&hits>=3)state=raw;
    int current=current(mono);int interval=continuous&&previous==current&&current==raw?current:-1;
    if(continuous&&delta>0)add(lastWall,lastWall+delta,interval);
    if(state==1&&previous!=1&&firstArrival==0)firstArrival=wall;
    lastMono=mono;lastWall=wall;
  }
  void add(long start,long end,int kind){
    if(end<=start)return;
    if(!segments.isEmpty()){Segment p=segments.get(segments.size()-1);if(p.state==kind&&p.end==start){p.end=end;return;}}
    segments.add(new Segment(start,end,kind));
    while(segments.size()>10000)segments.remove(0);
  }
  int current(long mono){return lastMono>=0&&mono>=lastMono&&mono-lastMono<=STALE_MS&&candidate==state&&hits>=3?state:-1;}
  void pause(){state=candidate=-1;hits=0;lastMono=-1;lastCandidateMono=-1;}
  long[] totals(long start,long end){long[] t=new long[3];for(Segment s:segments){long ms=Math.max(0,Math.min(end,s.end)-Math.max(start,s.start));t[s.state+1]+=ms;}return t;}
  void prune(long cutoff){for(Iterator<Segment> i=segments.iterator();i.hasNext();){Segment s=i.next();if(s.end<=cutoff)i.remove();else if(s.start<cutoff)s.start=cutoff;}}
}
