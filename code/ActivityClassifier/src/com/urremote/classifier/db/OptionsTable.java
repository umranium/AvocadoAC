package com.urremote.classifier.db;

import java.security.acl.LastOwnerException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.urremote.classifier.BootReceiver;
import com.urremote.classifier.common.Constants;
import com.urremote.classifier.common.StringComparator;
import com.urremote.classifier.utils.Calibrator;
import com.urremote.classifier.utils.PhoneInfo;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.hardware.SensorManager;
import android.util.Log;

/**
 * Handles storage and retrieval of system options.
 * Please don't create an instance of this class,
 * instead use the {@link SqlLiteAdapter} class to obtain
 * an instance of it.
 * 
 * @author Umran
 *
 */
public class OptionsTable extends DbTableAdapter {
	
	public static final String TABLE_NAME = "options";
	
	private static final int DEFAULT_ROW_ID = 1;
	
	/**
	 * Column names in startinfo Table
	 */
	public static final String KEY_ID = "id";
	public static final String KEY_IS_SERVICE_USER_STARTED = "isServiceStarted";
	public static final String KEY_IS_CALIBRATED = "isCalibrated";
	public static final String KEY_VALUE_OF_GRAVITY = "valueOfGravity";
	public static final String KEY_SD_X = "sdX";
	public static final String KEY_SD_Y = "sdY";
	public static final String KEY_SD_Z = "sdZ";
	public static final String KEY_MEAN_X = "meanX";
	public static final String KEY_MEAN_Y = "meanY";
	public static final String KEY_MEAN_Z = "meanZ";
	public static final String KEY_OFFSET_X = "offsetX";
	public static final String KEY_OFFSET_Y = "offsetY";
	public static final String KEY_OFFSET_Z = "offsetZ";
	public static final String KEY_SCALE_X = "scaleX";
	public static final String KEY_SCALE_Y = "scaleY";
	public static final String KEY_SCALE_Z = "scaleZ";
	public static final String KEY_COUNT = "count";
	public static final String KEY_ALLOWED_MULTIPLES_OF_SD = "allowedMultiplesOfSd";
	public static final String KEY_IS_ACCOUNT_SENT = "isAccountSent";
	public static final String KEY_IS_WAKE_LOCK_SET = "isWakeLockSet";
	public static final String KEY_USE_AGGREGATOR = "useAggregator";
	public static final String KEY_INVOKE_MYTRACKS = "invokeMyTracks";
	public static final String KEY_FULLTIME_ACCEL = "fullTimeAccel";
	public static final String KEY_SENSOR_RATE = "accelSensorRate";
	public static final String KEY_UPLOAD_ACCOUNT = "uploadAccount";
	private static final String KEY_LAST_UPDATED_AT = "lastUpdatedAt";	//	for system use only
	
	public static final String[] SD_KEYS;
	public static final String[] MEAN_KEYS;
	public static final String[] OFFSET_KEYS;
	public static final String[] SCALE_KEYS;
	
	static {
		SD_KEYS = new String[Constants.ACCEL_DIM];
		MEAN_KEYS = new String[Constants.ACCEL_DIM];
		OFFSET_KEYS = new String[Constants.ACCEL_DIM];
		SCALE_KEYS = new String[Constants.ACCEL_DIM];
		
		SD_KEYS[Constants.ACCEL_X_AXIS] = KEY_SD_X;
		SD_KEYS[Constants.ACCEL_Y_AXIS] = KEY_SD_Y;
		SD_KEYS[Constants.ACCEL_Z_AXIS] = KEY_SD_Z;
		
		MEAN_KEYS[Constants.ACCEL_X_AXIS] = KEY_MEAN_X;
		MEAN_KEYS[Constants.ACCEL_Y_AXIS] = KEY_MEAN_Y;
		MEAN_KEYS[Constants.ACCEL_Z_AXIS] = KEY_MEAN_Z;

		OFFSET_KEYS[Constants.ACCEL_X_AXIS] = KEY_OFFSET_X;
		OFFSET_KEYS[Constants.ACCEL_Y_AXIS] = KEY_OFFSET_Y;
		OFFSET_KEYS[Constants.ACCEL_Z_AXIS] = KEY_OFFSET_Z;

		SCALE_KEYS[Constants.ACCEL_X_AXIS] = KEY_SCALE_X;
		SCALE_KEYS[Constants.ACCEL_Y_AXIS] = KEY_SCALE_Y;
		SCALE_KEYS[Constants.ACCEL_Z_AXIS] = KEY_SCALE_Z;
	}
	
