package com.cyberyichen.bloub;
import java.io.*;
import java.nio.charset.StandardCharsets;
/** Standard JPEG APP1 XMP: a training annotation travels with the original JPEG. */
final class PhotoAnnotation {
  static String escape(String s){return s.replace("&","&amp;").replace("\"","&quot;").replace("<","&lt;").replace(">","&gt;");}
  static byte[] embed(byte[] jpeg,String metadata)throws IOException{
    if(jpeg.length<2||(jpeg[0]&255)!=255||(jpeg[1]&255)!=216)throw new IOException("Invalid JPEG");
    String xml="<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"><rdf:Description rdf:about=\"\" xmlns:bloub=\"https://github.com/Cyber-Yichen/Bloub-N5D/ns/1.0/\" bloub:annotation=\""+escape(metadata)+"\"/></rdf:RDF></x:xmpmeta>";
    byte[] header="http://ns.adobe.com/xap/1.0/\0".getBytes(StandardCharsets.US_ASCII),body=xml.getBytes(StandardCharsets.UTF_8);int length=header.length+body.length+2;if(length>65535)throw new IOException("Annotation too large");
    ByteArrayOutputStream out=new ByteArrayOutputStream(jpeg.length+length+2);out.write(jpeg,0,2);out.write(255);out.write(225);out.write(length>>8);out.write(length&255);out.write(header);out.write(body);out.write(jpeg,2,jpeg.length-2);return out.toByteArray();
  }
}
