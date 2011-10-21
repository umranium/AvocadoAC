/*
 * Copyright (c) 2009-2010 Chris Smith
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package au.urremote.classifier.common;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;

import au.urremote.classifier.rpc.ActivityRecorderBinder;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Environment;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;
import au.urremote.classifier.activity.MainTabActivity;
import au.urremote.classifier.service.RecorderService;

/**
 * An exception handler which reports any uncaught exceptions to the context
 * API's website, in order to facilitate remote diagnostics of user errors.
 *
 * @author chris
 */
public class ExceptionHandler implements UncaughtExceptionHandler {

    private UncaughtExceptionHandler defaultUEH;

    private String appname, url, version, imei;

    private String localPath= Environment.getExternalStorageDirectory() + File.separator + "avocadoAC";
    
    private Context context;
    
    protected ExceptionHandler(Context context, String appname, String url, String version, String imei) {
    	this.context = context;
        this.appname = appname;
        this.url = url;
        this.version = version;
        this.imei = imei;
        this.defaultUEH = Thread.getDefaultUncaughtExceptionHandler();
    }

    public ExceptionHandler(Context context) {
        this(context, getAppName(context), "http://testingjungoo.appspot.com/bug.jsp",
                getVersionName(context), getIMEI(context));
    }

    private static String getVersionName(final Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (NameNotFoundException ex) {
            return "Unknown";
        }
    }

    private static String getAppName(final Context context) {
        try {
            return context.getPackageManager().getApplicationLabel(context
                    .getPackageManager().getPackageInfo(context.getPackageName(), 0)
                    .applicationInfo).toString().replaceAll("[^A-Za-z]", "");
        } catch (NameNotFoundException ex) {
            return "Unknown";
        }
    }

    private static String getIMEI(final Context context) {
        return ((TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE)).getDeviceId();
    }

    public void uncaughtException(Thread t, Throwable e) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        final Writer result = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(result);
        printWriter.println("Exception Thrown By Thread:"+t.getId()+" '"+t.getName()+"'");
        e.printStackTrace(printWriter);
        String stacktrace = result.toString();
        printWriter.close();
        String filename = timestamp + ".stacktrace";
        writeToFile(stacktrace, filename);

        sendToServer(stacktrace, filename);
        
        defaultUEH.uncaughtException(t, e);
    }
    
    private void writeToFile(String stacktrace, String filename) {
        try {
        	File outputFile = new File(Constants.PATH_SD_CARD_APP_LOC + File.separator + filename);
        	Log.v(Constants.DEBUG_TAG, "Output Trace File:"+outputFile);
        	if (!outputFile.getParentFile().exists()) {
        		if (!outputFile.getParentFile().mkdirs()) {
                    Log.e("Avocado AC Error", 
                    		"Unable to create log file directory:\n"+
                    		outputFile.getParentFile()+
                    		"\nCaused when writing stack to file:\n"+
                    		stacktrace);
        		}
        	}
        	if (!outputFile.exists()) {
        		if (!outputFile.createNewFile()) {
                    Log.e("Avocado AC Error", 
                    		"Unable to create log file:\n"+
                    		outputFile.getParentFile()+
                    		"\nCaused when writing stack to file:\n"+
                    		stacktrace);
        		}
        	}
            BufferedWriter bos = new BufferedWriter(new FileWriter(outputFile));
            bos.write(stacktrace);
            bos.flush();
            bos.close();
            Log.e(Constants.DEBUG_TAG, stacktrace);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendToServer(String stacktrace, String filename) {
        DefaultHttpClient httpClient = new DefaultHttpClient();
        HttpPost httpPost = new HttpPost(url);

        httpPost.setHeader("x-application", appname + "-exception");
        httpPost.setHeader("x-version", version);
        httpPost.setHeader("x-imei", imei);

        List<NameValuePair> nvps = new ArrayList<NameValuePair>();
        nvps.add(new BasicNameValuePair("filename", filename));
        nvps.add(new BasicNameValuePair("stacktrace", stacktrace));
        try {
            httpPost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
            httpClient.execute(httpPost);
        } catch (IOException e) {
            // Do nothing
        }
    }
}
