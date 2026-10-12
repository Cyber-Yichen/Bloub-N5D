// MediaPipe-compatible factory: this distribution uses the official no-op stats logger.
// The upstream API is Apache-2.0; see assets/MEDIAPIPE-LICENSE.txt.
package com.google.mediapipe.tasks.core.logging;
import android.content.Context;
public final class TasksStatsLoggerFactory {
  public static TasksStatsLogger create(Context context,String task,String mode){return TasksStatsDummyLogger.create(context,task,mode);}
}
