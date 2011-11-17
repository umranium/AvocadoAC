package com.urremote.classifier.activity;

import java.text.ParseException;
import java.util.concurrent.locks.ReentrantLock;

import com.urremote.classifier.R;
import com.urremote.classifier.rpc.ActivityRecorderBinder;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.TabActivity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Window;
import android.widget.TabHost;

import com.flurry.android.FlurryAgent;
import com.urremote.classifier.common.Constants;
import com.urremote.classifier.common.ExceptionHandler;
import com.urremote.classifier.db.OptionsTable;
import com.urremote.classifier.db.SqlLiteAdapter;
import com.urremote.classifier.service.RecorderService;

public class MainTabActivity extends TabActivity {
	public static boolean serviceIsRunning = false;

	private final Handler handler = new Handler();

	private ActivityRecorderBinder service = null;

	private ProgressDialog dialog;

	/**
	 * enable to delete database in the device repository.
	 */
	private boolean EnableDeletion;

	private SqlLiteAdapter sqlLiteAdapter;
	private OptionsTable optionsTable;

	/**
	 * Displays the progress dialog, waits some time, starts the service, waits some more,
	 * 	then hides the dialog.
	 */
	private final StartServiceRunnable startServiceRunnable = new StartServiceRunnable();
	private final UpdateButtonRunnable updateButtonRunnable = new UpdateButtonRunnable();

	/**
	 *	Performs necessary tasks when the connection to the service
	 *	is established, and after it is disconnected.
	 */
	private final ServiceConnection connection = new ServiceConnection() {

		public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
			service = ActivityRecorderBinder.Stub.asInterface(iBinder);
			updateButtonRunnable.updateNow();
			
			Log.v(Constants.DEBUG_TAG, "MainTabActivity: Connected to service");
			
			try {
				Log.v(Constants.DEBUG_TAG, "Was the service previously started by the user? "+optionsTable.isServiceUserStarted()+", Is Running? "+service.isRunning());
				if (optionsTable.isServiceUserStarted() && !service.isRunning()) {
					MainTabActivity.this.startService();
				}
			} catch (RemoteException e) {
				Log.v(Constants.DEBUG_TAG, "Error while attempting to automatically start service", e);
			}
		}

