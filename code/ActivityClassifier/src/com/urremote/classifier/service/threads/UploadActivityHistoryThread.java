package com.urremote.classifier.service.threads;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import com.urremote.classifier.common.Constants;
import com.urremote.classifier.common.ExceptionHandler;
import com.urremote.classifier.db.ActivitiesTable;
import com.urremote.classifier.db.OptionsTable;
import com.urremote.classifier.db.SqlLiteAdapter;
import com.urremote.classifier.repository.ActivityQueries;
import com.urremote.classifier.rpc.Classification;
import com.urremote.classifier.utils.PhoneInfo;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.util.Log;

/**
 * 
 * @author Justin Lee
 *
 *	<p>
 *	Changes made by Umran: <br>
 *	Class used to be called <code>UploadActivityHistory</code>
 *	Changed class from using a Timer, to being a thread on its own. As a thread,
 *	the delays involved in uploading content to the internet will be sheltered
 *	from the rest of the application. The thread rests for a period given
 *	as {@link Constants.DELAY_UPLOAD_DATA} before uploading another batch of data.
 *
 */
public class UploadActivityHistoryThread extends Thread {
	
	private static final DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	private static final DateFormat df1 = new SimpleDateFormat("Z z");

	private Context context;
	
	protected AccountManager accountManager;

	private SqlLiteAdapter sqlLiteAdapter;
	private OptionsTable optionsTable;
	private ActivitiesTable activitiesTable;
	
	private boolean shouldExit;
	private PhoneInfo phoneInfo;
	private boolean uploading;		//	is the thread currently uploading (to avoid interrupting)
	private long lastUploadTime;	//	last time data was uploaded
	
	//	reusable stuff
	private StringBuilder htmlMessage = new StringBuilder();
	private List<Long> processed = new ArrayList<Long>();
	private Classification classification = new Classification();
	private Date tempdate = new Date();

	/**
	 * 
	 * @param context context from Activity or Service classes
	 */
	public UploadActivityHistoryThread(Context context, PhoneInfo phoneInfo) {
    	super(UploadActivityHistoryThread.class.getName());
    	
    	this.context = context;
    	
    	this.sqlLiteAdapter = SqlLiteAdapter.getInstance(context);
    	this.optionsTable = sqlLiteAdapter.getOptionsTable();
		this.activitiesTable = sqlLiteAdapter.getActivitiesTable();
		
		this.phoneInfo = phoneInfo;
	}

	/**
	 * cancel timer when the background service is destroyed.
	 */
	public synchronized void cancelUploads(){
		//	signal the thread to exit
		this.shouldExit = true;

		//	not sure what problems may happen if interrupted while uploading
		//	so lets avoid interrupting during an upload,
		//	even if it means that the service may delay during destruction and
		//	probably be force-closed.
		if (!this.uploading)
			this.interrupt();
	}

	/**
	 * By using {@link ActivitiesTable} class, check every un-sent activities from device repository,
	 * and upload un-sent activities from device repository to Web server.
	 * Timer scheduler is set to every 5 min.
	 *
	 * @param accountName
	 */
	public void startUploads() {

		if (this.isAlive()) {
			return;
		}

		this.shouldExit = false;
		this.uploading = false;
		this.lastUploadTime = System.currentTimeMillis();
		this.start();
	}


	@Override
	public void run() {
		Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler(context));
		
		String accountName = null;
		long currentTime;

		while (!shouldExit) {
			//	wait for next time
			try {
				//	sleep for 1 second
				Thread.sleep(1000);
			} catch (InterruptedException e) {
			}
			
			accountName = optionsTable.getUploadAccount();

			currentTime = System.currentTimeMillis();
			//	wait until our download time has reached
			if (accountName!=null && currentTime-lastUploadTime>=Constants.DELAY_UPLOAD_DATA) {
				//	upload the data
				uploading = true;
				uploadData(accountName);
				uploading = false;
				lastUploadTime = currentTime;
			}
		}

	}

	private void uploadData(String accountName)
	{
		Log.v(Constants.DEBUG_TAG, "Upload Activity History Thread attempting to upload data.");
		
		HttpClient client = new DefaultHttpClient();

		final HttpPost post = new HttpPost(Constants.URL_ACTIVITY_POST);
		//final File file = new File(Constants.PATH_ACTIVITY_RECORDS_FILE);
		//final FileEntity entity = new FileEntity(file, "text/plain");

		/*
		 * if there are un-posted activities in the device repository,
		 * then merge every information of activity into one string(message),
		 * then upload to Web server.
		 */
		activitiesTable.loadUnchecked(classification, new ActivitiesTable.ClassificationDataCallback() {
			boolean first = true;
			boolean bufferFull = false;
			
			public void onRetrieve(Classification cl) {
				//	if previously the buffer was full, don't do any more processing...
				if (bufferFull)
					return;
				
				//	compute what we have to append
				String[] dbStartSplitted =  cl.getDbStartTime().split(" ");
				String[] dbEndSplitted =  cl.getDbEndTime().split(" ");
				String appenditure = cl.getClassification()+"&&"+
									dbStartSplitted[0]+" "+
									dbStartSplitted[1]+"&&"+
									dbEndSplitted[0]+" "+
									dbEndSplitted[1]+"&&"+
									df1.format(tempdate);
				if (!first)
					appenditure = "##" + appenditure;
				else
					first = false;
				
				//	check, after appending, does the size go past 500?
				bufferFull = (appenditure.length() + htmlMessage.length()) > 500;
				
				//	if it does, don't append
				if (bufferFull)
					return;
				
				//	append message...
				tempdate.setTime(cl.getStart());
				htmlMessage.append(appenditure);
				processed.add(cl.getStart());
			}
		});
		
	    //send un-posted activities with the size, date, and Google account to Web server.
	    try {
	    	StringEntity entity = new StringEntity("Okiey");
	    	
	    	String message = htmlMessage.toString();
	    	
	    	Date systemdate = Calendar.getInstance().getTime();
	    	String reportDate = df.format(systemdate);
	    	post.setHeader("sysdate",reportDate);
	    	post.setHeader("size",Integer.toString(processed.size()));
  	        post.setHeader("message", message);
  	        post.setHeader("UID", accountName);
  	        post.setEntity(entity);
  	        
  	        Log.i(Constants.DEBUG_TAG, "Uploading activities using account: "+accountName);
  	        int code = new DefaultHttpClient().execute(post).getStatusLine().getStatusCode();
  	        //Log.i(Constants.DEBUG_TAG, "Upload Activity History Result Code: "+code+"\n"+message);
  	        
  	        for (Long st:processed) {
  	        	activitiesTable.updateChecked(st);
  	        }
	    } catch (Exception ex) {
	    	Log.e(Constants.DEBUG_TAG, "Unable to upload sensor logs: "+ex.getMessage());
	    } 
	    
		htmlMessage.setLength(0);
		processed.clear();
		
	}

}
