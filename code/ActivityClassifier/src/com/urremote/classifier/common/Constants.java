package com.urremote.classifier.common;

import java.io.File;
import java.text.SimpleDateFormat;

import com.urremote.classifier.activity.ActivityChartActivity;
import com.urremote.classifier.service.RecorderService;
import com.urremote.classifier.service.threads.AccountThread;
import com.urremote.classifier.service.threads.UploadActivityHistoryThread;

import android.os.Environment;

/**
 * 
 * @author Umran
 *
 */
public class Constants {
	
	/**
	 *	<p>Affects the options application has.</p>
	 *	<p>
	 *  Extra options are given to developers. Like:<br/>
	 *		pick whether aggregation is used or not.
	 *	</p> 
	 */
	public final static boolean IS_DEV_VERSION = true;
	
	/**
	 * The default application package. Used for retrieving resources.
	 */
	public final static String DEFAULT_PACKAGE = "com.urremote.classifier";
	
	
	/**
	 * Used to differentiate between times when the application is
	 * being debugged (i.e. plugged into the computer), and normal
	 * usage.
	 */
	public final static boolean IS_DEBUGGING = true; 
	
	/**
	 * Should we or should we output debugging data?
	 */
	public static final boolean OUTPUT_DEBUG_INFO = false;
	
	/**
	 * While outputting debugging data,
	 * should we or should we output raw data to files in the SD card?
	 */
	public static final boolean OUTPUT_RAW_DATA = false;
	
	/**
	 * Default value for Full Time accelerometer option
	 */
	public static final boolean DEF_USE_FULLTIME_ACCEL = false;
	
	/**
	 * Default value to the "Invoke MyTracks" option
	 */
	public final static boolean DEF_USE_MYTRACKS = false;
	
	/**
	 * The Id of the flurry session
	 */
	public static final String FLURRY_SESSION_ID = "VSE8KMFZDAKZJPJHH2RL";
	
	/**
	 * The delay between two consecutive sampling batches.
	 */
	public static final int DELAY_SAMPLE_BATCH = IS_DEBUGGING?(10*1000):(30*1000); //	30 secs in ms
	
	/**
	 * The delay between two consecutive samples in a sample batch.
	 */
	public static final int DELAY_BETWEEN_SAMPLES = 50; //	50ms
	
	/**
	 *	The number of accelerometer (x,y & z) samples in a batch of samples.
	 */
	public static final int NUM_OF_SAMPLES_PER_BATCH = 128;
	
	/**
	 * Duration for the {@link AccountThread} to wait for the user's account
	 * to be set on the phone before checking again.
	 */
	public static final int DURATION_WAIT_FOR_USER_ACCOUNT = 60*60*1000; // 1 hr in ms
	
	/**
	 * The interval between successive user interface updates in the {@link ActivityRecorderActivity}
	 */
	public static final int DELAY_UI_UPDATE = 1000;
	
	/**
	 * The interval between successive user interface updates in the {@link ActivityChartActivity}
	 * This value should be quite large because updating the chart takes a large amount of
	 * processing.
	 */
	public static final int DELAY_UI_GRAPHIC_UPDATE = DELAY_SAMPLE_BATCH;
	
	/**
	 * The interval between successive data uploads in {@link UploadActivityHistoryThread}
	 */
	public static final int DELAY_UPLOAD_DATA = IS_DEBUGGING?(10*1000):(5*60*1000);	//	5min in ms
	
	/**
	 * The delay after the dialog appears and before the {@link RecorderService} in
	 * the 'start service' display sequence in the class {@link ActivityRecorderActivity}
	 */
	public static final int DELAY_SERVICE_START = 1000;

	/**
	 * A tag that can be used to identify this application's log entries.
	 */
	public static final String TAG = "AvocadoAC";
	
	/**
	 * Records file name.
	 */
	public static final String RECORDS_FILE_NAME = "activityrecords.db";	
	
