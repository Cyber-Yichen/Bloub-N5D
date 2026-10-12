package com.cyberyichen.bloub;
import java.util.*;
/** Requested event windows, independent of codec segmentation; overlapping motion events merge. */
final class MotionWindow {
  static final class Span {long start,end;Span(long start,long end){this.start=start;this.end=end;}}
  final ArrayList<Span> spans=new ArrayList<>();
  synchronized void event(long wall,long minutes){event(wall,minutes*60,minutes*60);}
  synchronized void event(long wall,long beforeSeconds,long afterSeconds){Span next=new Span(wall-beforeSeconds*1000,wall+afterSeconds*1000);if(!spans.isEmpty()&&spans.get(spans.size()-1).end>=next.start){Span previous=spans.get(spans.size()-1);previous.start=Math.min(previous.start,next.start);previous.end=Math.max(previous.end,next.end);}else spans.add(next);while(spans.size()>100)spans.remove(0);}
  synchronized List<Span> intersection(long start,long end){ArrayList<Span> result=new ArrayList<>();for(Span span:spans){long a=Math.max(start,span.start),b=Math.min(end,span.end);if(b>a)result.add(new Span(a,b));}return result;}
  synchronized void prune(long cutoff){spans.removeIf(s->s.end<=cutoff);}
  synchronized boolean active(long wall){return !spans.isEmpty()&&spans.get(spans.size()-1).end>wall;}
}
