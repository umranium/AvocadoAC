package com.urremote.classifier.utils;

import java.io.File;

import com.urremote.classifier.common.Constants;

import android.util.Log;

public class LogRedirect {

	public static void dumpLog(File outputFile) {
		try {
			Log.i(Constants.TAG, "Redirecting device debug logs to sdcard...");
			if (!outputFile.getParentFile().exists()) {
				outputFile.getParentFile().mkdirs();
			}
			if (!outputFile.exists())
				outputFile.createNewFile(); 
			//	format=time, dump log contents and exit, to file 
		    String dumpCmd = "logcat -v time -b main -d -f "+outputFile.getAbsolutePath();
		    Runtime.getRuntime().exec(dumpCmd);
		    //	clear
//		    String clearCmd = "logcat -c";
//		    Runtime.getRuntime().exec(clearCmd);
			Log.i(Constants.TAG, "\tDebug logs redirected to "+outputFile.getAbsolutePath());
		} catch (Exception e) {
			throw new RuntimeException("Exception while trying to redirect device debug logs", e);
		}
	}
	
}
