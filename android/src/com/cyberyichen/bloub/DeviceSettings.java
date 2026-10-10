package com.cyberyichen.bloub;
import android.content.*;
import org.json.*;
final class DeviceSettings {
  private final Context context;
  DeviceSettings(Context c){context=c.getApplicationContext();}
  synchronized boolean initialized(){return prefs().contains("data");}
  private SharedPreferences prefs(){return context.getSharedPreferences("device_settings",0);}
  synchronized JSONObject get(){try{JSONObject d=SettingsSpec.defaults();if(initialized())d=SettingsSpec.patch(d,new JSONObject(prefs().getString("data","{}")));return d;}catch(Exception e){try{return SettingsSpec.defaults();}catch(JSONException impossible){throw new IllegalStateException(impossible);}}}
  synchronized JSONObject save(JSONObject delta)throws JSONException{JSONObject d=SettingsSpec.patch(get(),delta);if(!prefs().edit().putString("data",d.toString()).commit())throw new JSONException("设置保存失败");return d;}
  synchronized void remember(String key,Object value){try{save(new JSONObject().put(key,value));}catch(JSONException ignored){}}
  long photoInterval(String state){JSONObject d=get();String k="occupied".equals(state)?"occupiedMinutes":"empty".equals(state)?"emptyMinutes":"unknownMinutes";return d.optLong(k,2)*60000;}
}
