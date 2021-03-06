package com.urremote.classifier.activity;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Set;

import com.urremote.classifier.R;
import com.urremote.classifier.rpc.ActivityRecorderBinder;

import android.accounts.Account;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.TabActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import com.flurry.android.FlurryAgent;
import com.urremote.classifier.activity.AccountChooser.AccountHandler;
import com.urremote.classifier.common.Constants;
import com.urremote.classifier.common.DbFileUtil;
import com.urremote.classifier.db.Migrator;
import com.urremote.classifier.db.OptionUpdateHandler;
import com.urremote.classifier.db.OptionsTable;
import com.urremote.classifier.db.SqlLiteAdapter;
import com.urremote.classifier.service.RecorderService;
import com.urremote.classifier.service.threads.ClassifierThread;
import com.urremote.classifier.utils.Calibrator;
import com.urremote.classifier.utils.MyTracksIntergration;

public class MainSettingsActivity extends PreferenceActivity {

	private static final int DIALOG_YES_NO_MESSAGE_FOR_DELETION = 0;
	private static final int DIALOG_YES_NO_MESSAGE_FOR_RESET_CALIBRATION = 1;
	private static final int DIALOG_YES_NO_MESSAGE_FOR_CALIBRATION_START = 2;


	private ActivityRecorderBinder service = null;

	private int isWakeLockSet;
	private boolean wakelock;

	private SqlLiteAdapter sqlLiteAdapter;
	private OptionsTable optionsTable;

	private CheckBoxPreferenceWithLongSummary calibrationSummary; 
	private CheckBoxPreference aggregatePref;
	private CheckBoxPreference fulltimeAccelPref;
	private PreferenceScreen selectAccountPref;
	private CheckBoxPreference fftOnPref; 
	private PreferenceScreen forceCalibPref;
	private PreferenceScreen copyPref;
	private PreferenceScreen restorePref;
	

	private Handler mainLooperHandler;

	private OptionUpdateHandler optionUpdateHandler = new OptionUpdateHandler() {
		public void onFieldChange(Set<String> updatedKeys) {
			if (updatedKeys.contains(OptionsTable.KEY_IS_CALIBRATED)) {
				if (calibrationSummary!=null) {
					mainLooperHandler.post(new Runnable() {
						public void run() {
							String summary = getScreenSummary();
							calibrationSummary.setSummary(summary);
							if(!ClassifierThread.forceCalibration)
							{
								forceCalibPref.setEnabled(true);
								forceCalibPref.setTitle("Start Calibration now");
								forceCalibPref.setSelectable(true);
							}
						}
					});
				}
			}
			if (updatedKeys.contains(OptionsTable.KEY_UPLOAD_ACCOUNT)) {
				if (calibrationSummary!=null) {
					mainLooperHandler.post(new Runnable() {
						public void run() {
							String summary = optionsTable.getUploadAccount();
							selectAccountPref.setSummary(summary==null?"":summary);
						}
					});
				}
			}
			if (updatedKeys.contains(OptionsTable.KEY_IS_SERVICE_USER_STARTED)) {
				if (forceCalibPref!=null) {
					mainLooperHandler.post(new Runnable() {
						public void run() {
							//In case of any changes to the service the force calibration should become false
							// This is to re initiate the manual calibration process.
							ClassifierThread.forceCalibration = false;

							updateServiceStateDependantPreferences();
						}
					});
				}
			}
			
			if (Constants.IS_DEV_VERSION &&
					updatedKeys.contains(OptionsTable.KEY_USE_AGGREGATOR)) {
				mainLooperHandler.post(new Runnable() {
					public void run() {
						aggregatePref.setChecked(optionsTable.getUseAggregator());
					}
				});
				
			}
			
		}
	};
	
