/*
 * To change this template, choose Tools | Templates and open the template in
 * the editor.
 */

package au.urremote.classifier.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import au.urremote.classifier.R;
import au.urremote.classifier.rpc.ActivityRecorderBinder;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.util.Log;
import android.widget.Toast;
import au.urremote.classifier.accel.SampleBatch;
import au.urremote.classifier.accel.SampleBatchBuffer;
import au.urremote.classifier.accel.Sampler;
import au.urremote.classifier.accel.SamplerCallback;
import au.urremote.classifier.accel.async.AsyncAccelReader;
import au.urremote.classifier.accel.async.AsyncSampler;
import au.urremote.classifier.activity.MainTabActivity;
import au.urremote.classifier.common.ActivityNames;
import au.urremote.classifier.common.Constants;
import au.urremote.classifier.common.ExceptionHandler;
import au.urremote.classifier.db.ActivitiesTable;
import au.urremote.classifier.db.DebugDataTable;
import au.urremote.classifier.db.OptionsTable;
import au.urremote.classifier.db.SqlLiteAdapter;
import au.urremote.classifier.rpc.Classification;
import au.urremote.classifier.service.threads.AccountThread;
import au.urremote.classifier.service.threads.ClassifierThread;
import au.urremote.classifier.service.threads.UploadActivityHistoryThread;
import au.urremote.classifier.utils.ActivityWatcher;
import au.urremote.classifier.utils.MetUtilFinal;
import au.urremote.classifier.utils.MetUtilOrig;
import au.urremote.classifier.utils.PhoneInfo;
import au.urremote.classifier.utils.RawDump;

/**
 * RecorderService is main background service. This service uses broadcast to
 * get the information of charging status and screen status. The battery status
 * is sent to ClassifierService to determine Charging state. The screen status
 * is used when turns the screen on during sampling if Screen Lock setting is
 * on.
 * 
 * It calls Sampler and AccelReader to sample for 6.4 sec (128 sample point
 * every 50 msec), and it repeats every 30 sec.
 * 
 * Update activity history to web server every 5 min. If there is bad internet
 * connection, then it does not send them and waits for next time.
 * 
 * @author chris, modified by Justin
 * 
 * 
 */

public class RecorderService extends Service implements Runnable {

	private static final int ONGOING_NOTIFICATION_ID = 1;

	private Sampler sampler;

	private boolean charging = false;

	private Looper threadLooper = null;
	private Handler handler = null;

	private int isWakeLockSet;

	private PowerManager.WakeLock PARTIAL_WAKE_LOCK_MANAGER;
	private PowerManager.WakeLock SCREEN_DIM_WAKE_LOCK_MANAGER;

	private UploadActivityHistoryThread uploadActivityHistory;

	private SqlLiteAdapter sqlLiteAdapter;
	private OptionsTable optionsTable;
	private DebugDataTable debugDataTable;
	private ActivitiesTable activitiesTable;

	private boolean running;

	private Boolean SCREEN_DIM_WAKE_LOCK_MANAGER_IsAcquired = false;
	private Boolean PARTIAL_WAKE_LOCK_MANAGER_IsAcquired = false;

	private PowerManager pm;

	private final List<Classification> adapter = new ArrayList<Classification>();

	private PhoneInfo phoneInfo;

	private SampleBatchBuffer batchBuffer;
	private ClassifierThread classifierThread;
	private AccountThread registerAccountThread;
	private ActivityWatcher activityWatcher;
	private MetUtilOrig metUtil;
	private RawDump rawDump;

	private Classification latestClassification;

	/**
	 * broadcastReceiver that receive phone's screen state
	 */
	private BroadcastReceiver myScreenReceiver = new BroadcastReceiver() {

		public void onReceive(Context arg0, Intent arg1) {

			if (arg1.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
				Log.i("screen", "off");
				if (!PARTIAL_WAKE_LOCK_MANAGER_IsAcquired) {
					SCREEN_DIM_WAKE_LOCK_MANAGER_IsAcquired = false;
					applyWakeLock(true);
				}
			} else
				if (arg1.getAction().equals(Intent.ACTION_SCREEN_ON)) {
					Log.i("screen", "on");
				}
		}
	};

