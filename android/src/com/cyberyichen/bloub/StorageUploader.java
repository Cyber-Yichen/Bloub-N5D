package com.cyberyichen.bloub;
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import org.json.*;
/** Streaming, signed PUT; redirects are refused so credentials remain at the configured endpoint. */
final class StorageUploader {
  private static final java.util.Timer deadlines=new java.util.Timer("Bloub-upload-deadline",true);
  private static java.util.TimerTask deadline(HttpURLConnection c){java.util.TimerTask task=new java.util.TimerTask(){public void run(){c.disconnect();}};deadlines.schedule(task,180000);return task;}
  private static final java.util.Map<String,long[]> clockOffsets=new java.util.concurrent.ConcurrentHashMap<>();
  private static long signingTime(String root){long now=System.currentTimeMillis(),mono=android.os.SystemClock.elapsedRealtime();long[] cache=clockOffsets.get(root);if(cache!=null&&mono-cache[1]<3600000&&Math.abs((now-cache[2])-(mono-cache[1]))<30000)return now+cache[0];long offset=0;try{HttpURLConnection c=connect("HEAD",new URL(root));try{c.getResponseCode();long server=c.getHeaderFieldDate("Date",0);if(server>0)offset=server-System.currentTimeMillis();}finally{c.disconnect();}}catch(Exception ignored){}clockOffsets.put(root,new long[]{offset,mono,now});return System.currentTimeMillis()+offset;}
  static String digest(File f,String algorithm)throws Exception{MessageDigest d=MessageDigest.getInstance(algorithm);try(InputStream in=new FileInputStream(f)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}return UploadSigner.hex(d.digest());}
  static String md5(File f)throws Exception{MessageDigest d=MessageDigest.getInstance("MD5");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}return UploadSigner.base64(d.digest());}
  private static HttpURLConnection connect(String method,URL url)throws Exception{HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setInstanceFollowRedirects(false);c.setRequestMethod(method);return c;}
  private static void check(int code)throws IOException{if(code<200||code>=300)throw new IOException("上传 HTTP "+code);}
  static void upload(File file,JSONObject cfg,String secret,String token,String relative)throws Exception{
    URI endpoint=StorageConfig.validateEndpoint(cfg);String provider=cfg.getString("provider"),prefix=cfg.optString("prefix"),key=(prefix.isEmpty()?"":prefix+"/")+relative;
    String base=endpoint.getPath()==null?"":endpoint.getPath().replaceAll("/+$","");String path=base+"/"+(provider.equals("s3")?cfg.getString("bucket")+"/":"")+key;
    String authority=endpoint.getRawAuthority(),root=endpoint.getScheme()+"://"+authority;URL url=new URL(root+UploadSigner.encode(path,true));String mime=file.getName().endsWith(".mp4")?"video/mp4":"application/json";
    if(provider.equals("webdav")){String auth="Basic "+UploadSigner.base64(UploadSigner.utf(cfg.optString("username")+":"+secret));ensureDav(root,path,auth);HttpURLConnection c=connect("PUT",url);java.util.TimerTask timeout=deadline(c);try{c.setRequestProperty("Authorization",auth);write(c,file,mime);check(c.getResponseCode());}finally{timeout.cancel();c.disconnect();}return;}
    long now=signingTime(root);TreeMap<String,String> headers=new TreeMap<>();headers.put("host",authority);headers.put("content-type",mime);headers.put("content-md5",md5(file));String id=cfg.getString("accessId"),auth;
    if(provider.equals("s3")){String hash=digest(file,"SHA-256");headers.put("x-amz-content-sha256",hash);headers.put("x-amz-date",UploadSigner.date(now,"yyyyMMdd'T'HHmmss'Z'"));if(!token.isEmpty())headers.put("x-amz-security-token",token);auth=UploadSigner.s3("PUT",path,headers,hash,cfg.getString("region"),id,secret,now);}
    else if(provider.equals("oss")){headers.put("Date",UploadSigner.date(now,"EEE, dd MMM yyyy HH:mm:ss 'GMT'"));if(!token.isEmpty())headers.put("x-oss-security-token",token);auth=UploadSigner.oss(cfg.getString("bucket"),key,mime,headers.get("content-md5"),headers.get("Date"),id,secret,token);}
    else{if(!token.isEmpty())headers.put("x-cos-security-token",token);auth=UploadSigner.cos(path,headers,id,secret,now/1000-60,now/1000+600);}
    HttpURLConnection c=connect("PUT",url);java.util.TimerTask timeout=deadline(c);try{for(Map.Entry<String,String> h:headers.entrySet())c.setRequestProperty(h.getKey(),h.getValue());c.setRequestProperty("Authorization",auth);write(c,file,mime);check(c.getResponseCode());}finally{timeout.cancel();c.disconnect();}
  }
  private static void write(HttpURLConnection c,File f,String mime)throws Exception{c.setDoOutput(true);c.setFixedLengthStreamingMode(f.length());c.setRequestProperty("Content-Type",mime);try(OutputStream out=c.getOutputStream();InputStream in=new FileInputStream(f)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Stopped");out.write(b,0,n);}}}
  private static void ensureDav(String root,String path,String auth)throws Exception{int at=path.indexOf('/',1);while(at>=0){WebDavFolders.create(new URL(root+UploadSigner.encode(path.substring(0,at),true)),auth);at=path.indexOf('/',at+1);}}
}
