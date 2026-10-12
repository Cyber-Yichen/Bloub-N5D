package com.cyberyichen.bloub;
import android.content.*;
import org.json.*;
final class DeviceSettings {
  private final Context context;
  DeviceSettings(Context c){context=c.getApplicationContext();if(prefs().contains("data"))try{JSONObject old=new JSONObject(prefs().getString("data","{}"));boolean changed=false;if(!old.has("observationMinutes")){old.put("observationMinutes",1).put("unknownMinutes",5);changed=true;}if(old.optBoolean("monitorEnabled")&&old.optBoolean("gallerySaving")){old.put("gallerySaving",false);changed=true;}if(changed&&!prefs().edit().putString("data",old.toString()).commit())throw new IllegalStateException("设置迁移失败");}catch(JSONException invalid){android.util.Log.w("BloubSettings","Settings migration",invalid);}}
  synchronized boolean initialized(){return prefs().contains("data");}
  private SharedPreferences prefs(){return context.getSharedPreferences("device_settings",0);}
  synchronized JSONObject get(){try{JSONObject d=SettingsSpec.defaults();if(initialized())d=SettingsSpec.patch(d,new JSONObject(prefs().getString("data","{}")));return d;}catch(Exception e){try{return SettingsSpec.defaults();}catch(JSONException impossible){throw new IllegalStateException(impossible);}}}
  synchronized JSONObject save(JSONObject delta)throws JSONException{JSONObject d=SettingsSpec.patch(get(),delta);if(!prefs().edit().putString("data",d.toString()).commit())throw new JSONException("设置保存失败");return d;}
  synchronized void remember(String key,Object value){try{save(new JSONObject().put(key,value));}catch(JSONException ignored){}}
  long photoInterval(String state){JSONObject d=get();String k="occupied".equals(state)?"occupiedMinutes":"empty".equals(state)?"emptyMinutes":"unknownMinutes";return d.optLong(k,5)*60000;}
  long observationInterval(){return get().optLong("observationMinutes",1)*60000;}
  boolean scheduledObservation(){return get().optBoolean("observationEnabled",true);}
  boolean shouldSave(String state,int minute){JSONObject d=get();if(!d.optBoolean("gallerySaving")||d.optBoolean("monitorEnabled"))return false;if(CameraSchedule.quietMinute(minute,SettingsSpec.minutes(d.optString("quietStart")),SettingsSpec.minutes(d.optString("quietEnd")))&&!d.optBoolean("nightSaving"))return false;String key="occupied".equals(state)?"occupiedSaving":"empty".equals(state)?"emptySaving":"unknownSaving";return d.optBoolean(key,true);}
}
