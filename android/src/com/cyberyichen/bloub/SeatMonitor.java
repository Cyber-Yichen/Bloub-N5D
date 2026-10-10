package com.cyberyichen.bloub;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.util.*;
import org.json.*;
import java.util.*;
import java.text.SimpleDateFormat;
/** One device-local seat. Features, adaptive prototypes and 90-day estimated intervals stay private. */
final class SeatMonitor {
  private final Context context;private final HandlerThread thread=new HandlerThread("BloubSeat");private final Handler worker;
  private final SeatLearner learner=new SeatLearner();private final PresenceTimeline timeline=new PresenceTimeline();
  private volatile boolean enabled,active,busy;private volatile long detectionUntil;private long lastStatistic,lastRetentionSweep;private volatile long previewUntil,lastInference;private volatile String preview="",message="选择座位区域，确认空位和在位",modelStatus="模型待加载";
  private volatile DetectionFrame boxes;
  private static final class DetectionFrame {final long at;final java.util.List<PersonDetector.Box> items;DetectionFrame(long at,java.util.List<PersonDetector.Box> items){this.at=at;this.items=items;}}
  private PersonDetector detector;private int version;private String cameraId;
  private float[] roi={.15f,.08f,.85f,.95f},latest;private long latestAt,lastWall;private double personScore,inferenceMs;private boolean usable;
  SeatMonitor(Context c){context=c.getApplicationContext();enabled=prefs().getBoolean("enabled",false);cameraId=c.getSharedPreferences("sensors",0).getString("camera_id","2");load();thread.start();worker=new Handler(thread.getLooper());}
  private SharedPreferences prefs(){return context.getSharedPreferences("seat",0);}
  boolean enabled(){return enabled;}
  boolean detecting(){return active&&SystemClock.elapsedRealtime()<detectionUntil;}
  void detection(boolean value){detectionUntil=value?SystemClock.elapsedRealtime()+90000:0;if(!value)boxes=null;}
  void heartbeat(){if(detecting())detectionUntil=SystemClock.elapsedRealtime()+90000;}
  synchronized String currentState(){int s=active&&enabled&&learner.ready()?timeline.current(SystemClock.elapsedRealtime()):-1;return s==1?"occupied":s==0?"empty":"unknown";}
  String detections(){try{DetectionFrame f=boxes;JSONArray list=new JSONArray();if(detecting()&&f!=null&&SystemClock.elapsedRealtime()-f.at<1500)for(PersonDetector.Box b:f.items)list.put(new JSONObject().put("label",b.label).put("classId",b.classId).put("score",b.score).put("left",b.left).put("top",b.top).put("right",b.right).put("bottom",b.bottom));return new JSONObject().put("enabled",detecting()).put("captureUptime",f==null?0:f.at).put("boxes",list).toString();}catch(JSONException e){return "{}";}}
  void draw(Bitmap image,long at){DetectionFrame f=boxes;if(!detecting()||f==null||at<f.at||at-f.at>1500)return;Canvas canvas=new Canvas(image);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setStrokeWidth(2.5f);paint.setTextSize(18);
    for(PersonDetector.Box b:f.items){float x=b.left*image.getWidth(),y=b.top*image.getHeight(),right=b.right*image.getWidth(),bottom=b.bottom*image.getHeight();if(right<=x||bottom<=y)continue;paint.setColor(Color.rgb(57,118,203));paint.setStyle(Paint.Style.STROKE);canvas.drawRect(x,y,right,bottom,paint);String text=(b.classId==0?"人":b.label)+" "+Math.round(b.score*100)+"%";float width=Math.min(image.getWidth(),paint.measureText(text)+12),left=Math.min(x,image.getWidth()-width),top=y>27?y-27:Math.min(image.getHeight()-27,y);paint.setStyle(Paint.Style.FILL);canvas.drawRect(left,top,left+width,top+27,paint);paint.setColor(Color.WHITE);canvas.drawText(text,left+6,top+20,paint);}
  }
  boolean previewing(){return active&&SystemClock.elapsedRealtime()<previewUntil;}
  synchronized void resume(){active=true;}
  synchronized void suspendObservation(){timeline.pause();lastStatistic=0;persist();}
  synchronized void pause(){active=false;previewUntil=0;detection(false);version++;timeline.pause();persist();}
  synchronized void enabled(boolean value){enabled=value;version++;timeline.pause();prefs().edit().putBoolean("enabled",value).apply();message=value?(learner.ready()?"开始观察":"先确认空位和在位"):"已暂停";persist();}
  synchronized void preview(boolean value){previewUntil=value?SystemClock.elapsedRealtime()+15000:0;}
  synchronized void cameraChanged(String id){if(id.equals(cameraId))return;cameraId=id;learner.reset();timeline.pause();version++;latest=null;preview="";message="相机已更换，请重新确认空位和在位";persist();}
  synchronized void region(float left,float top,float right,float bottom){
    if(!Float.isFinite(left)||!Float.isFinite(top)||!Float.isFinite(right)||!Float.isFinite(bottom)||left<0||top<0||right>1||bottom>1||right-left<.15||bottom-top<.15)return;
    roi=new float[]{left,top,right,bottom};version++;learner.reset();timeline.pause();latest=null;message="区域已更新，请确认空位和在位";persist();
  }
  synchronized String label(String kind){try{
    if(!active||!usable||latest==null||SystemClock.elapsedRealtime()-latestAt>5000)return new JSONObject().put("accepted",false).put("message","等清晰的新画面出现后再确认").toString();
    int label="empty".equals(kind)?0:"occupied".equals(kind)?1:-1;if(label<0)return "{}";
    learner.label(label,latest);timeline.pause();message=learner.ready()?"已完成校准，开始适应工位":learner.emptyLabels>0&&learner.occupiedLabels>0?"两种画面太接近，请调整座位区域":"已记住"+(label==0?"空位":"在位")+"，再确认另一种";persist();
    return new JSONObject().put("accepted",true).put("message",message).toString();
  }catch(JSONException e){return "{}";}}
  String frame(){return preview;}
  synchronized boolean reserve(long now){if(!active||busy||(!enabled&&!previewing()&&!detecting())||now-lastInference<(detecting()?500:previewing()?2000:18000))return false;busy=true;lastInference=now;return true;}
  void cancelFrame(){busy=false;}
  void observe(Bitmap raw,String sourceId,long capturedAt){final int generation;final float[] region;synchronized(this){generation=version;region=roi.clone();}
    worker.post(()->{Bitmap upright=raw;try{
      if(!active)return;
      if("2".equals(sourceId)){Matrix m=new Matrix();m.postRotate(90);upright=Bitmap.createBitmap(raw,0,0,raw.getWidth(),raw.getHeight(),m,true);}
      if(detector==null){detector=new PersonDetector(context);modelStatus="EfficientDet-Lite0 · 本机 CPU";}
      float[] features=features(upright,region);double score=features==null?0:detector.person(upright,region);
      java.io.ByteArrayOutputStream image=new java.io.ByteArrayOutputStream();upright.compress(Bitmap.CompressFormat.JPEG,82,image);String jpeg="data:image/jpeg;base64,"+android.util.Base64.encodeToString(image.toByteArray(),android.util.Base64.NO_WRAP);
      long now=SystemClock.elapsedRealtime(),wall=System.currentTimeMillis();
      synchronized(this){if(!active||generation!=version||!sourceId.equals(cameraId))return;
        preview=jpeg;latest=features;latestAt=now;usable=features!=null;personScore=score;inferenceMs=detector.inferenceMs;lastWall=wall;boxes=new DetectionFrame(capturedAt,detector.detections);
        int guess=features==null?-1:learner.predict(features,score);
        if(enabled&&(lastStatistic==0||now-lastStatistic>=18000)){lastStatistic=now;timeline.sample(guess,now,wall);if(timeline.current(now)==guess&&guess>=0&&learner.adapt(guess,features,score))Log.d("BloubSeat","ADAPT state="+guess);persist();}
        if(features==null)message="画面偏暗或被遮挡，暂不统计";
        else if(learner.ready())message=guess<0?"画面待确认":"正在观察";
        Log.i("BloubSeat","INFERENCE person="+score+" guess="+guess+" state="+timeline.current(now)+" ms="+inferenceMs);
      }
    }catch(Throwable e){modelStatus="模型暂不可用";message="暂不统计，请检查相机";synchronized(this){timeline.pause();}Log.w("BloubSeat","Inference",e);}
    finally{if(upright!=raw)upright.recycle();raw.recycle();busy=false;}
    });
  }
  private float[] features(Bitmap image,float[] r){
    int x=Math.round(r[0]*image.getWidth()),y=Math.round(r[1]*image.getHeight()),w=Math.max(1,Math.round((r[2]-r[0])*image.getWidth())),h=Math.max(1,Math.round((r[3]-r[1])*image.getHeight()));w=Math.min(w,image.getWidth()-x);h=Math.min(h,image.getHeight()-y);
    Bitmap crop=Bitmap.createBitmap(image,x,y,w,h),small=Bitmap.createScaledBitmap(crop,12,12,true);int[] pixels=new int[144];small.getPixels(pixels,0,12,0,0,12,12);if(small!=crop)small.recycle();if(crop!=image)crop.recycle();
    float[] luminance=new float[144];double mean=0;for(int i=0;i<144;i++){int p=pixels[i];luminance[i]=(.299f*((p>>16)&255)+.587f*((p>>8)&255)+.114f*(p&255));mean+=luminance[i]/144;}
    double variance=0;for(float l:luminance)variance+=(l-mean)*(l-mean)/144;if(mean<12||mean>245||variance<16)return null;
    float[] f=new float[432];for(int i=0;i<144;i++){int p=pixels[i],red=(p>>16)&255,green=(p>>8)&255,blue=p&255;f[i*3]=(float)((luminance[i]-mean)/128);f[i*3+1]=(red-green)/255f;f[i*3+2]=(blue-green)/255f;}return f;
  }
  synchronized String snapshot(int days){try{
    long now=System.currentTimeMillis(),mono=SystemClock.elapsedRealtime();if(mono-lastRetentionSweep>=3600000||lastRetentionSweep==0){persist();lastRetentionSweep=mono;}Calendar today=Calendar.getInstance();today.setTimeInMillis(now);today.set(Calendar.HOUR_OF_DAY,0);today.set(Calendar.MINUTE,0);today.set(Calendar.SECOND,0);today.set(Calendar.MILLISECOND,0);
    int state=active&&enabled&&learner.ready()?timeline.current(mono):-1;
    String status=!enabled?"未开启":!learner.ready()?"等待工位校准":state==1?"座位有人":state==0?"座位无人":latestAt==0||mono-latestAt>60000?"未观察":"待确认";
    JSONArray daily=new JSONArray();SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd",Locale.US);
    for(int j=Math.max(1,Math.min(90,days))-1;j>=0;j--){Calendar begin=(Calendar)today.clone();begin.add(Calendar.DATE,-j);Calendar end=(Calendar)begin.clone();end.add(Calendar.DATE,1);long a=begin.getTimeInMillis(),b=end.getTimeInMillis();long[] t=timeline.totals(a,b);JSONArray intervals=new JSONArray();long first=0,last=0;
      for(PresenceTimeline.Segment seg:timeline.segments){long start=Math.max(a,seg.start),finish=Math.min(b,seg.end);if(finish<=start)continue;if(seg.state==1){if(first==0)first=start;last=finish;}intervals.put(new JSONObject().put("start",start).put("end",finish).put("state",seg.state==1?"occupied":seg.state==0?"empty":"unknown"));}
      daily.put(new JSONObject().put("date",format.format(begin.getTime())).put("start",a).put("end",b).put("occupiedMs",t[2]).put("emptyMs",t[1]).put("uncertainMs",t[0]).put("observedMs",t[0]+t[1]+t[2]).put("firstOccupiedAt",first).put("lastOccupiedAt",last).put("intervals",intervals));
    }
    return new JSONObject().put("detectionEnabled",detecting()).put("enabled",enabled).put("active",active).put("state",state==1?"occupied":state==0?"empty":"unknown").put("status",status).put("ready",learner.ready()).put("emptyConfirmed",learner.emptyLabels>0).put("occupiedConfirmed",learner.occupiedLabels>0).put("message",message).put("cameraId",cameraId).put("roi",array(roi)).put("lastObservedAt",lastWall).put("personScore",personScore).put("inferenceMs",inferenceMs).put("model",modelStatus).put("adaptations",learner.adaptations).put("retentionDays",90).put("sampleIntervalSeconds",20).put("timezone",TimeZone.getDefault().getID()).put("deviceTime",now).put("days",daily).toString();
  }catch(JSONException e){return "{}";}}
  private JSONArray array(float[] f)throws JSONException{JSONArray a=new JSONArray();if(f!=null)for(float v:f)a.put(v);return a;}
  private float[] vector(JSONObject d,String name,int length)throws JSONException{JSONArray a=d.optJSONArray(name);if(a==null||a.length()!=length)return null;float[] f=new float[length];for(int i=0;i<length;i++){f[i]=(float)a.getDouble(i);if(!Float.isFinite(f[i])||Math.abs(f[i])>2)return null;}return f;}
  private synchronized void persist(){try{
    timeline.prune(System.currentTimeMillis()-90L*86400000);JSONArray records=new JSONArray();for(PresenceTimeline.Segment s:timeline.segments)records.put(new JSONArray().put(s.start).put(s.end).put(s.state));
    JSONObject data=new JSONObject().put("roi",array(roi)).put("emptyAnchor",array(learner.emptyAnchor)).put("occupiedAnchor",array(learner.occupiedAnchor)).put("emptyMean",array(learner.emptyMean)).put("occupiedMean",array(learner.occupiedMean)).put("emptyLabels",learner.emptyLabels).put("occupiedLabels",learner.occupiedLabels).put("adaptations",learner.adaptations).put("cameraId",cameraId).put("segments",records);
    prefs().edit().putString("data",data.toString()).apply();
  }catch(JSONException ignored){}}
  private void load(){try{
    JSONObject d=new JSONObject(prefs().getString("data","{}"));float[] saved=vector(d,"roi",4);if(saved!=null&&saved[0]>=0&&saved[1]>=0&&saved[2]<=1&&saved[3]<=1&&saved[2]-saved[0]>=.15&&saved[3]-saved[1]>=.15)roi=saved;
    if(cameraId.equals(d.optString("cameraId"))){learner.emptyAnchor=vector(d,"emptyAnchor",432);learner.occupiedAnchor=vector(d,"occupiedAnchor",432);learner.emptyMean=vector(d,"emptyMean",432);learner.occupiedMean=vector(d,"occupiedMean",432);learner.emptyLabels=Math.max(0,d.optInt("emptyLabels"));learner.occupiedLabels=Math.max(0,d.optInt("occupiedLabels"));learner.adaptations=Math.max(0,d.optInt("adaptations"));
      if(SeatLearner.distance(learner.emptyAnchor,learner.emptyMean)>.08&&learner.emptyAnchor!=null)learner.emptyMean=learner.emptyAnchor.clone();if(SeatLearner.distance(learner.occupiedAnchor,learner.occupiedMean)>.08&&learner.occupiedAnchor!=null)learner.occupiedMean=learner.occupiedAnchor.clone();}
    JSONArray records=d.optJSONArray("segments");if(records!=null)for(int i=Math.max(0,records.length()-10000);i<records.length();i++){JSONArray s=records.getJSONArray(i);long a=s.getLong(0),b=s.getLong(1);int kind=s.getInt(2);if(a>0&&b>a&&kind>=-1&&kind<=1)timeline.add(a,b,kind);}
    timeline.prune(System.currentTimeMillis()-90L*86400000);
  }catch(Exception e){Log.w("BloubSeat","Stored calibration",e);learner.reset();timeline.segments.clear();}}
  void destroy(){pause();worker.post(()->{if(detector!=null)detector.close();thread.quitSafely();});}
}
