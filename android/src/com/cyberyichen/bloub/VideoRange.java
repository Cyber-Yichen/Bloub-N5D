package com.cyberyichen.bloub;
/** A single RFC 9110 byte range; malformed and unsatisfiable requests are rejected. */
final class VideoRange {
  final long start,end;VideoRange(long start,long end){this.start=start;this.end=end;}long length(){return end-start+1;}
  static VideoRange parse(String header,long size){if(size<=0)throw new IllegalArgumentException("Empty video");if(header==null||header.isEmpty())return new VideoRange(0,size-1);if(!header.matches("bytes=[0-9]*-[0-9]*"))throw new IllegalArgumentException("Invalid range");String[] p=header.substring(6).split("-",-1);long a,b;try{if(p[0].isEmpty()){long suffix=Long.parseLong(p[1]);if(suffix<=0)throw new IllegalArgumentException();a=Math.max(0,size-suffix);b=size-1;}else{a=Long.parseLong(p[0]);b=p[1].isEmpty()?size-1:Math.min(size-1,Long.parseLong(p[1]));if(a<0||a>=size||b<a)throw new IllegalArgumentException();}}catch(NumberFormatException e){throw new IllegalArgumentException("Invalid range");}return new VideoRange(a,b);}
}