	/**
	 * When the Service connection is established in this class
	 */
	private final ServiceConnection connection = new ServiceConnection() {

		public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
			service = ActivityRecorderBinder.Stub.asInterface(iBinder);
			try {
				if(service==null || !service.isRunning()) {
				}
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}

		public void onServiceDisconnected(ComponentName componentName) {
			service = null;
		}


	};


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.sqlLiteAdapter = SqlLiteAdapter.getInstance(this);
		this.optionsTable = this.sqlLiteAdapter.getOptionsTable();
		this.mainLooperHandler = new Handler(this.getMainLooper());
		wakelock=false;
		
		optionsTable.registerUpdateHandler(optionUpdateHandler);

		setPreferenceScreen(createPreferenceHierarchy());
	}
	/**
	 * 
	 */
	protected void onResume() {
		super.onResume();
	}

	/**
	 * 
	 */
	protected void onPause() {
		super.onPause();
	}

	/**
	 * 
	 */
	@Override
	protected void onStart() {
		super.onStart();
		FlurryAgent.onStartSession(this, Constants.FLURRY_SESSION_ID);
		bindToService();
		
		FlurryAgent.onEvent("Settings Viewed");
	}

	/**
	 * 
	 */
	@Override
	protected void onStop() {
		super.onStop();
		getApplicationContext().unbindService(connection);
		FlurryAgent.onEndSession(this);
	}
	/**
	 * 
	 */
	@Override
	protected void onDestroy() {
		optionsTable.unregisterUpdateHandler(optionUpdateHandler);
		super.onDestroy();
	}
	/**
	 * 
	 * @param intent
	 * @return null
	 */
	public IBinder onBind(Intent intent) {
		return null;
	}

	private void bindToService()
	{
		Intent intent = new Intent(this, RecorderService.class);
		if(!getApplicationContext().bindService(intent, connection, Context.BIND_AUTO_CREATE)){
			throw new IllegalStateException("Binding to service failed " + intent);
		}
	}
	
	private PreferenceScreen createPreferenceHierarchy(){

		PreferenceScreen root = getPreferenceManager().createPreferenceScreen(this);

		// Inline preferences 
		PreferenceCategory lockPrefCat = new PreferenceCategory(this);
		lockPrefCat.setTitle("Accelerometer Lock Settings ");
		root.addPreference(lockPrefCat);

		// Toggle preference
//		final CheckBoxPreference screenPref = new CheckBoxPreference(this);
//		screenPref.setKey("screen_preference");
//		screenPref.setTitle("Screen Locked On");
//		screenPref.setSummary("Some phones (e.g. HTC Desire) require this.");
//		wakelock = optionsTable.isWakeLockSet();
//		Log.i("wake",wakelock+"");
//		screenPref.setChecked(wakelock);  
//		Log.i("wake",screenPref.isChecked()+"");
//		//		getApplicationContext().bindService(new Intent(this, RecorderService.class),
//		//				connection, Context.BIND_AUTO_CREATE);
//		screenPref.setOnPreferenceChangeListener(new CheckBoxPreference.OnPreferenceChangeListener(){
//
//			public boolean onPreferenceChange(Preference arg0, Object arg1) {
//				if((Boolean) arg1){
//					wakelock=(Boolean) arg1;//true value
//					Toast.makeText(getBaseContext(), "Screen Locked On", Toast.LENGTH_SHORT).show();
//					//update Wake Lock state to 1 (true)
//					optionsTable.setWakeLockSet(true);
//					optionsTable.save();
//					//					getApplicationContext().unbindService(connection);
//					//					getApplicationContext().bindService(new Intent(getBaseContext(), RecorderService.class),
//					//							connection, Context.BIND_AUTO_CREATE);
//
//				}
//				else{
//					wakelock=(Boolean) arg1;
//					Toast.makeText(getBaseContext(), "Screen Locked Off", Toast.LENGTH_SHORT).show();
//					optionsTable.setWakeLockSet(false);
//					optionsTable.save();
//				}
//				getApplicationContext().unbindService(connection);
//				getApplicationContext().bindService(new Intent(getBaseContext(), RecorderService.class),
//						connection, Context.BIND_AUTO_CREATE);
//				screenPref.setChecked(wakelock);
//
//				return false;
//			}
//
//		});
		
		fulltimeAccelPref = new CheckBoxPreference(this);
		fulltimeAccelPref.setKey("fulltime_accel_preference");
		fulltimeAccelPref.setTitle("Keep Accelerometer On");
		fulltimeAccelPref.setSummary("Keep accelerometer on all the time\n(requires service restart)");
		fulltimeAccelPref.setOnPreferenceChangeListener(new CheckBoxPreference.OnPreferenceChangeListener(){

			public boolean onPreferenceChange(Preference arg0, Object arg1) {
				boolean checked = (Boolean) arg1; 
				optionsTable.setFullTimeAccel(checked);
				optionsTable.save();
				fulltimeAccelPref.setChecked(checked);
				return false;
			}

		});
		fulltimeAccelPref.setChecked(optionsTable.getFullTimeAccel());

		
//		lockPrefCat.addPreference(screenPref);
		lockPrefCat.addPreference(fulltimeAccelPref);

		// Dialog based preferences
		PreferenceCategory calibrationSettingsCat = new PreferenceCategory(this);
		calibrationSettingsCat.setTitle("Calibration Settings");
		root.addPreference(calibrationSettingsCat);

		PreferenceScreen resetPref = getPreferenceManager().createPreferenceScreen(this);
		resetPref.setKey("screen_preference");
		resetPref.setTitle("Re-set Calibration values");
		resetPref.setSummary("Reset calibration values to default.");
		resetPref.setOnPreferenceClickListener(new PreferenceScreen.OnPreferenceClickListener(){

			public boolean onPreferenceClick(Preference preference) {
				showDialog(DIALOG_YES_NO_MESSAGE_FOR_RESET_CALIBRATION);

				return false;
			}

		});

		forceCalibPref = getPreferenceManager().createPreferenceScreen(this);
		forceCalibPref.setKey("screen_preference");
		forceCalibPref.setSummary("Tab to initiate calibration task now.");
		forceCalibPref.setOnPreferenceClickListener(new PreferenceScreen.OnPreferenceClickListener(){

			public boolean onPreferenceClick(Preference preference) {
				//				showDialog(DIALOG_YES_NO_MESSAGE_FOR_CALIBRATION_START);
				//
				//				return false;
				FlurryAgent.onEvent("Initiated Calibration");

				try {
					service.showServiceToast("Performing calibration. Please keep the phone still.");
				} catch (RemoteException e) {
				}

				ClassifierThread.forceCalibration = true;
				//Calibrator.isCalibrated = false;

				preference.setEnabled(false);
				preference.setTitle("Calibration in progress ...");
				preference.setSelectable(false);

				Calibrator.resetCalibrationOptionsForForceCalib(optionsTable);
				//optionsTable.save();
				calibrationSummary.setSummary(getScreenSummary());

				return false;

			}



		});

		//		calibrationSettingsCat.addPreference(resetPref);
		calibrationSettingsCat.addPreference(forceCalibPref);

		this.calibrationSummary =  new CheckBoxPreferenceWithLongSummary(this);
		calibrationSummary.setKey("cali_preference");
		calibrationSummary.setTitle("Current Calibration Values");
		calibrationSummary.setSummary(getScreenSummary());
		calibrationSummary.setSelectable(false);		
		calibrationSettingsCat.addPreference(calibrationSummary);

		PreferenceCategory dataPrefCat = new PreferenceCategory(this);
		dataPrefCat.setTitle("Data settings");
		root.addPreference(dataPrefCat);

		//		PreferenceScreen deletePref = getPreferenceManager().createPreferenceScreen(this);
		//		deletePref.setKey("delete_preference");
		//		deletePref.setTitle("Delete Database");
		//		deletePref.setSummary("Delete all activity data and user information.");
		//		deletePref.setOnPreferenceClickListener(new PreferenceScreen.OnPreferenceClickListener(){
		//
		//			public boolean onPreferenceClick(Preference preference) {
		//				showDialog(DIALOG_YES_NO_MESSAGE_FOR_DELETION);
		//				return false;
		//			}
		//
		//		});
		//		dataPrefCat.addPreference(deletePref);

		this.selectAccountPref = getPreferenceManager().createPreferenceScreen(this);
		selectAccountPref.setKey("select_account_preference");
		selectAccountPref.setTitle("Select data upload account");
		String currentUploadAccount = optionsTable.getUploadAccount();
		selectAccountPref.setSummary(currentUploadAccount==null?"":currentUploadAccount);
		selectAccountPref.setOnPreferenceClickListener(new PreferenceScreen.OnPreferenceClickListener(){
			public boolean onPreferenceClick(Preference preference) {
				AccountChooser accountChooser = new AccountChooser();
				accountChooser.chooseAccount(MainSettingsActivity.this, 
						new AccountHandler() {
					public void onAccountSelected(Account account) {
						if (account!=null) {
							optionsTable.setUploadAccount(account.name);
						}
					}
				});
				return false;
			}
		});
		dataPrefCat.addPreference(selectAccountPref);

		copyPref = getPreferenceManager().createPreferenceScreen(this);
		copyPref.setKey("copy_preference");
		copyPref.setTitle("Copy DB to SD card");
		copyPref.setOnPreferenceClickListener(new PreferenceScreen.OnPreferenceClickListener(){

			public boolean onPreferenceClick(Preference preference) {
				DbFileUtil.copyFileToSd(getBaseContext(), Constants.RECORDS_FILE_NAME, sqlLiteAdapter, false);
				return false;
			}

		});
		dataPrefCat.addPreference(copyPref);

		if (Constants.IS_DEV_VERSION) {
			PreferenceCategory developerPrefCat = new PreferenceCategory(this);
			developerPrefCat.setTitle("Experimental");

			aggregatePref = new CheckBoxPreference(this);
			aggregatePref.setKey("aggregate_preference");
			aggregatePref.setTitle("Aggregate Activities");
			aggregatePref.setSummary("Smoothen activity classification\n(requires service restart)");
			aggregatePref.setOnPreferenceChangeListener(new CheckBoxPreference.OnPreferenceChangeListener(){

				public boolean onPreferenceChange(Preference arg0, Object arg1) {
					boolean checked = (Boolean) arg1; 
					optionsTable.setUseAggregator(checked);
					optionsTable.save();
					aggregatePref.setChecked(checked);
					return false;
				}

			});
			aggregatePref.setChecked(optionsTable.getUseAggregator());

			final CheckBoxPreference invokeMyTracksPref = new CheckBoxPreference(this);
			invokeMyTracksPref.setKey("invoke_mytracks_pref");
			invokeMyTracksPref.setTitle("Invoke Google MyTracks Recording");
			invokeMyTracksPref.setSummary("Automatically invoke Google MyTracks to record while walking");
			invokeMyTracksPref.setOnPreferenceChangeListener(new CheckBoxPreference.OnPreferenceChangeListener(){
				public boolean onPreferenceChange(Preference arg0, Object arg1) {
					boolean newValue = (Boolean) arg1;
					optionsTable.setInvokeMyTracks(newValue);
					optionsTable.save();
					invokeMyTracksPref.setChecked(optionsTable.getInvokeMyTracks());
					return false;
				}
			});
			invokeMyTracksPref.setChecked(optionsTable.getInvokeMyTracks());
			if (!MyTracksIntergration.isMyTracksInstalled(this)) {
				invokeMyTracksPref.setEnabled(false);
				invokeMyTracksPref.setSummary(invokeMyTracksPref.getSummary()+"\nGoogle MyTracks is not installed yet.");
			}
			
			restorePref = getPreferenceManager().createPreferenceScreen(this);
			restorePref.setKey("restore_preference");
			restorePref.setTitle("Restore DB from SD card");
			restorePref.setOnPreferenceClickListener(new PreferenceScreen.OnPreferenceClickListener(){

				public boolean onPreferenceClick(Preference preference) {
					boolean success = DbFileUtil.copyFileFromSd(getBaseContext(), Constants.RECORDS_FILE_NAME, sqlLiteAdapter, false);
					
					if (success) {
						Activity parentActivity = MainSettingsActivity.this.getParent();
						if (parentActivity instanceof TabActivity) {
							TabActivity tabActivity = (TabActivity)parentActivity;
							Intent intent = tabActivity.getIntent();
							tabActivity.finish();
							MainSettingsActivity.this.startActivity(intent);
						} else {
							Intent intent = MainSettingsActivity.this.getIntent();
							MainSettingsActivity.this.finish();
							MainSettingsActivity.this.startActivity(intent);
						}
						Toast.makeText(MainSettingsActivity.this.getApplicationContext(), "UI reset to new DB", Toast.LENGTH_LONG).show();
					}
					
					return false;
				}

			});
			
			root.addPreference(developerPrefCat);
			developerPrefCat.addPreference(aggregatePref);
			developerPrefCat.addPreference(invokeMyTracksPref);
			developerPrefCat.addPreference(restorePref);
		}
		
		updateServiceStateDependantPreferences();

		return root;
	}


	private void updateServiceStateDependantPreferences() {

		//TODO: Change to service.isRunning()
		if(!optionsTable.isServiceUserStarted())
		{
			forceCalibPref.setTitle("First, start the service.");
			forceCalibPref.setEnabled(false);
			forceCalibPref.setSelectable(false);
		}
		else if(!ClassifierThread.forceCalibration)
			forceCalibPref.setTitle("Start Calibration now");
		else
		{
			forceCalibPref.setTitle("Calibration in progress ...");
			forceCalibPref.setEnabled(false);
			forceCalibPref.setSelectable(false);
		}

//		if(!optionsTable.isServiceUserStarted())
//		{
//			forceCalibPref.setTitle("First, start the service.");
//			forceCalibPref.setEnabled(false);
//			forceCalibPref.setSelectable(false);
//			
//			restorePref.setTitle("Restore ");
//		}
//		else if (optionsTable.isServiceUserStarted())
//		{
//			forceCalibPref.setTitle("Start Calibration now");
//			forceCalibPref.setEnabled(true);
//			forceCalibPref.setSelectable(true);
//		}
		
		if (optionsTable.isServiceUserStarted()) {
			copyPref.setEnabled(false);
			copyPref.setSummary("Please stop service first");
		} else {
			copyPref.setEnabled(true);
			copyPref.setSummary("Copy database to SD card");
		}
		
		if (restorePref!=null) {
			if (optionsTable.isServiceUserStarted()) {
				restorePref.setEnabled(false);
				restorePref.setSummary("Please stop service first");
			} else {
				restorePref.setEnabled(true);
				restorePref.setSummary("Restore database from SD card.");
			}
		}
		
	}

	
	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case DIALOG_YES_NO_MESSAGE_FOR_DELETION:
			return new AlertDialog.Builder(this)
			.setIcon(R.drawable.arrow_down_float)
			.setTitle("Warning")
			.setMessage("Your all activity history data will be deleted. Do you really want to delete the database?")
			.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
					/* User clicked OK so do some stuff */
					try {
						if (sqlLiteAdapter.isOpen()) {
							String dbFilePath = sqlLiteAdapter.getPath();
							File dbFile = new File(dbFilePath);
							if (dbFile.exists()) {
								if (service==null || !service.isRunning()) {
									sqlLiteAdapter.close();
									dbFile.delete();
									sqlLiteAdapter.open();
									Toast.makeText(getBaseContext(), "Database deleted", Toast.LENGTH_LONG).show();
								} else {
									Toast.makeText(getBaseContext(), "Stop Service first!", Toast.LENGTH_LONG).show();
								}
							} else {
								Toast.makeText(getBaseContext(), "Database file does not exist!", Toast.LENGTH_LONG).show();
							}
						} else {
							Toast.makeText(getBaseContext(), "Database is not available!", Toast.LENGTH_LONG).show();
						}
					} catch (RemoteException ex) {
						Log.e(Constants.TAG, "Unable to get service state", ex);
					}


				}
			})
			.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {

					/* User clicked Cancel so do some stuff */
				}
			})
			.create();
		case DIALOG_YES_NO_MESSAGE_FOR_RESET_CALIBRATION:
			return new AlertDialog.Builder(this)
			.setIcon(R.drawable.arrow_down_float)
			.setTitle("Warning")
			.setMessage("Proceed with reseting the calibration values?")
			.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
					try {
						service.showServiceToast("Calibration values reseted. Auto calibration will take place" +
						" when the phone is still for more that a minute.");
					} catch (RemoteException e) {
					}

					Calibrator.optionsTable = optionsTable;
					optionsTable.save();
					calibrationSummary.setSummary(getScreenSummary());
				}
			})
			.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {

					/* User clicked Cancel so do some stuff */
				}
			})
			.create();
		case DIALOG_YES_NO_MESSAGE_FOR_CALIBRATION_START:
			return new AlertDialog.Builder(this)
			.setIcon(R.drawable.arrow_down_float)
			.setTitle("Warning")
			.setMessage("Start the calibration now?")
			.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
					try {
						service.showServiceToast("Performing calibration. Please keep the phone still.");
					} catch (RemoteException e) {
					}

					ClassifierThread.forceCalibration = true;
					Calibrator.isCalibrated = false;

					//Calibrator.resetCalibrationOptions(optionsTable);
					//optionsTable.save();
					calibrationSummary.setSummary(getScreenSummary());
				}
			})
			.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {

					/* User clicked Cancel so do some stuff */
				}
			})
			.create();
		}
		return null;
	}

	private String getScreenSummary(){
		float[] sd = optionsTable.getSd();
		float[] offSet = optionsTable.getOffset();
		return 
		"Standard Deviation X : "+sd[Constants.ACCEL_X_AXIS]+"\n" +
		"Standard Deviation Y : "+sd[Constants.ACCEL_Y_AXIS]+"\n" +
		"Standard Deviation Z : "+sd[Constants.ACCEL_Z_AXIS]+"\n" +
		"Offset X                        : "+offSet[Constants.ACCEL_X_AXIS]+"\n" +
		"Offset Y                        : "+offSet[Constants.ACCEL_Y_AXIS]+"\n" +
		"Offset Z                        : "+offSet[Constants.ACCEL_Z_AXIS]+"\n";
	}

	public class CheckBoxPreferenceWithLongSummary extends CheckBoxPreference{

		public CheckBoxPreferenceWithLongSummary(Context context) {
			super(context);
		}

		public CheckBoxPreferenceWithLongSummary(Context context, AttributeSet attrs) {
			super(context, attrs);
		}
		public CheckBoxPreferenceWithLongSummary(Context context, AttributeSet attrs, int defStyle) {
			super(context, attrs, defStyle);
		}

		@Override
		protected void onBindView(View view) {
			super.onBindView(view);
			TextView summaryView = (TextView) view.findViewById(android.R.id.summary);
			summaryView.setMaxLines(10);
			CheckBox checkBox = (CheckBox) view.findViewById(android.R.id.checkbox);
			checkBox.setVisibility(view.GONE);
		}
	}

}
