package activity.classifier.common;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.TreeMap;

import activity.classifier.activity.ActivityChartActivity;
import activity.classifier.service.RecorderService;
import activity.classifier.service.threads.AccountThread;
import activity.classifier.service.threads.UploadActivityHistoryThread;
import android.graphics.Color;
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
	 * Used to differentiate between times when the application is
	 * being debugged (i.e. plugged into the computer), and normal
	 * usage.
	 */
	public final static boolean IS_DEBUGGING = true; 
	
	/**
	 * Should we or should we not output debugging information?
	 */
	public static final boolean OUTPUT_DEBUG_INFO = false;
	
	/**
	 * Whether to use FFT or not
	 */
	public static final boolean USE_FFT = false;
	
	/**
	 * Default value for Full Time accelerometer option
	 */
	public static final boolean DEF_USE_FULLTIME_ACCEL = true;
	
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
	//	TODO: CHANGE THIS BEFORE COMMIT
	public static final int DELAY_SAMPLE_BATCH = IS_DEBUGGING?(10*1000):(30*1000); //	30 secs in ms
	
	/**
	 * The delay between two consecutive samples in a sample batch.
	 */
	public static final int DELAY_BETWEEN_SAMPLES = 50; //	50ms
	
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
	//	TODO change this
//	public static final int DELAY_UPLOAD_DATA = 5*60*1000;	//	5min in ms
	public static final int DELAY_UPLOAD_DATA = 30*1000;
	
	/**
	 * The delay after the dialog appears and before the {@link RecorderService} in
	 * the 'start service' display sequence in the class {@link ActivityRecorderActivity}
	 */
	public static final int DELAY_SERVICE_START = 1000;

	/**
	 * A tag that can be used to identify this application's log entries.
	 */
	public static final String DEBUG_TAG = "AvocadoAC";
	
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
	 *	The number of accelerometer (x,y & z) samples in a batch of samples.
	 */
	public static final int NUM_OF_SAMPLES_PER_BATCH = 128;
	
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
	public static final float MIN_GRAVITY_DEV = 0.5f; // 15% of gravity	
	public static final float MAX_GRAVITY_DEV = 0.5f; // 100% of gravity	
	
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
	//	TODO: CHANGE THIS BEFORE COMMIT
	public final static long DURATION_OF_CALIBRATION = 60*1000; // 60 seconds
//	public final static long DURATION_OF_CALIBRATION = 10*60*1000; // 60 seconds

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

	/*
	 ****************************************************************************************************************
	 *		MY TRACKS INTERGRATION RELATED CONSTANTS
	 ****************************************************************************************************************
	 */
	
	/**
	 * The duration to wait while someone is walking before starting the MyTracks application recording
	 */
	//	TODO: CHANGE THIS BEFORE COMMIT
	public final static long DURATION_BEFORE_START_MYTRACKS = 5*60*1000L;
//	public final static long DURATION_BEFORE_START_MYTRACKS = 20*1000L;
	
	/**
	 * The duration to wait while someone has stopped walking before stopping the MyTracks application recording
	 */
	//	TODO: CHANGE THIS BEFORE COMMIT
	public final static long DURATION_BEFORE_STOP_MYTRACKS = 5*60*1000L;
//	public final static long DURATION_BEFORE_STOP_MYTRACKS = 20*1000L;

}