	/**
	 * Path to the location where this application stores files in the SD card
	 */
	public static final String PATH_SD_CARD_APP_LOC =
		Environment.getExternalStorageDirectory() + File.separator + "AvocadoAC"; 
//		File.separator + "sdcard" + File.separator + "activityclassifier"; 
	
	/**
	 * Path to the activity records file
	 */
	public static final String PATH_SD_CARD_DUMP_DB =  PATH_SD_CARD_APP_LOC + File.separator + RECORDS_FILE_NAME;
	
	
	/**
	 * Path to the device debug logs 
	 */
	public static final String PATH_SD_CARD_LOG =  PATH_SD_CARD_APP_LOC + File.separator + "debug.log";
	
	/**
	 * URL where the user's information is posted.
	 */
	public static final String URL_USER_DETAILS_POST = "http://activity.urremote.com/accountservlet";
	
	/**
	 * URL to where the user's activity history can be viewed.
	 */
	public static final String URL_ACTIVITY_HISTORY = "http://activity.urremote.com/actihistory.jsp";
	
	/**
	 * URL where the user's activity data is posted
	 */
	public static final String URL_ACTIVITY_POST = "http://activity.urremote.com/activity";

	
	/**
	 * The type of account used to identify the user when
	 * uploading data to the server.
	 */
	public static final String UPLOAD_ACCOUNT_TYPE = "com.google";
	
	/**
	 *	The value of gravity
	 */
	public static final float GRAVITY = 9.81f;
	
	/**
	 *	The deviation from gravity that a sample is allowed,
	 *	any deviation greater than this makes the sample invalid.
	 *
	 *	Lesser values can be caused due to the phone being rotated
	 *	during the sampling period, while larger values can be caused
	 *	because of the phone accelerating fast during the sampling period
	 *	e.g. in a car after a traffic light.
	 */
	public static final float MIN_GRAVITY_DEV = 0.50f; // 50% of gravity	
	public static final float MAX_GRAVITY_DEV = 0.15f; // 15% of gravity	
	
	/**
	 * The number of axi on the accelerometer
	 */
	public final static int ACCEL_DIM = 3;
	
	/**
	 * The indexes of the x axis on the accelerometer
	 */
	public final static int ACCEL_X_AXIS = 0;

	/**
	 * The indexes of the y axis on the accelerometer
	 */
	public final static int ACCEL_Y_AXIS = 1;

	/**
	 * The indexes of the z axis on the accelerometer
	 */
	public final static int ACCEL_Z_AXIS = 2;
	
	/**
	 * The duration the phone is required to be stationary before doing calibration
	 */
	public final static long DURATION_OF_CALIBRATION = IS_DEBUGGING?(60*1000):10*60*1000;

	/**
	 * The deviation in the means & sd of the accelerometer axis
	 * 	within the calibration waiting duration given as {@link #DURATION_WAIT_FOR_CALIBRATION}
	 * 
	 * This value is only used initially, after calibration, the values used in
	 * the calibration are used instead.
	 * 
	 * See {@link #CALIBARATION_ALLOWED_MULTIPLES_DEVIATION} for more info.
	 * See {@link #CALIBARATION_MIN_ALLOWED_BASE_DEVIATION} for more info.
	 */
	public final static float CALIBARATION_ALLOWED_BASE_DEVIATION = 0.05f;

	/**
	 * The minimum deviation in the means & sd of the accelerometer axis
	 * 	within the calibration waiting duration given as {@link #DURATION_WAIT_FOR_CALIBRATION}
	 * 
	 * See {@link #CALIBARATION_ALLOWED_MULTIPLES_DEVIATION} for more info.
	 * See {@link #CALIBARATION_ALLOWED_BASE_DEVIATION} for more info.
	 */
	public final static float CALIBARATION_MIN_ALLOWED_BASE_DEVIATION = 0.05f;
	
