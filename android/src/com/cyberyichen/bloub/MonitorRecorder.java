package com.cyberyichen.bloub;
import android.content.*;
import android.media.*;
import android.view.Surface;
import android.util.Size;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
/** Foreground-only silent H.264 segments. Finalized files survive crashes and failed uploads. */
final class MonitorRecorder {
  private final Context context;final StorageConfig config;private final DeviceSettings settings;private final File directory;
  private final ScheduledExecutorService uploads=Executors.newSingleThreadScheduledExecutor();private volatile boolean foreground,recording;private volatile String status="关闭",uploadStatus="未上传";private volatile long nextRetry;private int failures;private MediaRecorder recorder;private File partial;private long began,beginMono;
  MonitorRecorder(Context c){context=c.getApplicationContext();config=new StorageConfig(c);settings=new DeviceSettings(c);directory=new File(context.getFilesDir(),"monitor-video");directory.mkdirs();recover();uploads.scheduleWithFixedDelay(this::uploadTick,5,15,TimeUnit.SECONDS);}
  boolean enabled(){return settings.get().optBoolean("monitorEnabled");}
  boolean recording(){return recording;}
  void foreground(boolean value){foreground=value;}
  private File[] videos(){File[] files=directory.listFiles((d,n)->n.matches("[0-9]{13}-[a-f0-9]{8}\\.mp4"));if(files==null)return new File[0];Arrays.sort(files,(a,b)->a.getName().compareTo(b.getName()));return files;}
  private void recover(){File[] files=directory.listFiles();if(files!=null)for(File f:files)if(f.getName().endsWith(".part.mp4")){try{MediaMetadataRetriever m=new MediaMetadataRetriever();try{m.setDataSource(f.getAbsolutePath());if(Long.parseLong(m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION))<500)throw new IOException();}finally{m.release();}File target=new File(directory,f.getName().replace(".part.mp4",".mp4"));if(f.renameTo(target))writeMeta(target,new JSONObject().put("recovered",true).put("annotationSource","unknown_after_crash"));}catch(Exception invalid){f.delete();}}}
  private long bytes(){long size=0;File[] files=directory.listFiles();if(files!=null)for(File f:files)size+=f.length();return size;}
  private void prune(){long cutoff=System.currentTimeMillis()-config.get().optLong("localDays",1)*86400000;for(File f:videos()){File meta=new File(directory,f.getName()+".json");try{JSONObject d=read(meta);if(d.optBoolean("uploaded")&&d.optLong("endedAt",f.lastModified())<cutoff){f.delete();meta.delete();}}catch(Exception ignored){}}}
  synchronized Surface prepare(Size size,int rotation)throws Exception{
    if(recorder!=null||!foreground||!enabled())return null;prune();long cap=config.get().optLong("storageMiB",1024)*1024*1024,reserve=segmentSeconds()*312500L+4*1024*1024;if(bytes()+reserve>cap||new android.os.StatFs(directory.getAbsolutePath()).getAvailableBytes()<reserve+16*1024*1024){status="本地空间已满，等待上传或清理";return null;}
    began=System.currentTimeMillis();beginMono=android.os.SystemClock.elapsedRealtime();partial=new File(directory,began+"-"+UUID.randomUUID().toString().substring(0,8)+".part.mp4");
    recorder=new MediaRecorder();try{recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);recorder.setOutputFile(partial.getAbsolutePath());recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);recorder.setVideoSize(size.getWidth(),size.getHeight());recorder.setVideoFrameRate(30);recorder.setVideoEncodingBitRate(2000000);recorder.setOrientationHint(rotation);recorder.prepare();status="准备录制";return recorder.getSurface();}catch(Exception e){release();throw e;}
  }
  synchronized void start(){if(recorder==null)return;recorder.start();recording=true;status="正在录制";}
  int segmentSeconds(){return config.get().optInt("segmentSeconds",60);}
  synchronized void finish(){MediaRecorder r=recorder;if(r==null)return;boolean complete=false;try{if(recording){r.stop();complete=true;}}catch(RuntimeException e){status="分段未完成，稍后重试";}finally{try{r.reset();}catch(RuntimeException ignored){}finally{try{r.release();}finally{recorder=null;recording=false;}}}
    if(complete&&partial!=null&&partial.length()>1024){File target=new File(directory,partial.getName().replace(".part.mp4",".mp4"));if(partial.renameTo(target))try{writeMeta(target,new JSONObject().put("schemaVersion",1).put("startedAt",began).put("endedAt",System.currentTimeMillis()).put("durationMs",Math.max(0,android.os.SystemClock.elapsedRealtime()-beginMono)).put("silent",true).put("uploaded",false));status=enabled()?"等待下一段":"关闭";}catch(Exception e){status="录像元数据保存失败";}}
    else if(partial!=null)partial.delete();partial=null;
  }
  private synchronized void release(){if(recorder!=null){recorder.release();recorder=null;}recording=false;if(partial!=null)partial.delete();partial=null;}
  synchronized void failed(){release();status="录像配置失败，稍后重试";}
  private static JSONObject read(File f)throws Exception{try(InputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return new JSONObject(out.toString("UTF-8"));}}
  private void writeMeta(File video,JSONObject d)throws Exception{File f=new File(directory,video.getName()+".json"),tmp=new File(directory,video.getName()+".json.tmp");try(FileOutputStream out=new FileOutputStream(tmp)){out.write(d.toString().getBytes("UTF-8"));out.getFD().sync();}if(!tmp.renameTo(f))throw new IOException("Metadata save failed");}
  private void uploadTick(){if(!foreground)return;try{prune();JSONObject c=config.get();if(!c.optBoolean("autoUpload")||System.currentTimeMillis()<nextRetry)return;for(File f:videos()){File sidecar=new File(directory,f.getName()+".json");JSONObject d=sidecar.exists()?read(sidecar):new JSONObject().put("recovered",true).put("uploaded",false);if(d.optBoolean("uploaded"))continue;
      uploadStatus="正在上传";String secret=config.secrets.get("secret"),token=config.secrets.get("token");String key=UploadSigner.date(d.optLong("startedAt",f.lastModified()),"yyyy-MM-dd")+"/"+f.getName();StorageUploader.upload(f,c,secret,token,key);d.put("uploadedAt",System.currentTimeMillis()).put("objectKey",key);writeMeta(f,d);StorageUploader.upload(sidecar,c,secret,token,key+".json");d.put("uploaded",true);writeMeta(f,d);failures=0;nextRetry=0;uploadStatus="上传完成";break;}
    }catch(Exception e){failures=Math.min(8,failures+1);nextRetry=System.currentTimeMillis()+Math.min(3600000,15000L*(1L<<failures));uploadStatus=e instanceof IOException?e.getMessage():"上传配置或凭证不可用";}}
  String info(){try{JSONArray list=new JSONArray();int pending=0;File[] files=videos();for(int i=0;i<files.length;i++){File f=files[i];JSONObject d;try{d=read(new File(directory,f.getName()+".json"));}catch(Exception e){d=new JSONObject().put("uploaded",false);}if(!d.optBoolean("uploaded"))pending++;if(i>=files.length-100)list.put(new JSONObject().put("id",f.getName()).put("bytes",f.length()).put("metadata",d));}return new JSONObject().put("enabled",enabled()).put("recording",recording).put("status",enabled()&&!settings.get().optBoolean("camera")?"先开启相机":status).put("uploadStatus",uploadStatus).put("nextRetryAt",nextRetry).put("bytes",bytes()).put("pending",pending).put("config",config.publicInfo()).put("videos",list).toString();}catch(Exception e){return "{}";}}
  InputStream open(String id)throws IOException{if(!id.matches("[0-9]{13}-[a-f0-9]{8}\\.mp4"))throw new IOException("Invalid video");return new FileInputStream(new File(directory,id));}
  synchronized boolean delete(String id){if(!id.matches("[0-9]{13}-[a-f0-9]{8}\\.mp4"))return false;File f=new File(directory,id);boolean ok=!f.exists()||f.delete();if(ok)new File(directory,id+".json").delete();return ok;}
  void retry(){nextRetry=0;failures=0;uploadStatus=config.get().optBoolean("autoUpload")?"等待上传":"未上传";}
  void destroy(){foreground=false;finish();uploads.shutdownNow();}
}