	private static final String SELECT_SQL =
		"SELECT " +
		KEY_IS_SERVICE_USER_STARTED + ", " +
		KEY_IS_CALIBRATED + ", " +
		KEY_VALUE_OF_GRAVITY + ", " +
		KEY_SD_X + ", " +
		KEY_SD_Y + ", " +
		KEY_SD_Z + ", " +
		KEY_MEAN_X + ", " +
		KEY_MEAN_Y + ", " +
		KEY_MEAN_Z + ", " +
		KEY_OFFSET_X + ", " +
		KEY_OFFSET_Y + ", " +
		KEY_OFFSET_Z + ", " +
		KEY_SCALE_X + ", " +
		KEY_SCALE_Y + ", " +
		KEY_SCALE_Z + ", " +
		KEY_COUNT + ", " +
		KEY_ALLOWED_MULTIPLES_OF_SD + ", " +
		KEY_IS_ACCOUNT_SENT + ", " +
		KEY_IS_WAKE_LOCK_SET + ", " +
		KEY_USE_AGGREGATOR + ", " +
		KEY_INVOKE_MYTRACKS + ", " +
		KEY_FULLTIME_ACCEL + ", " +
		KEY_SENSOR_RATE + ", " +
		KEY_UPLOAD_ACCOUNT + ", " +
		KEY_LAST_UPDATED_AT + 
		" FROM " + TABLE_NAME;
	
	private boolean databaseLoaded = false;
	
	private PhoneInfo phoneInfo = null;
	
	//	reusable
	private ContentValues contentValues;
	
	//	various system states
	private boolean isServiceUserStarted;
	private boolean isAccountSent;
	private boolean isWakeLockSet;
	private boolean useAggregator;
	private boolean fullTimeAccel;
	private boolean invokeMyTracks;
	private int accelSensorRate;
	private String uploadAccount;
	
	// calibration
	private boolean isCalibrated;
	private float valueOfGravity;
	private float[] sd = new float[Constants.ACCEL_DIM];
	private float[] mean = new float[Constants.ACCEL_DIM];
	private float[] offset = new float[Constants.ACCEL_DIM];
	private float[] scale = new float[Constants.ACCEL_DIM];
	private int count;
	private float allowedMultiplesOfSd;
	
	//	updates
	private long lastUpdatedAt;
	private Set<String> updatedKeys; 
	private List<OptionUpdateHandler> updateHandlers; 
	
	protected OptionsTable(Context context) {
		super(context);
		this.contentValues = new ContentValues();
		this.updatedKeys = new TreeSet<String>(new StringComparator(false));
		this.updateHandlers = new ArrayList<OptionUpdateHandler>();
		this.phoneInfo = new PhoneInfo(context);
	}
	
