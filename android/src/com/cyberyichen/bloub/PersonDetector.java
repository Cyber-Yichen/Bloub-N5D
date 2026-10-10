package com.cyberyichen.bloub;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import java.io.*;
import java.nio.*;
import java.util.*;
import org.tensorflow.lite.*;
/** Official EfficientDet-Lite0 uint8, CPU. All labels may be drawn; only person is used for seat occupancy. */
final class PersonDetector implements AutoCloseable {
  private final ByteBuffer modelData;private final Interpreter model;private final ByteBuffer input;private final HashMap<Integer,Object> outputs=new HashMap<>();
  private final float[][][] boxes;private final float[][] classes,scores;private final float[] count=new float[1];
  private final int width,height;private final ArrayList<String> labels=new ArrayList<>();
  static final class Box {final float left,top,right,bottom,score;final int classId;final String label;Box(float[] b,float s,int id,String label){top=clamp(b[0]);left=clamp(b[1]);bottom=clamp(b[2]);right=clamp(b[3]);score=s;classId=id;this.label=label;}private static float clamp(float v){return Math.max(0,Math.min(1,v));}}
  java.util.List<Box> detections=Collections.emptyList();
  double inferenceMs;
  PersonDetector(Context context)throws IOException{
    byte[] bytes;try(InputStream in=context.getAssets().open("seat-detector.tflite");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)out.write(b,0,n);bytes=out.toByteArray();}
    modelData=ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.nativeOrder());modelData.put(bytes).rewind();
    model=new Interpreter(modelData,new Interpreter.Options().setNumThreads(2));
    model.allocateTensors();try(BufferedReader reader=new BufferedReader(new InputStreamReader(context.getAssets().open("seat-labels.txt"),"UTF-8"))){String line;while((line=reader.readLine())!=null)labels.add(line);}
    int[] shape=model.getInputTensor(0).shape();
    if(shape.length!=4||shape[3]!=3||model.getInputTensor(0).dataType()!=DataType.UINT8||model.getOutputTensorCount()!=4){model.close();throw new IOException("Unexpected detector signature");}
    height=shape[1];width=shape[2];
    int detections=model.getOutputTensor(0).shape()[1];boxes=new float[1][detections][4];classes=new float[1][detections];scores=new float[1][detections];
    outputs.put(0,boxes);outputs.put(1,classes);outputs.put(2,scores);outputs.put(3,count);
    input=ByteBuffer.allocateDirect(height*width*3).order(ByteOrder.nativeOrder());
    Log.i("BloubSeat","MODEL_READY bytes="+bytes.length+" input="+Arrays.toString(shape)+" detections="+detections+" CPU_THREADS=2");
  }
  double person(Bitmap upright,float[] roi){
    Bitmap resized=Bitmap.createScaledBitmap(upright,width,height,true);int[] pixels=new int[width*height];resized.getPixels(pixels,0,width,0,0,width,height);if(resized!=upright)resized.recycle();
    input.clear();for(int p:pixels)input.put((byte)(p>>16)).put((byte)(p>>8)).put((byte)p);input.rewind();
    long start=android.os.SystemClock.elapsedRealtime();model.runForMultipleInputsOutputs(new Object[]{input},outputs);inferenceMs=android.os.SystemClock.elapsedRealtime()-start;
    ArrayList<Box> detected=new ArrayList<>();double best=0;int n=Math.min(scores[0].length,Math.max(0,(int)count[0]));
    for(int j=0;j<n;j++){int category=Math.round(classes[0][j]);if(!Float.isFinite(scores[0][j])||category<0||category>=labels.size())continue;float[] b=boxes[0][j];if(scores[0][j]>=.35&&!"???".equals(labels.get(category)))detected.add(new Box(b,scores[0][j],category,labels.get(category)));if(category!=0)continue;
      double area=Math.max(0,b[3]-b[1])*Math.max(0,b[2]-b[0]);double intersection=Math.max(0,Math.min(b[3],roi[2])-Math.max(b[1],roi[0]))*Math.max(0,Math.min(b[2],roi[3])-Math.max(b[0],roi[1]));
      double region=(roi[2]-roi[0])*(roi[3]-roi[1]);if(intersection/Math.max(.0001,Math.min(area,region))>=.35)best=Math.max(best,scores[0][j]);
    }
    detections=Collections.unmodifiableList(detected);return Math.max(0,Math.min(1,best));
  }
  public void close(){model.close();}
}
