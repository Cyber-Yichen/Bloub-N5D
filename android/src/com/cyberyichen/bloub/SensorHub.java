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
    private final MonitorRecorder monitor;private final SeatMonitor seat;private final LandmarkHub landmarks;private final MotionDetector changes=new MotionDetector();private volatile boolean showFace,showHand,showHolistic,showYamnet;private long lastMotionFrame;private final Activity activity;private final Output output;private final TextureView texture;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final HandlerThread cameraThread=new HandlerThread("BloubCamera"),streamThread=new HandlerThread("BloubStream");private final Handler cameraHandler,streamHandler;
    private volatile boolean stopped;private final boolean micWanted,cameraWanted,tofWanted;
    private volatile int quietStart=1380,quietEnd=480;
    private volatile long lastAudioAt,lastDistanceAt,lastSoundAt,liveUntil;private volatile boolean liveRequested,pendingShot;private volatile StreamFrame liveFrame;private volatile String lastCaptureId="";private volatile boolean liveEncoding;private volatile long streamFrames;private volatile double streamFps,streamEncodeMs;private long firstEncodedAt,lastLiveSource;private volatile int liveVersion;private boolean cameraScheduled;private Size activePreview;
    private volatile long monitorPreviewUntil;private volatile boolean monitorPreviewRequested;private volatile JSONObject monitorPreviewOptions;private volatile StreamFrame monitorPreviewFrame;private volatile int monitorPreviewVersion;private long monitorPreviewSequence;private final java.util.concurrent.atomic.AtomicBoolean audioRunning=new java.util.concurrent.atomic.AtomicBoolean();
    private volatile AudioRecord recorder;private volatile java.lang.Process tofHelper;
    private volatile float audioConfidence,level,music,speech,motion,faceX,faceY;private volatile int distance=-1,faces;
    private volatile String audioLabel="",micStatus="关闭",cameraStatus="关闭",tofStatus="关闭";
    private volatile boolean softwareExposure;private long exposureGain;private Range<Long> exposureRange;private Range<Integer> isoRange;
    private CaptureRequest.Builder repeatingBuilder;private Range<Integer> compensationRange;private double compensationStep=.5;private int requestedCompensation;private long lastMeter,cameraOpenedAt;
    private volatile long exposureTimeNs;private volatile int sensitivity,aeState=-1,exposureCompensation;
    private volatile boolean cameraOn,processing,photoSaving;private boolean ownsRecorder,opening;private Surface previewSurface;
    private volatile CameraDevice camera;private volatile CameraCaptureSession session;
    static final class StreamFrame {final byte[] jpeg;final long capturedAt,sequence;final double encodeMs;StreamFrame(byte[] bytes,long at,long sequence,double ms){jpeg=bytes;capturedAt=at;this.sequence=sequence;encodeMs=ms;}}
    private int savedGeneration=-1;private final Gallery gallery;private final String cameraId;private volatile ImageReader photoReader;
    private long lastPhotoAt,lastPhotoAttempt;private final DeviceSettings settings;private long lastBitmap,cameraFrames;private volatile int cameraGeneration;private int[] previousPixels;private long lastCamera;
    private final Runnable publish=new Runnable(){public void run(){
        if(stopped)return;
        long now=SystemClock.elapsedRealtime();
        if(distance>=0&&now-lastDistanceAt>2500){distance=-1;tofStatus="距离读取过期";}
        if(lastAudioAt>0&&now-lastAudioAt>2500){level=music=speech=0;}
        try{
            JSONObject d=new JSONObject();d.put("level",level);d.put("music",music);d.put("speech",speech);
            d.put("distance",distance);d.put("motion",motion);d.put("faces",faces);d.put("faceX",faceX);d.put("faceY",faceY);
            d.put("micStatus",micStatus);d.put("cameraStatus",cameraStatus);d.put("tofStatus",tofStatus);
            d.put("lastPhotoUptime",lastPhotoAt);d.put("seatState",seat.currentState());d.put("seatObserving",seat.enabled()&&cameraWanted);d.put("cameraOn",cameraOn);d.put("cameraFrames",cameraFrames);output.accept(d);
        }catch(JSONException ignored){}
        ui.postDelayed(this,150);
    }};
    SensorHub(Activity activity,TextureView texture,boolean mic,boolean cam,boolean tof,SeatMonitor seat,MonitorRecorder monitor,Output output){
        this.monitor=monitor;this.seat=seat;landmarks=new LandmarkHub(activity);settings=new DeviceSettings(activity);lastPhotoAt=activity.getSharedPreferences("gallery",0).getLong("last_photo_uptime",0);if(lastPhotoAt>SystemClock.elapsedRealtime())lastPhotoAt=0;this.activity=activity;this.texture=texture;this.output=output;gallery=new Gallery(activity);cameraId=activity.getSharedPreferences("sensors",0).getString("camera_id","2");
        quietStart=activity.getSharedPreferences("sensors",0).getInt("quiet_start",1380);quietEnd=activity.getSharedPreferences("sensors",0).getInt("quiet_end",480);
        micWanted=mic;cameraWanted=cam;tofWanted=tof;
        cameraThread.start();cameraHandler=new Handler(cameraThread.getLooper());streamThread.start();streamHandler=new Handler(streamThread.getLooper());
        if(mic||monitor.needsMicrophone()){if(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)ensureAudio();else micStatus="需要麦克风权限";}
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
    private boolean needsAudio(){return micWanted||monitor.needsMicrophone()||(monitorPreview()&&monitorPreviewOptions!=null&&monitorPreviewOptions.optBoolean("overlayYamnet"));}
    private void ensureAudio(){if(!stopped&&needsAudio()&&activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED&&audioRunning.compareAndSet(false,true))new Thread(this::audioLoop,"BloubAudio").start();}
    private void audioLoop(){
        micStatus="正在加载本地模型";
        AudioModel model=null;AudioRecord audio=null;java.util.concurrent.ExecutorService analysis=java.util.concurrent.Executors.newSingleThreadExecutor();java.util.concurrent.atomic.AtomicBoolean analyzing=new java.util.concurrent.atomic.AtomicBoolean();
        try{
            try{model=new AudioModel(activity);}catch(Throwable e){Log.w("BloubSensors","YAMNET unavailable",e);micStatus="模型失败 · 仅声音能量";}
            final AudioModel classifier=model;
            if(stopped)return;
            int buffer=Math.max(4096,AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT));
            audio=new AudioRecord(MediaRecorder.AudioSource.MIC,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,buffer);
            recorder=audio;if(audio.getState()!=AudioRecord.STATE_INITIALIZED)throw new IOException("AudioRecord not initialized");
            short[] chunk=new short[1024],ring=new short[15600];int cursor=0,total=0,soundChunks=0;long classified=0,started=SystemClock.elapsedRealtime();float noiseFloor=.02f;
            audio.startRecording();if(model!=null)micStatus="YAMNet 本地音乐识别";else micStatus="仅声音能量";
            while(!stopped&&needsAudio()){
                int n=audio.read(chunk,0,chunk.length);if(n>0)monitor.pcm(chunk,n,System.nanoTime());if(n<=0)throw new IOException("Audio read "+n);
                double square=0;for(int i=0;i<n;i++){float v=chunk[i]/32768f;square+=v*v;ring[cursor]=chunk[i];cursor=(cursor+1)%ring.length;}total=Math.min(ring.length,total+n);
                level=(float)Math.min(1,Math.sqrt(square/n)*12);lastAudioAt=SystemClock.elapsedRealtime();
                long now=SystemClock.elapsedRealtime();
                if(now-started<2000)noiseFloor=noiseFloor*.9f+level*.1f;
                else {
                    float threshold=Math.max(.18f,noiseFloor*2.5f);
                    if(level>threshold){if(++soundChunks>=3){boolean wake=lastSoundAt==0||now-lastSoundAt>30000;lastSoundAt=now;if(wake&&cameraWanted)cameraHandler.post(this::openCamera);}}
                    else {soundChunks=0;noiseFloor=noiseFloor*.995f+level*.005f;}
                }
                if(classifier!=null&&total>=ring.length&&now-classified>=1100&&analyzing.compareAndSet(false,true)){
                    classified=now;short[] snapshot=new short[ring.length];for(int i=0;i<snapshot.length;i++)snapshot[i]=ring[(cursor+i)%ring.length];analysis.execute(()->{try{float[] scores=classifier.classify(snapshot,0);if(!stopped){if(micWanted){music=scores[0];speech=scores[1];}audioLabel=classifier.label;audioConfidence=classifier.confidence;}}catch(Throwable failure){Log.w("BloubSensors","Audio classification",failure);}finally{analyzing.set(false);}});
                }
            }
        }catch(Throwable e){if(!stopped){micStatus="麦克风不可用";Log.w("BloubSensors","Audio stopped",e);}}
        finally{level=music=speech=0;recorder=null;if(audio!=null){try{audio.stop();}catch(Exception ignored){}audio.release();}if(model!=null){AudioModel completed=model;analysis.execute(completed::close);}analysis.shutdown();audioRunning.set(false);if(!stopped)micStatus="关闭";}
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
            info.put("maxAeRegions",c.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE));info.put("aeModes",Arrays.toString(c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES))).put("exposureCompensationRange",String.valueOf(c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE))).put("exposureCompensationStep",String.valueOf(c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)));info.put("preview",preview).put("jpeg",jpeg).put("capabilities",Arrays.toString(c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES))).put("fpsRanges",Arrays.toString(c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)));data.put(id,info);
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
        if(softwareExposure){r.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_OFF);applyGain(r);return;}
        r.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON);
        r.set(CaptureRequest.CONTROL_AE_LOCK,false);
        Range<Integer> compensation=c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
        android.util.Rational step=c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
        if(compensation!=null&&step!=null&&step.doubleValue()>0)r.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,compensation.clamp(requestedCompensation));
        Range<Integer>[] rates=c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
        if(rates!=null)for(Range<Integer> rate:rates)if(rate.getLower()==30&&rate.getUpper()==30){r.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,rate);break;}

    }
    private boolean automaticSaveAllowed(){Calendar time=Calendar.getInstance();return settings.shouldSave(seat.currentState(),time.get(Calendar.HOUR_OF_DAY)*60+time.get(Calendar.MINUTE));}
    private void capturePhoto(int generation,CameraDevice device,CameraCharacteristics c,ImageReader reader){
        if(stopped||generation!=cameraGeneration||camera!=device||session==null||!gallery.enabled()||(!pendingShot&&!automaticSaveAllowed())||(!pendingShot&&lastPhotoAt>0&&SystemClock.elapsedRealtime()-lastPhotoAt<settings.photoInterval(seat.currentState())))return;
        try{
            CaptureRequest.Builder shot=device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);shot.addTarget(reader.getSurface());configureExposure(shot,c);
            shot.set(CaptureRequest.JPEG_QUALITY,(byte)95);shot.set(CaptureRequest.JPEG_ORIENTATION,0);
            session.capture(shot.build(),new CameraCaptureSession.CaptureCallback(){public void onCaptureFailed(CameraCaptureSession s,CaptureRequest r,CaptureFailure f){Log.w("BloubSensors","PHOTO_FAILED id="+cameraId+" reason="+f.getReason());}},cameraHandler);
        }catch(Exception e){Log.w("BloubSensors","Photo capture failed",e);}
    }
    private void savePreview(int generation,Size preview,boolean forced){
        if(photoSaving||(!forced&&SystemClock.elapsedRealtime()-lastPhotoAttempt<10000)||stopped||generation!=cameraGeneration||!cameraOn||!gallery.enabled()||(!forced&&!automaticSaveAllowed())||(!forced&&lastPhotoAt>0&&SystemClock.elapsedRealtime()-lastPhotoAt<settings.photoInterval(seat.currentState())))return;
        photoSaving=true;lastPhotoAttempt=SystemClock.elapsedRealtime();long capturedAt=System.currentTimeMillis(),capturedMono=SystemClock.elapsedRealtime();JSONObject annotation=seat.annotation(capturedAt,capturedMono);Bitmap frame;try{frame=texture.getBitmap(preview.getWidth(),preview.getHeight());}catch(RuntimeException e){photoSaving=false;return;}if(frame==null){photoSaving=false;return;}
        cameraHandler.post(()->{try{
            if(stopped||generation!=cameraGeneration||!gallery.enabled()||(!forced&&!automaticSaveAllowed())||(!forced&&lastPhotoAt>0&&SystemClock.elapsedRealtime()-lastPhotoAt<settings.photoInterval(seat.currentState())))return;
            java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
            if(!frame.compress(Bitmap.CompressFormat.JPEG,95,bytes))throw new IOException("Preview JPEG failed");
            lastCaptureId=gallery.saveJpeg(bytes.toByteArray(),faces,motion,cameraId,"2".equals(cameraId)?90:0,annotation);savedGeneration=generation;pendingShot=false;lastPhotoAt=SystemClock.elapsedRealtime();activity.getSharedPreferences("gallery",0).edit().putLong("last_photo_uptime",lastPhotoAt).apply();
            Log.i("BloubSensors","PHOTO_PREVIEW_SAVED id="+cameraId+" size="+preview);
        }catch(Exception e){Log.w("BloubSensors","Preview photo save failed",e);}finally{frame.recycle();photoSaving=false;}});
    }
    void quietHours(int start,int end){quietStart=start;quietEnd=end;cameraHandler.post(()->{if(!cameraOn)cameraStatus=automaticAllowed()?"等待短时观察":"夜间休息 · 声音唤醒";});}
    private boolean automaticAllowed(){if(settings.get().optBoolean("nightSaving"))return true;Calendar c=Calendar.getInstance();return CameraSchedule.allows(c.get(Calendar.HOUR_OF_DAY)*60+c.get(Calendar.MINUTE),quietStart,quietEnd,SystemClock.elapsedRealtime(),lastSoundAt,micWanted);}
    private boolean monitorPreview(){return !stopped&&monitorPreviewRequested&&SystemClock.elapsedRealtime()<monitorPreviewUntil;}
    private boolean watching(){return live()||monitorPreview();}
    private boolean live(){return !stopped&&liveRequested&&SystemClock.elapsedRealtime()<liveUntil;}
    String landmarkInfo(){return landmarks.info();}
    String remoteStatus(){try{return new JSONObject().put("live",live()).put("monitorPreview",monitorPreview()).put("cameraOn",cameraOn).put("cameraId",cameraId).put("status",cameraStatus).put("detectionEnabled",seat.detecting()).put("landmarks",new JSONObject(landmarks.info())).put("yamnetEnabled",showYamnet).put("audioLabel",audioLabel).put("audioConfidence",audioConfidence).put("faceIdentity",false).put("lastCaptureId",lastCaptureId).put("quietStart",quietStart).put("quietEnd",quietEnd).put("automaticAllowed",automaticAllowed()).put("uptimeMs",SystemClock.elapsedRealtime()).put("streamFrames",streamFrames).put("streamFps",streamFps).put("streamEncodeMs",streamEncodeMs).put("exposureMode",softwareExposure?"software_auto":"hardware_auto").put("exposureTimeNs",exposureTimeNs).put("iso",sensitivity).put("aeState",aeState).put("exposureCompensation",exposureCompensation).toString();}catch(JSONException e){return "{}";}}
    boolean remote(String action){
        if(stopped||!cameraWanted||activity.checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)return false;
        if("capture".equals(action)&&!gallery.enabled())return false;if("yamnet-on".equals(action)&&!micWanted&&!monitor.needsMicrophone())return false;
        cameraHandler.post(()->{if(stopped)return;
            if(action.startsWith("landmark-")){boolean on=action.endsWith("-on");if(action.startsWith("landmark-face-"))showFace=on;else if(action.startsWith("landmark-hand-"))showHand=on;else if(action.startsWith("landmark-holistic-"))showHolistic=on;updateLandmarkLayers();return;}
            if(action.startsWith("yamnet-")){showYamnet=action.endsWith("-on");return;}
            if("detect-on".equals(action)||"detect-off".equals(action)){seat.detection("detect-on".equals(action)&&live());return;}
            if("stop".equals(action)){seat.detection(false);showFace=showHand=showHolistic=showYamnet=false;updateLandmarkLayers();liveVersion++;liveRequested=false;liveFrame=null;if(!ownsRecorder&&!monitorPreview())closeCamera();return;}
            if("start".equals(action)){boolean watched=watching(),already=live();liveRequested=true;liveUntil=SystemClock.elapsedRealtime()+90000;if(!already){liveVersion++;liveFrame=null;streamFrames=0;streamFps=0;firstEncodedAt=0;lastLiveSource=0;if(!watched)ui.post(liveTick);}}
            if("capture".equals(action)){pendingShot=true;if(cameraOn&&activePreview!=null){Size size=activePreview;int generation=cameraGeneration;ui.post(()->savePreview(generation,size,true));return;}}
            lastCamera=0;openCamera();
        });return true;
    }
    void monitorPreview(boolean enabled,JSONObject options)throws Exception{
        final JSONObject parsed=enabled?MonitorPreviewSpec.parse(options):null;if(enabled){VideoEncoding.format(activity,cameraId,parsed);if(parsed.optBoolean("overlayYamnet")&&activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)throw new IOException("需要麦克风权限");}
        cameraHandler.post(()->{if(stopped)return;boolean watched=watching();monitorPreviewVersion++;monitorPreviewOptions=parsed;monitorPreviewRequested=enabled;monitorPreviewUntil=enabled?SystemClock.elapsedRealtime()+90000:0;monitorPreviewFrame=null;updateLandmarkLayers();ensureAudio();if(enabled){if(!watched)ui.post(liveTick);lastCamera=0;openCamera();}else if(!live()&&!ownsRecorder)closeCamera();});
    }
    StreamFrame monitorPreviewFrame(){return monitorPreview()?monitorPreviewFrame:null;}
    void monitorPreviewHeartbeat(){if(monitorPreview())monitorPreviewUntil=SystemClock.elapsedRealtime()+90000;}
    StreamFrame streamFrame(){return live()?liveFrame:null;}
    void streamHeartbeat(){if(liveRequested&&!stopped){liveUntil=SystemClock.elapsedRealtime()+90000;seat.heartbeat();}}
    private void scheduleCamera(){
        if(stopped||!cameraWanted||cameraScheduled)return;cameraScheduled=true;
        cameraHandler.postDelayed(new Runnable(){public void run(){if(stopped)return;openCamera();cameraHandler.postDelayed(this,5000);}},800);
    }
    private void closeAfterObservation(int generation,Surface target){
        if(generation!=cameraGeneration){target.release();return;}
        if(stopped||(!watching()&&!seat.previewing()&&!monitor.recording())){liveRequested=false;liveFrame=null;closeCamera();target.release();}
        else cameraHandler.postDelayed(()->closeAfterObservation(generation,target),1000);
    }
    private final Runnable liveTick=new Runnable(){public void run(){if(!watching())return;long began=SystemClock.uptimeMillis();if(cameraOn)updateLiveFrame();ui.postAtTime(this,Math.max(began+20,SystemClock.uptimeMillis()+1));}};
    private void updateLiveFrame(){
        if(!watching()||liveEncoding)return;SurfaceTexture source=texture.getSurfaceTexture();if(source==null)return;long timestamp=source.getTimestamp();if(timestamp>0&&timestamp==lastLiveSource)return;lastLiveSource=timestamp;liveEncoding=true;final int version=liveVersion,previewVersion=monitorPreviewVersion;final long capturedAt=SystemClock.elapsedRealtime();final boolean liveWanted=live(),previewWanted=monitorPreview();final JSONObject previewOptions=monitorPreviewOptions;
        Bitmap frame;try{frame=texture.getBitmap("2".equals(cameraId)?480:640,"2".equals(cameraId)?640:480);}catch(RuntimeException e){liveEncoding=false;return;}if(frame==null){liveEncoding=false;return;}
        streamHandler.post(()->{Bitmap upright=frame;try{if(!watching())return;if("2".equals(cameraId)){Matrix m=new Matrix();m.postRotate(90);upright=Bitmap.createBitmap(frame,0,0,frame.getWidth(),frame.getHeight(),m,true);}
            if(previewWanted&&previewOptions!=null&&previewVersion==monitorPreviewVersion){Bitmap monitorImage=composeMonitorPreview(upright,capturedAt,previewOptions);try{java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();monitorImage.compress(Bitmap.CompressFormat.JPEG,80,bytes);if(monitorPreview()&&previewVersion==monitorPreviewVersion)monitorPreviewFrame=new StreamFrame(bytes.toByteArray(),capturedAt,++monitorPreviewSequence,SystemClock.elapsedRealtime()-capturedAt);}finally{monitorImage.recycle();}}
            if(liveWanted&&live()&&version==liveVersion){if(seat.detecting())seat.draw(upright,capturedAt);landmarks.draw(upright,capturedAt,showFace,showHand,showHolistic);if(showYamnet)drawAudioLabel(upright,capturedAt);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();upright.compress(Bitmap.CompressFormat.JPEG,80,out);long done=SystemClock.elapsedRealtime();if(!live()||version!=liveVersion)return;streamEncodeMs=done-capturedAt;if(firstEncodedAt==0)firstEncodedAt=done;long sequence=++streamFrames;streamFps=sequence<2?0:1000.0*(sequence-1)/Math.max(1,done-firstEncodedAt);liveFrame=new StreamFrame(out.toByteArray(),capturedAt,sequence,streamEncodeMs);}
        }catch(Exception failure){Log.w("BloubMonitor","Preview frame",failure);}finally{if(upright!=frame)upright.recycle();frame.recycle();liveEncoding=false;if(watching())ui.post(this::updateLiveFrame);}});
    }
    private Bitmap composeMonitorPreview(Bitmap source,long at,JSONObject options)throws Exception{
        JSONObject format=VideoEncoding.format(activity,cameraId,options);float[] crop=ImageTransform.crop(source.getWidth()/(float)source.getHeight(),format.getInt("width")/(float)format.getInt("height"));
        Bitmap full=source.copy(Bitmap.Config.ARGB_8888,true);drawMonitorGeometry(full,at,options);Bitmap image=Bitmap.createBitmap(full,Math.round(crop[0]*full.getWidth()),Math.round(crop[1]*full.getHeight()),Math.max(1,Math.round((crop[2]-crop[0])*full.getWidth())),Math.max(1,Math.round((crop[3]-crop[1])*full.getHeight())));if(image!=full)full.recycle();
        int turn=options.optBoolean("rotationEnabled")?options.optInt("rotation"):0;boolean mirror=options.optBoolean("mirror");if(turn!=0||mirror){Matrix m=new Matrix();m.postRotate(turn);if(mirror)m.postScale(-1,1);Bitmap transformed=Bitmap.createBitmap(image,0,0,image.getWidth(),image.getHeight(),m,true);if(transformed!=image)image.recycle();image=transformed;}
        drawMonitorHud(image,at,System.currentTimeMillis(),options,crop);return image;
    }
    private void drawMonitorGeometry(Bitmap image,long at,JSONObject options){if(options.optBoolean("overlayObjects"))seat.draw(image,at,true,false);landmarks.draw(image,at,options.optBoolean("overlayFaces"),options.optBoolean("overlayHands"),options.optBoolean("overlayHolistic"),false);}
    private void drawMonitorHud(Bitmap image,long at,long wall,JSONObject options,float[] crop){int turn=options.optBoolean("rotationEnabled")?options.optInt("rotation"):0;boolean mirror=options.optBoolean("mirror");if(options.optBoolean("overlayObjects"))seat.drawLabels(image,at,turn,mirror,crop);landmarks.drawLabels(image,at,options.optBoolean("overlayFaces"),options.optBoolean("overlayHands"),options.optBoolean("overlayHolistic"),turn,mirror,crop);if(options.optBoolean("timestamp"))drawHud(image,new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(new Date(wall)),0);if(options.optBoolean("overlayYamnet"))drawAudioLabel(image,at);}

    void monitorChanged(){cameraHandler.post(()->{closeCamera();lastCamera=0;openCamera();});}
    void seatWake(){cameraHandler.post(this::openCamera);}
    private void openCamera(){
        if(stopped||!cameraWanted||activity.checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED||cameraOn||opening||camera!=null)return;
        long now=SystemClock.elapsedRealtime();if(!watching()&&!pendingShot&&!seat.previewing()&&!monitor.enabled()){if(!settings.scheduledObservation())return;if(!automaticAllowed()){cameraStatus="夜间休息 · 声音唤醒";return;}if(lastCamera>0&&now-lastCamera<settings.observationInterval())return;}
        opening=true;final int generation=++cameraGeneration;cameraStatus="正在短时观察";lastCamera=SystemClock.elapsedRealtime();
        try{
            CameraManager manager=(CameraManager)activity.getSystemService(Context.CAMERA_SERVICE);
            if(!Arrays.asList(manager.getCameraIdList()).contains(cameraId))throw new IOException("Camera absent");
            final CameraCharacteristics c=manager.getCameraCharacteristics(cameraId);
            softwareExposure=false;int[] capabilities=c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);if("2".equals(cameraId)&&capabilities!=null)for(int capability:capabilities)if(capability==CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)softwareExposure=true;
            exposureRange=c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);isoRange=c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);if(exposureRange==null||isoRange==null)softwareExposure=false;exposureGain=activity.getSharedPreferences("sensors",0).getLong("ae_gain_"+cameraId,800000000L);
            compensationRange=c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);android.util.Rational step=c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);compensationStep=step==null?.5:step.doubleValue();requestedCompensation=compensationRange==null?0:compensationRange.clamp(activity.getSharedPreferences("sensors",0).getInt("ae_bias_"+cameraId,0));cameraOpenedAt=SystemClock.elapsedRealtime();lastMeter=0;
            StreamConfigurationMap map=c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if(map==null)throw new IOException("Missing stream map");
            final Size preview=chooseSize(map.getOutputSizes(SurfaceTexture.class),"2".equals(cameraId)?.75:4.0/3,1300000);
            // N5D camera 3 advertises JPEG but its HAL stalls with a JPEG target; use its full preview.
            Size[] jpegSizes=("3".equals(cameraId)||monitor.enabled()||!gallery.enabled())?null:map.getOutputSizes(ImageFormat.JPEG);final Size picture=jpegSizes==null||jpegSizes.length==0?null:chooseSize(jpegSizes,preview.getWidth()/(double)preview.getHeight(),2100000);
            SurfaceTexture t=texture.getSurfaceTexture();if(t==null){opening=false;return;}t.setDefaultBufferSize(preview.getWidth(),preview.getHeight());
            final ImageReader photos=picture==null?null:ImageReader.newInstance(picture.getWidth(),picture.getHeight(),ImageFormat.JPEG,2);photoReader=photos;
            if(photos!=null)photos.setOnImageAvailableListener(reader->{Image image=null;try{
                image=reader.acquireLatestImage();if(image==null||stopped||generation!=cameraGeneration||savedGeneration==generation||!gallery.enabled()||(!pendingShot&&!automaticSaveAllowed()))return;
                java.nio.ByteBuffer buffer=image.getPlanes()[0].getBuffer();byte[] jpeg=new byte[buffer.remaining()];buffer.get(jpeg);
                gallery.saveJpeg(jpeg,faces,motion,cameraId,"2".equals(cameraId)?90:0,seat.annotation(System.currentTimeMillis(),SystemClock.elapsedRealtime()));savedGeneration=generation;pendingShot=false;lastPhotoAt=SystemClock.elapsedRealtime();activity.getSharedPreferences("gallery",0).edit().putLong("last_photo_uptime",lastPhotoAt).apply();
                Log.i("BloubSensors","PHOTO_SAVED id="+cameraId+" size="+picture);
            }catch(Exception e){Log.w("BloubSensors","Photo save failed",e);}finally{if(image!=null)image.close();}},cameraHandler);
            Log.i("BloubSensors","CAMERA_CONFIG id="+cameraId+" preview="+preview+" jpeg="+picture);
            activePreview=preview;final Surface target=new Surface(t);previewSurface=target;Surface video=null;
            if(monitor.enabled()){Size[] videoSizes=map.getOutputSizes(android.media.MediaRecorder.class);if(videoSizes!=null&&videoSizes.length>0)try{Size videoSize=VideoEncoding.size(activity,cameraId,monitor.options());video=monitor.prepare(videoSize,"2".equals(cameraId)?90:0,new VideoCompositor.Overlay(){
                final float sourceRatio="2".equals(cameraId)?preview.getHeight()/(float)preview.getWidth():preview.getWidth()/(float)preview.getHeight();
                final float targetRatio="2".equals(cameraId)?videoSize.getHeight()/(float)videoSize.getWidth():videoSize.getWidth()/(float)videoSize.getHeight();
                final float[] crop=ImageTransform.crop(sourceRatio,targetRatio);
                public void geometry(Bitmap image,long mono){JSONObject options=monitor.recordingOptions();if(Math.abs(sourceRatio-targetRatio)<.001f){drawMonitorGeometry(image,mono,options);return;}Bitmap full=Bitmap.createBitmap(640,Math.round(640/sourceRatio),Bitmap.Config.ARGB_8888);try{drawMonitorGeometry(full,mono,options);Rect from=new Rect(Math.round(crop[0]*full.getWidth()),Math.round(crop[1]*full.getHeight()),Math.round(crop[2]*full.getWidth()),Math.round(crop[3]*full.getHeight()));new Canvas(image).drawBitmap(full,from,new Rect(0,0,image.getWidth(),image.getHeight()),new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));}finally{full.recycle();}}
                public void hud(Bitmap image,long wall,long mono){drawMonitorHud(image,mono,wall,monitor.recordingOptions(),crop);}
            });ownsRecorder=video!=null;}catch(Exception e){if(ownsRecorder){monitor.failed();ownsRecorder=false;}Log.w("BloubMonitor","Video preparation failed",e);}}
            final Surface videoTarget=video;
            final java.util.List<Surface> targets=new ArrayList<>();targets.add(target);if(photos!=null)targets.add(photos.getSurface());if(videoTarget!=null)targets.add(videoTarget);
            manager.openCamera(cameraId,new CameraDevice.StateCallback(){
                public void onOpened(CameraDevice d){
                    if(stopped||generation!=cameraGeneration){d.close();target.release();return;}opening=false;camera=d;cameraOn=true;
                    try{d.createCaptureSession(targets,new CameraCaptureSession.StateCallback(){
                        public void onConfigured(CameraCaptureSession s){
                            if(stopped||generation!=cameraGeneration||camera!=d){s.close();return;}session=s;
                            try{
                                CaptureRequest.Builder r=d.createCaptureRequest(videoTarget==null?CameraDevice.TEMPLATE_PREVIEW:CameraDevice.TEMPLATE_RECORD);r.addTarget(target);if(videoTarget!=null)r.addTarget(videoTarget);
                                configureExposure(r,c);
                                Integer maxRegions=c.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE);Rect active=c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                                if(maxRegions!=null&&maxRegions>0&&active!=null){float[] roi=seat.region();float l="2".equals(cameraId)?roi[1]:roi[0],top="2".equals(cameraId)?1-roi[2]:roi[1],right="2".equals(cameraId)?roi[3]:roi[2],bottom="2".equals(cameraId)?1-roi[0]:roi[3];Rect meter=new Rect(active.left+Math.round(l*active.width()),active.top+Math.round(top*active.height()),active.left+Math.round(right*active.width()),active.top+Math.round(bottom*active.height()));r.set(CaptureRequest.CONTROL_AE_REGIONS,new android.hardware.camera2.params.MeteringRectangle[]{new android.hardware.camera2.params.MeteringRectangle(meter,1000)});}
                                repeatingBuilder=r;
                                s.setRepeatingRequest(r.build(),new CameraCaptureSession.CaptureCallback(){
                                    private boolean logged;
                                    public void onCaptureCompleted(CameraCaptureSession cs,CaptureRequest cr,TotalCaptureResult result){Long time=result.get(CaptureResult.SENSOR_EXPOSURE_TIME);Integer iso=result.get(CaptureResult.SENSOR_SENSITIVITY),state=result.get(CaptureResult.CONTROL_AE_STATE),ev=result.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION);if(time!=null)exposureTimeNs=time;if(iso!=null)sensitivity=iso;if(state!=null)aeState=state;if(ev!=null)exposureCompensation=ev;if(!logged){logged=true;Log.i("BloubSensors","CAMERA_EXPOSURE id="+cameraId+" ns="+result.get(CaptureResult.SENSOR_EXPOSURE_TIME)+" iso="+result.get(CaptureResult.SENSOR_SENSITIVITY)+" duration="+result.get(CaptureResult.SENSOR_FRAME_DURATION));}}
                                },cameraHandler);if(videoTarget!=null){monitor.start();cameraHandler.postDelayed(()->{if(!stopped&&generation==cameraGeneration&&monitor.recording()){closeCamera();if(monitor.enabled())openCamera();}},monitor.segmentSeconds()*1000L);}if(photos!=null)cameraHandler.postDelayed(()->capturePhoto(generation,d,c,photos),2400);ui.postDelayed(()->savePreview(generation,preview,pendingShot),3800);cameraStatus=live()?"网页实时观察":"相机 "+cameraId+" · 短时观察";
                            }catch(Exception e){cameraStatus="相机配置失败";closeCamera();}
                        }
                        public void onConfigureFailed(CameraCaptureSession s){cameraStatus="相机配置失败";closeCamera();}
                    },cameraHandler);}catch(Exception e){cameraStatus="相机会话失败";closeCamera();}
                }
                public void onDisconnected(CameraDevice d){d.close();if(generation==cameraGeneration){closeCamera();cameraStatus="相机已断开";}}
                public void onError(CameraDevice d,int error){d.close();if(generation==cameraGeneration){closeCamera();cameraStatus="相机不可用 "+error;}}
            },cameraHandler);
            cameraHandler.postDelayed(()->closeAfterObservation(generation,target),seat.enabled()?24500:5500);
        }catch(Exception e){opening=false;cameraStatus="相机不可用";if(ownsRecorder){monitor.failed();ownsRecorder=false;}Log.w("BloubSensors","Camera",e);}
    }
    private void observeFrame(){
        if(stopped||!cameraOn)return;cameraFrames++;long now=SystemClock.elapsedRealtime();updateLandmarkLayers();
        if(landmarks.readyForFrame(now)){Bitmap frame=null;try{frame=texture.getBitmap("2".equals(cameraId)?480:640,"2".equals(cameraId)?640:480);if(frame!=null){if("2".equals(cameraId)){Matrix transform=new Matrix();transform.postRotate(90);Bitmap upright=Bitmap.createBitmap(frame,0,0,frame.getWidth(),frame.getHeight(),transform,true);frame.recycle();frame=upright;}landmarks.offer(frame,now);}}catch(RuntimeException failure){if(frame!=null)frame.recycle();Log.w("BloubLandmarks","Frame read",failure);}}
        if(monitor.recording()&&monitor.recordingOptions().optBoolean("motionOnly")&&now-lastMotionFrame>=500){lastMotionFrame=now;Bitmap sample=null;try{sample=texture.getBitmap(32,24);if(sample!=null){int[] pixels=new int[768];sample.getPixels(pixels,0,32,0,0,32,24);for(int i=0;i<pixels.length;i++){int pixel=pixels[i];pixels[i]=(int)(.299*((pixel>>16)&255)+.587*((pixel>>8)&255)+.114*(pixel&255));}if(changes.changed(pixels,now))monitor.motion(System.currentTimeMillis());}}catch(RuntimeException failure){Log.w("BloubMonitor","Motion sample",failure);}finally{if(sample!=null)sample.recycle();}}
        if(now-cameraOpenedAt>3300&&activePreview!=null&&gallery.enabled()&&automaticSaveAllowed()&&(lastPhotoAt==0||SystemClock.elapsedRealtime()-lastPhotoAt>=settings.photoInterval(seat.currentState())))savePreview(cameraGeneration,activePreview,false);
        long seatNow=SystemClock.elapsedRealtime();if(seat.reserve(seatNow)){Bitmap frame=null;try{frame=texture.getBitmap("2".equals(cameraId)?240:320,"2".equals(cameraId)?320:240);}catch(RuntimeException e){Log.w("BloubSeat","Frame read",e);}if(frame==null)seat.cancelFrame();else seat.observe(frame,cameraId,seatNow);}
        if(now-cameraOpenedAt>1500&&now-lastMeter>800){lastMeter=now;meterExposure();}if(processing||now-lastBitmap<(live()?1000:450))return;
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
    private void meterExposure(){
        if((!softwareExposure&&(compensationRange==null||compensationStep<=0))||repeatingBuilder==null)return;Bitmap image;try{image=texture.getBitmap(48,64);}catch(RuntimeException ignored){return;}if(image==null)return;
        float[] roi=seat.region();int[] pixels=new int[48*64];image.getPixels(pixels,0,48,0,0,48,64);image.recycle();java.util.ArrayList<Integer> values=new java.util.ArrayList<>();
        for(int y=0;y<64;y++)for(int x=0;x<48;x++){double u="2".equals(cameraId)?1-(y+.5)/64:(x+.5)/48,v="2".equals(cameraId)?(x+.5)/48:(y+.5)/64;if(u<roi[0]||u>roi[2]||v<roi[1]||v>roi[3])continue;int pixel=pixels[y*48+x];values.add((int)(.299*((pixel>>16)&255)+.587*((pixel>>8)&255)+.114*(pixel&255)));}
        int[] luma=new int[values.size()];for(int i=0;i<luma.length;i++)luma[i]=values.get(i);int change=ExposureMeter.correction(luma);if(change==0)return;
        final int generation=cameraGeneration;cameraHandler.post(()->{if(stopped||generation!=cameraGeneration||session==null||repeatingBuilder==null)return;if(softwareExposure){long next=ExposureMeter.nextGain(exposureGain,change,exposureRange.getLower()*isoRange.getLower(),Math.min(20000000L,exposureRange.getUpper())*Math.min(1600,isoRange.getUpper()));if(next==exposureGain)return;try{exposureGain=next;applyGain(repeatingBuilder);session.setRepeatingRequest(repeatingBuilder.build(),exposureCallback,cameraHandler);activity.getSharedPreferences("sensors",0).edit().putLong("ae_gain_"+cameraId,exposureGain).apply();}catch(Exception e){Log.w("BloubSensors","Automatic metering",e);}return;}
            int lower=Math.max(compensationRange.getLower(),(int)Math.ceil(-2/compensationStep)),upper=Math.min(compensationRange.getUpper(),(int)Math.floor(2/compensationStep)),next=Math.max(lower,Math.min(upper,requestedCompensation+change));if(next==requestedCompensation)return;
            try{repeatingBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,next);session.setRepeatingRequest(repeatingBuilder.build(),exposureCallback,cameraHandler);requestedCompensation=next;activity.getSharedPreferences("sensors",0).edit().putInt("ae_bias_"+cameraId,next).apply();}catch(Exception e){Log.w("BloubSensors","Exposure correction",e);}
        });
    }
    private void applyGain(CaptureRequest.Builder request){
        long time=exposureRange.clamp(Math.min(10000000L,Math.max(exposureRange.getLower(),exposureGain/isoRange.getLower())));int iso=isoRange.clamp((int)Math.max(isoRange.getLower(),Math.min(1600,exposureGain/time)));time=exposureRange.clamp(Math.min(20000000L,Math.max(exposureRange.getLower(),exposureGain/iso)));
        request.set(CaptureRequest.SENSOR_EXPOSURE_TIME,time);request.set(CaptureRequest.SENSOR_SENSITIVITY,iso);request.set(CaptureRequest.SENSOR_FRAME_DURATION,33350000L);
    }
    private final CameraCaptureSession.CaptureCallback exposureCallback=new CameraCaptureSession.CaptureCallback(){public void onCaptureCompleted(CameraCaptureSession s,CaptureRequest r,TotalCaptureResult result){Long time=result.get(CaptureResult.SENSOR_EXPOSURE_TIME);Integer iso=result.get(CaptureResult.SENSOR_SENSITIVITY),state=result.get(CaptureResult.CONTROL_AE_STATE),ev=result.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION);if(time!=null)exposureTimeNs=time;if(iso!=null)sensitivity=iso;if(state!=null)aeState=state;if(ev!=null)exposureCompensation=ev;}};
    private void updateLandmarkLayers(){JSONObject c=monitor.recordingOptions();boolean recording=monitor.enabled();JSONObject preview=monitorPreview()&&monitorPreviewOptions!=null?monitorPreviewOptions:new JSONObject();landmarks.layers(showFace||(recording&&c.optBoolean("overlayFaces"))||preview.optBoolean("overlayFaces"),showHand||(recording&&c.optBoolean("overlayHands"))||preview.optBoolean("overlayHands"),showHolistic||(recording&&c.optBoolean("overlayHolistic"))||preview.optBoolean("overlayHolistic"));seat.recordingDemand((recording&&c.optBoolean("overlayObjects"))||preview.optBoolean("overlayObjects"));}
    private void drawAudioLabel(Bitmap image,long mono){String label=lastAudioAt>0&&mono-lastAudioAt<3000&&!audioLabel.isEmpty()?"YAMNet: "+audioLabel+" "+Math.round(audioConfidence*100)+"%":"YAMNet: 等待声音";drawHud(image,label,1);}
    private static void drawHud(Bitmap image,String text,int row){Canvas canvas=new Canvas(image);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);float scale=image.getWidth()/640f,height=28*scale;paint.setTextSize(16*scale);paint.setTypeface(Typeface.MONOSPACE);float x=12*scale,y=12*scale+row*(height+6*scale),width=Math.min(image.getWidth()-x*2,paint.measureText(text)+16*scale);paint.setColor(Color.argb(175,12,18,25));canvas.drawRoundRect(new RectF(x,y,x+width,y+height),4*scale,4*scale,paint);paint.setColor(Color.WHITE);canvas.drawText(text,x+8*scale,y+20*scale,paint);}
    private void closeCamera(){
        repeatingBuilder=null;
        if(session!=null)try{session.stopRepeating();}catch(Exception ignored){}if(ownsRecorder){monitor.finish();ownsRecorder=false;}
        cameraGeneration++;opening=false;ImageReader oldReader=photoReader;photoReader=null;if(oldReader!=null)oldReader.close();cameraOn=false;faces=0;motion=0;previousPixels=null;
        CameraCaptureSession s=session;session=null;if(s!=null)s.close();
        CameraDevice d=camera;camera=null;if(d!=null)d.close();Surface oldSurface=previewSurface;previewSurface=null;if(oldSurface!=null)oldSurface.release();
        if(!stopped)cameraStatus=automaticAllowed()?"相机 "+cameraId+" · 等待观察":"夜间休息 · 声音唤醒";
    }
    void stop(){
        stopped=true;monitorPreviewRequested=false;monitorPreviewFrame=null;landmarks.close();seat.recordingDemand(false);seat.suspendObservation();seat.detection(false);liveVersion++;liveRequested=false;liveFrame=null;pendingShot=false;ui.removeCallbacks(publish);
        java.lang.Process p=tofHelper;if(p!=null){try{p.getOutputStream().close();}catch(Exception ignored){}p.destroy();}
        cameraHandler.removeCallbacksAndMessages(null);cameraHandler.post(()->{closeCamera();cameraThread.quitSafely();});streamHandler.post(()->streamThread.quitSafely());
        // Audio reads finish in ~64ms, then release on their own worker.
    }
}