	@Override
	protected void createTable(SQLiteDatabase database) {
		//	create the create sql
		String sql = 
			"CREATE TABLE "+TABLE_NAME+" (" +
			KEY_ID+" INTEGER PRIMARY KEY, " +
			KEY_IS_SERVICE_USER_STARTED+" INTEGER NOT NULL, " +
			KEY_IS_CALIBRATED+" INTEGER NOT NULL, " +
			KEY_VALUE_OF_GRAVITY+" REAL NOT NULL, " +
			KEY_SD_X+" REAL NOT NULL, " +
			KEY_SD_Y+" REAL NOT NULL, " +
			KEY_SD_Z+" REAL NOT NULL, " +
			KEY_MEAN_X+" REAL NOT NULL, " +
			KEY_MEAN_Y+" REAL NOT NULL, " +
			KEY_MEAN_Z+" REAL NOT NULL, " +
			KEY_OFFSET_X+" REAL NOT NULL, " +
			KEY_OFFSET_Y+" REAL NOT NULL, " +
			KEY_OFFSET_Z+" REAL NOT NULL, " +
			KEY_SCALE_X+" REAL NOT NULL, " +
			KEY_SCALE_Y+" REAL NOT NULL, " +
			KEY_SCALE_Z+" REAL NOT NULL, " +
			KEY_COUNT+" INTEGER NOT NULL, " +
			KEY_ALLOWED_MULTIPLES_OF_SD+" REAL NOT NULL, " + 
			KEY_IS_ACCOUNT_SENT+" INTEGER NOT NULL, " +
			KEY_IS_WAKE_LOCK_SET+" INTEGER NOT NULL, " +
			KEY_USE_AGGREGATOR+" INTEGER NOT NULL, " +
			KEY_INVOKE_MYTRACKS+" INTEGER NOT NULL, " +
			KEY_FULLTIME_ACCEL+" INTEGER NOT NULL, " +
			KEY_SENSOR_RATE+" INTEGER NOT NULL, " +
			KEY_UPLOAD_ACCOUNT+ " TEXT NULL, " +
			KEY_LAST_UPDATED_AT+" LONG NOT NULL " +
			")";
		//	run the sql
		database.execSQL(sql);
		
		//	insert default values
		setServiceUserStarted(true);
		setAccountSent(false);
		setWakeLockSet(false);
		setUseAggregator(false);
		setInvokeMyTracks(Constants.DEF_USE_MYTRACKS);
		setFullTimeAccel(false);
		setAccelSensorRate(SensorManager.SENSOR_DELAY_NORMAL);
		setUploadAccount(phoneInfo.getFirstAccountName());
		Calibrator.resetCalibrationOptions(this);	// set calibration values to defaults
		setLastUpdated(System.currentTimeMillis());	// last updated now
		contentValues.put(KEY_ID, DEFAULT_ROW_ID);	//	set row id
		database.insertOrThrow(TABLE_NAME, null, contentValues);
		contentValues.remove(KEY_ID);				//	remove row id from context values (only used in insert)
		lastUpdatedAt = 0;							//	reset last updated time to zero so that system can re-load all the values
		updatedKeys.clear();						//	nothing has been updated yet
		
		//	if database was re-created... reload it
		this.databaseLoaded = true;
	}

	@Override
	protected void dropTable(SQLiteDatabase database) {
		//	drop existing table if it exists
		database.execSQL("DROP TABLE IF EXISTS "+TABLE_NAME);
	}

	@Override
	protected boolean init(SQLiteDatabase database) {
		super.init(database);
		
		//	only load the first time... any init afterwards shouldn't be loaded
		if (!this.databaseLoaded) {
			this.load();
			this.databaseLoaded = true;
		}
		
		return true;
	}

	@Override
	protected void done() {
		if (!this.updatedKeys.isEmpty())
			this.save();
		super.done();
	}
	
	/**
	 * Allows objects in the system to register handlers that receive
	 * notifications whenever changes are made and 
	 * @param updateHandler
	 */
	public void registerUpdateHandler(OptionUpdateHandler updateHandler)
	{
		synchronized (updateHandlers) {
			updateHandlers.add(updateHandler);
		}
	}
	
	public void unregisterUpdateHandler(OptionUpdateHandler updateHandler)
	{
		synchronized (updateHandlers) {
			updateHandlers.remove(updateHandler);
		}
	}
	
	private void notifyAllUpdateHandlers()
	{
		synchronized (updateHandlers) {
			for (OptionUpdateHandler updateHandler:updateHandlers) {
				updateHandler.onFieldChange(updatedKeys);
			}
		}
	}
	
