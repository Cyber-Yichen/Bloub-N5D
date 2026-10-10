package com.cyberyichen.bloub;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.*;
import android.os.*;
import android.util.*;
import android.view.*;
import java.io.*;
import java.lang.Process;
import java.util.*;
import java.util.regex.*;
import org.json.*;

/** Foreground-only local sensors; a stopped generation cannot publish or reopen. */
final class SensorHub {
    interface Output {void accept(JSONObject data);}
    private final Activity activity;private final Output output;private final TextureView texture;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final HandlerThread cameraThread=new HandlerThread("BloubCamera"),streamThread=new HandlerThread("BloubStream");private final Handler cameraHandler,streamHandler;
    private volatile boolean stopped;private final boolean micWanted,cameraWanted,tofWanted;
    private volatile int quietStart=1380,quietEnd=480;
    private volatile long lastAudioAt,lastDistanceAt,lastSoundAt,liveUntil;private volatile boolean liveRequested,pendingShot;private volatile StreamFrame liveFrame;private volatile String lastCaptureId="";private volatile boolean liveEncoding;private volatile long streamFrames;private volatile double streamFps,streamEncodeMs;private long firstEncodedAt,lastLiveSource;private volatile int liveVersion;private boolean cameraScheduled;private Size activePreview;
    private volatile AudioRecord recorder;private volatile java.lang.Process tofHelper;
    private volatile float level,music,speech,motion,faceX,faceY;private volatile int distance=-1,faces;
    private volatile String micStatus="关闭",cameraStatus="关闭",tofStatus="关闭";
    private volatile boolean cameraOn,processing;
    private volatile CameraDevice camera;private volatile CameraCaptureSession session;
    static final class StreamFrame {final byte[] jpeg;final long capturedAt,sequence;final double encodeMs;StreamFrame(byte[] bytes,long at,long sequence,double ms){jpeg=bytes;capturedAt=at;this.sequence=sequence;encodeMs=ms;}}
    private int savedGeneration=-1;private final Gallery gallery;private final String cameraId;private volatile ImageReader photoReader;
    private long lastBitmap,cameraFrames;private volatile int cameraGeneration;private int[] previousPixels;private long lastCamera;
    private final Runnable publish=new Runnable(){public void run(){
        if(stopped)return;
        long now=SystemClock.elapsedRealtime();
        if(distance>=0&&now-lastDistanceAt>2500){distance=-1;tofStatus="距离读取过期";}
        if(lastAudioAt>0&&now-lastAudioAt>2500){level=music=speech=0;}
        try{
            JSONObject d=new JSONObject();d.put("level",level);d.put("music",music);d.put("speech",speech);
            d.put("distance",distance);d.put("motion",motion);d.put("faces",faces);d.put("faceX",faceX);d.put("faceY",faceY);
            d.put("micStatus",micStatus);d.put("cameraStatus",cameraStatus);d.put("tofStatus",tofStatus);
            d.put("cameraOn",cameraOn);d.put("cameraFrames",cameraFrames);output.accept(d);
        }catch(JSONException ignored){}
        ui.postDelayed(this,150);
    }};
    SensorHub(Activity activity,TextureView texture,boolean mic,boolean cam,boolean tof,Output output){
        this.activity=activity;this.texture=texture;this.output=output;gallery=new Gallery(activity);cameraId=activity.getSharedPreferences("sensors",0).getString("camera_id","2");
        quietStart=activity.getSharedPreferences("sensors",0).getInt("quiet_start",1380);quietEnd=activity.getSharedPreferences("sensors",0).getInt("quiet_end",480);
        micWanted=mic;cameraWanted=cam;tofWanted=tof;
        cameraThread.start();cameraHandler=new Handler(cameraThread.getLooper());streamThread.start();streamHandler=new Handler(streamThread.getLooper());
        if(mic){if(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)new Thread(this::audioLoop,"BloubAudio").start();else micStatus="需要麦克风权限";}
        if(tof)new Thread(this::tofLoop,"BloubToF").start();
        if(cam){
            if(activity.checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)cameraStatus="需要相机权限";
            else {
                cameraStatus="等待短时观察";
                texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){
                    public void onSurfaceTextureAvailable(SurfaceTexture t,int w,int h){scheduleCamera();}
                    public void onSurfaceTextureSizeChanged(SurfaceTexture t,int w,int h){}
                    public boolean onSurfaceTextureDestroyed(SurfaceTexture t){return true;}
                    public void onSurfaceTextureUpdated(SurfaceTexture t){observeFrame();}
                });
                if(texture.isAvailable())scheduleCamera();
            }
        }
        ui.post(publish);
    }
    private void audioLoop(){
        micStatus="正在加载本地模型";
        AudioModel model=null;AudioRecord audio=null;
        try{
            try{model=new AudioModel(activity);}catch(Throwable e){Log.w("BloubSensors","YAMNET unavailable",e);micStatus="模型失败 · 仅声音能量";}
            if(stopped)return;
            int buffer=Math.max(4096,AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT));
            audio=new AudioRecord(MediaRecorder.AudioSource.MIC,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,buffer);
            recorder=audio;if(audio.getState()!=AudioRecord.STATE_INITIALIZED)throw new IOException("AudioRecord not initialized");
            short[] chunk=new short[1024],ring=new short[15600];int cursor=0,total=0,soundChunks=0;long classified=0,started=SystemClock.elapsedRealtime();float noiseFloor=.02f;
            audio.startRecording();if(model!=null)micStatus="YAMNet 本地音乐识别";else micStatus="仅声音能量";
            while(!stopped){
                int n=audio.read(chunk,0,chunk.length);if(n<=0)throw new IOException("Audio read "+n);
                double square=0;for(int i=0;i<n;i++){float v=chunk[i]/32768f;square+=v*v;ring[cursor]=chunk[i];cursor=(cursor+1)%ring.length;}total=Math.min(ring.length,total+n);
                level=(float)Math.min(1,Math.sqrt(square/n)*12);lastAudioAt=SystemClock.elapsedRealtime();
                long now=SystemClock.elapsedRealtime();
                if(now-started<2000)noiseFloor=noiseFloor*.9f+level*.1f;
                else {
                    float threshold=Math.max(.18f,noiseFloor*2.5f);
                    if(level>threshold){if(++soundChunks>=3){boolean wake=lastSoundAt==0||now-lastSoundAt>30000;lastSoundAt=now;if(wake&&cameraWanted)cameraHandler.post(this::openCamera);}}
                    else {soundChunks=0;noiseFloor=noiseFloor*.995f+level*.005f;}
                }
                if(model!=null&&total>=ring.length&&now-classified>=1100){
                    classified=now;float[] scores=model.classify(ring,cursor);
                    music=scores[0];speech=scores[1];
                    Log.d("BloubSensors","AUDIO music="+music+" level="+level);
                }
            }
        }catch(Throwable e){if(!stopped){micStatus="麦克风不可用";Log.w("BloubSensors","Audio stopped",e);}}
        finally{level=music=speech=0;recorder=null;if(audio!=null){try{audio.stop();}catch(Exception ignored){}audio.release();}if(model!=null)model.close();}
    }
    private static final Pattern RANGE=Pattern.compile("status:(\\d+)\\s+distance:(\\d+)");
    private void parseDistance(String text){
        lastDistanceAt=SystemClock.elapsedRealtime();Matcher m=RANGE.matcher(text==null?"":text);
        if(m.find()&&"0".equals(m.group(1))){int mm=Integer.parseInt(m.group(2));distance=mm>=0&&mm<=4000?mm:-1;tofStatus=distance>=0?"距离有效":"距离无效";}
        else{distance=-1;tofStatus="暂无有效距离";}
    }
    private static String readNode()throws IOException{
        try(BufferedReader in=new BufferedReader(new FileReader("/sys/class/misc/stmvl53l4cd/tof_distance"))){return in.readLine();}
    }
    private void tofLoop(){
        tofStatus="正在读取距离";
        try{
            parseDistance(readNode());
            while(!stopped){Thread.sleep(1000);if(!stopped)parseDistance(readNode());}
        }catch(IOException denied){
            // Fixed read-only helper; no LED writes, init, chmod, or arbitrary shell.
            tofStatus="等待 ToF Root 授权";
            try{
                String apk=activity.getApplicationInfo().sourceDir.replace("'","'\\''");
                java.lang.Process helper=new ProcessBuilder("/sbin/su","-c","CLASSPATH='"+apk+"' /system/bin/app_process /system/bin com.cyberyichen.bloub.TofReader "+android.os.Process.myPid()).redirectErrorStream(true).start();
                tofHelper=helper;
                if(stopped){helper.destroy();return;}
                boolean deniedRoot=false;
                try(BufferedReader lines=new BufferedReader(new InputStreamReader(helper.getInputStream()))){
                    String line;while(!stopped&&(line=lines.readLine())!=null){if(line.contains("Permission denied"))deniedRoot=true;if(!line.startsWith("status:"))Log.w("BloubSensors","TOF_HELPER "+line.substring(0,Math.min(256,line.length())));parseDistance(line);}
                }
                if(!stopped){distance=-1;tofStatus=deniedRoot?"请在 Magisk 允许本应用":"ToF 授权或读取失败";}
            }catch(Exception e){if(!stopped){distance=-1;tofStatus="ToF 无法读取";}}
            finally{Process p=tofHelper;tofHelper=null;if(p!=null)p.destroy();}
        }catch(Exception e){if(!stopped){distance=-1;tofStatus="ToF 读取已停止";}}
    }
    static String cameraInfo(Activity activity){
        JSONObject data=new JSONObject();CameraManager manager=(CameraManager)activity.getSystemService(Context.CAMERA_SERVICE);
        for(String id:new String[]{"2","3"})try{
            CameraCharacteristics c=manager.getCameraCharacteristics(id);StreamConfigurationMap map=c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            JSONObject info=new JSONObject().put("orientation",c.get(CameraCharacteristics.SENSOR_ORIENTATION)).put("facing",c.get(CameraCharacteristics.LENS_FACING)).put("level",c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)).put("exposure",String.valueOf(c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE))).put("iso",String.valueOf(c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)));
            JSONArray preview=new JSONArray(),jpeg=new JSONArray();if(map!=null){Size[] p=map.getOutputSizes(SurfaceTexture.class),j=map.getOutputSizes(ImageFormat.JPEG);if(p!=null)for(Size s:p)preview.put(s.toString());if(j!=null)for(Size s:j)jpeg.put(s.toString());}
            info.put("preview",preview).put("jpeg",jpeg).put("capabilities",Arrays.toString(c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES))).put("fpsRanges",Arrays.toString(c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)));data.put(id,info);
        }catch(Exception e){try{data.put(id,new JSONObject().put("error","unavailable"));}catch(JSONException ignored){}}
        return data.toString();
    }
    private static Size chooseSize(Size[] sizes,double aspect,int maxArea)throws IOException{
        if(sizes==null||sizes.length==0)throw new IOException("No camera sizes");
        Size best=null;double error=Double.MAX_VALUE;int area=0;
        for(Size s:sizes){int a=s.getWidth()*s.getHeight();if(a>maxArea)continue;double e=Math.abs(s.getWidth()/(double)s.getHeight()-aspect);if(e<error-.03||Math.abs(e-error)<.03&&a>area){best=s;error=e;area=a;}}
        if(best==null)best=sizes[sizes.length-1];return best;
    }
    private void configureExposure(CaptureRequest.Builder r,CameraCharacteristics c){
        r.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);r.set(CaptureRequest.CONTROL_AWB_MODE,CaptureRequest.CONTROL_AWB_MODE_AUTO);
        int[] capabilities=c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);boolean manual=false;if(capabilities!=null)for(int cap:capabilities)if(cap==CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)manual=true;
        if(manual){
            r.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_OFF);
            Range<Long> times=c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);Range<Integer> iso=c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
            r.set(CaptureRequest.SENSOR_EXPOSURE_TIME,times==null?10000000L:times.clamp(10000000L));r.set(CaptureRequest.SENSOR_SENSITIVITY,iso==null?400:iso.clamp(400));r.set(CaptureRequest.SENSOR_FRAME_DURATION,33350000L);
        }else r.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON);
    }
    private void capturePhoto(int generation,CameraDevice device,CameraCharacteristics c,ImageReader reader){
        if(stopped||generation!=cameraGeneration||camera!=device||session==null||!gallery.enabled())return;
        try{
            CaptureRequest.Builder shot=device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);shot.addTarget(reader.getSurface());configureExposure(shot,c);
            shot.set(CaptureRequest.JPEG_QUALITY,(byte)95);shot.set(CaptureRequest.JPEG_ORIENTATION,0);
            session.capture(shot.build(),new CameraCaptureSession.CaptureCallback(){public void onCaptureFailed(CameraCaptureSession s,CaptureRequest r,CaptureFailure f){Log.w("BloubSensors","PHOTO_FAILED id="+cameraId+" reason="+f.getReason());}},cameraHandler);
        }catch(Exception e){Log.w("BloubSensors","Photo capture failed",e);}
    }
    private void savePreview(int generation,Size preview,boolean forced){
        if(stopped||generation!=cameraGeneration||(!forced&&savedGeneration==generation)||!cameraOn||!gallery.enabled())return;
        Bitmap frame=texture.getBitmap(preview.getWidth(),preview.getHeight());if(frame==null)return;
        cameraHandler.post(()->{try{
            if(stopped||generation!=cameraGeneration||(!forced&&savedGeneration==generation)||!gallery.enabled()||(!forced&&!live()&&!automaticAllowed()))return;
            java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
            if(!frame.compress(Bitmap.CompressFormat.JPEG,95,bytes))throw new IOException("Preview JPEG failed");
            lastCaptureId=gallery.saveJpeg(bytes.toByteArray(),faces,motion,cameraId,"2".equals(cameraId)?90:0);savedGeneration=generation;pendingShot=false;
            Log.i("BloubSensors","PHOTO_PREVIEW_SAVED id="+cameraId+" size="+preview);
        }catch(Exception e){Log.w("BloubSensors","Preview photo save failed",e);}finally{frame.recycle();}});
    }
    void quietHours(int start,int end){quietStart=start;quietEnd=end;cameraHandler.post(()->{if(!cameraOn)cameraStatus=automaticAllowed()?"等待短时观察":"夜间休息 · 声音唤醒";});}
    private boolean automaticAllowed(){Calendar c=Calendar.getInstance();return CameraSchedule.allows(c.get(Calendar.HOUR_OF_DAY)*60+c.get(Calendar.MINUTE),quietStart,quietEnd,SystemClock.elapsedRealtime(),lastSoundAt,micWanted);}
    private boolean live(){return !stopped&&liveRequested&&SystemClock.elapsedRealtime()<liveUntil;}
    String remoteStatus(){try{return new JSONObject().put("live",live()).put("cameraOn",cameraOn).put("cameraId",cameraId).put("status",cameraStatus).put("faceAnalysis",false).put("lastCaptureId",lastCaptureId).put("quietStart",quietStart).put("quietEnd",quietEnd).put("automaticAllowed",automaticAllowed()).put("uptimeMs",SystemClock.elapsedRealtime()).put("streamFrames",streamFrames).put("streamFps",streamFps).put("streamEncodeMs",streamEncodeMs).toString();}catch(JSONException e){return "{}";}}
    boolean remote(String action){
        if(stopped||!cameraWanted||activity.checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)return false;
        if("capture".equals(action)&&!gallery.enabled())return false;
        cameraHandler.post(()->{if(stopped)return;
            if("stop".equals(action)){liveVersion++;liveRequested=false;liveFrame=null;closeCamera();return;}
            if("start".equals(action)){boolean already=live();liveRequested=true;liveUntil=SystemClock.elapsedRealtime()+90000;if(!already){liveVersion++;liveFrame=null;streamFrames=0;streamFps=0;firstEncodedAt=0;lastLiveSource=0;ui.post(liveTick);}}
            if("capture".equals(action)){pendingShot=true;if(cameraOn&&activePreview!=null){Size size=activePreview;int generation=cameraGeneration;ui.post(()->savePreview(generation,size,true));return;}}
            lastCamera=0;openCamera();
        });return true;
    }
    StreamFrame streamFrame(){return live()?liveFrame:null;}
    void streamHeartbeat(){if(liveRequested&&!stopped)liveUntil=SystemClock.elapsedRealtime()+90000;}
    private void scheduleCamera(){
        if(stopped||!cameraWanted||cameraScheduled)return;cameraScheduled=true;
        cameraHandler.postDelayed(new Runnable(){public void run(){if(stopped)return;openCamera();cameraHandler.postDelayed(this,15000);}},800);
    }
    private void closeAfterObservation(int generation,Surface target){
        if(generation!=cameraGeneration){target.release();return;}
        if(stopped||!live()){liveRequested=false;liveFrame=null;closeCamera();target.release();}
        else cameraHandler.postDelayed(()->closeAfterObservation(generation,target),1000);
    }
    private final Runnable liveTick=new Runnable(){public void run(){if(!live())return;long began=SystemClock.uptimeMillis();if(cameraOn)updateLiveFrame();ui.postAtTime(this,Math.max(began+20,SystemClock.uptimeMillis()+1));}};
    private void updateLiveFrame(){
        if(!live()||liveEncoding)return;SurfaceTexture source=texture.getSurfaceTexture();if(source==null)return;long timestamp=source.getTimestamp();if(timestamp>0&&timestamp==lastLiveSource)return;lastLiveSource=timestamp;liveEncoding=true;final int version=liveVersion;final long capturedAt=SystemClock.elapsedRealtime();
        Bitmap frame;try{frame=texture.getBitmap("2".equals(cameraId)?480:640,"2".equals(cameraId)?640:480);}catch(RuntimeException e){liveEncoding=false;Log.w("BloubSensors","Stream frame read",e);return;}if(frame==null){liveEncoding=false;return;}
        streamHandler.post(()->{Bitmap upright=frame;try{
            if(!live()||version!=liveVersion)return;
            if("2".equals(cameraId)){Matrix m=new Matrix();m.postRotate(90);upright=Bitmap.createBitmap(frame,0,0,frame.getWidth(),frame.getHeight(),m,true);}
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();upright.compress(Bitmap.CompressFormat.JPEG,80,out);long done=SystemClock.elapsedRealtime();if(!live()||version!=liveVersion)return;streamEncodeMs=done-capturedAt;if(firstEncodedAt==0)firstEncodedAt=done;long sequence=++streamFrames;streamFps=sequence<2?0:1000.0*(sequence-1)/Math.max(1,done-firstEncodedAt);liveFrame=new StreamFrame(out.toByteArray(),capturedAt,sequence,streamEncodeMs);
        }finally{if(upright!=frame)upright.recycle();frame.recycle();liveEncoding=false;if(live())ui.post(this::updateLiveFrame);}});
    }
    private void openCamera(){
        if(stopped||cameraOn||camera!=null)return;
        long now=SystemClock.elapsedRealtime();if(!live()&&!pendingShot){if(!automaticAllowed()){cameraStatus="夜间休息 · 声音唤醒";return;}if(!CameraSchedule.due(now,lastCamera))return;}
        final int generation=++cameraGeneration;cameraStatus="正在短时观察";lastCamera=SystemClock.elapsedRealtime();
        try{
            CameraManager manager=(CameraManager)activity.getSystemService(Context.CAMERA_SERVICE);
            if(!Arrays.asList(manager.getCameraIdList()).contains(cameraId))throw new IOException("Camera absent");
            final CameraCharacteristics c=manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map=c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if(map==null)throw new IOException("Missing stream map");
            final Size preview=chooseSize(map.getOutputSizes(SurfaceTexture.class),"2".equals(cameraId)?.75:4.0/3,1300000);
            // N5D camera 3 advertises JPEG but its HAL stalls with a JPEG target; use its full preview.
            Size[] jpegSizes="3".equals(cameraId)?null:map.getOutputSizes(ImageFormat.JPEG);final Size picture=jpegSizes==null||jpegSizes.length==0?null:chooseSize(jpegSizes,preview.getWidth()/(double)preview.getHeight(),2100000);
            SurfaceTexture t=texture.getSurfaceTexture();if(t==null)return;t.setDefaultBufferSize(preview.getWidth(),preview.getHeight());
            final ImageReader photos=picture==null?null:ImageReader.newInstance(picture.getWidth(),picture.getHeight(),ImageFormat.JPEG,2);photoReader=photos;
            if(photos!=null)photos.setOnImageAvailableListener(reader->{Image image=null;try{
                image=reader.acquireLatestImage();if(image==null||stopped||generation!=cameraGeneration||savedGeneration==generation||!gallery.enabled())return;
                java.nio.ByteBuffer buffer=image.getPlanes()[0].getBuffer();byte[] jpeg=new byte[buffer.remaining()];buffer.get(jpeg);
                gallery.saveJpeg(jpeg,faces,motion,cameraId,"2".equals(cameraId)?90:0);savedGeneration=generation;
                Log.i("BloubSensors","PHOTO_SAVED id="+cameraId+" size="+picture);
            }catch(Exception e){Log.w("BloubSensors","Photo save failed",e);}finally{if(image!=null)image.close();}},cameraHandler);
            Log.i("BloubSensors","CAMERA_CONFIG id="+cameraId+" preview="+preview+" jpeg="+picture);
            activePreview=preview;final Surface target=new Surface(t);
            manager.openCamera(cameraId,new CameraDevice.StateCallback(){
                public void onOpened(CameraDevice d){
                    if(stopped||generation!=cameraGeneration){d.close();target.release();return;}camera=d;cameraOn=true;
                    try{d.createCaptureSession(photos==null?Arrays.asList(target):Arrays.asList(target,photos.getSurface()),new CameraCaptureSession.StateCallback(){
                        public void onConfigured(CameraCaptureSession s){
                            if(stopped||generation!=cameraGeneration||camera!=d){s.close();return;}session=s;
                            try{
                                CaptureRequest.Builder r=d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);r.addTarget(target);
                                configureExposure(r,c);
                                s.setRepeatingRequest(r.build(),new CameraCaptureSession.CaptureCallback(){
                                    private boolean logged;
                                    public void onCaptureCompleted(CameraCaptureSession cs,CaptureRequest cr,TotalCaptureResult result){if(!logged){logged=true;Log.i("BloubSensors","CAMERA_EXPOSURE id="+cameraId+" ns="+result.get(CaptureResult.SENSOR_EXPOSURE_TIME)+" iso="+result.get(CaptureResult.SENSOR_SENSITIVITY)+" duration="+result.get(CaptureResult.SENSOR_FRAME_DURATION));}}
                                },cameraHandler);if(photos!=null)cameraHandler.postDelayed(()->capturePhoto(generation,d,c,photos),2400);ui.postDelayed(()->savePreview(generation,preview,pendingShot),3800);cameraStatus=live()?"网页实时观察":"相机 "+cameraId+" · 短时观察";
                            }catch(Exception e){cameraStatus="相机配置失败";closeCamera();}
                        }
                        public void onConfigureFailed(CameraCaptureSession s){cameraStatus="相机配置失败";closeCamera();}
                    },cameraHandler);}catch(Exception e){cameraStatus="相机会话失败";closeCamera();}
                }
                public void onDisconnected(CameraDevice d){d.close();if(camera==d)camera=null;cameraOn=false;cameraStatus="相机已断开";}
                public void onError(CameraDevice d,int error){d.close();if(camera==d)camera=null;cameraOn=false;cameraStatus="相机不可用 "+error;}
            },cameraHandler);
            cameraHandler.postDelayed(()->closeAfterObservation(generation,target),5500);
        }catch(Exception e){cameraStatus="相机不可用";Log.w("BloubSensors","Camera",e);}
    }
    private void observeFrame(){
        if(stopped||!cameraOn)return;cameraFrames++;
        long now=SystemClock.elapsedRealtime();if(processing||now-lastBitmap<(live()?1000:450))return;
        lastBitmap=now;processing=true;
        Bitmap source=texture.getBitmap("2".equals(cameraId)?240:320,"2".equals(cameraId)?320:240);if(source==null){processing=false;return;}
        cameraHandler.post(()->{
            try{
                if(stopped)return;
                Bitmap small=Bitmap.createScaledBitmap(source,30,40,false);int[] pixels=new int[1200];small.getPixels(pixels,0,30,0,0,30,40);small.recycle();
                if(previousPixels!=null){double delta=0;for(int i=0;i<pixels.length;i++)delta+=Math.abs((pixels[i]&255)-(previousPixels[i]&255));motion=(float)Math.min(1,delta/pixels.length/25);}
                previousPixels=pixels;
                // Face analysis is deferred until a user-trained model is available.
                faces=0;faceX=faceY=0;
                Log.d("BloubSensors","CAMERA_FRAME faces="+faces+" motion="+motion);
            }catch(Throwable e){Log.w("BloubSensors","Camera analysis",e);}
            finally{source.recycle();processing=false;}
        });
    }
    private void closeCamera(){
        cameraGeneration++;ImageReader oldReader=photoReader;photoReader=null;if(oldReader!=null)oldReader.close();cameraOn=false;faces=0;motion=0;previousPixels=null;
        CameraCaptureSession s=session;session=null;if(s!=null)s.close();
        CameraDevice d=camera;camera=null;if(d!=null)d.close();
        if(!stopped)cameraStatus=automaticAllowed()?"相机 "+cameraId+" · 每两分钟观察":"夜间休息 · 声音唤醒";
    }
    void stop(){
        stopped=true;liveVersion++;liveRequested=false;liveFrame=null;pendingShot=false;ui.removeCallbacks(publish);
        java.lang.Process p=tofHelper;if(p!=null){try{p.getOutputStream().close();}catch(Exception ignored){}p.destroy();}
        cameraHandler.removeCallbacksAndMessages(null);cameraHandler.post(()->{closeCamera();cameraThread.quitSafely();});streamHandler.post(()->streamThread.quitSafely());
        // Audio reads finish in ~64ms, then release on their own worker.
    }
}
