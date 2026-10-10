package com.cyberyichen.bloub;
import org.json.*;
/** A strict shared schema for native and LAN controls. */
final class SettingsSpec {
  static JSONObject defaults()throws JSONException{return new JSONObject("{\"calm\":false,\"lights\":true,\"brightness\":42,\"theme\":\"paper\",\"mic\":false,\"camera\":false,\"tof\":false,\"cameraId\":\"2\",\"quietStart\":\"23:00\",\"quietEnd\":\"08:00\",\"ringOffset\":0,\"gallerySaving\":true,\"galleryServerEnabled\":false,\"seatEnabled\":false,\"occupiedMinutes\":1,\"emptyMinutes\":10,\"unknownMinutes\":2,\"monitorEnabled\":false}");}
  static JSONObject patch(JSONObject previous,JSONObject delta)throws JSONException{
    JSONObject result=new JSONObject(previous.toString());JSONObject base=defaults();java.util.Iterator<String> keys=delta.keys();
    while(keys.hasNext()){String k=keys.next();if(!base.has(k))throw new JSONException("未知设置: "+k);Object v=delta.get(k),type=base.get(k);
      if(type instanceof Boolean){if(!(v instanceof Boolean))throw new JSONException("开关格式错误: "+k);}
      else if(type instanceof Number){if(!(v instanceof Number)||!Double.isFinite(((Number)v).doubleValue()))throw new JSONException("数字格式错误: "+k);double n=((Number)v).doubleValue();double min=k.equals("brightness")?15:k.equals("ringOffset")?-180:1,max=k.equals("brightness")?100:k.equals("ringOffset")?180:1440;if(n<min||n>max||n!=Math.floor(n))throw new JSONException("数字超出范围: "+k);}
      else{if(!(v instanceof String))throw new JSONException("文本格式错误: "+k);String s=(String)v;if(k.equals("theme")&&!s.matches("paper|lagoon|sand|night|auto")||k.equals("cameraId")&&!s.matches("2|3")||k.startsWith("quiet")&&!s.matches("([01][0-9]|2[0-3]):[0-5][0-9]"))throw new JSONException("设置无效: "+k);}
      result.put(k,v);
    }return result;
  }
  static int minutes(String value){return Integer.parseInt(value.substring(0,2))*60+Integer.parseInt(value.substring(3,5));}
}
