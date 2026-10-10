package com.cyberyichen.bloub;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.util.*;
import java.text.SimpleDateFormat;
/** Small PUT-only signers, no remote SDK or credentials in URLs. */
final class UploadSigner {
  static byte[] utf(String s){return s.getBytes(StandardCharsets.UTF_8);}
  static byte[] hmac(String alg,byte[] key,String text)throws GeneralSecurityException{Mac m=Mac.getInstance(alg);m.init(new SecretKeySpec(key,alg));return m.doFinal(utf(text));}
  static String hex(byte[] data){StringBuilder s=new StringBuilder();for(byte b:data)s.append(String.format(Locale.US,"%02x",b&255));return s.toString();}
  static String hash(String algorithm,String s)throws GeneralSecurityException{return hex(MessageDigest.getInstance(algorithm).digest(utf(s)));}
  static String base64(byte[] bytes){String chars="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";StringBuilder s=new StringBuilder();for(int i=0;i<bytes.length;i+=3){int a=bytes[i]&255,b=i+1<bytes.length?bytes[i+1]&255:0,c=i+2<bytes.length?bytes[i+2]&255:0;s.append(chars.charAt(a>>2)).append(chars.charAt(((a&3)<<4)|(b>>4))).append(i+1<bytes.length?chars.charAt(((b&15)<<2)|(c>>6)):'=').append(i+2<bytes.length?chars.charAt(c&63):'=');}return s.toString();}
  static String encode(String s,boolean slash){StringBuilder out=new StringBuilder();for(byte v:utf(s)){int b=v&255;if(b>='a'&&b<='z'||b>='A'&&b<='Z'||b>='0'&&b<='9'||b=='-'||b=='_'||b=='.'||b=='~'||slash&&b=='/')out.append((char)b);else out.append(String.format(Locale.US,"%%%02X",b));}return out.toString();}
  static String date(long now,String pattern){SimpleDateFormat f=new SimpleDateFormat(pattern,Locale.US);f.setTimeZone(TimeZone.getTimeZone("UTC"));return f.format(new Date(now));}
  static String s3(String method,String path,Map<String,String> headers,String hash,String region,String id,String secret,long now)throws GeneralSecurityException{
    TreeMap<String,String> sorted=new TreeMap<>();for(Map.Entry<String,String> e:headers.entrySet())sorted.put(e.getKey().toLowerCase(Locale.US),e.getValue().trim().replaceAll("\\s+"," "));
    StringBuilder canonical=new StringBuilder(),names=new StringBuilder();for(Map.Entry<String,String> e:sorted.entrySet()){canonical.append(e.getKey()).append(':').append(e.getValue()).append('\n');if(names.length()>0)names.append(';');names.append(e.getKey());}
    String day=date(now,"yyyyMMdd"),scope=day+"/"+region+"/s3/aws4_request";
    String request=method+"\n"+encode(path,true)+"\n\n"+canonical+"\n"+names+"\n"+hash;
    String sign="AWS4-HMAC-SHA256\n"+headers.get("x-amz-date")+"\n"+scope+"\n"+hash("SHA-256",request);
    byte[] key=hmac("HmacSHA256",utf("AWS4"+secret),day);key=hmac("HmacSHA256",key,region);key=hmac("HmacSHA256",key,"s3");key=hmac("HmacSHA256",key,"aws4_request");
    return "AWS4-HMAC-SHA256 Credential="+id+"/"+scope+", SignedHeaders="+names+", Signature="+hex(hmac("HmacSHA256",key,sign));
  }
  static String oss(String bucket,String key,String mime,String md5,String date,String id,String secret,String token)throws GeneralSecurityException{
    String canonical=token.isEmpty()?"":"x-oss-security-token:"+token+"\n";
    return "OSS "+id+":"+base64(hmac("HmacSHA1",utf(secret),"PUT\n"+md5+"\n"+mime+"\n"+date+"\n"+canonical+"/"+bucket+"/"+key));
  }
  static String cos(String path,Map<String,String> headers,String id,String secret,long start,long end)throws GeneralSecurityException{
    TreeMap<String,String> sorted=new TreeMap<>();for(Map.Entry<String,String> e:headers.entrySet())sorted.put(encode(e.getKey().toLowerCase(Locale.US),false).toLowerCase(Locale.US),encode(e.getValue().trim(),false));
    StringBuilder canonical=new StringBuilder(),names=new StringBuilder();for(Map.Entry<String,String> e:sorted.entrySet()){if(names.length()>0){names.append(';');canonical.append('&');}names.append(e.getKey());canonical.append(e.getKey()).append('=').append(e.getValue());}
    String time=start+";"+end,key=hex(hmac("HmacSHA1",utf(secret),time));String sign="sha1\n"+time+"\n"+hash("SHA-1","put\n"+path+"\n\n"+canonical+"\n")+"\n";
    return "q-sign-algorithm=sha1&q-ak="+id+"&q-sign-time="+time+"&q-key-time="+time+"&q-header-list="+names+"&q-url-param-list=&q-signature="+hex(hmac("HmacSHA1",utf(key),sign));
  }
}
