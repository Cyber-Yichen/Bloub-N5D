package com.cyberyichen.bloub;

import android.content.*;
import android.os.*;
import android.util.Log;
import android.util.SparseArray;
import org.json.*;

/** Short-lived, renewable ownership of RingStudio. Each story is one session. */
final class Lights {
    interface Status {void show(String code);}
    interface Reply {void accept(Bundle data);}
    private final Context context;
    private final Status status;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Client client;
    private boolean active,blocked,stopped,acquiring,inFlight;
    private String session;
    private long sequence,lastUpdate,lastState,acquiredAt;
    private final double[] previousRgb=new double[72],previousWhite=new double[24];
    private static final int[] RGB_GROUPS={2,21,17,13,9,5,1,4,8,12,16,20,0,23,19,15,11,7,3,22,18,14,10,6};
    private static final int[] WHITE_CHANNELS={78,77,89,76,88,75,87,84,72,73,85,74,86,83,95,82,81,94,93,80,92,91,79,90};
    private double mix;
    private byte[] targetRgb=new byte[72],targetWhite=new byte[24];
    private final Runnable watchdog=new Runnable(){public void run(){
        if(stopped)return;
        if(active&&SystemClock.elapsedRealtime()-lastUpdate>1800)release("IDLE");
        handler.postDelayed(this,750);
    }};
    Lights(Context context,Status status){this.context=context.getApplicationContext();this.status=status;handler.postDelayed(watchdog,750);}

    void update(boolean requested,double blend,byte[] rgb,byte[] white){
        if(stopped)return;
        lastUpdate=SystemClock.elapsedRealtime();mix=blend;targetRgb=rgb;targetWhite=white;
        if(!requested){if(active||client!=null)release("IDLE");blocked=false;return;}
        active=true;
        if(blocked||acquiring)return;
        if(session==null){connect();return;}
        sendFrame();
    }
    private void connect(){
        acquiring=true;
        final Client c=new Client();client=c;
        if(!c.bind(hello->{
            if(c!=client||!active||stopped){c.close();return;}
            if(!hello.getBoolean("ok")){fail(hello.getString("code","UNAVAILABLE"));return;}
            try{
                JSONObject capabilities=new JSONObject(hello.getString("data","{}"));
                if(!capabilities.optBoolean("enabled")){fail("DISABLED");return;}
                if(capabilities.optInt("api_version")!=1||capabilities.optInt("rgb_pixels")!=24||capabilities.optInt("white_pixels")!=24||capabilities.optInt("maximum_fps")<10||!"n5d-clockwise-2026-10".equals(capabilities.optString("mapping_id"))){fail("UNSUPPORTED_VERSION");return;}
                Bundle b=new Bundle();b.putInt("lease_ms",10000);b.putBoolean("restore_on_end",true);
                c.send(10,b,result->{
                    if(c==client)acquiring=false;
                    if(!result.getBoolean("ok")){if(c==client)fail(result.getString("code","UNAVAILABLE"));else c.close();return;}
                    try{
                        String id=new JSONObject(result.getString("data")).getString("session_id");
                        if(c!=client||!active||stopped){returnSession(c,id);return;}
                        session=id;sequence=0;acquiredAt=SystemClock.elapsedRealtime();inFlight=true;
                        status.show("CONNECTED");Log.i("BloubN5D","ACQUIRED story session");
                        // ACQUIRE preserves the live frame: sample it before changing any output.
                        c.send(2,new Bundle(),snapshot->{
                            if(c!=client)return;
                            if(!snapshot.getBoolean("ok")){fail("UNAVAILABLE");return;}
                            try{
                                JSONObject data=new JSONObject(snapshot.getString("data"));JSONArray channels=data.getJSONArray("channels");
                                double rgbFade=data.getInt("brightness")/255.0,whiteFade=data.getInt("white_brightness")/255.0;
                                for(int i=0;i<24;i++){
                                    for(int j=0;j<3;j++)previousRgb[i*3+j]=channels.getInt(RGB_GROUPS[i]*3+2-j)*rgbFade;
                                    previousWhite[i]=channels.getInt(WHITE_CHANNELS[i])*whiteFade;
                                }
                                inFlight=false;sendFrame();
                            }catch(JSONException e){fail("UNAVAILABLE");}
                        });
                    }catch(JSONException e){fail("UNAVAILABLE");}
                });
            }catch(JSONException e){fail("UNSUPPORTED_VERSION");}
        })){fail("UNAVAILABLE");}
    }
    private void sendFrame(){
        if(client==null||session==null||!active||inFlight)return;
        final Client owner=client;
        byte[] rgb=new byte[72],white=new byte[24];
        // The JS sampler owns geometry and choreography. Native code only owns
        // the lease and blends to/from the frame captured before acquisition.
        double blend=smooth((SystemClock.elapsedRealtime()-acquiredAt)/2500.0)*mix;
        for(int i=0;i<72;i++)rgb[i]=(byte)Math.round(previousRgb[i]*(1-blend)+(targetRgb[i]&255)*blend);
        for(int i=0;i<24;i++)white[i]=(byte)Math.round(previousWhite[i]*(1-blend)+(targetWhite[i]&255)*blend);
        Bundle b=new Bundle();b.putString("session_id",session);b.putString("format","rgb_white");b.putByteArray("rgb",rgb);b.putByteArray("white",white);
        b.putInt("brightness",255);b.putInt("white_brightness",255);b.putLong("sequence",++sequence);
        // Logo is deliberately omitted: it remains controlled by the user's saved setting.
        inFlight=true;
        owner.send(12,b,r->{if(owner!=client)return;inFlight=false;if(!r.getBoolean("ok"))fail(r.getString("code","UNAVAILABLE"));});
        long now=SystemClock.elapsedRealtime();
        if(now-lastState>5000){lastState=now;owner.send(2,new Bundle(),r->{
            if(owner!=client||!r.getBoolean("ok"))return;
            try{JSONObject s=new JSONObject(r.getString("data","{}"));if(!s.optBoolean("external_control"))fail("NOT_OWNER");}catch(JSONException ignored){}
        });}
    }
    private static double smooth(double value){double a=Math.max(0,Math.min(1,value));return a*a*a*(10+a*(-15+6*a));}
    private void fail(String code){blocked=true;release(code);}
    void release(String code){
        active=false;sessionClear(code);
    }
    private void sessionClear(String code){
        final Client c=client;final String id=session;client=null;session=null;inFlight=false;
        boolean pending=acquiring;acquiring=false;
        if(c!=null){
            if(id!=null)returnSession(c,id);
            else if(pending)c.closeWhenSettled();
            else c.close();
        }
        status.show(code);
    }
    private void returnSession(Client c,String id){
        Bundle b=new Bundle();b.putString("session_id",id);b.putBoolean("resume_local",true);
        c.send(15,b,r->{Log.i("BloubN5D","RELEASED restore=true ok="+r.getBoolean("ok"));c.close();});
        handler.postDelayed(c::close,1800);
    }
    void stop(){stopped=true;handler.removeCallbacks(watchdog);release("IDLE");}