	private void load()
	{
		Cursor cursor = database.rawQuery(
				 SELECT_SQL + 
				 " WHERE " + KEY_ID + "=" + DEFAULT_ROW_ID + " AND " + KEY_LAST_UPDATED_AT + ">"+lastUpdatedAt, 
				 null);
		
		try {
			float sd[] = new float[Constants.ACCEL_DIM];
			float mean[] = new float[Constants.ACCEL_DIM];
			float offset[] = new float[Constants.ACCEL_DIM];
			float scale[] = new float[Constants.ACCEL_DIM];
			
			if (cursor.moveToNext()) {
				setServiceUserStarted(cursor.getInt(cursor.getColumnIndex(KEY_IS_SERVICE_USER_STARTED))!=0);
				setCalibrated(cursor.getInt(cursor.getColumnIndex(KEY_IS_CALIBRATED))!=0);
				setValueOfGravity(cursor.getFloat(cursor.getColumnIndex(KEY_VALUE_OF_GRAVITY)));
				setCount(cursor.getInt(cursor.getColumnIndex(KEY_COUNT)));
				setAllowedMultiplesOfSd(cursor.getFloat(cursor.getColumnIndex(KEY_ALLOWED_MULTIPLES_OF_SD)));
				setAccountSent(cursor.getInt(cursor.getColumnIndex(KEY_IS_ACCOUNT_SENT))!=0);
				setWakeLockSet(cursor.getInt(cursor.getColumnIndex(KEY_IS_WAKE_LOCK_SET))!=0);
				setUseAggregator(cursor.getInt(cursor.getColumnIndex(KEY_USE_AGGREGATOR))!=0);
				setInvokeMyTracks(cursor.getInt(cursor.getColumnIndex(KEY_INVOKE_MYTRACKS))!=0);
				setFullTimeAccel(cursor.getInt(cursor.getColumnIndex(KEY_FULLTIME_ACCEL))!=0);
				setAccelSensorRate(cursor.getInt(cursor.getColumnIndex(KEY_SENSOR_RATE)));
				setUploadAccount(cursor.getString(cursor.getColumnIndex(KEY_UPLOAD_ACCOUNT)));

				sd[Constants.ACCEL_X_AXIS] = cursor.getFloat(cursor.getColumnIndex(KEY_SD_X));
				sd[Constants.ACCEL_Y_AXIS] = cursor.getFloat(cursor.getColumnIndex(KEY_SD_Y));
				sd[Constants.ACCEL_Z_AXIS] = cursor.getFloat(cursor.getColumnIndex(KEY_SD_Z));
				setSd(sd);
				
				mean[Constants.ACCEL_X_AXIS] = cursor.getFloat(cursor.getColumnIndex(KEY_MEAN_X));
				mean[Constants.ACCEL_Y_AXIS] = cursor.getFloat(cursor.getColumnIndex(KEY_MEAN_Y));
				mean[Constants.ACCEL_Z_AXIS] = cursor.getFloat(cursor.getColumnIndex(KEY_MEAN_Z));
				setMean(mean);
				
				offset[Constants.ACCEL_X_AXIS] = cursor.getFloat(cursor.getColumnIndex(KEY_OFFSET_X));
				offset[Constants.ACCEL_Y_AXIS] = cursor.getFloat(cursor.getColumnIndex(KEY_OFFSET_Y));
				offset[Constants.ACCEL_Z_AXIS] = cursor.getFloat(cursor.getColumnIndex(KEY_OFFSET_Z));
				setOffset(offset);
				
				scale[Constants.ACCEL_X_AXIS] = cursor.getFloat(cursor.getColumnIndex(KEY_SCALE_X));
				scale[Constants.ACCEL_Y_AXIS] = cursor.getFloat(cursor.getColumnIndex(KEY_SCALE_Y));
				scale[Constants.ACCEL_Z_AXIS] = cursor.getFloat(cursor.getColumnIndex(KEY_SCALE_Z));
				setScale(scale);
				
				setLastUpdated(cursor.getLong(cursor.getColumnIndex(KEY_LAST_UPDATED_AT)));

				updatedKeys.clear();	//	nothing has been updated yet
			}
		} finally {
			cursor.close();
		}
	}
	
	/**
	 * Saves the cached values to the database
	 */
	synchronized
	public void save()
	{
		if (this.updatedKeys.isEmpty()) {
			Log.w(Constants.DEBUG_TAG, "WARNING: OptionsTable.save() function called while no changes have been made to options.");
		}
		
		if (this.isDatabaseAvailable()) {
			Log.v(Constants.DEBUG_TAG, "Saving Options: "+this.updatedKeys.toString());
			setLastUpdated(System.currentTimeMillis());
			int rows = database.update(TABLE_NAME, contentValues, KEY_ID+"="+DEFAULT_ROW_ID, null);
			if (rows==0)
				throw new RuntimeException("Unable to update option values for table '"+TABLE_NAME+"'");
			notifyAllUpdateHandlers();
			this.updatedKeys.clear();
		}
	}
	
	private void setLastUpdated(long time) {
		this.lastUpdatedAt = time;
		this.contentValues.put(KEY_LAST_UPDATED_AT, time);
		this.updatedKeys.add(KEY_LAST_UPDATED_AT);
	}

	/**
	 * @return the isServiceUserStarted
	 */
	public boolean isServiceUserStarted() {
		return isServiceUserStarted;
	}