		public void onServiceDisconnected(ComponentName componentName) {
			service = null;
			Log.v(Constants.DEBUG_TAG, "MainTabActivity: Disconnected from service");
		}


	};


	/**
	 * 
	 * @param intent
	 * @return null
	 */
	public IBinder onBind(Intent intent) {
		return null;
	}


	/**
	 * Enable to use menu button to make option menus.
	 */
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		
		Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler(this));

		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.layout.menu, menu);
		return true;
	}

	/**
	 * Set the number of option menus on this Activity.
	 */
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
		menu.findItem(R.id.startService).setIcon(android.R.drawable.ic_media_play).setEnabled(EnableDeletion);
		menu.findItem(R.id.stopService).setIcon(android.R.drawable.ic_media_pause).setEnabled(!EnableDeletion);

		return true;
	}

	/**
	 * Performs the option menu with an appropriate option clicked.   
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {

		case R.id.startService:
			startService();
			break;
		case R.id.stopService:
			stopService();
			break;
		}
		return true;

	}

	/**
	 * 
	 */
	@Override
	protected void onDestroy() {
		super.onDestroy();
		unbindService(connection);
	}
	
	/**
	 * 
	 */
	@Override
	protected void onStart() {
		super.onStart();
		updateButtonRunnable.start();
		FlurryAgent.onStartSession(this, Constants.FLURRY_SESSION_ID);
	}

	/**
	 * 
	 */
	@Override
	protected void onStop() {
		super.onStop();
		updateButtonRunnable.stop();
		FlurryAgent.onEndSession(this);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		//set exception handler
		Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler(this));

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		sqlLiteAdapter = SqlLiteAdapter.getInstance(this);
		optionsTable = sqlLiteAdapter.getOptionsTable();

		final TabHost tabHost = getTabHost();

		tabHost.addTab(tabHost.newTabSpec("tab1")
				.setIndicator(" ",getResources().getDrawable(R.drawable.chart72))
				.setContent(new Intent(this, ActivityChartActivity.class)
				));

		tabHost.addTab(tabHost.newTabSpec("tab2")
				.setIndicator(" ",getResources().getDrawable(R.drawable.database72))
				.setContent(new Intent(this, ActivityListActivity.class)));

		tabHost.addTab(tabHost.newTabSpec("tab3")
				.setIndicator(" ",getResources().getDrawable(R.drawable.settings72))
				.setContent(new Intent(this, MainSettingsActivity.class)
				.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)));
		
		bindService(new Intent(this, RecorderService.class), connection, BIND_AUTO_CREATE);
		EnableDeletion = true;
		
	}
	
	
	private void startService()
	{
		try {

			Log.v(Constants.DEBUG_TAG, "User starting RecorderService");
			EnableDeletion = false;
			FlurryAgent.onEvent("recording_start");
			startServiceRunnable.startService();
			
			optionsTable.setServiceUserStarted(true);
			optionsTable.save();

			
		} catch (Exception ex) {
			Log.e(Constants.DEBUG_TAG, "Unable to get service state", ex);
		}
	}
	
	private void stopService()
	{
		try {
			Log.v(Constants.DEBUG_TAG, "User stopping RecorderService");
			if (service.isRunning()) {
				
				optionsTable.setServiceUserStarted(false);
				optionsTable.save();
				
				EnableDeletion = true;
				FlurryAgent.onEvent("recording_stop");

				onDestroy();
				stopService(new Intent(MainTabActivity.this, RecorderService.class));
				//					unbindService(connection);
				bindService(new Intent(MainTabActivity.this, RecorderService.class),
						connection, BIND_AUTO_CREATE);

			}
		} catch (RemoteException ex) {
			Log.e(Constants.DEBUG_TAG, "Unable to get service state", ex);
		}
	}

	/**
	 * 
	 * @author Umran
	 *
	 */
	private class StartServiceRunnable implements Runnable {

		private static final int DISPLAY_START_DIALOG = 0;
		private static final int CLOSE_START_DIALOG = 1;

		private int nextStep;

		public void startService() {
			if (dialog!=null) {
				return;
			}

			dialog = ProgressDialog.show(
					MainTabActivity.this,
					"Starting service",
					"Please wait...", true);			
			nextStep = DISPLAY_START_DIALOG;
			

			handler.postDelayed(this, 100);
		}

		public void run() {
			//			Log.i("button","run");
			switch (nextStep) {
			case DISPLAY_START_DIALOG:
			{
				Intent intent = new Intent(
						MainTabActivity.this,
						RecorderService.class
						);
				MainTabActivity.this.startService(intent);

				nextStep = CLOSE_START_DIALOG;					
				handler.postDelayed(this, Constants.DELAY_SERVICE_START);
				break;
			}
			case CLOSE_START_DIALOG:
			{
				try {
					//	hide the progress dialog box
					dialog.dismiss();
					dialog = null;

					//	if the service is still not running by the end of the delay
					//		display an error message
					if (service==null || !service.isRunning())
					{
						AlertDialog.Builder builder = new AlertDialog.Builder(MainTabActivity.this);
						builder.setTitle("Error");
						builder.setMessage("Unable to start service"); // TODO: Put this into the string file
						builder.show();							
					}
				} catch (RemoteException e) {
					Log.e(Constants.DEBUG_TAG, "Error Starting Recorder Service", e);
				}
				break;
			}
			}


		}

	}
	private class UpdateButtonRunnable implements Runnable {

		//	save the state of the service, if it was previously running or not
		//		to avoid unnecessary updates
		private boolean prevServiceRunning = false;

		//	avoids conflicts between scheduled updates,
		//		and once-off updates 
		private ReentrantLock reentrantLock = new ReentrantLock();

		//	starts scheduled interface updates
		public void start() {
			handler.postDelayed(updateButtonRunnable, Constants.DELAY_UI_UPDATE);
		}

		//	stops scheduled interface updates
		public void stop() {
			handler.removeCallbacks(updateButtonRunnable);
		}

		//	performs a once-off unsynchronised (unscheduled) interface update
		//		please note that this can be called from another thread
		//		without interfering with the normal scheduled updates.
		public void updateNow() {
			if (reentrantLock.tryLock()) {

				try {
					updateButton();
				} catch (ParseException ex) {
					Log.e(Constants.DEBUG_TAG, "Error while performing scheduled UI update.", ex);
				}

				reentrantLock.unlock();
			}
		}

		public void run() {
			if (reentrantLock.tryLock()) {

				try {
					updateButton();
				} catch (ParseException e) {
					e.printStackTrace();
				}

				reentrantLock.unlock();
			}

			handler.postDelayed(updateButtonRunnable, Constants.DELAY_UI_UPDATE);
		}



		/**
		 * 
		 * changed from updateButton to updateUI
		 * 
		 * updates the user interface:
		 * 	the toggle button's text is changed.
		 * 	the classification list's entries are updated.
		 * 
		 * @throws ParseException
		 */
		private void updateButton() throws ParseException {

			try {
				boolean isServiceRunning = service!=null && service.isRunning();

				//	update toggle text only if service state has changed
				if (isServiceRunning!=prevServiceRunning) {
					try {
						if(service==null || !service.isRunning()){
							//							Log.i("button","true");
							EnableDeletion = true;
						}else{
							//							Log.i("button","false");
							EnableDeletion = false;
						}
					} catch (RemoteException e) {
						e.printStackTrace();
					}
				}

				prevServiceRunning = isServiceRunning;

			} catch (RemoteException ex) {
				Log.e(Constants.DEBUG_TAG, "Error while updating user interface", ex);
			}
		}

	}

}

