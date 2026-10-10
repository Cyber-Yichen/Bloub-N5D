package com.cyberyichen.bloub;
import android.content.*;
public final class GalleryCleanupReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context context,Intent intent){
    final PendingResult result=goAsync();
    new Thread(()->{try{Gallery store=new Gallery(context);store.prune();store.schedule();}finally{result.finish();}},"BloubGalleryCleanup").start();
  }
}
