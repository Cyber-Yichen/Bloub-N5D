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
  private static void check(int code)throws IOException{if(code>=200&&code<300)return;String message=code==401||code==403?"服务拒绝请求，请检查凭证和操作权限":code==404?"存储桶或目录不存在":code==429?"请求过于频繁，请稍后重试":code>=500?"存储服务暂不可用，请稍后重试":code>=300&&code<400?"服务地址发生跳转，请填写直接访问地址":"存储服务拒绝请求";throw new IOException(message+"（"+code+"）");}
  static void upload(File file,JSONObject cfg,String secret,String token,String relative)throws Exception{request("PUT",file,cfg,secret,token,relative);}
  private static void request(String method,File file,JSONObject cfg,String secret,String token,String relative)throws Exception{
    URI endpoint=StorageConfig.validateEndpoint(cfg);String provider=cfg.getString("provider"),prefix=cfg.optString("prefix"),key=(prefix.isEmpty()?"":prefix+"/")+relative;
    String base=endpoint.getPath()==null?"":endpoint.getPath().replaceAll("/+$","");String path=base+"/"+(provider.equals("s3")?cfg.getString("bucket")+"/":"")+key;
    String authority=endpoint.getRawAuthority(),root=endpoint.getScheme()+"://"+authority;URL url=new URL(root+UploadSigner.encode(path,true));String mime=file.getName().endsWith(".mp4")?"video/mp4":"application/json";
    if(provider.equals("webdav")){String auth="Basic "+UploadSigner.base64(UploadSigner.utf(cfg.optString("username")+":"+secret));ensureDav(root,path,auth);HttpURLConnection c=connect(method,url);java.util.TimerTask timeout=deadline(c);try{c.setRequestProperty("Authorization",auth);write(c,file,mime);check(c.getResponseCode());}finally{timeout.cancel();c.disconnect();}return;}
    long now=signingTime(root);TreeMap<String,String> headers=new TreeMap<>();headers.put("host",authority);headers.put("content-type",mime);headers.put("content-md5",md5(file));String id=cfg.getString("accessId"),auth;
    if(provider.equals("s3")){String hash=digest(file,"SHA-256");headers.put("x-amz-content-sha256",hash);headers.put("x-amz-date",UploadSigner.date(now,"yyyyMMdd'T'HHmmss'Z'"));if(!token.isEmpty())headers.put("x-amz-security-token",token);auth=UploadSigner.s3(method,path,headers,hash,cfg.getString("region"),id,secret,now);}
    else if(provider.equals("oss")){headers.put("x-oss-date",UploadSigner.date(now,"yyyyMMdd'T'HHmmss'Z'"));headers.put("x-oss-content-sha256","UNSIGNED-PAYLOAD");if(!token.isEmpty())headers.put("x-oss-security-token",token);auth=UploadSigner.oss4(method,cfg.getString("bucket"),key,headers,cfg.getString("region"),id,secret,now);}
    else{if(!token.isEmpty())headers.put("x-cos-security-token",token);auth=UploadSigner.cos(method,path,headers,id,secret,now/1000-60,now/1000+600);}
    HttpURLConnection c=connect(method,url);java.util.TimerTask timeout=deadline(c);try{for(Map.Entry<String,String> h:headers.entrySet())c.setRequestProperty(h.getKey(),h.getValue());c.setRequestProperty("Authorization",auth);write(c,file,mime);check(c.getResponseCode());}finally{timeout.cancel();c.disconnect();}
  }
  static JSONObject test(android.content.Context context,JSONObject config,String secret,String token)throws Exception{
    if(!config.optString("provider").matches("s3|oss|cos"))throw new IllegalArgumentException("选择 S3、OSS 或 COS");if(config.optString("bucket").isEmpty()||config.optString("accessId").isEmpty()||secret.isEmpty())throw new IllegalArgumentException("填写存储桶、访问 ID 和密钥");StorageConfig.validateEndpoint(config);
    File file=File.createTempFile("bloub-connection-",".json",context.getCacheDir());String object=".bloub-connection-test/"+UUID.randomUUID()+".json";try{try(FileOutputStream out=new FileOutputStream(file)){out.write("{\"app\":\"Bloub\",\"connectionTest\":true}".getBytes("UTF-8"));}request("PUT",file,config,secret,token,object);JSONObject result=new JSONObject().put("connected",true).put("uploadVerified",true);try{try(FileOutputStream empty=new FileOutputStream(file)){}request("DELETE",file,config,secret,token,object);result.put("cleanup",true).put("message","连接成功，上传验证通过");}catch(Exception cleanup){result.put("cleanup",false).put("testObject",(config.optString("prefix").isEmpty()?"":config.optString("prefix")+"/")+object).put("message","上传验证通过，测试文件未能删除，请检查删除权限");}return result;}finally{file.delete();}
  }
  private static void write(HttpURLConnection c,File f,String mime)throws Exception{c.setDoOutput(true);c.setFixedLengthStreamingMode(f.length());c.setRequestProperty("Content-Type",mime);try(OutputStream out=c.getOutputStream();InputStream in=new FileInputStream(f)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Stopped");out.write(b,0,n);}}}
  private static void ensureDav(String root,String path,String auth)throws Exception{int at=path.indexOf('/',1);while(at>=0){WebDavFolders.create(new URL(root+UploadSigner.encode(path.substring(0,at),true)),auth);at=path.indexOf('/',at+1);}}
}
