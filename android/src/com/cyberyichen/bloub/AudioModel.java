package com.cyberyichen.bloub;

import android.content.Context;
import android.util.Log;
import java.io.*;
import java.nio.*;
import java.util.*;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.nnapi.NnApiDelegate;

/** Official YAMNet mobile classifier. Raw samples and inference remain in memory. */
final class AudioModel implements AutoCloseable {
    private Interpreter interpreter;
    private ByteBuffer modelBuffer;
    private NnApiDelegate delegate;
    private final ByteBuffer input;
    private final Map<Integer,Object> outputs=new HashMap<>();
    final int samples;
    AudioModel(Context context)throws IOException {
        ByteArrayOutputStream data=new ByteArrayOutputStream();
        try(InputStream stream=context.getAssets().open("yamnet.tflite")){
            byte[] buffer=new byte[8192];int n;while((n=stream.read(buffer))!=-1)data.write(buffer,0,n);
        }
        byte[] bytes=data.toByteArray();
        ByteBuffer model=ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.nativeOrder());model.put(bytes);model.rewind();
        modelBuffer=model;interpreter=createInterpreter(model);
        int count=1;for(int d:interpreter.getInputTensor(0).shape())count*=d;samples=count;
        if(samples!=15600)throw new IOException("Unexpected YAMNet input: "+samples);
        input=ByteBuffer.allocateDirect(samples*4).order(ByteOrder.nativeOrder());
        for(int i=0;i<interpreter.getOutputTensorCount();i++){
            outputs.put(i,ByteBuffer.allocateDirect(interpreter.getOutputTensor(i).numBytes()).order(ByteOrder.nativeOrder()));
        }
        if(interpreter.getOutputTensor(0).numElements()!=521)throw new IOException("Unexpected YAMNet labels");
        float[] silent;try{silent=classify(new short[samples],0);}catch(Throwable error){close();throw new IOException("YAMNet self-test",error);}
        Log.i("BloubSensors","YAMNET_READY samples="+samples+" outputs="+outputs.size()+" silenceMusic="+silent[0]);
    }
    private Interpreter createInterpreter(ByteBuffer model){
        Interpreter candidate=null;
        if(android.os.Build.VERSION.SDK_INT>=29)try{
            delegate=new NnApiDelegate(new NnApiDelegate.Options().setAcceleratorName("apunn")
                .setUseNnapiCpu(false).setExecutionPreference(NnApiDelegate.Options.EXECUTION_PREFERENCE_LOW_POWER));
            candidate=new Interpreter(model,new Interpreter.Options().setNumThreads(2).addDelegate(delegate));
            Map<Integer,Object> check=new HashMap<>();
            for(int i=0;i<candidate.getOutputTensorCount();i++)check.put(i,ByteBuffer.allocateDirect(candidate.getOutputTensor(i).numBytes()).order(ByteOrder.nativeOrder()));
            candidate.runForMultipleInputsOutputs(new Object[]{ByteBuffer.allocateDirect(15600*4).order(ByteOrder.nativeOrder())},check);
            if(delegate.hasErrors())throw new IOException("NNAPI errno "+delegate.getNnapiErrno());
            Log.i("BloubSensors","NNAPI_APUNN_ACCEPTED; actual delegated partitions require runtime evidence");
            return candidate;
        }catch(Throwable unavailable){
            Log.i("BloubSensors","NNAPI_APUNN_UNAVAILABLE CPU fallback: "+unavailable);
            if(candidate!=null)candidate.close();if(delegate!=null){delegate.close();delegate=null;}
        }
        model.rewind();return new Interpreter(model,new Interpreter.Options().setNumThreads(2));
    }
    float[] classify(short[] ring,int cursor){
        input.clear();for(int i=0;i<samples;i++)input.putFloat(ring[(cursor+i)%samples]/32768f);input.rewind();
        for(Object b:outputs.values())((ByteBuffer)b).clear();
        try{interpreter.runForMultipleInputsOutputs(new Object[]{input},outputs);}
        catch(RuntimeException failure){
            if(delegate==null)throw failure;
            Log.w("BloubSensors","NNAPI runtime failure; switch to CPU",failure);
            interpreter.close();delegate.close();delegate=null;modelBuffer.rewind();
            interpreter=new Interpreter(modelBuffer,new Interpreter.Options().setNumThreads(2));
            input.rewind();for(Object b:outputs.values())((ByteBuffer)b).clear();
            interpreter.runForMultipleInputsOutputs(new Object[]{input},outputs);
        }
        ByteBuffer result=(ByteBuffer)outputs.get(0);result.rewind();FloatBuffer scores=result.asFloatBuffer();
        // Class 132 is Music; 133 is Musical instrument in the bundled official map.
        float music=Math.max(scores.get(132),scores.get(133));
        return new float[]{music,scores.get(0)};
    }
    public void close(){interpreter.close();if(delegate!=null)delegate.close();}
}
