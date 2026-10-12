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
  int retentionDays(){return new DeviceSettings(context).get().optInt("photoRetentionDays",7);}
  private long retentionMillis(){return retentionDays()*86400000L;}
  private static final Object LOCK=new Object();private static long diskVersion,lastPruneMono,lastPruneWall;private static int lastPruneDays;
  private long indexVersion=-1;private ArrayList<JSONObject> index;private final HashMap<String,JSONObject> byId=new HashMap<>();
  private void invalidate(){diskVersion++;index=null;}
  private ArrayList<JSONObject> entries(){
    if(index!=null&&indexVersion==diskVersion)return index;
    ArrayList<JSONObject> all=new ArrayList<>();File[] files=directory.listFiles();
    if(files!=null)for(File f:files)if(f.getName().endsWith(".json"))try{
      JSONObject d=new JSONObject(new String(readAll(f),"UTF-8"));String id=d.getString("id");if(file(id,".jpg")==null||!file(id,".jpg").exists())continue;
      if(!d.has("rotationClockwise"))d.put("rotationClockwise",90);if(!d.has("captureOrder"))d.put("captureOrder",d.getLong("capturedAt"));
      if(!d.has("seatState"))d.put("seatState","unknown").put("annotationSource","legacy").put("manualGroundTruth",false);all.add(d);
    }catch(Exception ignored){}
    all.sort((a,b)->Long.compare(b.optLong("captureOrder"),a.optLong("captureOrder")));index=all;byId.clear();for(JSONObject d:all)byId.put(d.optString("id"),d);indexVersion=diskVersion;return all;
  }
  private final Context context;private final File directory;
  Gallery(Context c){context=c.getApplicationContext();directory=new File(context.getFilesDir(),"observations");directory.mkdirs();}
  boolean enabled(){return context.getSharedPreferences("gallery",0).getBoolean("enabled",true)&&!new DeviceSettings(context).get().optBoolean("monitorEnabled");}
  void enabled(boolean value){context.getSharedPreferences("gallery",0).edit().putBoolean("enabled",value).apply();}
  private File file(String id,String extension){if(id==null||!id.matches("[0-9]{10,17}"))return null;return new File(directory,id+extension);}
  void prune(){synchronized(LOCK){
    long wall=System.currentTimeMillis(),mono=SystemClock.elapsedRealtime();int days=retentionDays();
    if(lastPruneDays==days&&lastPruneMono>0&&mono-lastPruneMono<300000&&Math.abs((wall-lastPruneWall)-(mono-lastPruneMono))<30000)return;
    lastPruneMono=mono;lastPruneWall=wall;lastPruneDays=days;long cutoff=wall-days*86400000L;File[] files=directory.listFiles();if(files==null)return;
    Set<String> expired=new HashSet<>();for(File f:files)if(f.getName().endsWith(".json"))try{JSONObject d=new JSONObject(new String(readAll(f),"UTF-8"));if(d.optLong("capturedAt",Long.MAX_VALUE)<=cutoff)expired.add(f.getName().split("\\.")[0]);}catch(Exception ignored){}
    boolean changed=false;for(File f:files){String id=f.getName().split("\\.")[0];boolean old=false;try{old=Long.parseLong(id)<=cutoff&&!new File(directory,id+".json").exists();}catch(NumberFormatException ignored){}
      if(expired.contains(id)||old||f.getName().endsWith(".tmp"))changed=f.delete()||changed;
    }if(changed)invalidate();
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
    JSONObject data=new JSONObject(annotation.toString()).put("id",id).put("capturedAt",now).put("captureOrder",order).put("faces",annotation.optInt("faceCount",0)).put("faceAnalysis",annotation.has("faceScore")).put("faceIdentity",false).put("motion",motion).put("cameraId",cameraId).put("rotationClockwise",rotation).put("width",bounds.outWidth).put("height",bounds.outHeight);
    File metadataTemp=file(id,".json.tmp");try(FileOutputStream out=new FileOutputStream(metadataTemp)){out.write(data.toString().getBytes("UTF-8"));out.getFD().sync();}if(!metadataTemp.renameTo(file(id,".json")))throw new IOException("Metadata save failed");
    }catch(IOException|JSONException e){file(id,".jpg").delete();file(id,".json.tmp").delete();throw e;}
    try{
    android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inSampleSize=Math.max(1,Math.max(bounds.outWidth,bounds.outHeight)/320);
    Bitmap thumbnail=android.graphics.BitmapFactory.decodeByteArray(jpeg,0,jpeg.length,options);
    if(thumbnail!=null){try(FileOutputStream out=new FileOutputStream(file(id,".thumb.jpg"))){thumbnail.compress(Bitmap.CompressFormat.JPEG,85,out);}finally{thumbnail.recycle();}}
    }catch(IOException thumbnailFailure){file(id,".thumb.jpg").delete();}
    invalidate();return id;
  }}
  String list(long before){return list(before,0,Long.MAX_VALUE,"all");}
  String list(long before,long start,long end,String state){return list(before,start,end,state,0,12);}
  String list(long before,long start,long end,String state,int requestedPage,int limit){synchronized(LOCK){
    prune();ArrayList<JSONObject> all=new ArrayList<>();long cutoff=System.currentTimeMillis()-retentionMillis();
    for(JSONObject d:entries()){long captured=d.optLong("capturedAt");if(captured<=cutoff||captured<start||captured>=end||(!state.equals("all")&&!state.equals(d.optString("seatState"))))continue;all.add(d);}
    int pageSize=Math.max(1,Math.min(96,limit)),totalPages=Math.max(1,(all.size()+pageSize-1)/pageSize),page=Math.max(1,Math.min(totalPages,requestedPage));
    JSONArray items=new JSONArray();long next=0;int offset=requestedPage>0?(page-1)*pageSize:0,index=0;
    for(JSONObject d:all){long order=d.optLong("captureOrder");if(requestedPage<=0&&before>0&&order>=before)continue;if(index++<offset)continue;
      if(items.length()>=pageSize){next=items.optJSONObject(items.length()-1).optLong("captureOrder");break;}try{JSONObject item=new JSONObject(d.toString());String id=item.getString("id");item.put("url","/observations/"+id+".jpg").put("thumbnailUrl","/thumbnails/"+id+".jpg");items.put(item);}catch(JSONException ignored){}
    }
    try{return new JSONObject().put("enabled",enabled()).put("retentionDays",retentionDays()).put("total",all.size()).put("items",items).put("nextBefore",next).put("page",page).put("pageSize",pageSize).put("totalPages",totalPages).toString();}catch(JSONException e){return "{}";}
  }}

  private long nextOrder(long now){
    android.content.SharedPreferences prefs=context.getSharedPreferences("gallery",0);long previous=prefs.getLong("capture_order",0);
    if(previous==0){File[] files=directory.listFiles();if(files!=null)for(File f:files)if(f.getName().endsWith(".json"))try{JSONObject data=new JSONObject(new String(readAll(f),"UTF-8"));previous=Math.max(previous,data.optLong("captureOrder",data.optLong("capturedAt")));}catch(Exception ignored){}}
    long order=Math.max(now,previous+1);prefs.edit().putLong("capture_order",order).apply();return order;
  }
  private void requirePhoto(String id)throws IOException{File f=file(id,".jpg");if(f==null||!f.exists())throw new FileNotFoundException("Photo not found");entries();JSONObject d=byId.get(id);if(d==null)throw new FileNotFoundException("Photo metadata missing");if(d.optLong("capturedAt")<=System.currentTimeMillis()-retentionMillis())throw new FileNotFoundException("Photo expired");}

  InputStream open(String id)throws IOException{synchronized(LOCK){prune();requirePhoto(id);return new FileInputStream(file(id,".jpg"));}}
  InputStream thumbnail(String id)throws IOException{synchronized(LOCK){prune();requirePhoto(id);File f=file(id,".thumb.jpg");if(!f.exists()){
    android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();bounds.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeFile(file(id,".jpg").getPath(),bounds);
    android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inSampleSize=Math.max(1,Math.max(bounds.outWidth,bounds.outHeight)/360);
    Bitmap bitmap=android.graphics.BitmapFactory.decodeFile(file(id,".jpg").getPath(),options);if(bitmap==null)throw new IOException("Thumbnail unavailable");
    try(FileOutputStream out=new FileOutputStream(f)){if(!bitmap.compress(Bitmap.CompressFormat.JPEG,82,out))throw new IOException("Thumbnail failed");}catch(IOException e){f.delete();throw e;}finally{bitmap.recycle();}
  }return new FileInputStream(f);}}
  JSONObject metadata(String id)throws IOException,JSONException{synchronized(LOCK){prune();requirePhoto(id);File f=file(id,".json");if(f==null)throw new IOException("Invalid photo");JSONObject data=new JSONObject(new String(readAll(f),"UTF-8"));if(!data.has("rotationClockwise"))data.put("rotationClockwise",90);return data;}}
  static String filename(JSONObject d){String at=new java.text.SimpleDateFormat("yyyyMMdd'T'HHmmssSSS",Locale.US).format(new Date(d.optLong("capturedAt")));return "Bloub_"+at+"_"+d.optString("seatState","unknown")+"_"+d.optString("id")+".jpg";}
  boolean delete(String id){synchronized(LOCK){File f=file(id,".jpg"),meta=file(id,".json");if(f==null)return false;boolean removed=!f.exists()||f.delete();if(meta!=null)meta.delete();File thumb=file(id,".thumb.jpg");if(thumb!=null)thumb.delete();invalidate();return removed;}}
  void clear(){synchronized(LOCK){File[] files=directory.listFiles();if(files!=null)for(File f:files)if(f.getName().matches("[0-9]{10,17}\\.(jpg|thumb\\.jpg|json)"))f.delete();invalidate();}}
  private byte[] readAll(File f)throws IOException{try(FileInputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[2048];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);return out.toByteArray();}}
  void schedule(){
    AlarmManager alarm=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
    Intent intent=new Intent(context,GalleryCleanupReceiver.class);
    PendingIntent pending=PendingIntent.getBroadcast(context,7,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    alarm.setInexactRepeating(AlarmManager.ELAPSED_REALTIME,SystemClock.elapsedRealtime()+3600000,3600000,pending);
  }
}
