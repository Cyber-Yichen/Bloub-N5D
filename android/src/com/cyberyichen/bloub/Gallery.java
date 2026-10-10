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
    JSONObject data=new JSONObject().put("id",id).put("capturedAt",now).put("faces",faces).put("motion",motion).put("width",image.getWidth()).put("height",image.getHeight());
    try(FileOutputStream out=new FileOutputStream(file(id,".json"))){out.write(data.toString().getBytes("UTF-8"));}
  }}
  String list(long before){synchronized(LOCK){
    prune();ArrayList<File> all=new ArrayList<>();File[] files=directory.listFiles();
    if(files!=null)for(File f:files)if(f.getName().endsWith(".json"))all.add(f);
    all.sort((a,b)->b.getName().compareTo(a.getName()));JSONArray items=new JSONArray();long next=0;
    for(File f:all){try{
      String text=new String(readAll(f),"UTF-8");JSONObject d=new JSONObject(text);long at=d.getLong("capturedAt");
      if(before>0&&at>=before)continue;
      if(items.length()>=12){next=items.getJSONObject(items.length()-1).getLong("capturedAt");break;}
      d.put("url","/observations/"+d.getString("id")+".jpg");items.put(d);
    }catch(Exception ignored){}}
    try{return new JSONObject().put("enabled",enabled()).put("retentionDays",7).put("total",all.size()).put("items",items).put("nextBefore",next).toString();}catch(JSONException e){return "{}";}
  }}
  InputStream open(String id)throws IOException{synchronized(LOCK){prune();File f=file(id,".jpg");if(f==null)throw new IOException("Invalid photo");return new FileInputStream(f);}}
  boolean delete(String id){synchronized(LOCK){File f=file(id,".jpg"),meta=file(id,".json");if(f==null)return false;boolean removed=!f.exists()||f.delete();if(meta!=null)meta.delete();return removed;}}
  void clear(){synchronized(LOCK){File[] files=directory.listFiles();if(files!=null)for(File f:files)if(f.getName().matches("[0-9]{10,17}\\.(jpg|json)"))f.delete();}}
  private byte[] readAll(File f)throws IOException{try(FileInputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[2048];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);return out.toByteArray();}}
  void schedule(){
    AlarmManager alarm=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
    Intent intent=new Intent(context,GalleryCleanupReceiver.class);
    PendingIntent pending=PendingIntent.getBroadcast(context,7,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    alarm.setInexactRepeating(AlarmManager.ELAPSED_REALTIME,SystemClock.elapsedRealtime()+3600000,3600000,pending);
  }
}
