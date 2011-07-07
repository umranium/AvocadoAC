/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package activity.classifier.service.threads;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import activity.classifier.common.Constants;
import activity.classifier.common.ExceptionHandler;
import activity.classifier.db.OptionsTable;
import activity.classifier.db.SqlLiteAdapter;
import activity.classifier.rpc.ActivityRecorderBinder;
import activity.classifier.service.RecorderService;
import activity.classifier.utils.PhoneInfo;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;
/**
 * 
 * Used for sending user's google account nick-name in order to match the user's activity history
 * in web server.
 * It happens when the application runs at the very first time or next time if there is no account synced with the phone.
 * AccountManager is an API to get a user's current account name.
 * The type of account would be many depended on users, so it is required to specify the type as "com.google"
 * in order to get google account.
 * The number of google accounts would also be more than one but only single account is sent to the website for now.
 * 
 * Sending components : User account name, IMEI number, device model name
 * 
 *	<p>
 *	Changes made by Umran: <br>
 *	The class used to be called AccountService, a service which was responsible for
 *	posting the user's information. Now changed to a Thread which can is started in 
 *	the {@link RecorderService} if the account information wasn't sent before. The thread posts the
 *	user's information then displays a toast message. If the user doesn't have an
 *	account set up in the phone, the thread waits checking once in a while (specified
 *	in the constants class) if the user has entered the account information.
 * 
 * @author Justin Lee
 *
 */
public class AccountThread extends Thread {	

	private Context context;
	private ActivityRecorderBinder service;
	private SqlLiteAdapter sqlLiteAdapter;
	private OptionsTable optionsTable;
	private PhoneInfo phoneInfo;
	private String toastString;
	private boolean shouldExit;

    public AccountThread(Context context, ActivityRecorderBinder service, PhoneInfo phoneInfo) {
    	super(AccountThread.class.getName());
    	this.context = context;
    	this.service = service;
    	this.sqlLiteAdapter = SqlLiteAdapter.getInstance(context);
    	this.optionsTable = sqlLiteAdapter.getOptionsTable();
    	this.phoneInfo = phoneInfo;
    	this.shouldExit = false;
    }
    
	public void run() {
		Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler(context));
		
		boolean sent = false;
		do {
			String accountName = phoneInfo.getFirstAccountName();

			if (accountName==null) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
				}
			} else {
				postUserDetail(accountName,phoneInfo.getModel(),phoneInfo.getIMEI());
				
				try {
					service.showServiceToast(toastString);
				} catch (RemoteException e) {
					Log.d(Constants.DEBUG_TAG, "Error while attempting to show toast msg from service thread.", e);
				}
				
				sent = true;
			}
			
		} while (!sent && !shouldExit);
	}
	
	public synchronized void exit() {
		//	signal the thread to exit
		this.shouldExit = true;
		//	interrupt if sleeping
		this.interrupt();
	}
	
    /**
     * A method to post user's Google account, device model name, and IMEI to Web server
     * @param accountName user's Google account
     * @param modelName device model name
     * @param IMEI IMEI number
     * @throws UnsupportedEncodingException 
     */
    private void postUserDetail(String accountName, String modelName, String IMEI) {
    	
    	if (accountName!=null){
    		
    		Log.v(Constants.DEBUG_TAG, "Account thread attempting to upload account details.");
    		
			HttpClient client = new DefaultHttpClient();
			final HttpPost post = new HttpPost(Constants.URL_USER_DETAILS_POST);
			final File file = context.getFileStreamPath(Constants.RECORDS_FILE_NAME);
			//final FileEntity entity = new FileEntity(file, "text/plain");

			//post user's information
			try {
				StringEntity entity = new StringEntity("Okiey");
				
				post.setHeader("UID",accountName);
				post.setHeader("IMEI",IMEI);
				post.setHeader("Model",modelName);
				post.setEntity(entity);

				/*
				 *  integer data type variable, code, store a state value of the Internet response.
				 *  For now, an error occurs when there is just no Internet connection, 
				 *  but will use this variable to filter among the various of the Internet response states.
				 */
				int code = new DefaultHttpClient().execute(post).getStatusLine().getStatusCode();

				//set the pop-up message
				toastString = "User Information submission completed.\n" +
				"   phone model  : "+modelName+"\n" +
				"   account name : "+accountName+"\n"+
				"   IMEI number  : "+IMEI;

				//set the account state to 1 (true)
				optionsTable.setAccountSent(true);
				optionsTable.save();
				Log.i("postUserDetail","posted");

			} catch (IOException ex) {
				//Log.e(getClass().getName(), "Unable to upload sensor logs", ex);
				Log.e(Constants.DEBUG_TAG, "Unable to upload account details: "+ex.getMessage());
				//set the pop-up message
				toastString = "Submission failed,\n check phone's Internet connectivity and try again.";
				//set the account state to 0 (false)
				optionsTable.setAccountSent(false);
				optionsTable.save();
				Log.i("postUserDetail","Not posted");
			}
		}
	}

}

