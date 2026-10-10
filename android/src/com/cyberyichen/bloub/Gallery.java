package com.cyberyichen.bloub;
import android.content.*;
import android.app.*;
import android.os.SystemClock;
import android.graphics.Bitmap;
import java.io.*;
import java.util.*;
import org.json.*;
/** App-private observations. No upload, biometric identity or unrestricted file API. */
final class Gallery {
  static final long TTL=7L*24*60*60*1000;
  private static final Object LOCK=new Object();
  private final Context context;private final File directory;
  Gallery(Context c){context=c.getApplicationContext();directory=new File(context.getFilesDir(),"observations");directory.mkdirs();}
  boolean enabled(){return context.getSharedPreferences("gallery",0).getBoolean("enabled",true);}
  void enabled(boolean value){context.getSharedPreferences("gallery",0).edit().putBoolean("enabled",value).apply();}
  private File file(String id,String extension){if(id==null||!id.matches("[0-9]{10,17}"))return null;return new File(directory,id+extension);}
  void prune(){synchronized(LOCK){
    long cutoff=System.currentTimeMillis()-TTL;File[] files=directory.listFiles();if(files==null)return;
    Set<String> expired=new HashSet<>();for(File f:files){String id=f.getName().split("\\.")[0];try{long captured=Long.parseLong(id);File meta=file(id,".json");if(meta!=null&&meta.exists())try{captured=new JSONObject(new String(readAll(meta),"UTF-8")).optLong("capturedAt",captured);}catch(Exception ignored){}if(captured<=cutoff)expired.add(id);}catch(NumberFormatException ignored){}}
    for(File f:files)if(expired.contains(f.getName().split("\\.")[0])||f.getName().endsWith(".tmp"))f.delete();
  }}
  void save(Bitmap image,int faces,float motion)throws IOException,JSONException{ByteArrayOutputStream out=new ByteArrayOutputStream();if(!image.compress(Bitmap.CompressFormat.JPEG,95,out))throw new IOException("JPEG failed");saveJpeg(out.toByteArray(),faces,motion,"2",90);}
  String saveJpeg(byte[] jpeg,int faces,float motion,String cameraId,int rotation)throws IOException,JSONException{return saveJpeg(jpeg,faces,motion,cameraId,rotation,new JSONObject());}
  String saveJpeg(byte[] jpeg,int faces,float motion,String cameraId,int rotation,JSONObject annotation)throws IOException,JSONException{synchronized(LOCK){
    if(!enabled())return "";prune();
    android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();bounds.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeByteArray(jpeg,0,jpeg.length,bounds);
    if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IOException("Invalid JPEG");
    long now=annotation.optLong("capturedAt",System.currentTimeMillis()),order=nextOrder(System.currentTimeMillis());String id=Long.toString(order);File temp=file(id,".tmp");
    annotation.put("cameraId",cameraId).put("rotationClockwise",rotation).put("width",bounds.outWidth).put("height",bounds.outHeight);
    if(!annotation.has("seatState"))annotation.put("seatState","unknown").put("annotationSource","unavailable").put("manualGroundTruth",false).put("capturedAt",now);
    jpeg=PhotoAnnotation.embed(jpeg,annotation.toString());
    try(FileOutputStream out=new FileOutputStream(temp)){out.write(jpeg);out.getFD().sync();}if(!temp.renameTo(file(id,".jpg"))){temp.delete();throw new IOException("Save failed");}
    try{
    JSONObject data=new JSONObject(annotation.toString()).put("id",id).put("capturedAt",now).put("captureOrder",order).put("faces",0).put("faceAnalysis",false).put("motion",motion).put("cameraId",cameraId).put("rotationClockwise",rotation).put("width",bounds.outWidth).put("height",bounds.outHeight);
    File metadataTemp=file(id,".json.tmp");try(FileOutputStream out=new FileOutputStream(metadataTemp)){out.write(data.toString().getBytes("UTF-8"));out.getFD().sync();}if(!metadataTemp.renameTo(file(id,".json")))throw new IOException("Metadata save failed");
    }catch(IOException|JSONException e){file(id,".jpg").delete();file(id,".json.tmp").delete();throw e;}
    try{
    android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inSampleSize=Math.max(1,Math.max(bounds.outWidth,bounds.outHeight)/320);
    Bitmap thumbnail=android.graphics.BitmapFactory.decodeByteArray(jpeg,0,jpeg.length,options);
    if(thumbnail!=null){try(FileOutputStream out=new FileOutputStream(file(id,".thumb.jpg"))){thumbnail.compress(Bitmap.CompressFormat.JPEG,85,out);}finally{thumbnail.recycle();}}
    }catch(IOException thumbnailFailure){file(id,".thumb.jpg").delete();}
    return id;
  }}
  String list(long before){return list(before,0,Long.MAX_VALUE,"all");}
  String list(long before,long start,long end,String state){synchronized(LOCK){
    prune();ArrayList<JSONObject> all=new ArrayList<>();File[] files=directory.listFiles();
    if(files!=null)for(File f:files)if(f.getName().endsWith(".json"))try{
      JSONObject d=new JSONObject(new String(readAll(f),"UTF-8"));
      if(!d.has("rotationClockwise"))d.put("rotationClockwise",90);
      if(!d.has("captureOrder"))d.put("captureOrder",d.getLong("capturedAt"));
      if(!d.has("seatState"))d.put("seatState","unknown").put("annotationSource","legacy").put("manualGroundTruth",false);
      long captured=d.optLong("capturedAt");if(captured<start||captured>=end||(!state.equals("all")&&!state.equals(d.optString("seatState"))))continue;
      String id=d.getString("id");d.put("url","/observations/"+id+".jpg").put("thumbnailUrl","/thumbnails/"+id+".jpg");all.add(d);
    }catch(Exception ignored){}
    all.sort((a,b)->Long.compare(b.optLong("captureOrder"),a.optLong("captureOrder")));
    JSONArray items=new JSONArray();long next=0;
    for(JSONObject d:all){long order=d.optLong("captureOrder");if(before>0&&order>=before)continue;
      if(items.length()>=12){next=items.optJSONObject(items.length()-1).optLong("captureOrder");break;}items.put(d);
    }
    try{return new JSONObject().put("enabled",enabled()).put("retentionDays",7).put("total",all.size()).put("items",items).put("nextBefore",next).toString();}catch(JSONException e){return "{}";}
  }}
  private long nextOrder(long now){
    android.content.SharedPreferences prefs=context.getSharedPreferences("gallery",0);long previous=prefs.getLong("capture_order",0);
    if(previous==0){File[] files=directory.listFiles();if(files!=null)for(File f:files)if(f.getName().endsWith(".json"))try{JSONObject data=new JSONObject(new String(readAll(f),"UTF-8"));previous=Math.max(previous,data.optLong("captureOrder",data.optLong("capturedAt")));}catch(Exception ignored){}}
    long order=Math.max(now,previous+1);prefs.edit().putLong("capture_order",order).apply();return order;
  }
  InputStream open(String id)throws IOException{synchronized(LOCK){prune();File f=file(id,".jpg");if(f==null)throw new IOException("Invalid photo");return new FileInputStream(f);}}
  InputStream thumbnail(String id)throws IOException{synchronized(LOCK){prune();File f=file(id,".thumb.jpg");if(f==null)throw new IOException("Invalid photo");return f.exists()?new FileInputStream(f):open(id);}}
  JSONObject metadata(String id)throws IOException,JSONException{synchronized(LOCK){prune();File f=file(id,".json");if(f==null)throw new IOException("Invalid photo");JSONObject data=new JSONObject(new String(readAll(f),"UTF-8"));if(!data.has("rotationClockwise"))data.put("rotationClockwise",90);return data;}}
  static String filename(JSONObject d){String at=new java.text.SimpleDateFormat("yyyyMMdd'T'HHmmssSSS",Locale.US).format(new Date(d.optLong("capturedAt")));return "Bloub_"+at+"_"+d.optString("seatState","unknown")+"_"+d.optString("id")+".jpg";}
  boolean delete(String id){synchronized(LOCK){File f=file(id,".jpg"),meta=file(id,".json");if(f==null)return false;boolean removed=!f.exists()||f.delete();if(meta!=null)meta.delete();File thumb=file(id,".thumb.jpg");if(thumb!=null)thumb.delete();return removed;}}
  void clear(){synchronized(LOCK){File[] files=directory.listFiles();if(files!=null)for(File f:files)if(f.getName().matches("[0-9]{10,17}\\.(jpg|thumb\\.jpg|json)"))f.delete();}}
  private byte[] readAll(File f)throws IOException{try(FileInputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[2048];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);return out.toByteArray();}}
  void schedule(){
    AlarmManager alarm=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
    Intent intent=new Intent(context,GalleryCleanupReceiver.class);
    PendingIntent pending=PendingIntent.getBroadcast(context,7,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    alarm.setInexactRepeating(AlarmManager.ELAPSED_REALTIME,SystemClock.elapsedRealtime()+3600000,3600000,pending);
  }
}
