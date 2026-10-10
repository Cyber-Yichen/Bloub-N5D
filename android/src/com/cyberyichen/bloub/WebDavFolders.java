package com.cyberyichen.bloub;
import java.io.*;
import java.net.*;
import javax.net.ssl.*;
import java.nio.charset.StandardCharsets;
/** Bounded DAV request; platform trust and hostname checks remain enabled. */
final class WebDavFolders {
  static void create(URL url,String authorization)throws Exception{
    int port=url.getPort()>0?url.getPort():url.getProtocol().equals("https")?443:80;
    Socket socket=new Socket();
    try{
      socket.connect(new InetSocketAddress(url.getHost(),port),15000);socket.setSoTimeout(15000);
      if(url.getProtocol().equals("https")){
        SSLSocket tls=(SSLSocket)((SSLSocketFactory)SSLSocketFactory.getDefault()).createSocket(socket,url.getHost(),port,true);socket=tls;
        boolean checked=false;
        try{SSLParameters parameters=tls.getSSLParameters();SSLParameters.class.getMethod("setEndpointIdentificationAlgorithm",String.class).invoke(parameters,"HTTPS");tls.setSSLParameters(parameters);checked=true;}catch(ReflectiveOperationException unavailable){}
        tls.startHandshake();if(!checked&&!HttpsURLConnection.getDefaultHostnameVerifier().verify(url.getHost(),tls.getSession()))throw new SSLHandshakeException("NAS 证书主机名不匹配");
      }
      String host=url.getHost()+(url.getPort()>0?":"+url.getPort():"");String path=url.getFile().isEmpty()?"/":url.getFile();
      OutputStream out=socket.getOutputStream();out.write(("MKCOL "+path+" HTTP/1.1\r\nHost: "+host+"\r\nAuthorization: "+authorization+"\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.flush();
      InputStream in=socket.getInputStream();StringBuilder line=new StringBuilder();int ch;while((ch=in.read())!=-1&&ch!='\n'){if(line.length()>=4096)throw new IOException("NAS 响应过长");if(ch!='\r')line.append((char)ch);}String status=line.toString();
      if(!status.matches("HTTP/1\\.[01] [0-9]{3}.*"))throw new IOException("NAS 响应无效");int code=Integer.parseInt(status.substring(9,12));if(code!=200&&code!=201&&code!=204&&code!=405)throw new IOException("NAS 目录 HTTP "+code);
    }finally{socket.close();}
  }
}
