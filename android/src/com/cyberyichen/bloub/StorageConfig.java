package com.cyberyichen.bloub;
import android.content.*;
import org.json.*;
import java.net.*;
import java.io.IOException;
import java.util.*;
final class StorageConfig {
  private final Context context;final SecretStore secrets;
  StorageConfig(Context c){context=c.getApplicationContext();secrets=new SecretStore(context);}
  synchronized JSONObject get(){try{JSONObject d=new JSONObject("{\"provider\":\"webdav\",\"endpoint\":\"\",\"bucket\":\"\",\"region\":\"us-east-1\",\"prefix\":\"bloub\",\"accessId\":\"\",\"username\":\"\",\"autoUpload\":false,\"segmentSeconds\":60,\"storageMiB\":1024,\"localDays\":1,\"allowHttp\":false}");JSONObject saved=new JSONObject(context.getSharedPreferences("monitor",0).getString("config","{}"));Iterator<String> keys=saved.keys();while(keys.hasNext()){String k=keys.next();if(d.has(k))d.put(k,saved.get(k));}return d;}catch(JSONException e){throw new IllegalStateException(e);}}
  synchronized JSONObject publicInfo(){JSONObject d=get();try{d.put("secretConfigured",secrets.has("secret")).put("tokenConfigured",secrets.has("token"));}catch(JSONException ignored){}return d;}
  synchronized void save(JSONObject patch)throws Exception{
    for(String k:new String[]{"secret","token"})if(patch.has(k)){Object v=patch.get(k);if(!(v instanceof String)||((String)v).length()>4096||((String)v).matches("(?s).*\\p{Cntrl}.*"))throw new JSONException("凭证格式无效");}
    for(String k:new String[]{"clearSecret","clearToken"})if(patch.has(k)&&!(patch.get(k) instanceof Boolean))throw new JSONException("清除开关格式无效");
    JSONObject d=get();Iterator<String> keys=patch.keys();while(keys.hasNext()){String k=keys.next();if(k.equals("secret")||k.equals("token")||k.equals("clearSecret")||k.equals("clearToken"))continue;if(!d.has(k))throw new JSONException("未知监控设置: "+k);Object v=patch.get(k);if(d.get(k) instanceof Boolean&&!(v instanceof Boolean)||d.get(k) instanceof Number&&!(v instanceof Number)||d.get(k) instanceof String&&!(v instanceof String))throw new JSONException("监控设置格式错误");d.put(k,v);}
    if(!d.getString("provider").matches("oss|cos|s3|webdav"))throw new JSONException("存储类型无效");
    for(String k:new String[]{"segmentSeconds","storageMiB","localDays"}){double n=d.getDouble(k);int lo=k.equals("segmentSeconds")?15:k.equals("storageMiB")?128:1,hi=k.equals("segmentSeconds")?300:k.equals("storageMiB")?8192:7;if(!Double.isFinite(n)||n!=Math.floor(n)||n<lo||n>hi)throw new JSONException("监控数字超出范围: "+k);}
    for(String k:new String[]{"endpoint","bucket","region","prefix","accessId","username"}){String v=d.getString(k);if(v.length()>1024||v.matches("(?s).*\\p{Cntrl}.*"))throw new JSONException("存储字段无效");}
    String prefix=d.getString("prefix");if(prefix.startsWith("/")||Arrays.asList(prefix.split("/",-1)).contains("..")||prefix.contains("\\"))throw new JSONException("目录必须为相对路径");
    if(!d.getString("endpoint").isEmpty())validateEndpoint(d);
    if(d.getBoolean("autoUpload")&&patch.optBoolean("clearSecret"))throw new JSONException("清除密钥前先关闭自动上传");
    if(d.getBoolean("autoUpload")){if(d.getString("endpoint").isEmpty())throw new JSONException("填写上传地址");if(!d.getString("provider").equals("webdav")&&(d.getString("bucket").isEmpty()||d.getString("accessId").isEmpty()))throw new JSONException("填写存储桶和访问 ID");if(!secrets.has("secret")&&patch.optString("secret").isEmpty())throw new JSONException("填写存储密钥 / 密码");}
    if(patch.optBoolean("clearSecret"))secrets.set("secret","");else if(!patch.optString("secret").isEmpty())secrets.set("secret",patch.getString("secret"));
    if(patch.optBoolean("clearToken"))secrets.set("token","");else if(!patch.optString("token").isEmpty())secrets.set("token",patch.getString("token"));
    if(!context.getSharedPreferences("monitor",0).edit().putString("config",d.toString()).commit())throw new IOException("设置保存失败");
  }
  static URI validateEndpoint(JSONObject d)throws Exception{URI uri=new URI(d.getString("endpoint"));if(uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||(!uri.getScheme().equals("https")&&!(uri.getScheme().equals("http")&&d.getBoolean("allowHttp"))))throw new JSONException("使用 HTTPS 地址；局域网 HTTP 需打开允许开关");String provider=d.getString("provider"),host=uri.getHost().toLowerCase(Locale.US);if(provider.equals("oss")&&!host.endsWith(".aliyuncs.com")||provider.equals("cos")&&!host.endsWith(".myqcloud.com"))throw new JSONException("使用云服务官方存储桶地址");if((provider.equals("oss")||provider.equals("cos"))&&!host.startsWith(d.getString("bucket")+"."))throw new JSONException("地址需包含完整存储桶域名");if((provider.equals("oss")||provider.equals("cos"))&&!uri.getPath().isEmpty()&&!uri.getPath().equals("/"))throw new JSONException("云地址不含目录，目录填在前缀中");if(!provider.equals("webdav")&&!d.getString("bucket").matches("[A-Za-z0-9][A-Za-z0-9.-]{1,126}"))throw new JSONException("存储桶格式无效");if(!d.getString("region").matches("[a-z0-9-]{1,64}"))throw new JSONException("地域格式无效");return uri;}
}
