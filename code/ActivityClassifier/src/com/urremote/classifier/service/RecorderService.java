/*
 * To change this template, choose Tools | Templates and open the template in
 * the editor.
 */

package com.urremote.classifier.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.urremote.classifier.accel.SampleBatch;
import com.urremote.classifier.accel.SampleBatchBuffer;
import com.urremote.classifier.accel.Sampler;
import com.urremote.classifier.accel.SamplerCallback;
import com.urremote.classifier.accel.async.AsyncAccelReader;
import com.urremote.classifier.accel.async.AsyncSampler;
import com.urremote.classifier.activity.MainTabActivity;
import com.urremote.classifier.auth.AuthManager;
import com.urremote.classifier.common.ActivityNames;
import com.urremote.classifier.common.Constants;
import com.urremote.classifier.common.ExceptionHandler;
import com.urremote.classifier.db.ActivitiesTable;
import com.urremote.classifier.db.DebugDataTable;
import com.urremote.classifier.db.OptionUpdateHandler;
import com.urremote.classifier.db.OptionsTable;
import com.urremote.classifier.db.SqlLiteAdapter;
import com.urremote.classifier.fusiontables.FusionTableActivitySync;
import com.urremote.classifier.rpc.Classification;
import com.urremote.classifier.service.threads.AccountThread;
import com.urremote.classifier.service.threads.ClassifierThread;
import com.urremote.classifier.service.threads.UploadActivityHistoryThread;
import com.urremote.classifier.utils.ActivityWatcher;
import com.urremote.classifier.utils.MetUtilFinal;
import com.urremote.classifier.utils.MetUtilOrig;
import com.urremote.classifier.utils.PhoneInfo;
import com.urremote.classifier.utils.RawDump;

