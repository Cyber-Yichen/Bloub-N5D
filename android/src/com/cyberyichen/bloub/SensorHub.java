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
    private final HandlerThread cameraThread=new HandlerThread("BloubCamera");private final Handler cameraHandler;
    private volatile boolean stopped;private final boolean micWanted,cameraWanted,tofWanted;
    private volatile long lastAudioAt,lastDistanceAt;
    private volatile AudioRecord recorder;private volatile java.lang.Process tofHelper;
    private volatile float level,music,speech,motion,faceX,faceY;private volatile int distance=-1,faces;
    private volatile String micStatus="关闭",cameraStatus="关闭",tofStatus="关闭";
    private volatile boolean cameraOn,processing;
    private volatile CameraDevice camera;private volatile CameraCaptureSession session;
    private long lastBitmap,cameraFrames;private int cameraGeneration;private int[] previousPixels;private long lastCamera;
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
        this.activity=activity;this.texture=texture;this.output=output;
        micWanted=mic;cameraWanted=cam;tofWanted=tof;
        cameraThread.start();cameraHandler=new Handler(cameraThread.getLooper());
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
            short[] chunk=new short[1024],ring=new short[15600];int cursor=0,total=0;long classified=0;
            audio.startRecording();if(model!=null)micStatus="YAMNet 本地音乐识别";else micStatus="仅声音能量";
            while(!stopped){
                int n=audio.read(chunk,0,chunk.length);if(n<=0)throw new IOException("Audio read "+n);
                double square=0;for(int i=0;i<n;i++){float v=chunk[i]/32768f;square+=v*v;ring[cursor]=chunk[i];cursor=(cursor+1)%ring.length;}total=Math.min(ring.length,total+n);
                level=(float)Math.min(1,Math.sqrt(square/n)*12);lastAudioAt=SystemClock.elapsedRealtime();
                long now=SystemClock.elapsedRealtime();
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
    private void scheduleCamera(){
        if(stopped||!cameraWanted)return;
        cameraHandler.postDelayed(this::openCamera,800);
    }
    private void openCamera(){
        if(stopped||cameraOn||camera!=null)return;final int generation=++cameraGeneration;cameraStatus="正在短时观察";lastCamera=SystemClock.elapsedRealtime();
        try{
            CameraManager manager=(CameraManager)activity.getSystemService(Context.CAMERA_SERVICE);
            if(!Arrays.asList(manager.getCameraIdList()).contains("2"))throw new IOException("Camera 2 absent");
            CameraCharacteristics c=manager.getCameraCharacteristics("2");
            StreamConfigurationMap map=c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if(map==null||!Arrays.asList(map.getOutputSizes(SurfaceTexture.class)).contains(new Size(480,640)))throw new IOException("Unsupported preview size");
            SurfaceTexture t=texture.getSurfaceTexture();if(t==null)return;t.setDefaultBufferSize(480,640);
            final Surface target=new Surface(t);
            manager.openCamera("2",new CameraDevice.StateCallback(){
                public void onOpened(CameraDevice d){
                    if(stopped||generation!=cameraGeneration){d.close();target.release();return;}camera=d;cameraOn=true;
                    try{d.createCaptureSession(Collections.singletonList(target),new CameraCaptureSession.StateCallback(){
                        public void onConfigured(CameraCaptureSession s){
                            if(stopped||camera!=d){s.close();return;}session=s;
                            try{
                                CaptureRequest.Builder r=d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);r.addTarget(target);
                                r.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);
                                r.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_OFF);
                                r.set(CaptureRequest.SENSOR_EXPOSURE_TIME,10000000L);r.set(CaptureRequest.SENSOR_SENSITIVITY,400);
                                r.set(CaptureRequest.SENSOR_FRAME_DURATION,33350000L);
                                s.setRepeatingRequest(r.build(),new CameraCaptureSession.CaptureCallback(){
                                    private boolean logged;
                                    public void onCaptureCompleted(CameraCaptureSession cs,CaptureRequest cr,TotalCaptureResult result){if(!logged){logged=true;Log.i("BloubSensors","CAMERA_EXPOSURE ns="+result.get(CaptureResult.SENSOR_EXPOSURE_TIME)+" iso="+result.get(CaptureResult.SENSOR_SENSITIVITY)+" duration="+result.get(CaptureResult.SENSOR_FRAME_DURATION));}}
                                },cameraHandler);cameraStatus="本地观察 · 不保存画面";
                            }catch(Exception e){cameraStatus="相机配置失败";closeCamera();}
                        }
                        public void onConfigureFailed(CameraCaptureSession s){cameraStatus="相机配置失败";closeCamera();}
                    },cameraHandler);}catch(Exception e){cameraStatus="相机会话失败";closeCamera();}
                }
                public void onDisconnected(CameraDevice d){d.close();if(camera==d)camera=null;cameraOn=false;cameraStatus="相机已断开";}
                public void onError(CameraDevice d,int error){d.close();if(camera==d)camera=null;cameraOn=false;cameraStatus="相机不可用 "+error;}
            },cameraHandler);
            cameraHandler.postDelayed(()->{closeCamera();target.release();},5500);
            cameraHandler.postDelayed(this::openCamera,120000);
        }catch(Exception e){cameraStatus="相机不可用";Log.w("BloubSensors","Camera",e);}
    }
    private void observeFrame(){
        if(stopped||!cameraOn)return;cameraFrames++;
        long now=SystemClock.elapsedRealtime();if(processing||now-lastBitmap<450)return;
        lastBitmap=now;processing=true;
        Bitmap source=texture.getBitmap(240,320);if(source==null){processing=false;return;}
        cameraHandler.post(()->{
            try{
                if(stopped)return;
                Bitmap small=Bitmap.createScaledBitmap(source,30,40,false);int[] pixels=new int[1200];small.getPixels(pixels,0,30,0,0,30,40);small.recycle();
                if(previousPixels!=null){double delta=0;for(int i=0;i<pixels.length;i++)delta+=Math.abs((pixels[i]&255)-(previousPixels[i]&255));motion=(float)Math.min(1,delta/pixels.length/25);}
                previousPixels=pixels;
                Bitmap gray=source.copy(Bitmap.Config.RGB_565,false);FaceDetector.Face[] found=new FaceDetector.Face[1];
                faces=new FaceDetector(240,320,1).findFaces(gray,found);
                if(faces>0&&found[0].confidence()>.35){PointF mid=new PointF();found[0].getMidPoint(mid);faceX=mid.x/240-.5f;faceY=mid.y/320-.5f;}else faces=0;
                gray.recycle();Log.d("BloubSensors","CAMERA_FRAME faces="+faces+" motion="+motion);
            }catch(Throwable e){Log.w("BloubSensors","Camera analysis",e);}
            finally{source.recycle();processing=false;}
        });
    }
    private void closeCamera(){
        cameraGeneration++;cameraOn=false;faces=0;motion=0;previousPixels=null;
        CameraCaptureSession s=session;session=null;if(s!=null)s.close();
        CameraDevice d=camera;camera=null;if(d!=null)d.close();
        if(!stopped)cameraStatus="休息中 · 每两分钟观察五秒";
    }
    void stop(){
        stopped=true;ui.removeCallbacks(publish);
        java.lang.Process p=tofHelper;if(p!=null){try{p.getOutputStream().close();}catch(Exception ignored){}p.destroy();}
        cameraHandler.removeCallbacksAndMessages(null);cameraHandler.post(()->{closeCamera();cameraThread.quitSafely();});
        // Audio reads finish in ~64ms, then release on their own worker.
    }
}