    private final class Client {
        private final SparseArray<Reply> replies=new SparseArray<>();
        private final SparseArray<Runnable> timeouts=new SparseArray<>();
        private int counter;private boolean bound,closed;private Messenger remote;
        private Reply hello;
        private final Messenger callback=new Messenger(new Handler(Looper.getMainLooper()){
            @Override public void handleMessage(Message m){
                Runnable timeout=timeouts.get(m.arg1);if(timeout!=null)handler.removeCallbacks(timeout);timeouts.remove(m.arg1);
                Reply reply=replies.get(m.arg1);replies.remove(m.arg1);if(reply!=null)reply.accept(m.getData());
            }
        });
        private final ServiceConnection connection=new ServiceConnection(){
            public void onServiceConnected(ComponentName name,IBinder binder){if(closed)return;remote=new Messenger(binder);send(1,new Bundle(),hello);}
            public void onServiceDisconnected(ComponentName name){remote=null;if(client==Client.this)fail("DISCONNECTED");close();}
        };
        boolean bind(Reply callback){hello=callback;try{bound=context.bindService(new Intent("com.codex.ringlab.CONTROL").setComponent(new ComponentName("com.codex.ringlab","com.codex.ringlab.PublicControlService")),connection,Context.BIND_AUTO_CREATE);return bound;}catch(Exception e){return false;}}
        void send(int what,Bundle data,Reply reply){
            if(remote==null||closed){if(reply!=null)reply.accept(error("DISCONNECTED"));return;}
            Message m=Message.obtain(null,what);m.arg1=++counter;m.replyTo=callback;data.putInt("api_version",1);m.setData(data);
            if(reply!=null){final int n=m.arg1;replies.put(n,reply);Runnable timeout=()->{Reply r=replies.get(n);replies.remove(n);timeouts.remove(n);if(r!=null)r.accept(error("UNAVAILABLE"));};timeouts.put(n,timeout);handler.postDelayed(timeout,what==10?20000:2500);}
            try{remote.send(m);}catch(RemoteException e){if(client==this)fail("DISCONNECTED");else close();}
        }
        // ACQUIRE may be waiting for root authorization. Keep its reply alive so a late grant is released.
        void closeWhenSettled(){handler.postDelayed(this::close,22000);}
        void close(){if(closed)return;closed=true;for(int i=0;i<timeouts.size();i++)handler.removeCallbacks(timeouts.valueAt(i));timeouts.clear();replies.clear();remote=null;if(bound){try{context.unbindService(connection);}catch(Exception ignored){}bound=false;}}
        Bundle error(String code){Bundle b=new Bundle();b.putBoolean("ok",false);b.putString("code",code);return b;}
    }
}