import com.urremote.classifier.R;
import com.urremote.classifier.rpc.ActivityRecorderBinder;
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
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.util.Log;
import android.widget.Toast;

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

	//private UploadActivityHistoryThread uploadActivityHistory;
	private AuthManager authManager;
	private FusionTableActivitySync activityTableSync;

	private SqlLiteAdapter sqlLiteAdapter;
	private OptionsTable optionsTable;
	private DebugDataTable debugDataTable;
	private ActivitiesTable activitiesTable;

	private boolean running;

	private PowerManager powerManager;
	private PowerManager.WakeLock partialWakeLock;
	private boolean partialWakeLockShouldBeOn;
	private AlarmManager alarmManager;
	private PendingIntent pendingSamplingIntent;
	private ExecutorService samplingExecutorService;
	
	private NotificationManager notificationManager;
	
	private final List<Classification> adapter = new ArrayList<Classification>();

	private PhoneInfo phoneInfo;

	private SampleBatchBuffer batchBuffer;
	private ClassifierThread classifierThread;
	private AccountThread registerAccountThread;
	private ActivityWatcher activityWatcher;
	private MetUtilOrig metUtil;
	private RawDump rawDump;
	
	private boolean hardwareNotificationOn = false;
	private long hardwareNotificationStartTime = 0;

	private Classification latestClassification = new Classification();
	
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
	 * Option table update handler
	 */
	private OptionUpdateHandler optionUpdateHandler = new OptionUpdateHandler() {
		public void onFieldChange(Set<String> updatedKeys) {
			if (updatedKeys.contains(OptionsTable.KEY_FULLTIME_ACCEL)) {
				applyWakeLock(partialWakeLockShouldBeOn);
			}
		}
	};
	
	/**
	 * Performs screen wake lock depends on the screen on/off
	 * 
	 * @param wakelock
	 */
	private void applyWakeLock(boolean wakelock) {
		if (partialWakeLock!=null) {
			if (wakelock && !partialWakeLock.isHeld()) {
				partialWakeLock.acquire();
				partialWakeLockShouldBeOn = true;
			}
			if (!wakelock && partialWakeLock.isHeld()) {
				if (!optionsTable.getFullTimeAccel()) {
					partialWakeLock.release();
				}
				partialWakeLockShouldBeOn = false;
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
			Log.i(Constants.TAG, "Recorder Service: Received classification: '" + classification + "'");
			updateScores(sampleTime, classification, eeAct, met);
		}

		public List<Classification> getClassifications() throws RemoteException {
			return Collections.emptyList();
		}

		public boolean isRunning() throws RemoteException {
			return running;
		}
		
		public void showServiceToast(final String message) {
			Handler mainLooperHandler = getMainLooperHandler();

			mainLooperHandler.post(new Runnable() {
				public void run() {
					Toast.makeText(RecorderService.this, message, Toast.LENGTH_LONG).show();
				}
			});
		}
		
		public boolean isHardwareNotificationOn() {
			return hardwareNotificationOn;
		}
		
		public void handleHardwareFaultException(String title, String msg)
				throws RemoteException {
			RecorderService.this.handleHardwareFaultException(title, msg);
		}
		
		public void cancelHardwareNotification() {
			RecorderService.this.cancelHardwareNotification();
		}
	};
	
	private BroadcastReceiver startSamplingBroadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (!samplingExecutorService.isShutdown()) {
				Log.i(Constants.TAG, "Sampling Broadcast Receiver received sampling notification");
				if (!sampler.isSampling()) {
					samplingExecutorService.submit(samplingInvoker);
				}
			}
		}
	};
	
	/**
	 * Sampling has to be started in another thread, since it's time consuming.
	 * If done in the main UI thread, the UI gets unresponsive and the application crashes.
	 */
	private Runnable samplingInvoker = new Runnable() {
		
		public void run() {
			//	if the sampler is not sampling...
			if (!sampler.isSampling()) {
				applyWakeLock(true); // start of sampling/classification cycle, turn on wake lock
				
				//	take an empty batch and give it to the sampler to sample...
				try {
					//	to make the sample timing more accurate,
					//		we invoke the gc before we start sampling,
					//		in the hopes that it wont happen in the sampling
					//		period.
					System.gc();

					//					Log.v(Constants.TAG, "Sending an empty batch for sampling.");

					//	please note that this function blocks until an empty batch is found
					//	which is why it is important to start with a sufficient number of batches
					//	and process the batches as fast as possible
					SampleBatch batch = batchBuffer.takeEmptyInstance();
					Log.i(Constants.TAG, "Sampling Batch");
					sampler.start(batch);
					
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	};

	//	called by the sampler when sampling a batch of samples is done.
	private final SamplerCallback samplerCallback = new SamplerCallback() {

		public void samplerFinished(SampleBatch batch) {
			if (batch.getSize()==0) {
				samplerStopped(batch);
			} else {
				//	set any required properties
				batch.setCharging(charging);
				
				//	put it back into the buffer as a filled batch
				try {
					batchBuffer.returnFilledInstance(batch);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}

		public void samplerStopped(SampleBatch batch) {
			//	put it back into the buffer as a filled batch
			try {
				batchBuffer.returnEmptyInstance(batch);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

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
		
		if (activitiesTable.loadLatest(latestClassification)) {
			long durationSinceLast = start-latestClassification.getEnd()-2;
			
			if (durationSinceLast>Constants.DURATION_EMPTY_INSERT_UNKNOWN) {
				Classification unknown = new Classification(ActivityNames.UNKNOWN, latestClassification.getEnd()+1, start-1);
				unknown.withContext(this);
				unknown.setNumberOfBatches(1);
				unknown.setMet(1.0f);
				
				insertOrUpdate(latestClassification, unknown);
				
				latestClassification = unknown;
			}
			
			Classification newClass = new Classification(best, start, end);
			newClass.withContext(this);
			newClass.setNumberOfBatches(1);
			newClass.setMet((float)met);
			insertOrUpdate(latestClassification, newClass);
		} else {
			latestClassification.init(best, start, end);
			latestClassification.withContext(this);
			latestClassification.setNumberOfBatches(1);
			latestClassification.setMet((float)met);

			insertOrUpdate(null, latestClassification);
		}
		
		if (Constants.OUTPUT_DEBUG_INFO) {
			debugDataTable.updateFinalSystemOutput(sampleTime, best);
		}
		
		applyWakeLock(false); // end of sampling/classification cycle, turn off wake lock after updating
	}

	private void insertOrUpdate(Classification latestClassification, Classification newClassification) {
		if (latestClassification==null) {
			activityWatcher.processLatest(newClassification);
			activitiesTable.insert(newClassification);
		} else {
			if (latestClassification.getEnd()<newClassification.getStart()) {
				latestClassification.setEnd(newClassification.getStart()-1);
				activitiesTable.update(latestClassification);
			}
			
			if (latestClassification.getClassification().equals(newClassification.getClassification())) {
				int prevNumBatches = latestClassification.getNumberOfBatches();
				int currNumBatches = newClassification.getNumberOfBatches();
				int totalNumBatches = prevNumBatches + currNumBatches;
				
				double totalMet = latestClassification.getMet()*prevNumBatches + newClassification.getMet()*currNumBatches;
				
				latestClassification.setNumberOfBatches(totalNumBatches);
				latestClassification.setMet((float)(totalMet / totalNumBatches));
				latestClassification.setEnd(newClassification.getEnd());
				
				activityWatcher.processLatest(latestClassification);
				activitiesTable.update(latestClassification);
			} else {
				activityWatcher.processLatest(newClassification);
				activitiesTable.insert(newClassification);
			}
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
		Log.v(Constants.TAG, "RecorderService.onCreate()");
	}

	/**
	 * 
	 */
	@Override
	public void onStart(final Intent intent, final int startId) {
		super.onStart(intent, startId);

		Log.v(Constants.TAG, "RecorderService.onStart()");

		//	all the initialization code that was here,
		//		has been moved to the run() function		
		(new Thread(this, RecorderService.class.getName()+"-Thread")).start();
		running = true;
	}

	@Override
	public void onLowMemory() {
		super.onLowMemory();
		Log.v(Constants.TAG, "Low Memory Call Received");
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		Log.v(Constants.TAG, "RecorderService.onDestroy()");

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

		Log.v(Constants.TAG, "Recorder Service Exitting!!");		
	}

	/**
	 * This code is run in the service's separate thread.
	 * The code is used to initialise all the features of the
	 * 	service when the service starts running, then set
	 * the state of the service to running state.
	 */
	private void initService()
	{
		powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
		partialWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, Constants.TAG);
		alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		samplingExecutorService = Executors.newSingleThreadExecutor();
		
		// receive phone battery status
		this.registerReceiver(this.myBatteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		
		sqlLiteAdapter = SqlLiteAdapter.getInstance(this);
		optionsTable = sqlLiteAdapter.getOptionsTable();
		debugDataTable = sqlLiteAdapter.getDebugDataTable();
		activitiesTable = sqlLiteAdapter.getActivitiesTable();
		
		optionsTable.registerUpdateHandler(optionUpdateHandler);

		phoneInfo = new PhoneInfo(this);
		batchBuffer = new SampleBatchBuffer();
		
		if (shouldBeRunningRawDump()) {
			IntentFilter mountedFilter = new IntentFilter(Intent.ACTION_MEDIA_MOUNTED); 
			mountedFilter.addDataScheme("file"); 
			registerReceiver(this.sdCardMountedReceiver, new IntentFilter(mountedFilter));
			IntentFilter unmountedFilter = new IntentFilter(Intent.ACTION_MEDIA_EJECT); 
			unmountedFilter.addDataScheme("file"); 
			registerReceiver(this.sdCardMountedReceiver, new IntentFilter(unmountedFilter));
			
			if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
				rawDump = new RawDump();
			else {
				rawDump = null;
			}
		}
		else {
			rawDump = null;
		}
		
		activityWatcher = new ActivityWatcher(this);
		activityWatcher.init();
		
		/*
		 * if the background service is dead somehow last time, there is no clue when the service is finished.
		 * Check the last activity name whether it's finished properly or not by the activity name "END",
		 * then if it was not "END", then insert "END" data into the database with the end time of the last activity.
		 * 
		 * No more inserting 'END', instead insert an 'off' activity for the duration from the last
		 * classification till now.
		 */
		{
			Classification lastClassification = new Classification();
			//	load the latest classification available (false if the database is empty)
			if (activitiesTable.loadLatest(lastClassification)) {
				long end = System.currentTimeMillis();
				if (lastClassification.getEnd()>end) {
					lastClassification.setEnd(end-1);
					activitiesTable.update(lastClassification);
				}
				long start = lastClassification.getEnd() + 1;
				
				if (ActivityNames.OFF.equals(lastClassification.getClassification())) {
					lastClassification.setEnd(end);
					activitiesTable.update(lastClassification);
				} else {
					Classification offClass = new Classification(ActivityNames.OFF, start, end);
					offClass.setMet(1.0f);
					activitiesTable.insert(offClass);
				}
			} else {
//				long start = System.currentTimeMillis();
//				Classification endClass = new Classification(ActivityNames.END, start, start);
//				activitiesTable.insert(endClass);
			}
		}

		AsyncAccelReader reader = new AsyncAccelReader(this);
		sampler = new AsyncSampler(binder, reader, samplerCallback, handler);

		//        SyncAccelReader reader = new SyncAccelReaderFactory().getReader(this);
		//		sampler = new SyncSampler(reader, analyseRunnable);
		
		metUtil = new MetUtilOrig(90.0, 185.0, 26.0, MetUtilFinal.GENDER_MALE);
		
		classifierThread = new ClassifierThread(this, binder, batchBuffer, metUtil, rawDump);
		classifierThread.start();

		// if the account wasn't previously sent,
		// start a thread to register the account.
		if (!optionsTable.isAccountSent()) {
			//registerAccountThread = new AccountThread(this, binder, phoneInfo);
			//registerAccountThread.start();
		} else {
			registerAccountThread = null;
		}

		// start to upload un-posted activities to Web server
		activityTableSync = new FusionTableActivitySync(this);

		String samplingIntentAction = Constants.DEFAULT_PACKAGE+".intent.sampling.start";
		Intent samplingIntent = new Intent(samplingIntentAction);
		pendingSamplingIntent = PendingIntent.getBroadcast(this, 0, samplingIntent, 0);
		alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, 
				Constants.DELAY_SAMPLE_BATCH, Constants.DELAY_SAMPLE_BATCH, pendingSamplingIntent);
		this.registerReceiver(startSamplingBroadcastReceiver, new IntentFilter(samplingIntentAction));

		//	if the service started successfully, then start set the system to show
		//		that the service started
		optionsTable.setServiceUserStarted(true);
		optionsTable.save();

		//	put on-going notification to show that service is running in the background
		notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

		int icon = R.drawable.icon;
		CharSequence tickerText = "Avocado AC Running";
		long when = System.currentTimeMillis();
		Notification notification = new Notification(icon, tickerText, when);

		notification.defaults = 0;
		notification.flags = Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;

		Context context = getApplicationContext();
		CharSequence contentTitle = "Avocado AC";

		CharSequence contentText;
		if (ClassifierThread.forceCalibration)
			contentText = "Avocado AC calibration is running";
		else
			contentText = "Avocado AC service is running";

		Intent notificationIntent = new Intent(this, MainTabActivity.class);
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
		notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
		
		this.startForeground(ONGOING_NOTIFICATION_ID, notification);
	}

	/**
	 * The code is used to destroy all the features of the
	 * 	service when the service is being destroyed. 
	 */
	private void destroyService()
	{
		this.stopForeground(true);
		
		optionsTable.unregisterUpdateHandler(optionUpdateHandler);

		//	stop sampling
		if (sampler != null) {
			sampler.stop();
		}
		this.unregisterReceiver(startSamplingBroadcastReceiver);
		this.alarmManager.cancel(pendingSamplingIntent);
		this.samplingExecutorService.shutdown();

		//	stop threads
		//uploadActivityHistory.cancelUploads();
		activityTableSync.quit();
		classifierThread.exit();
		if (registerAccountThread!=null) {
			registerAccountThread.exit();
		}

		activityWatcher.done();

		// save message "END" to recognise when the background service is
		// finished.
		//	No more inserting 'END', an 'OFF' will be inserted once the service is
		// 		started again, with the duration the service was off
//		long start = System.currentTimeMillis();
//		Classification endClass = new Classification(ActivityNames.END, start, start);
//		activitiesTable.insert(endClass);
		MainTabActivity.serviceIsRunning = false;

		this.unregisterReceiver(myBatteryReceiver);

		if (partialWakeLock!=null) {
			if (partialWakeLock.isHeld())
				partialWakeLock.release();
			partialWakeLock = null;
		}

	}

	protected void handleHardwareFaultException(String title, String msg) {
		hardwareNotificationStartTime = System.currentTimeMillis();
		handler.postDelayed(cancelHardwareNotificationRunnable, Constants.DURATION_KEEP_HARDWARE_NOTIFICATION);
		
		Log.d(Constants.TAG, "Displaying Faulty Hardware Notification");
		hardwareNotificationOn = true;
		
		Context context = getApplicationContext();
		
		// put up the fault notification
		Intent notificationIntent = new Intent(this, MainTabActivity.class);
		PendingIntent pendingNotIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
		
		Notification notification = new Notification(
				R.drawable.icon,
				title,
				System.currentTimeMillis());
		if (Constants.IS_DEBUGGING) {
			notification.defaults = Notification.DEFAULT_ALL;
		} else {
			notification.defaults = 0;			
		}
		notification.flags = Notification.FLAG_ONLY_ALERT_ONCE;
		notification.setLatestEventInfo(context, title, msg, pendingNotIntent);
		notificationManager.notify(Constants.NOTIFICATION_ID_HARDWARE_FAULT, notification);
		
		//	turn off the service
//		this.stopSelf();
//		stopService();
//		optionsTable.setServiceUserStarted(false);
//		optionsTable.save();
		
//		//	set an alarm to turn it on after a short while
//		Intent alarmIntent = new Intent(this, RecorderService.class);
//		PendingIntent pendingAlarmIntent = PendingIntent.getService(this, 0, alarmIntent, 0);
//		alarmManager.set(	AlarmManager.RTC,
//				System.currentTimeMillis()+Constants.DURATION_SLEEP_AFTER_FAULT,
//				pendingAlarmIntent	);
	}
	
	private Runnable cancelHardwareNotificationRunnable = new Runnable() {
		public void run() {
			if (hardwareNotificationOn) {
				long currentTime = System.currentTimeMillis();
				if (currentTime-hardwareNotificationStartTime>=Constants.DURATION_KEEP_HARDWARE_NOTIFICATION) {
					cancelHardwareNotification();
				}
			}
		}
	};
	
	protected void cancelHardwareNotification() {
		if (hardwareNotificationOn) {
			hardwareNotificationOn = false;
			Log.d(Constants.TAG, "Cancelling Faulty Hardware Notification");
			notificationManager.cancel(Constants.NOTIFICATION_ID_HARDWARE_FAULT);
		}
	}
	
	private boolean shouldBeRunningRawDump() {
		return (Constants.OUTPUT_DEBUG_INFO && Constants.OUTPUT_RAW_DATA);
	}
	
	private BroadcastReceiver sdCardMountedReceiver = new BroadcastReceiver() {
		@Override
		synchronized
		public void onReceive(Context arg0, Intent intent) {
			if (shouldBeRunningRawDump()) {
				if (rawDump==null && Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
					rawDump = new RawDump();
				}
				if (rawDump!=null && !Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
					rawDump = null;
				}
			}
		}
	};

}
