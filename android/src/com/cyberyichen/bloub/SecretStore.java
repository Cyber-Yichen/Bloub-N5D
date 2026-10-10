package com.cyberyichen.bloub;
import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.security.*;
/** Secret material stays encrypted by the device keystore, and is never returned to the web. */
final class SecretStore {
  private final Context context;private static final String ALIAS="bloub-storage-v1";
  SecretStore(Context c){context=c.getApplicationContext();}
  private SecretKey key()throws Exception{KeyStore s=KeyStore.getInstance("AndroidKeyStore");s.load(null);if(s.containsAlias(ALIAS))return (SecretKey)s.getKey(ALIAS,null);KeyGenerator g=KeyGenerator.getInstance("AES","AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return g.generateKey();}
  void set(String name,String value)throws Exception{if(value.isEmpty()){context.getSharedPreferences("storage_secrets",0).edit().remove(name).commit();return;}Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());byte[] encrypted=c.doFinal(value.getBytes("UTF-8"));context.getSharedPreferences("storage_secrets",0).edit().putString(name,Base64.encodeToString(c.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(encrypted,Base64.NO_WRAP)).commit();}
  String get(String name)throws Exception{String text=context.getSharedPreferences("storage_secrets",0).getString(name,"");if(text.isEmpty())return "";String[] p=text.split(":",2);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(p[0],Base64.NO_WRAP)));return new String(c.doFinal(Base64.decode(p[1],Base64.NO_WRAP)),"UTF-8");}
  boolean has(String name){return context.getSharedPreferences("storage_secrets",0).contains(name);}
}