	/**
	 * Broadcast receiver for battery manager
	 */
	private BroadcastReceiver myBatteryReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context arg0, Intent arg1) {
			int status = arg1.getIntExtra("plugged", -1);
			if (status != 0) {
				charging = true;
			} else {
				charging = false;
			}
		}
	};

	/**
	 * Performs screen wake lock depends on the screen on/off
	 * 
	 * @param wakelock
	 */
	private void applyWakeLock(boolean wakelock) {
		/*
		 * if wake lock is set, PARTIAL_WAKE_LOCK is released, then use
		 * SCREEN_DIM_WAKE_LOCK to turn the screen on.
		 */
		if (wakelock) {
			Log.i("Wakelock", "true? " + wakelock);
			if (PARTIAL_WAKE_LOCK_MANAGER != null) {
				PARTIAL_WAKE_LOCK_MANAGER.release();
				PARTIAL_WAKE_LOCK_MANAGER = null;
				PARTIAL_WAKE_LOCK_MANAGER_IsAcquired = false;
				Log.i("Wakelock", "PARTIAL_WAKE_LOCK_MANAGER is released");
			}
			if (!SCREEN_DIM_WAKE_LOCK_MANAGER_IsAcquired) {
				SCREEN_DIM_WAKE_LOCK_MANAGER = pm.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP
						| PowerManager.SCREEN_DIM_WAKE_LOCK, "screen onon");
				SCREEN_DIM_WAKE_LOCK_MANAGER.acquire();
				SCREEN_DIM_WAKE_LOCK_MANAGER_IsAcquired = true;
				Log.i("Wakelock", "SCREEN_DIM_WAKE_LOCK_MANAGE is 2acquired");
			}
		} else {
			Log.i("Wakelock", "false? " + wakelock);
			if (!PARTIAL_WAKE_LOCK_MANAGER_IsAcquired) {
				PARTIAL_WAKE_LOCK_MANAGER = pm.newWakeLock(	PowerManager.PARTIAL_WAKE_LOCK,
				"Activity recorder");
				PARTIAL_WAKE_LOCK_MANAGER.acquire();
				PARTIAL_WAKE_LOCK_MANAGER_IsAcquired = true;
				Log.i("Wakelock", "PARTIAL_WAKE_LOCK_MANAGER is acquired");
			}
			if (SCREEN_DIM_WAKE_LOCK_MANAGER != null) {
				SCREEN_DIM_WAKE_LOCK_MANAGER.release();
				SCREEN_DIM_WAKE_LOCK_MANAGER = null;
				SCREEN_DIM_WAKE_LOCK_MANAGER_IsAcquired = false;
			}
		}
	}

	/**
	 * when the connection is established, some information such as
	 * classification name, service running state, wake lock state, phone
	 * information are passed in this RecorderService.
	 */
	private final ActivityRecorderBinder.Stub binder = new ActivityRecorderBinder.Stub() {

		private Handler mainLooperHandler = null;

		//	To use that main thread's looper, we get a reference to it, and create a handler.
		//	Later any events that require the current thread to have a looper,
		//	(e.g. toasts) can post as a <code>Runnable</code> and the <code>Runnable</code>
		//	would be executed in the main thread.
		private Handler getMainLooperHandler() {

			if (mainLooperHandler!=null) {
				return mainLooperHandler;
			}
			else {
				Looper mainLooper = RecorderService.this.getMainLooper();

				if (mainLooper==null)
					return null;

				mainLooperHandler = new Handler(mainLooper);
			}

			return mainLooperHandler;
		}

		public void submitClassification(long sampleTime, String classification, double eeAct, double met) throws RemoteException {
			Log.i(Constants.DEBUG_TAG, "Recorder Service: Received classification: '" + classification + "'");
			updateScores(sampleTime, classification, eeAct, met);
		}

		public List<Classification> getClassifications() throws RemoteException {
			return Collections.emptyList();
		}

		public boolean isRunning() throws RemoteException {
			return running;
		}

		public void setWakeLock() throws RemoteException {
			Log.i("Wakelock", "GOt messege setWakelock from Setting");
			boolean wakelock = optionsTable.isWakeLockSet();
			Log.i("Wakelock", " " + wakelock);
			applyWakeLock(wakelock);

		}

		public void showServiceToast(final String message) {
			Handler mainLooperHandler = getMainLooperHandler();

			mainLooperHandler.post(new Runnable() {
				public void run() {
					Toast.makeText(RecorderService.this, message, Toast.LENGTH_LONG).show();
				}
			});
		}
		
		@Override
		public void handleHardwareFaultException(String title, String msg)
				throws RemoteException {
			RecorderService.this.handleHardwareFaultException(title, msg);
		}
		
	};

	private final Runnable registerRunnable = new Runnable() {

		public void run() {

			//	if the sampler is not sampling...
			if (!sampler.isSampling()) {
				//	take an empty batch and give it to the sampler to sample...
				try {
					//	to make the sample timing more accurate,
					//		we invoke the gc before we start sampling,
					//		in the hopes that it wont happen in the sampling
					//		period.
					System.gc();

					//					Log.v(Constants.DEBUG_TAG, "Sending an empty batch for sampling.");

					//	please note that this function blocks until an empty batch is found
					//	which is why it is important to start with a sufficient number of batches
					//	and process the batches as fast as possible
					SampleBatch batch = batchBuffer.takeEmptyInstance();
					Log.i(Constants.DEBUG_TAG, "Sampling Batch");
					sampler.start(batch);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			//	run next batch after some time
			handler.postDelayed(registerRunnable, Constants.DELAY_SAMPLE_BATCH);
		}

	};

	//	called by the sampler when sampling a batch of samples is done.
	private final SamplerCallback samplerCallback = new SamplerCallback() {

		@Override
		public void samplerFinished(SampleBatch batch) {
			if(batch!=null)
				//	set any required properties
				batch.setCharging(charging);

			//	put it back into the buffer as a filled batch
			try {
				batchBuffer.returnFilledInstance(batch);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void samplerStopped(SampleBatch batch) {
			//	put it back into the buffer as a filled batch
			try {
				batchBuffer.returnEmptyInstance(batch);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void samplerError(SampleBatch batch, Exception e) {
			//	put it back into the buffer as a filled batch
			try {
				batchBuffer.returnEmptyInstance(batch);
			} catch (InterruptedException ex) {
				ex.printStackTrace();
			}
		}

	};


	private void updateScores(long sampleTime, String best, double eeAct, double met) {
		long start = sampleTime;
		long end = sampleTime+Constants.DELAY_SAMPLE_BATCH;


		if (latestClassification!=null
				&& best.equals(latestClassification.getClassification())) {
			
			latestClassification.setNumberOfBatches(latestClassification.getNumberOfBatches()+1);
			latestClassification.setTotalEeAct(latestClassification.getTotalEeAct()+(float)eeAct);
			latestClassification.setTotalMet(latestClassification.getTotalMet()+(float)met);
			latestClassification.setEnd(end);
			
			activityWatcher.processLatest(latestClassification);
			activitiesTable.update(latestClassification);
		} else {
			if (latestClassification==null) {
				latestClassification = new Classification(best, start, end);
				latestClassification.withContext(this);
			} else {
				latestClassification.init(best, start, end);
				latestClassification.withContext(this);
			}
			
			latestClassification.setNumberOfBatches(1);
			latestClassification.setTotalEeAct((float)eeAct);
			latestClassification.setTotalMet((float)met);

			activityWatcher.processLatest(latestClassification);
			activitiesTable.insert(latestClassification);
		}

		if (Constants.OUTPUT_DEBUG_INFO) {
			debugDataTable.updateFinalSystemOutput(sampleTime, best);
		}


	}

	/**
	 * 
	 */
	@Override
	public IBinder onBind(Intent arg0) {
		return binder;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler(RecorderService.this));
		//		if (Constants.IS_DEV_VERSION) {
		//			LogRedirect.redirect(new File(Constants.PATH_SD_CARD_LOG));			
		//		}
		Log.v(Constants.DEBUG_TAG, "RecorderService.onCreate()");
	}

	/**
	 * 
	 */
	@Override
	public void onStart(final Intent intent, final int startId) {
		super.onStart(intent, startId);

		Log.v(Constants.DEBUG_TAG, "RecorderService.onStart()");

		//	all the initialization code that was here,
		//		has been moved to the run() function		
		(new Thread(this, RecorderService.class.getName()+"-Thread")).start();
		running = true;
	}

	@Override
	public void onLowMemory() {
		super.onLowMemory();
		Log.v(Constants.DEBUG_TAG, "Low Memory Call Received");
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		Log.v(Constants.DEBUG_TAG, "RecorderService.onDestroy()");

		stopService();
	}
	
	public void stopService() {
		if (running) {
			running = false;

			//	destroy any required features of the service
			destroyService();

			//	signal the looper to exit
			if (threadLooper!=null)
				threadLooper.quit();

		}
	}

	public void run() {		
		Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler(this));

		//	create a looper for this thread
		Looper.prepare();

		//	obtain a reference to the looper, and create a handler for the looper
		this.threadLooper = Looper.myLooper();
		this.handler = new Handler(threadLooper);

		//	initialise any required features of the service
		initService();

		//	loop, processing any messages queued
		//		please note that this function blocks until the looper's quit function is called
		//		for this case, the quit function is called when the service is being destroyed
		//		in the onDestroy method.
		Looper.loop();

		Log.v(Constants.DEBUG_TAG, "Recorder Service Exitting!!");		
	}

	/**
	 * This code is run in the service's separate thread.
	 * The code is used to initialise all the features of the
	 * 	service when the service starts running, then set
	 * the state of the service to running state.
	 */
	private void initService()
	{

		pm = (PowerManager) getSystemService(Context.POWER_SERVICE);

		// receive phone battery status
		this.registerReceiver(	this.myBatteryReceiver,
				new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		this.registerReceiver(this.myScreenReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
		this.registerReceiver(this.myScreenReceiver, new IntentFilter(Intent.ACTION_SCREEN_ON));

		sqlLiteAdapter = SqlLiteAdapter.getInstance(this);
		optionsTable = sqlLiteAdapter.getOptionsTable();
		debugDataTable = sqlLiteAdapter.getDebugDataTable();
		activitiesTable = sqlLiteAdapter.getActivitiesTable();

		phoneInfo = new PhoneInfo(this);
		batchBuffer = new SampleBatchBuffer();
		
		if (Constants.OUTPUT_DEBUG_INFO && Constants.OUTPUT_RAW_DATA)
			rawDump = new RawDump();
		else
			rawDump = null;

		latestClassification = null;

		activityWatcher = new ActivityWatcher(this);
		activityWatcher.init();
		
		//		optionQuery.setServiceRunningState(true);
		applyWakeLock(optionsTable.isWakeLockSet());
		//		optionQuery.save();

		/*
		 * if the background service is dead somehow last time, there is no clue when the service is finished.
		 * Check the last activity name whether it's finished properly or not by the activity name "END",
		 * then if it was not "END", then insert "END" data into the database with the end time of the last activity. 
		 */
		{
			Classification lastClassification = new Classification();
			//	load the latest classification available (false if the database is empty)
			if (activitiesTable.loadLatest(lastClassification)) {
				if(!ActivityNames.END.equals(lastClassification.getClassification())){
					// the beginning of the new activity is the end of the last + 1,
					//	we have to add one because if the end of the last activity is the same as its beginning (no duration passed)
					//	then having another activity with the same start throws an exception in the INSERT statement
					long start = lastClassification.getEnd() + 1;
					Classification endClass = new Classification(ActivityNames.END, start, start);
					activitiesTable.insert(endClass);
				}
			} else {
				long start = System.currentTimeMillis();
				Classification endClass = new Classification(ActivityNames.END, start, start);
				activitiesTable.insert(endClass);
			}
		}

		AsyncAccelReader reader = new AsyncAccelReader(this);
		sampler = new AsyncSampler(binder, reader, samplerCallback);

		//        SyncAccelReader reader = new SyncAccelReaderFactory().getReader(this);
		//		sampler = new SyncSampler(reader, analyseRunnable);
		
		metUtil = new MetUtilOrig(90.0, 185.0, 26.0, MetUtilFinal.GENDER_MALE);
		
		classifierThread = new ClassifierThread(this, binder, batchBuffer, metUtil, rawDump);
		classifierThread.start();

		// if the account wasn't previously sent,
		// start a thread to register the account.
		if (!optionsTable.isAccountSent()) {
			registerAccountThread = new AccountThread(this, binder, phoneInfo);
			registerAccountThread.start();
		} else {
			registerAccountThread = null;
		}

		// start to upload un-posted activities to Web server
		uploadActivityHistory = new UploadActivityHistoryThread(this, phoneInfo);		
		uploadActivityHistory.startUploads();

		handler.postDelayed(registerRunnable, Constants.DELAY_SAMPLE_BATCH);

		//	if the service started successfully, then start set the system to show
		//		that the service started
		optionsTable.setServiceUserStarted(true);
		optionsTable.save();

		//	put on-going notification to show that service is running in the background
		NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

		int icon = R.drawable.icon;
		CharSequence tickerText = "Avocado AC Running";
		long when = System.currentTimeMillis();
		Notification notification = new Notification(icon, tickerText, when);

		notification.defaults = 0;
		notification.flags = Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;

		Context context = getApplicationContext();
		CharSequence contentTitle = "Avocado AC";

		CharSequence contentText;

		if (ClassifierThread.bForceCalibration)
			contentText = "Avocado AC calibration is running";
		else
			contentText = "Avocado AC service is running";

		Intent notificationIntent = new Intent(this, MainTabActivity.class);
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
		notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);

		//notificationManager.notify(ONGOING_NOTIFICATION_ID, notification);
		this.startForeground(ONGOING_NOTIFICATION_ID, notification);
	}

	/**
	 * The code is used to destroy all the features of the
	 * 	service when the service is being destroyed. 
	 */
	private void destroyService()
	{
		//	put on-going notification to show that service is running in the background
		//NotificationManager notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		//notificationManager.cancel(ONGOING_NOTIFICATION_ID);
		this.stopForeground(true);

		//	stop sampling
		handler.removeCallbacks(registerRunnable);
		if (sampler != null) {
			sampler.stop();
		}

		//	stop threads
		uploadActivityHistory.cancelUploads();
		classifierThread.exit();
		if (registerAccountThread!=null) {
			registerAccountThread.exit();
		}

		activityWatcher.done();

		// save message "END" to recognise when the background service is
		// finished.
		long start = System.currentTimeMillis();
		Classification endClass = new Classification(ActivityNames.END, start, start);
		activitiesTable.insert(endClass);
		MainTabActivity.serviceIsRunning = false;

		this.unregisterReceiver(myBatteryReceiver);
		this.unregisterReceiver(myScreenReceiver);

		if (PARTIAL_WAKE_LOCK_MANAGER != null) {
			PARTIAL_WAKE_LOCK_MANAGER.release();
			PARTIAL_WAKE_LOCK_MANAGER = null;
		}
		if (SCREEN_DIM_WAKE_LOCK_MANAGER != null) {
			SCREEN_DIM_WAKE_LOCK_MANAGER.release();
			SCREEN_DIM_WAKE_LOCK_MANAGER = null;
		}

	}

	protected void handleHardwareFaultException(String title, String msg) {
		NotificationManager nm = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
		AlarmManager am = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
		Context context = getApplicationContext();
		
		// put up the fault notification
		Intent notificationIntent = new Intent(this, MainTabActivity.class);
		PendingIntent pendingNotIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
		
		Notification notification = new Notification(
				R.drawable.icon,
				title,
				System.currentTimeMillis());
		notification.defaults = Notification.DEFAULT_ALL;
		notification.flags = Notification.FLAG_ONLY_ALERT_ONCE;
		notification.setLatestEventInfo(context, title, msg+ " Please restart service, after restarting phone.", pendingNotIntent);
		nm.notify(Constants.NOTIFICATION_ID_HARDWARE_FAULT, notification);
		
		//	turn off the service
		this.stopSelf();
		stopService();
		optionsTable.setServiceUserStarted(false);
		optionsTable.save();
		
//		//	set an alarm to turn it on after a short while
//		Intent alarmIntent = new Intent(this, RecorderService.class);
//		PendingIntent pendingAlarmIntent = PendingIntent.getService(this, 0, alarmIntent, 0);
//		am.set(	AlarmManager.RTC,
//				System.currentTimeMillis()+Constants.DURATION_SLEEP_AFTER_FAULT,
//				pendingAlarmIntent	);
	}

}
