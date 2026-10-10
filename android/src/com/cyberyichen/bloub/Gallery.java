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
    for(File f:files){String id=f.getName().split("\\.")[0];try{if(Long.parseLong(id)<=cutoff)f.delete();}catch(NumberFormatException ignored){if(f.getName().endsWith(".tmp"))f.delete();}}
  }}
  void save(Bitmap image,int faces,float motion)throws IOException,JSONException{synchronized(LOCK){
    if(!enabled())return;prune();long now=System.currentTimeMillis();String id=Long.toString(now);
    File temp=new File(directory,id+".tmp"),photo=file(id,".jpg");
    try(FileOutputStream out=new FileOutputStream(temp)){if(!image.compress(Bitmap.CompressFormat.JPEG,82,out))throw new IOException("JPEG failed");}
    if(!temp.renameTo(photo)){temp.delete();throw new IOException("Save failed");}
    JSONObject data=new JSONObject().put("id",id).put("capturedAt",now).put("captureOrder",nextOrder(now)).put("faces",0).put("faceAnalysis",false).put("motion",motion).put("rotationClockwise",90).put("cameraId","2").put("width",image.getWidth()).put("height",image.getHeight());
    try(FileOutputStream out=new FileOutputStream(file(id,".json"))){out.write(data.toString().getBytes("UTF-8"));}
  }}
  String saveJpeg(byte[] jpeg,int faces,float motion,String cameraId,int rotation)throws IOException,JSONException{synchronized(LOCK){
    if(!enabled())return "";prune();
    android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();bounds.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeByteArray(jpeg,0,jpeg.length,bounds);
    if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IOException("Invalid JPEG");
    long now=System.currentTimeMillis();String id=Long.toString(now);File temp=file(id,".tmp");
    try(FileOutputStream out=new FileOutputStream(temp)){out.write(jpeg);}if(!temp.renameTo(file(id,".jpg"))){temp.delete();throw new IOException("Save failed");}
    android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inSampleSize=Math.max(1,Math.max(bounds.outWidth,bounds.outHeight)/320);
    Bitmap thumbnail=android.graphics.BitmapFactory.decodeByteArray(jpeg,0,jpeg.length,options);
    if(thumbnail!=null){try(FileOutputStream out=new FileOutputStream(file(id,".thumb.jpg"))){thumbnail.compress(Bitmap.CompressFormat.JPEG,85,out);}finally{thumbnail.recycle();}}
    JSONObject data=new JSONObject().put("id",id).put("capturedAt",now).put("captureOrder",nextOrder(now)).put("faces",0).put("faceAnalysis",false).put("motion",motion).put("cameraId",cameraId).put("rotationClockwise",rotation).put("width",bounds.outWidth).put("height",bounds.outHeight);
    try(FileOutputStream out=new FileOutputStream(file(id,".json"))){out.write(data.toString().getBytes("UTF-8"));}
    return id;
  }}
  String list(long before){synchronized(LOCK){
    prune();ArrayList<JSONObject> all=new ArrayList<>();File[] files=directory.listFiles();
    if(files!=null)for(File f:files)if(f.getName().endsWith(".json"))try{
      JSONObject d=new JSONObject(new String(readAll(f),"UTF-8"));
      if(!d.has("rotationClockwise"))d.put("rotationClockwise",90);
      if(!d.has("captureOrder"))d.put("captureOrder",d.getLong("capturedAt"));
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
