package com.cyberyichen.bloub;
import android.content.Context;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

/** Foreground LAN gallery and user-controlled camera. Protected routes require a six-digit code. */
final class GalleryServer {
  static final int PORT=8768;
  interface CameraControl {String status();boolean command(String action);SensorHub.StreamFrame frame();void heartbeat();String presence(int days);String detections();String settings();String updateSettings(JSONObject d)throws Exception;String monitor();void monitorConfig(JSONObject d)throws Exception;void monitorRetry();InputStream video(String id)throws IOException;boolean videoDelete(String id);String seatControl(String action,JSONObject data)throws Exception;}
  private final CameraControl camera;private final Semaphore streams=new Semaphore(2);private final Map<String,long[]> failedCodes=new HashMap<>();
  private final Context context;private final Gallery gallery;private final String key;
  private volatile ServerSocket listener;private volatile String error="";
  private ThreadPoolExecutor workers;private ScheduledExecutorService deadlines;
  private final Set<Socket> clients=ConcurrentHashMap.newKeySet();
  GalleryServer(Context c,Gallery g,CameraControl camera){context=c.getApplicationContext();gallery=g;this.camera=camera;
    String saved=context.getSharedPreferences("lan_gallery",0).getString("key","");
    if(!saved.matches("[0-9]{6}")){saved=String.format(Locale.US,"%06d",new SecureRandom().nextInt(1000000));context.getSharedPreferences("lan_gallery",0).edit().putString("key",saved).apply();}
    key=saved;
  }
  boolean enabled(){return context.getSharedPreferences("lan_gallery",0).getBoolean("enabled",false);}
  void enabled(boolean value){context.getSharedPreferences("lan_gallery",0).edit().putBoolean("enabled",value).apply();}
  synchronized void start(){
    if(!enabled()||listener!=null)return;
    try{
      ServerSocket socket=new ServerSocket();socket.setReuseAddress(true);socket.bind(new InetSocketAddress("0.0.0.0",PORT),8);listener=socket;error="";
      workers=new ThreadPoolExecutor(3,3,0,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(8));deadlines=Executors.newSingleThreadScheduledExecutor();
      final ThreadPoolExecutor pool=workers;final ScheduledExecutorService timers=deadlines;
      new Thread(()->{while(!socket.isClosed())try{
        Socket client=socket.accept();client.setSoTimeout(5000);client.setTcpNoDelay(true);client.setSendBufferSize(32768);clients.add(client);
        try{pool.execute(()->{ScheduledFuture<?> expiry=null;try{expiry=timers.schedule(()->close(client),300,TimeUnit.SECONDS);serve(client);}catch(Exception failure){android.util.Log.w("BloubGalleryLAN","Request failed",failure);}finally{if(expiry!=null)expiry.cancel(false);clients.remove(client);close(client);}});}
        catch(RejectedExecutionException full){clients.remove(client);close(client);}
      }catch(IOException e){if(!socket.isClosed())error="连接中断";}},"BloubGalleryLAN").start();
    }catch(IOException e){error="端口不可用";stop();}
  }
  synchronized void stop(){ServerSocket old=listener;listener=null;if(old!=null)try{old.close();}catch(IOException ignored){}for(Socket s:clients)close(s);clients.clear();if(workers!=null)workers.shutdownNow();if(deadlines!=null)deadlines.shutdownNow();}
  private static void close(Socket socket){try{socket.close();}catch(IOException ignored){}}
  private List<String> addresses(){
    ArrayList<String> result=new ArrayList<>();
    try{Enumeration<NetworkInterface> all=NetworkInterface.getNetworkInterfaces();while(all.hasMoreElements()){NetworkInterface n=all.nextElement();if(!n.isUp()||n.isLoopback())continue;Enumeration<InetAddress> ips=n.getInetAddresses();while(ips.hasMoreElements()){InetAddress ip=ips.nextElement();if(ip instanceof Inet4Address&&ip.isSiteLocalAddress())result.add("http://"+ip.getHostAddress()+":"+PORT+"/?key="+key);}}}catch(Exception ignored){}
    return result;
  }
  String info(){try{List<String> urls=addresses();return new JSONObject().put("enabled",enabled()).put("running",listener!=null).put("port",PORT).put("url",urls.isEmpty()?"":urls.get(0)).put("urls",new JSONArray(urls)).put("key",key).put("error",error).put("foregroundOnly",true).toString();}catch(JSONException e){return "{}";}}
  private static String line(InputStream in)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();int b;while((b=in.read())!=-1){if(b=='\n')break;if(b!='\r')bytes.write(b);if(bytes.size()>4096)throw new IOException("Header too large");}return b==-1&&bytes.size()==0?null:bytes.toString("US-ASCII");}
  private Map<String,String> query(String q)throws IOException{Map<String,String> values=new HashMap<>();if(q==null)return values;for(String part:q.split("&")){String[] pair=part.split("=",2);values.put(URLDecoder.decode(pair[0],"UTF-8"),pair.length>1?URLDecoder.decode(pair[1],"UTF-8"):"");}return values;}
  private void headers(OutputStream out,int code,String type,long length,String filename)throws IOException{
    String status=code==200?"OK":code==401?"Unauthorized":code==404?"Not Found":code==405?"Method Not Allowed":code==409?"Conflict":code==429?"Too Many Requests":"Bad Request";
    String h="HTTP/1.1 "+code+" "+status+"\r\nContent-Type: "+type+"\r\nConnection: close\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nX-Content-Type-Options: nosniff\r\nContent-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self'; connect-src 'self'; frame-ancestors 'none'\r\n";
    if(length>=0)h+="Content-Length: "+length+"\r\n";if(filename!=null)h+="Content-Disposition: attachment; filename=\""+filename+"\"\r\n";
    if(code==405)h+="Allow: GET, POST\r\n";if(code==429)h+="Retry-After: 60\r\n";out.write((h+"\r\n").getBytes(StandardCharsets.US_ASCII));
  }
  private void bytes(OutputStream out,int code,String type,byte[] body)throws IOException{headers(out,code,type,body.length,null);out.write(body);}
  private void json(OutputStream out,int code,String body)throws IOException{bytes(out,code,"application/json; charset=utf-8",body.getBytes(StandardCharsets.UTF_8));}
  private byte[] asset(String name)throws IOException{try(InputStream in=context.getAssets().open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()){copy(in,out);return out.toByteArray();}}
  private static void copy(InputStream in,OutputStream out)throws IOException{byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
  private JSONObject listing(long before,long start,long end,String state)throws JSONException{
    JSONObject data=new JSONObject(gallery.list(before,start,end,state));JSONArray items=data.getJSONArray("items");
    for(int i=0;i<items.length();i++){JSONObject p=items.getJSONObject(i);String id=p.getString("id");p.put("url","/api/photos/"+id+"/image?key="+key).put("thumbnailUrl","/api/photos/"+id+"/thumbnail?key="+key).put("downloadUrl","/api/photos/"+id+"/image?download=1&key="+key).put("metadataUrl","/api/photos/"+id+"/metadata?key="+key);}
    return data;
  }
  private void serve(Socket client)throws Exception{
    InputStream in=client.getInputStream();OutputStream out=client.getOutputStream();String first=line(in);if(first==null)return;
    String[] parts=first.split(" ");if(parts.length!=3){json(out,400,"{\"error\":\"bad_request\"}");return;}
    Map<String,String> hdr=new HashMap<>();int size=first.length();String h;while((h=line(in))!=null&&!h.isEmpty()){size+=h.length();if(size>8192)throw new IOException("Headers too large");int colon=h.indexOf(':');if(colon>0)hdr.put(h.substring(0,colon).toLowerCase(Locale.US),h.substring(colon+1).trim());}
    URI uri=new URI(parts[1]);Map<String,String> q=query(uri.getRawQuery());String provided=q.getOrDefault("key","");String auth=hdr.getOrDefault("authorization","");if(auth.startsWith("Bearer "))provided=auth.substring(7);
    String path=uri.getPath();
    boolean authorized=MessageDigest.isEqual(key.getBytes(StandardCharsets.US_ASCII),provided.getBytes(StandardCharsets.US_ASCII));
    if(!authorized){
      if("GET".equals(parts[0])&&"/".equals(path)){bytes(out,200,"text/html; charset=utf-8",asset("lan-login.html"));return;}
      int code=failedCode(client.getInetAddress().getHostAddress())?429:401;json(out,code,code==429?"{\"error\":\"try_again_later\"}":"{\"error\":\"access_code_required\"}");return;
    }
    if("POST".equals(parts[0])){
      if(!auth.equals("Bearer "+key)){json(out,401,"{\"error\":\"bearer_required\"}");return;}
      String origin=hdr.getOrDefault("origin","");if(!origin.isEmpty()&&!origin.equals("http://"+hdr.getOrDefault("host",""))){json(out,400,"{\"error\":\"invalid_origin\"}");return;}
      JSONObject posted=null;try{int sizeBody=Integer.parseInt(hdr.getOrDefault("content-length","0"));if(sizeBody<0||sizeBody>16384||hdr.containsKey("transfer-encoding"))throw new IOException("请求过大或格式无效");if(sizeBody>0)posted=body(in,hdr);}catch(Exception bad){json(out,400,"{\"error\":\"invalid_body\"}");return;}
      if(path.equals("/api/settings")||path.equals("/api/monitor/config")||path.matches("/api/seat/(region|label|preview)")){try{JSONObject body=posted;if(body==null)throw new IOException("设置请求需要 JSON");if(path.equals("/api/settings"))json(out,200,camera.updateSettings(body));else if(path.equals("/api/monitor/config")){camera.monitorConfig(body);json(out,200,camera.monitor());}else json(out,200,camera.seatControl(path.substring(path.lastIndexOf('/')+1),body));}catch(Exception bad){json(out,400,new JSONObject().put("error","invalid_settings").put("message",bad.getMessage()==null?"设置无效":bad.getMessage()).toString());}return;}
      if(path.equals("/api/monitor/retry")){camera.monitorRetry();json(out,200,"{\"accepted\":true}");return;}
      if(path.matches("/api/videos/[0-9]{13}-[a-f0-9]{8}\\.mp4/delete")){json(out,200,new JSONObject().put("deleted",camera.videoDelete(path.split("/")[3])).toString());return;}
      if(path.matches("/api/photos/[0-9]{10,17}/delete")){json(out,200,new JSONObject().put("deleted",gallery.delete(path.split("/")[3])).toString());return;}
      if(path.equals("/api/photos/clear")){gallery.clear();json(out,200,"{\"accepted\":true}");return;}
      if(path.matches("/api/camera/detection/(on|off)")){boolean accepted=camera.command(path.endsWith("/on")?"detect-on":"detect-off");json(out,accepted?200:409,accepted?"{\"accepted\":true}":"{\"error\":\"enable_camera_on_device\"}");return;}
      if(path.matches("/api/camera/(start|stop|capture)")){String action=path.substring(path.lastIndexOf('/')+1);boolean accepted=camera.command(action);json(out,accepted?200:409,accepted?"{\"accepted\":true}":"{\"error\":\"enable_camera_and_saving_on_device\"}");return;}
      json(out,405,"{\"error\":\"unsupported_action\"}");return;
    }
    if(!"GET".equals(parts[0])){json(out,405,"{\"error\":\"method_not_allowed\"}");return;}
    if(path.equals("/api/settings")){json(out,200,camera.settings());return;}
    if(path.equals("/api/monitor")){json(out,200,camera.monitor());return;}
    if(path.matches("/api/videos/[0-9]{13}-[a-f0-9]{8}\\.mp4")){try(InputStream video=camera.video(path.split("/")[3])){headers(out,200,"video/mp4",-1,path.split("/")[3]);copy(video,out);}catch(FileNotFoundException missing){json(out,404,"{\"error\":\"video_not_found\"}");}return;}
    long start=0,end=Long.MAX_VALUE;String state=q.getOrDefault("state","all");if(path.equals("/api/photos")||path.equals("/api/archive.zip")){try{start=Long.parseLong(q.getOrDefault("start","0"));end=Long.parseLong(q.getOrDefault("end",Long.toString(Long.MAX_VALUE)));if(start<0||end<=start||!state.matches("all|occupied|empty|unknown"))throw new IllegalArgumentException();}catch(Exception bad){json(out,400,"{\"error\":\"invalid_range\"}");return;}}
    if("/api/detections".equals(path)){json(out,200,camera.detections());return;}
    if("/api/presence".equals(path)){int days=7;try{days=Integer.parseInt(q.getOrDefault("days","7"));}catch(NumberFormatException bad){json(out,400,"{\"error\":\"invalid_days\"}");return;}if(days<1||days>90){json(out,400,"{\"error\":\"invalid_days\"}");return;}json(out,200,camera.presence(days));return;}
    if("/api/camera".equals(path)){json(out,200,camera.status());return;}
    if("/api/camera/stream".equals(path)){stream(client,out);return;}
    if("/".equals(path)){bytes(out,200,"text/html; charset=utf-8",new String(asset("lan-gallery.html"),StandardCharsets.UTF_8).replace("__KEY__",key).getBytes(StandardCharsets.UTF_8));return;}
    if("/gallery.js".equals(path)){bytes(out,200,"application/javascript; charset=utf-8",asset("lan-gallery.js"));return;}
    if("/api/health".equals(path)){json(out,200,new JSONObject().put("version","0.9.0").put("retentionDays",7).put("galleryReadOnly",false).put("settingsControl",true).put("monitoring",true).put("cameraControl",true).put("deviceTime",System.currentTimeMillis()).toString());return;}
    if("/api/photos".equals(path)){long before=0;try{before=Long.parseLong(q.getOrDefault("before","0"));}catch(NumberFormatException bad){json(out,400,"{\"error\":\"invalid_cursor\"}");return;}json(out,200,listing(Math.max(0,before),start,end,state).toString());return;}
    if("/api/archive.zip".equals(path)){archive(out,start,end,state);return;}
    String[] route=path.split("/");
    if(route.length==5&&"api".equals(route[1])&&"photos".equals(route[2])&&route[3].matches("[0-9]{10,17}")){
      String id=route[3];try{
        if("metadata".equals(route[4])){JSONObject meta=gallery.metadata(id);byte[] data=meta.toString(2).getBytes(StandardCharsets.UTF_8);headers(out,200,"application/json; charset=utf-8",data.length,"1".equals(q.get("download"))?Gallery.filename(meta).replace(".jpg",".json"):null);out.write(data);return;}
        boolean thumbnail="thumbnail".equals(route[4]);
        if(thumbnail||"image".equals(route[4])){try(InputStream image=thumbnail?gallery.thumbnail(id):gallery.open(id)){headers(out,200,"image/jpeg",-1,"1".equals(q.get("download"))?Gallery.filename(gallery.metadata(id)):null);copy(image,out);}return;}
      }catch(IOException missing){json(out,404,"{\"error\":\"photo_not_found\"}");return;}
    }
    json(out,404,"{\"error\":\"not_found\"}");
  }
  private JSONObject body(InputStream in,Map<String,String> headers)throws Exception{if(!headers.getOrDefault("content-type","").toLowerCase(Locale.US).startsWith("application/json")||headers.containsKey("transfer-encoding"))throw new IOException("使用 JSON 请求");int n=Integer.parseInt(headers.getOrDefault("content-length","0"));if(n<2||n>16384)throw new IOException("请求过大或为空");byte[] data=new byte[n];int at=0;while(at<n){int read=in.read(data,at,n-at);if(read<0)throw new IOException("请求不完整");at+=read;}return new JSONObject(new String(data,StandardCharsets.UTF_8));}
  private synchronized boolean failedCode(String address){
    long now=android.os.SystemClock.elapsedRealtime();long[] state=failedCodes.get(address);
    if(state==null||now-state[0]>60000){if(failedCodes.size()>64)failedCodes.clear();state=new long[]{now,0};failedCodes.put(address,state);}
    return ++state[1]>8;
  }
  private void stream(Socket client,OutputStream out)throws IOException,JSONException{
    if(!streams.tryAcquire()){json(out,409,"{\"error\":\"stream_busy\"}");return;}
    try{
      JSONObject state=new JSONObject(camera.status());if(!state.optBoolean("live")){json(out,409,"{\"error\":\"start_stream_first\"}");return;}
      headers(out,200,"multipart/x-mixed-replace; boundary=bloubframe",-1,null);SensorHub.StreamFrame previous=null;long end=android.os.SystemClock.elapsedRealtime()+70000;
      while(listener!=null&&!client.isClosed()&&android.os.SystemClock.elapsedRealtime()<end&&new JSONObject(camera.status()).optBoolean("live")){
        camera.heartbeat();SensorHub.StreamFrame frame=camera.frame();
        if(frame!=null&&frame!=previous){out.write(("--bloubframe\r\nContent-Type: image/jpeg\r\nContent-Length: "+frame.jpeg.length+"\r\nX-Capture-Uptime: "+frame.capturedAt+"\r\nX-Frame-Sequence: "+frame.sequence+"\r\nX-Encode-Millis: "+frame.encodeMs+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(frame.jpeg);out.write("\r\n".getBytes(StandardCharsets.US_ASCII));out.flush();previous=frame;}
        try{Thread.sleep(10);}catch(InterruptedException stop){Thread.currentThread().interrupt();break;}
      }
      out.write("--bloubframe--\r\n".getBytes(StandardCharsets.US_ASCII));
    }finally{streams.release();}
  }
  private void archive(OutputStream out,long start,long end,String state)throws IOException,JSONException{
    headers(out,200,"application/zip",-1,"Bloub-observations.zip");JSONArray manifest=new JSONArray();long before=0;int count=0;
    try(ZipOutputStream zip=new ZipOutputStream(out)){
      do{JSONObject data=new JSONObject(gallery.list(before,start,end,state));JSONArray items=data.getJSONArray("items");
        for(int i=0;i<items.length()&&count<10000;i++){JSONObject photo=items.getJSONObject(i);String id=photo.getString("id");if(!id.matches("[0-9]{10,17}"))continue;
          try(InputStream image=gallery.open(id)){photo.remove("url");photo.remove("thumbnailUrl");String name=Gallery.filename(photo);zip.putNextEntry(new ZipEntry(name));copy(image,zip);zip.closeEntry();zip.putNextEntry(new ZipEntry(name.replace(".jpg",".json")));zip.write(photo.toString(2).getBytes(StandardCharsets.UTF_8));zip.closeEntry();photo.remove("url");photo.remove("thumbnailUrl");photo.put("file",name);manifest.put(photo);count++;}catch(FileNotFoundException expired){}
        }
        before=data.optLong("nextBefore");
      }while(before>0&&count<10000);
      zip.putNextEntry(new ZipEntry("manifest.json"));zip.write(new JSONObject().put("deviceTime",System.currentTimeMillis()).put("retentionDays",7).put("rangeStart",start).put("rangeEndExclusive",end).put("stateFilter",state).put("annotationsAreGroundTruth",false).put("limit",10000).put("truncated",count>=10000&&new JSONObject(gallery.list(0,start,end,state)).optInt("total")>count).put("items",manifest).toString(2).getBytes(StandardCharsets.UTF_8));zip.closeEntry();
    }
  }
}
