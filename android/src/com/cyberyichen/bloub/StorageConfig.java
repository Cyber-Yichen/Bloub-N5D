package com.cyberyichen.bloub;
import android.content.*;
import org.json.*;
import java.net.*;
import java.io.IOException;
import java.util.*;
final class StorageConfig {
  private final Context context;final SecretStore secrets;
  StorageConfig(Context c){context=c.getApplicationContext();secrets=new SecretStore(context);migrateSecrets();}
  private void migrateSecrets(){android.content.SharedPreferences prefs=context.getSharedPreferences("monitor",0);if(prefs.getBoolean("provider_secrets",false))return;
    String provider="webdav";try{provider=new JSONObject(prefs.getString("config","{}")).optString("provider","webdav");}catch(JSONException ignored){}if(!provider.matches("oss|cos|s3|webdav"))provider="webdav";
    android.content.SharedPreferences encrypted=context.getSharedPreferences("storage_secrets",0);android.content.SharedPreferences.Editor edit=encrypted.edit();for(String name:new String[]{"secret","token"})if(encrypted.contains(name)){if(!encrypted.contains(provider+"."+name))edit.putString(provider+"."+name,encrypted.getString(name,""));edit.remove(name);}if(!edit.commit()||!prefs.edit().putBoolean("provider_secrets",true).commit())throw new IllegalStateException("凭证迁移失败");
  }
  synchronized JSONObject get(){String active="webdav";try{active=new JSONObject(context.getSharedPreferences("monitor",0).getString("config","{}")).optString("provider","webdav");}catch(JSONException ignored){}return get(active);}
  synchronized JSONObject get(String provider){
try{JSONObject d=new JSONObject("{\"provider\":\"webdav\",\"endpoint\":\"\",\"endpointOverride\":\"\",\"bucket\":\"\",\"region\":\"us-east-1\",\"prefix\":\"bloub\",\"accessId\":\"\",\"username\":\"\",\"autoUpload\":false,\"segmentSeconds\":60,\"storageMiB\":1024,\"localDays\":1,\"allowHttp\":false,\"recordAudio\":false,\"timestamp\":true,\"mirror\":false,\"rotationEnabled\":false,\"rotation\":0,\"overlayObjects\":false,\"overlayFaces\":false,\"overlayHands\":false,\"overlayHolistic\":false,\"overlayYamnet\":false,\"uploadScheduleEnabled\":false,\"uploadTime\":\"20:00\",\"motionOnly\":false,\"motionWindowMinutes\":1}");d.put("provider",provider).put("region",provider.equals("oss")?"cn-hangzhou":provider.equals("cos")?"ap-guangzhou":provider.equals("s3")?"us-east-1":"");android.content.SharedPreferences prefs=context.getSharedPreferences("monitor",0);JSONObject profiles=new JSONObject(prefs.getString("profiles","{}")),legacy=new JSONObject(prefs.getString("config","{}"));JSONObject saved=profiles.optJSONObject(provider);if(saved==null)saved=provider.equals(legacy.optString("provider","webdav"))?legacy:new JSONObject();Iterator<String> keys=saved.keys();while(keys.hasNext()){String k=keys.next();if(d.has(k))d.put(k,saved.get(k));}if(!saved.has("endpointOverride")&&(d.getString("provider").equals("oss")||d.getString("provider").equals("cos")))d.put("region",StorageDestination.region(d.getString("provider"),d.getString("endpoint"),d.getString("region")));normalize(d);return d;}catch(JSONException e){throw new IllegalStateException(e);}}
  synchronized JSONObject publicInfo(){return publicInfo(get().optString("provider"));}
  synchronized JSONObject publicInfo(String provider){JSONObject d=get(provider);try{d.put("secretConfigured",secrets.has(provider+".secret")).put("tokenConfigured",secrets.has(provider+".token"));}catch(JSONException ignored){}return d;}
  synchronized JSONObject publicProfiles(){JSONObject all=new JSONObject();try{for(String provider:new String[]{"webdav","oss","cos","s3"})all.put(provider,publicInfo(provider));}catch(JSONException ignored){}return all;}
  String secret(String provider,String name)throws Exception{return secrets.get(provider+"."+name);}
  synchronized JSONObject preview(JSONObject patch)throws Exception{
    for(String k:new String[]{"secret","token"})if(patch.has(k)){Object v=patch.get(k);if(!(v instanceof String)||((String)v).length()>4096||((String)v).matches("(?s).*\\p{Cntrl}.*"))throw new JSONException("凭证格式无效");}
    for(String k:new String[]{"clearSecret","clearToken"})if(patch.has(k)&&!(patch.get(k) instanceof Boolean))throw new JSONException("清除开关格式无效");
    String target=patch.optString("provider",get().optString("provider"));if(!target.matches("oss|cos|s3|webdav"))throw new JSONException("存储类型无效");JSONObject d=get(target);Iterator<String> keys=patch.keys();while(keys.hasNext()){String k=keys.next();if(k.equals("secret")||k.equals("token")||k.equals("clearSecret")||k.equals("clearToken"))continue;if(!d.has(k))throw new JSONException("未知监控设置: "+k);Object v=patch.get(k);if(d.get(k) instanceof Boolean&&!(v instanceof Boolean)||d.get(k) instanceof Number&&!(v instanceof Number)||d.get(k) instanceof String&&!(v instanceof String))throw new JSONException("监控设置格式错误");d.put(k,v);}
    if(!d.getString("provider").matches("oss|cos|s3|webdav"))throw new JSONException("存储类型无效");
    for(String k:new String[]{"segmentSeconds","storageMiB","localDays"}){double n=d.getDouble(k);int lo=k.equals("segmentSeconds")?15:k.equals("storageMiB")?128:1,hi=k.equals("segmentSeconds")?300:k.equals("storageMiB")?8192:7;if(!Double.isFinite(n)||n!=Math.floor(n)||n<lo||n>hi)throw new JSONException("监控数字超出范围: "+k);}
    for(String k:new String[]{"endpoint","endpointOverride","bucket","region","prefix","accessId","username"}){String v=d.getString(k);if(v.length()>1024||v.matches("(?s).*\\p{Cntrl}.*"))throw new JSONException("存储字段无效");}
    normalize(d);
    String prefix=d.getString("prefix");if(prefix.startsWith("/")||Arrays.asList(prefix.split("/",-1)).contains("..")||prefix.contains("\\"))throw new JSONException("目录必须为相对路径");
    if(!d.getString("endpoint").isEmpty())validateEndpoint(d);
    if(d.getBoolean("autoUpload")&&patch.optBoolean("clearSecret"))throw new JSONException("清除密钥前先关闭自动上传");
    if(d.getBoolean("autoUpload")){if(d.getString("endpoint").isEmpty())throw new JSONException("填写上传地址");if(!d.getString("provider").equals("webdav")&&(d.getString("bucket").isEmpty()||d.getString("accessId").isEmpty()))throw new JSONException("填写存储桶和访问 ID");if((!secrets.has(target+".secret"))&&patch.optString("secret").isEmpty())throw new JSONException("填写存储密钥 / 密码");}
    if(!d.getString("uploadTime").matches("([01][0-9]|2[0-3]):[0-5][0-9]"))throw new JSONException("上传时间格式无效");
    int rotation=d.getInt("rotation");if(d.getDouble("rotation")!=rotation||rotation<0||rotation>270||rotation%90!=0)throw new JSONException("选择 0、90、180 或 270 度");
    double window=d.getDouble("motionWindowMinutes");if(!Double.isFinite(window)||window!=Math.floor(window)||window<1||window>30)throw new JSONException("画面变化保留范围为 1 至 30 分钟");
    if(d.optBoolean("motionOnly")&&d.getInt("storageMiB")<window*16+32)throw new JSONException("增加本机容量或缩短变化前后保留时长");
    return d;
  }
  synchronized void save(JSONObject patch)throws Exception{JSONObject d=preview(patch);String target=d.getString("provider");
    if(patch.optBoolean("clearSecret"))secrets.set(target+".secret","");else if(!patch.optString("secret").isEmpty())secrets.set(target+".secret",patch.getString("secret"));
    if(patch.optBoolean("clearToken"))secrets.set(target+".token","");else if(!patch.optString("token").isEmpty())secrets.set(target+".token",patch.getString("token"));
    android.content.SharedPreferences prefs=context.getSharedPreferences("monitor",0);JSONObject profiles=new JSONObject(prefs.getString("profiles","{}"));JSONObject previous=get();String active=previous.getString("provider");if(!profiles.has(active))profiles.put(active,previous);profiles.put(target,d);if(!prefs.edit().putString("profiles",profiles.toString()).putString("config",d.toString()).commit())throw new IOException("设置保存失败");
  }
  private static void normalize(JSONObject d)throws JSONException{
    String provider=d.getString("provider");if(provider.equals("webdav")){d.put("bucket","").put("region","").put("endpointOverride","");return;}
    if(provider.equals("s3")){if(d.getString("region").isEmpty())d.put("region","us-east-1");d.put("endpointOverride","");return;}
    if(provider.equals("oss")||provider.equals("cos")){if(provider.equals("cos"))d.put("endpointOverride","");if(!d.getString("bucket").isEmpty())try{d.put("endpoint",StorageDestination.endpoint(provider,d.getString("bucket"),d.getString("region"),d.getString("endpointOverride")));}catch(IllegalArgumentException bad){throw new JSONException(bad.getMessage());}else d.put("endpoint","");d.put("allowHttp",false);}
  }
  static URI validateEndpoint(JSONObject d)throws Exception{
    URI uri=new URI(d.getString("endpoint"));String scheme=uri.getScheme(),provider=d.getString("provider"),host=uri.getHost();
    if(host==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||(!"https".equals(scheme)&&!("http".equals(scheme)&&d.getBoolean("allowHttp")&&(provider.equals("s3")||provider.equals("webdav")))))throw new JSONException("使用 HTTPS 地址；局域网 HTTP 需打开允许开关");
    if(provider.equals("oss")||provider.equals("cos")){if(!uri.getPath().isEmpty()&&!uri.getPath().equals("/"))throw new JSONException("云地址不含目录，目录填在存放目录中");String expected=StorageDestination.endpoint(provider,d.getString("bucket"),d.getString("region"),d.optString("endpointOverride"));if(!expected.equals(d.getString("endpoint")))throw new JSONException("存储地址与存储桶或地域不一致");}
    if(!provider.equals("webdav")&&!d.getString("bucket").matches("[A-Za-z0-9][A-Za-z0-9.-]{1,126}"))throw new JSONException("存储桶格式无效");
    if(!provider.equals("webdav")&&!d.getString("region").matches("[a-z0-9-]{1,64}"))throw new JSONException("地域格式无效");return uri;
  }
}