	/**
	 * Make sure to call {@link #save()} after setting.
	 * 
	 * Please Note:
	 * This value is set to true when the service starts,
	 * not when the menu item responsible for starting
	 * the service is clicked. This is done for 2 reasons:
	 * 	1) During the service starting process, any
	 * 		number of things could go wrong resulting to
	 * 		the service not being started.
	 * 	2) The service could also be started through the
	 * 		boot process. see {@link BootReceiver}
	 *	
	 *	On the other hand, the value is set to false, when
	 *	and only when the user explicitly turns the service
	 *	off (i.e. using the menu item is clicked.)
	 * 
	 * @param isServiceUserStarted
	 * whether the service has been started or not
	 */
	public void setServiceUserStarted(boolean isServiceUserStarted) {
		this.isServiceUserStarted = isServiceUserStarted;
		this.contentValues.put(KEY_IS_SERVICE_USER_STARTED, isServiceUserStarted?1:0);
		this.updatedKeys.add(KEY_IS_SERVICE_USER_STARTED);
	}

	/**
	 * @return the isCalibrated
	 */
	public boolean isCalibrated() {
		return isCalibrated;
	}

	/**
	 * Make sure to call {@link #save()} after setting.
	 * 
	 * @param isCalibrated the isCalibrated to set
	 */
	public void setCalibrated(boolean isCalibrated) {
		this.isCalibrated = isCalibrated;
		this.contentValues.put(KEY_IS_CALIBRATED, isCalibrated?1:0);
		this.updatedKeys.add(KEY_IS_CALIBRATED);
	}

	/**
	 * @return the isAccountSent
	 */
	public boolean isAccountSent() {
		return isAccountSent;
	}

	/**
	 * Make sure to call {@link #save()} after setting.
	 * 
	 * @param isAccountSent the isAccountSent to set
	 */
	public void setAccountSent(boolean isAccountSent) {
		this.isAccountSent = isAccountSent;
		this.contentValues.put(KEY_IS_ACCOUNT_SENT, isAccountSent?1:0);
		this.updatedKeys.add(KEY_IS_ACCOUNT_SENT);
	}

	/**
	 * @return the isWakeLockSet
	 */
	public boolean isWakeLockSet() {
		return isWakeLockSet;
	}

	/**
	 * Make sure to call {@link #save()} after setting.
	 * 
	 * @param isWakeLockSet the isWakeLockSet to set
	 */
	public void setWakeLockSet(boolean isWakeLockSet) {
		this.isWakeLockSet = isWakeLockSet;
		this.contentValues.put(KEY_IS_WAKE_LOCK_SET, isWakeLockSet?1:0);
		this.updatedKeys.add(KEY_IS_WAKE_LOCK_SET);
	}

	/**
	 * @return the valueOfGravity
	 */
	public float getValueOfGravity() {
		return valueOfGravity;
	}

	/**
	 * Make sure to call {@link #save()} after setting.
	 * 
	 * @param valueOfGravity the valueOfGravity to set
	 */
	public void setValueOfGravity(float valueOfGravity) {
		this.valueOfGravity = valueOfGravity;
		this.contentValues.put(KEY_VALUE_OF_GRAVITY, valueOfGravity);
		this.updatedKeys.add(KEY_VALUE_OF_GRAVITY);
	}

	/**
	 * @return the sd
	 */
	public float[] getSd() {
		return sd;
	}

	/**
	 * Make sure to call {@link #save()} after setting.
	 * 
	 * @param sd the sd to set
	 */
	public void setSd(float[] sd) {
		for (int i=0; i<Constants.ACCEL_DIM; ++i) {
			this.sd[i] = sd[i];
			this.contentValues.put(SD_KEYS[i], sd[i]);
			this.updatedKeys.add(SD_KEYS[i]);
		}
	}

	/**
	 * @return the mean
	 */
	public float[] getMean() {
		return mean;
	}

	/**
	 * Make sure to call {@link #save()} after setting.
	 * 
	 * @param mean the mean to set
	 */
	public void setMean(float[] mean) {
		for (int i=0; i<Constants.ACCEL_DIM; ++i) {
			this.mean[i] = mean[i];
			this.contentValues.put(MEAN_KEYS[i], mean[i]);
			this.updatedKeys.add(MEAN_KEYS[i]);
		}
	}

	/**
	 * @return the count
	 */
	public int getCount() {
		return count;
	}

	/**
	 * Make sure to call {@link #save()} after setting.
	 * 
	 * @param count the count to set
	 */
	public void setCount(int count) {
		this.count = count;
		this.contentValues.put(KEY_COUNT, count);
		this.updatedKeys.add(KEY_COUNT);
	}

