/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package aus.csiro.justin.sensorlogger;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

/**
 *
 * @author chris
 * modified by justin
 */
public class UploaderService extends BoundService implements Runnable {

    private Map<String, String> headers = new HashMap<String, String>();

    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);

        headers.clear();
        for (String key : intent.getExtras().keySet()) {
            headers.put(key, intent.getStringExtra(key));
        }
    }

    @Override
    protected void serviceBound() {
        super.serviceBound();
        
        new Thread(this, "Upload thread").start();
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    public void run() {

    	DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    	 Date date = Calendar.getInstance().getTime();
    	 String reportDate = df.format(date);

     	
     	//servlet name 
        final HttpPost post = new HttpPost("http://testingjungoo.appspot.com/read");
//        HttpPost httppost = new HttpPost("http://testingjungoo.appspot.com/activity");
        int index=0;
        try {
			index=service.getIndex();
			for(int i=0;i<index+1;i++){
				final File file = getFileStreamPath("tmpsensors"+i+".log");

		        post.setHeader("FN", (i+1)+"/"+((index+1)) );
		        post.setHeader("date", reportDate);

		        if (file.exists() && file.length() > 10) {
		            // The file exists and contains a non-trivial amount of information
		            final FileEntity entity = new FileEntity(file, "text/plain");

		            for (Map.Entry<String, String> entry : headers.entrySet()) {
		                post.setHeader(entry.getKey(), entry.getValue());
		            }

		            post.setEntity(entity);
		            
		            try {
//		            	HttpResponse response = client.execute(post);
//		            	HttpEntity resEntity = response.getEntity();
//		            	if(resEntity != null){
//		            		Log.i("RESPONSE",EntityUtils.toString(resEntity));
//		            	}
		                int code = new DefaultHttpClient().execute(post).getStatusLine().getStatusCode();
		            } catch (IOException ex) {
		                Log.e(getClass().getName(), "Unable to upload sensor logs", ex);
		            
		            }
		        }else{
		        	
		        }
				
		        file.delete();
				}
		        try {
		            service.setState(7);
		        } catch (RemoteException ex) {
		            Log.e(getClass().getName(), "Unable to update state", ex);
		        }

		        
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		stopSelf();
    }

}
