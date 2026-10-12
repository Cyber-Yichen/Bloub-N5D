package com.cyberyichen.bloub;
import org.json.*;
final class EncodingSpec {
 static void validate(JSONObject d)throws JSONException{if(!d.getString("videoCodec").matches("h264|h265"))throw new JSONException("选择 H.264 或 H.265");if(!d.getString("resolution").matches("auto|[1-9][0-9]{2,3}x[1-9][0-9]{2,3}"))throw new JSONException("分辨率无效");double fps=d.getDouble("frameRate"),rate=d.getDouble("bitrateKbps");if(fps!=Math.floor(fps)||!(fps==10||fps==15||fps==24||fps==25||fps==30))throw new JSONException("帧率无效");if(!Double.isFinite(rate)||rate!=Math.floor(rate)||rate<128||rate>20000)throw new JSONException("码率范围为 128 至 20000 kbps");}
 static long bytes(long seconds,long kbps,boolean audio){return (long)Math.ceil(seconds*(kbps*1000d+(audio?64000:0))/8*1.03)+4096;}
}
