package activity.classifier.utils;

import java.io.File;

import activity.classifier.common.Constants;
import android.util.Log;

public class LogRedirect {

	public static void redirect(File outputFile) {
		try {
			Log.i(Constants.DEBUG_TAG, "Redirecting device debug logs to sdcard...");
			if (!outputFile.getParentFile().exists()) {
				outputFile.getParentFile().mkdirs();
			}
			if (!outputFile.exists())
				outputFile.createNewFile(); 
		    String cmd = "logcat -v time -d -f "+outputFile.getAbsolutePath();
		    Runtime.getRuntime().exec(cmd);
			Log.i(Constants.DEBUG_TAG, "\tDebug logs redirected to "+outputFile.getAbsolutePath());
		} catch (Exception e) {
			throw new RuntimeException("Exception while trying to redirect device debug logs", e);
		}
	}
	
}
