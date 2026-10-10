package com.cyberyichen.bloub;
/** Tiny, bounded online classifier. The pretrained detector is never retrained from its own guesses. */
final class SeatLearner {
  static final int UNKNOWN=-1,EMPTY=0,OCCUPIED=1;
  float[] emptyAnchor,occupiedAnchor,emptyMean,occupiedMean;
  int emptyLabels,occupiedLabels,adaptations;
  static double distance(float[] a,float[] b){if(a==null||b==null||a.length!=b.length||a.length==0)return Double.POSITIVE_INFINITY;double s=0;for(int i=0;i<a.length;i++){double d=a[i]-b[i];s+=d*d;}return Math.sqrt(s/a.length);}
  boolean ready(){return emptyAnchor!=null&&occupiedAnchor!=null&&distance(emptyAnchor,occupiedAnchor)>.07;}
  void label(int kind,float[] f){
    if(f==null||f.length==0)return;
    if(kind==EMPTY){emptyAnchor=f.clone();emptyMean=f.clone();emptyLabels++;}
    else if(kind==OCCUPIED){occupiedAnchor=f.clone();occupiedMean=f.clone();occupiedLabels++;}
  }
  int predict(float[] f,double person){
    if(!ready()||!Double.isFinite(person)||person<0||person>1)return UNKNOWN;
    double e=distance(f,emptyMean),o=distance(f,occupiedMean);
    double separation=distance(emptyAnchor,occupiedAnchor),margin=Math.min(.08,separation*.35);
    if(e<.10&&o>e+Math.min(.06,separation*.3))return EMPTY;
    if(person>=.55&&e>Math.min(.12,separation*.6))return OCCUPIED;
    if(person>=.25&&o<.15&&e>o+margin)return OCCUPIED;
    if(person<.15&&e<.16&&o>e+margin)return EMPTY;
    return UNKNOWN;
  }
  boolean adapt(int kind,float[] f,double person){
    if(!ready()||predict(f,person)!=kind)return false;
    float[] mean=kind==EMPTY?emptyMean:occupiedMean,anchor=kind==EMPTY?emptyAnchor:occupiedAnchor;
    if(kind==EMPTY&&person>=.15||kind==OCCUPIED&&person<.7)return false;
    if(distance(f,anchor)>.20||distance(f,mean)>.15)return false;
    float[] next=mean.clone();for(int i=0;i<f.length;i++)next[i]=mean[i]*.98f+f[i]*.02f;
    if(distance(next,anchor)>.08)return false;
    if(kind==EMPTY)emptyMean=next;else occupiedMean=next;adaptations++;return true;
  }
  void reset(){emptyAnchor=occupiedAnchor=emptyMean=occupiedMean=null;emptyLabels=occupiedLabels=adaptations=0;}
}
