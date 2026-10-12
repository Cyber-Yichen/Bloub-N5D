package com.cyberyichen.bloub;
import org.json.*;
public final class Release093CoreTest {
 static int n;static void check(boolean v,String label){n++;if(!v)throw new AssertionError(label);}
 static void reject(String json)throws Exception{try{MonitorPreviewSpec.parse(new JSONObject(json));throw new AssertionError("Expected rejection: "+json);}catch(JSONException expected){n++;}}
 static boolean near(float a,float b){return Math.abs(a-b)<.0001;}
 public static void main(String[] args)throws Exception{
 JSONObject c=MonitorPreviewSpec.parse(new JSONObject());check(c.getInt("frameRate")==30&&c.getInt("bitrateKbps")==2000,"encoding defaults");
 for(String s:new String[]{"{\"timestamp\":1}","{\"secret\":\"x\"}","{\"rotation\":45}","{\"rotation\":90.5}","{\"frameRate\":31}","{\"frameRate\":15.1}","{\"bitrateKbps\":127}","{\"bitrateKbps\":20001}","{\"bitrateKbps\":128.5}","{\"resolution\":\"0x480\"}","{\"videoCodec\":\"vp9\"}"})reject(s);
 check(MonitorPreviewSpec.parse(new JSONObject("{\"rotationEnabled\":true,\"rotation\":270,\"videoCodec\":\"h265\",\"frameRate\":15,\"resolution\":\"640x480\"}")).getInt("rotation")==270,"valid preview draft");
 for(int fps:new int[]{10,15,24,25,30}){FrameCadence cadence=new FrameCadence(fps);int accepted=0;long first=-1,last=-1;for(int i=0;i<300;i++){long at=1000000000L+i*1000000000L/30;if(cadence.accept(at)){if(first<0)first=at;last=at;accepted++;}}check(Math.abs(accepted-fps*10)<=1,"actual cadence "+fps);check(last-first>=9500000000L,"source duration preserved "+fps);}
 FrameCadence cadence=new FrameCadence(15);check(cadence.accept(1000000000L)&&!cadence.accept(1000000000L),"duplicate timestamps dropped");check(cadence.accept(20000000000L)&&!cadence.accept(20000000001L),"long gap recovery");
 MotionWindow events=new MotionWindow();events.event(1000000,20,120);check(events.spans.get(0).start==980000&&events.spans.get(0).end==1120000,"asymmetric before after");events.event(1100000,0,30);check(events.spans.size()==1&&events.spans.get(0).end==1130000,"merge asymmetric events");events.event(2000000,30,0);check(events.spans.get(1).start==1970000&&events.spans.get(1).end==2000000,"before only");check(!events.active(2000000),"event half open");
 float[] crop=ImageTransform.crop(4f/3,1280f/1080);check(near(crop[0],.05555556f)&&near(crop[2],.94444444f),"center crop");float[] b=ImageTransform.bounds(.1f,.2f,.9f,.8f,90,true,crop);check(near(b[0],.2f)&&near(b[1],.05f)&&near(b[2],.8f)&&near(b[3],.95f),"crop rotation mirror label alignment");check(ImageTransform.bounds(0,.1f,.02f,.5f,0,false,crop)==null,"outside crop label hidden");crop=ImageTransform.crop(4f/3,16f/9);check(near(crop[1],.125f)&&near(crop[3],.875f),"vertical crop");
 check(EncodingSpec.bytes(60,2000,false)==15454096,"video size estimate");check(EncodingSpec.bytes(60,2000,true)==15948496,"audio included");check(EncodingSpec.bytes(300,20000,true)>700000000L,"high bitrate reserve");
 System.out.println("Release093CoreTest: "+n+" checks passed");
 }
}