	/**
	 * This value is multiplied by the {@value #CALIBARATION_ALLOWED_BASE_DEVIATION} value
	 * to get the range which the phone's accelerometer axis standard deviation is allowed to be in 
	 * for the phone to be detected as stationary. 
	 * 
	 * The {@value #CALIBARATION_ALLOWED_BASE_DEVIATION} value is only used initially, 
	 * after calibration, the values used in the calibration are used instead.
	 * 
	 */
	public final static float CALIBARATION_ALLOWED_MULTIPLES_DEVIATION = 2.0f;	// 2 times the standard deviation
	
	/**
	 * The duration which the means of different axis should be the same for the state
	 * to be uncarried
	 */
	public final static long DURATION_WAIT_FOR_UNCARRIED = 60*1000;
	
	/**
	 * The format used to store date values into the database
	 */
	public final static SimpleDateFormat DB_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z z");
	
	/**
	 * The maximum duration that debugging data should be maintained in the database
	 */
	public final static long DURATION_KEEP_DB_DEBUG_DATA = 12*60*60*1000L;
	
	/**
	 * The maximum duration that activity data should be maintained in the database
	 */
	public final static long DURATION_KEEP_DB_ACTIVITY_DATA = 7*24*60*60*1000L;
	
	/**
	 * The period between successive authentication retries
	 */
	public final static long PERIOD_RETRY_AUTHENTICATION = 5000L;//15*60*1000L; // 15min

	/*
	 ****************************************************************************************************************
	 *		MY TRACKS INTERGRATION RELATED CONSTANTS
	 ****************************************************************************************************************
	 */
	
	/**
	 * The duration to wait while someone is walking before starting the MyTracks application recording
	 */
	public final static long DURATION_BEFORE_START_MYTRACKS = IS_DEBUGGING?(20*1000L):(3*60*1000L);
	
	/**
	 * The duration to monitor waiting for someone to stop for a 
	 * given duration {@link #DURATION_MIN_KEEP_MYTRACKS}
	 */
	public final static long DURATION_MONITOR_MYTRACKS = IS_DEBUGGING?(40*1000L):(5*60*1000L);
	
	/**
	 * Minimum duration required for a person to walk, within 
	 * the monitoring duration {@link #DURATION_MONITOR_MYTRACKS},
	 * for MyTracks to stay on. If a person walks any less than this,
	 * then MyTracks is turned off based on the assumption that he might be 
	 * walking around indoors.
	 * 
	 * Note: The minimum should be more than 1 sampling period from the
	 * duration {@link #DURATION_MONITOR_MYTRACKS}, otherwise MyTracks 
	 * will be stopped when the first non-walking activity is met. 
	 */
	public final static long DURATION_MIN_KEEP_MYTRACKS = IS_DEBUGGING?(20*1000L):(2*60*1000L);

	
	/*
	 ****************************************************************************************************************
	 *		NOTIFICATIONS
	 ****************************************************************************************************************
	 */
	public static final int NOTIFICATION_ID_ONGOING_SERVICE = 1;
	public static final int NOTIFICATION_ID_HARDWARE_FAULT = 2;
	public static final int NOTIFICATION_ID_AUTHENTICATION_FAILURE = 3;
	public static final int NOTIFICATION_ID_NO_ACCOUNT = 3;
 
	/*
	 ****************************************************************************************************************
	 *		HARDWARE FAULTS
	 ****************************************************************************************************************
	 */
	/**
	 * Amount of time between the service being stopped
	 * and turning it on again, after a hardware fault occurs.
	 */
	public static final long DURATION_SLEEP_AFTER_FAULT = IS_DEBUGGING?(30*1000L):(10*60*1000L);
	
	/*
	 ****************************************************************************************************************
	 *		FUSION TABLES
	 ****************************************************************************************************************
	 */
	public static final String FUSION_TABLE_NAME = "AvocadoAC_Activities";
}
