package com.cyberyichen.bloub;
import java.net.URI;
/** Cloud endpoints are derived from the bucket and region; DAV has no bucket or region. */
final class StorageDestination {
  static String endpoint(String provider,String bucket,String region,String override){
    if(!provider.equals("oss")&&!provider.equals("cos"))throw new IllegalArgumentException("Not a cloud provider");
    if(!bucket.matches("[a-z0-9][a-z0-9.-]{1,126}")||!region.matches("[a-z0-9-]{1,64}"))throw new IllegalArgumentException("存储桶或地域格式无效");
    if(provider.equals("oss")&&!override.isEmpty())return override;
    return "https://"+bucket+(provider.equals("oss")?".oss-"+region+".aliyuncs.com":".cos."+region+".myqcloud.com");
  }
  static String region(String provider,String endpoint,String fallback){try{String host=new URI(endpoint).getHost();if(host==null)return fallback;java.util.regex.Matcher m=java.util.regex.Pattern.compile(provider.equals("oss")?"\\.oss-([a-z0-9-]+)\\.aliyuncs\\.com$":"\\.cos\\.([a-z0-9-]+)\\.myqcloud\\.com$").matcher(host);return m.find()?m.group(1):fallback;}catch(Exception ignored){return fallback;}}
}