	/**
	 * @return the useAggregator
	 */
	public boolean getUseAggregator() {
		return useAggregator;
	}

	/**
	 * Make sure to call {@link #save()} after setting.
	 * 
	 * @param useAggregator the useAggregator to set
	 */
	public void setUseAggregator(boolean useAggregator) {
		this.useAggregator = useAggregator;
		this.contentValues.put(KEY_USE_AGGREGATOR, useAggregator?1:0);
		this.updatedKeys.add(KEY_USE_AGGREGATOR);
	}

	/**
	 * @return the allowedMultiplesOfSd
	 */
	public float getAllowedMultiplesOfSd() {
		return allowedMultiplesOfSd;
	}

	/**
	 * Make sure to call {@link #save()} after setting.
	 * 
	 * @param allowedMultiplesOfSd the allowedMultiplesOfSd to set
	 */
	public void setAllowedMultiplesOfSd(float allowedMultiplesOfSd) {
		this.allowedMultiplesOfSd = allowedMultiplesOfSd;
		this.contentValues.put(KEY_ALLOWED_MULTIPLES_OF_SD, allowedMultiplesOfSd);
		this.updatedKeys.add(KEY_ALLOWED_MULTIPLES_OF_SD);
	}

	/**
	 * @return the offset
	 */
	public float[] getOffset() {
		return offset;
	}

	/**
	 * @param offset the offset to set
	 */
	public void setOffset(float[] offset) {
		for (int i=0; i<Constants.ACCEL_DIM; ++i) {
			this.offset[i] = offset[i];
			this.contentValues.put(OFFSET_KEYS[i], offset[i]);
			this.updatedKeys.add(OFFSET_KEYS[i]);
		}
	}

	/**
	 * @return the scale
	 */
	public float[] getScale() {
		return scale;
	}

	/**
	 * @param scale the scale to set
	 */
	public void setScale(float[] scale) {
		for (int i=0; i<Constants.ACCEL_DIM; ++i) {
			this.scale[i] = scale[i];
			this.contentValues.put(SCALE_KEYS[i], scale[i]);
			this.updatedKeys.add(SCALE_KEYS[i]);
		}
	}

	/**
	 * @return the fullTimeAccel
	 */
	public boolean getFullTimeAccel() {
		return fullTimeAccel;
	}

	/**
	 * @param fullTimeAccel the fullTimeAccel to set
	 */
	public void setFullTimeAccel(boolean fullTimeAccel) {
		this.fullTimeAccel = fullTimeAccel;
		this.contentValues.put(KEY_FULLTIME_ACCEL, fullTimeAccel?1:0);
		this.updatedKeys.add(KEY_FULLTIME_ACCEL);
	}
	
	/**
	 * @return the accelSensorRate
	 */
	public int getAccelSensorRate() {
		return accelSensorRate;
	}

	/**
	 * @param accelSensorRate the accelSensorRate to set
	 */
	public void setAccelSensorRate(int accelSensorRate) {
		this.accelSensorRate = accelSensorRate;
		this.contentValues.put(KEY_SENSOR_RATE, accelSensorRate);
		this.updatedKeys.add(KEY_SENSOR_RATE);
	}

	/**
	 * @return the uploadAccount
	 */
	public String getUploadAccount() {
		return uploadAccount;
	}

	/**
	 * @param uploadAccount the uploadAccount to set
	 */
	public void setUploadAccount(String uploadAccount) {
		this.uploadAccount = uploadAccount;
		this.contentValues.put(KEY_UPLOAD_ACCOUNT, uploadAccount);
		this.updatedKeys.add(KEY_UPLOAD_ACCOUNT);
	}

	/**
	 * @return the whether or not to invoke MyTracks to start recording and stop recording 
	 */
	public boolean getInvokeMyTracks() {
		return invokeMyTracks;
	}

	/**
	 * @param invokeMyTracks the whether or not to invoke MyTracks to start recording and stop recording
	 */
	public void setInvokeMyTracks(boolean invokeMyTracks) {
		this.invokeMyTracks = invokeMyTracks;
		this.contentValues.put(KEY_INVOKE_MYTRACKS, invokeMyTracks);
		this.updatedKeys.add(KEY_INVOKE_MYTRACKS);
	}
	
}
