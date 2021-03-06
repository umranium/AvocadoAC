/**
 * 
 */
package com.urremote.classifier.db;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.urremote.classifier.common.ActivityNames;
import com.urremote.classifier.common.Constants;
import com.urremote.classifier.rpc.Classification;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

/**
 * @author Umran
 *
 */
public class ActivitiesTable extends DbTableAdapter {
	
	public static final String TABLE_NAME = "activities";
	
	public static interface ClassificationDataCallback {
		void onRetrieve(Classification classification);
	}
	
	/**
	 * Column names in activity Table
	 */
	public static final String KEY_START_LONG		= "start_long";
	public static final String KEY_END_LONG			= "end_long";
	public static final String KEY_ACTIVITY			= "activity";
	public static final String KEY_NUM_OF_BATCHES	= "num_of_batches";
	//public static final String KEY_TOTAL_EE_ACT 	= "total_ee_act";
	public static final String KEY_MET 				= "met";
	public static final String KEY_MYTRACKS_ID 		= "myTracks_id";
	public static final String KEY_LAST_UPDATED_AT = "lastUpdatedAt";	//	for system use only

	private static final String SELECT_SQL =
		"SELECT " +
		KEY_START_LONG + ", " +
		KEY_END_LONG + ", " +
		KEY_ACTIVITY + ", " +
		KEY_NUM_OF_BATCHES + ", " + 
		//KEY_TOTAL_EE_ACT + ", " + 
		KEY_MET + ", " +
		KEY_MYTRACKS_ID + ", " +
		KEY_LAST_UPDATED_AT +
		" FROM " + TABLE_NAME;
	
	//	reusable
	private ContentValues insertContentValues;
	private ContentValues updateContentValues;
	
	private long lastTrim = 0;
	
	protected ActivitiesTable(Context context) {
		super(context);
		
		this.insertContentValues = new ContentValues();		
		this.updateContentValues = new ContentValues();
	}
	
	@Override
	protected void createTable(SQLiteDatabase database) {
		//	create the create sql
		String sql = 
			"CREATE TABLE "+TABLE_NAME+" (" +
			KEY_START_LONG+" LONG PRIMARY KEY, " +
			KEY_END_LONG+" LONG NOT NULL, " +
			KEY_ACTIVITY+" TEXT NOT NULL, " +
			KEY_NUM_OF_BATCHES+" INTEGER NOT NULL, " +
			//KEY_TOTAL_EE_ACT+" REAL NOT NULL, " +
			KEY_MET+" REAL NOT NULL, " +
			KEY_MYTRACKS_ID + " LONG NULL, " +
			KEY_LAST_UPDATED_AT+" LONG NOT NULL " +
			")";
		//	run the sql
		database.execSQL(sql);
		Log.v(Constants.TAG, "Activities Table Created");
	}

	@Override
	protected void dropTable(SQLiteDatabase database) {
		//	drop existing table if it exists
		database.execSQL("DROP TABLE IF EXISTS "+TABLE_NAME);
	}

	@Override
	protected boolean init(SQLiteDatabase database) {
		super.init(database);
		
//		long now = System.currentTimeMillis();
//		long minute = 60L*1000L;
//		
//		long restingDur = 10*minute + (long)getRandVal(-2*minute,+2*minute);
//		long walkingDur = 5*minute + (long)getRandVal(-2*minute,+2*minute);
//		long runningDur = 45*minute + (long)getRandVal(-5*minute,+5*minute);
//		long officeDur = 120*minute + (long)getRandVal(-5*minute,+5*minute);
//		long cyclingDur = 30*minute + (long)getRandVal(-5*minute,+5*minute);
//		long getReadyDur = 30*minute + (long)getRandVal(-5*minute,+5*minute);
//		long chargingDur = 8*60*minute + (long)getRandVal(-10*minute,+10*minute);
//		long eveningDur = 2*60*minute + (long)getRandVal(-10*minute,+10*minute);
////		long beforeDur = 10*60*minute + (long)getRandVal(-10*minute,+10*minute);
//		
//		long restingStart = now-restingDur;
//		long walkingStart = restingStart-walkingDur;
//		long runningStart = walkingStart-runningDur;
//		long officeStart = runningStart-officeDur;
//		long cyclingStart = officeStart-cyclingDur;
//		long getReadyStart = cyclingStart-getReadyDur;
//		long chargingStart = getReadyStart-chargingDur;
//		long eveningStart = chargingStart-eveningDur;
////		long beforeStart = eveningStart-beforeDur;
//		
//		List<Classification> classifications = new ArrayList<Classification>();
//		classifications.add(new Classification(ActivityNames.STATIONARY, restingStart, now));
//		classifications.get(classifications.size()-1).setTotalMet(1.0f + (float)getRandVal(-0.5, 0.5));
//		
//		classifications.add(new Classification(ActivityNames.WALKING, walkingStart, restingStart));
//		classifications.get(classifications.size()-1).setTotalMet(3.5f + (float)getRandVal(-1.0, 1.0));
//		
//		classifications.add(new Classification(ActivityNames.RUNNING, runningStart, walkingStart));
//		classifications.get(classifications.size()-1).setTotalMet(7.0f + (float)getRandVal(-1.0, 1.0));
//		
//		classifications.add(new Classification(ActivityNames.STATIONARY, officeStart, runningStart));
//		classifications.get(classifications.size()-1).setTotalMet(1.0f);
//		
//		classifications.add(new Classification(ActivityNames.CYCLING, cyclingStart, officeStart));
//		classifications.get(classifications.size()-1).setTotalMet(5.5f + (float)getRandVal(-1.0, 1.0));
//		
//		classifications.add(new Classification(ActivityNames.WALKING, getReadyStart, cyclingStart));
//		classifications.get(classifications.size()-1).setTotalMet(2.3f + (float)getRandVal(-0.5, 0.5));
//		
//		classifications.add(new Classification(ActivityNames.CHARGING, chargingStart, getReadyStart));
//		classifications.get(classifications.size()-1).setTotalMet(1.0f);
//		
//		classifications.add(new Classification(ActivityNames.STATIONARY, eveningStart, getReadyStart));
//		classifications.get(classifications.size()-1).setTotalMet(1.0f);
//		
//		for (int l=classifications.size(), i=l-1; i>=0; --i) {
//			insert(classifications.get(i));
//		}
		
		return true;
	}
	
//	private double getRandVal(double min, double max)
//	{
//		return Math.random()*(max-min) - (max+min)/2.0; 
//	}

	@Override
	protected void done() {
		super.done();
	}
	
	/**
	 * Saves the cached values to the database
	 */
	public void insert(Classification classification)
	{
		if (isDatabaseAvailable()) {
			synchronized (insertContentValues) {
				Log.i(Constants.TAG, "Classification being inserted: "+classification.getStart());
				assignValuesToInsertContentValues(classification);
				Log.i(Constants.TAG, insertContentValues.toString());
				database.insertOrThrow(TABLE_NAME, null, insertContentValues);
			}
		}
	}
	
	/**
	 * Updates the final system's output ({@value #KEY_FINAL_SYSTEM_OUTPUT}),
	 * of the sample given by @param sampleTime.
	 */
	public void update(Classification classification)
	{
		if (isDatabaseAvailable()) {
			synchronized (updateContentValues) {
				Log.i(Constants.TAG, "Classification being updated: "+classification.getStart());
				assignValuesToUpdateContentValues(classification);
				int rows = database.update(TABLE_NAME, updateContentValues, KEY_START_LONG+"="+classification.getStart(), null);
				if (rows==0)
					Log.w(Constants.TAG, "Warning: Update Failed: table='"+TABLE_NAME+"', "+KEY_START_LONG+"="+classification.getStart()+
							", "+KEY_END_LONG+"="+classification.getEnd()+
							", "+KEY_LAST_UPDATED_AT+"="+classification.getLastUpdate());
			}
		}
	}
	
	/**
	 * Loads the latest classification
	 */
	public boolean loadLatest(Classification classification)
	{
		if (isDatabaseAvailable()) {
			Cursor cursor = database.rawQuery(SELECT_SQL + " WHERE "+KEY_START_LONG+"=(SELECT MAX("+KEY_START_LONG+") FROM "+TABLE_NAME+")", null);
			try {
				if (cursor.moveToNext()) {
					assignValuesToClassification(cursor, classification);
					return true;
				} else {
					return false;
				}
			} finally {
				cursor.close();
			}
		} else {
			return false;
		}
	}
	
	/**
	 * Loads the latest classification that occurred before the given time
	 * 
	 * Please note: This function shouldn't be called iteratively,
	 * it might lead to a heavy load on the database, and hence on the phone.
	 */
	public boolean loadLatestBefore(long time, Classification classification)
	{
		if (isDatabaseAvailable()) {
			Cursor cursor = database.rawQuery(
					SELECT_SQL + " WHERE "+KEY_START_LONG+"=(" +
							" SELECT MAX("+KEY_START_LONG+")" +
							" FROM "+TABLE_NAME +
							" WHERE "+KEY_START_LONG+"<"+time +
							")", 
					null);
			try {
				if (cursor.moveToNext()) {
					assignValuesToClassification(cursor, classification);
					return true;
				} else {
					return false;
				}
			} finally {
				cursor.close();
			}
		} else {
			return false;
		}
	}
	
	/**
	 * Loads the classification that started at the given time
	 */
	public boolean loadAllBetween(long startStartTime, long endStartTime, Classification reusableClassification, ClassificationDataCallback callback)
	{
		if (isDatabaseAvailable()) {
			boolean retrieved = false;
			Cursor cursor = database.rawQuery(
					SELECT_SQL + 
					" WHERE "+KEY_START_LONG+" BETWEEN "+Math.min(startStartTime, endStartTime)+" AND "+Math.max(startStartTime, endStartTime) +
					" ORDER BY " + KEY_START_LONG + " ASC ",
					null);
			try {
				while (cursor.moveToNext()) {
					retrieved = true;
					
					//	assign it
					assignValuesToClassification(cursor, reusableClassification);
					//	return it
					callback.onRetrieve(reusableClassification);
				}
			} finally {
				cursor.close();
			}
			return retrieved;
		} else {
			return false;
		}
	}
	
	/** 
	 * Loads all the classifications that started between the times given.
	 * Please note, make sure to provide enough {@link Classification} instances
	 */
	public void loadUpdated(long startStartTime, long endStartTime, long lastUpdate, Classification reusableClassification, ClassificationDataCallback callback)
	{
		if (isDatabaseAvailable()) {
			Cursor cursor = database.rawQuery(
					SELECT_SQL + 
					" WHERE (("+KEY_START_LONG+" BETWEEN "+Math.min(startStartTime, endStartTime)+" AND "+Math.max(startStartTime, endStartTime) + ") " +
					" OR ("+KEY_END_LONG+" BETWEEN "+Math.min(startStartTime, endStartTime)+" AND "+Math.max(startStartTime, endStartTime) + ")) " +
					" AND "+KEY_LAST_UPDATED_AT+">"+lastUpdate +
					" ORDER BY " + KEY_START_LONG + " ASC ",
					null);
			try {
				while (cursor.moveToNext()) {
					//	assign it
					assignValuesToClassification(cursor, reusableClassification);
					//	return it
					callback.onRetrieve(reusableClassification);
				}
			} finally {
				cursor.close();
			}
		} else {
			Log.e(Constants.TAG, "Database unavailable!");
		}
	}
	
	/** 
	 * Loads all the classifications that either started after the lastUploadStart time,
	 * or updated after the updateTimeThreshold.
	 * Please note, make sure to provide enough {@link Classification} instances
	 */
	public void loadUploadable(long lastUploadStart, long updateTimeThreshold, Classification reusableClassification, ClassificationDataCallback callback)
	{
		if (isDatabaseAvailable()) {
			Cursor cursor = database.rawQuery(
					SELECT_SQL + 
					" WHERE "+KEY_START_LONG+">"+lastUploadStart+" " + 
					" OR "+KEY_LAST_UPDATED_AT+">"+updateTimeThreshold +
					" ORDER BY " + KEY_START_LONG + " ASC ",
					null);
			try {
				while (cursor.moveToNext()) {
					//	assign it
					assignValuesToClassification(cursor, reusableClassification);
					//	return it
					callback.onRetrieve(reusableClassification);
				}
			} finally {
				cursor.close();
			}
		} else {
			Log.e(Constants.TAG, "Database unavailable!");
		}
	}
	
	/** 
	 * Loads all the classifications that have not been checked.
	 * Please note, make sure to provide enough {@link Classification} instances
	 */
//	public void loadUnchecked(Classification reusableClassification, ClassificationDataCallback callback)
//	{
//		if (isDatabaseAvailable()) {
//			Cursor cursor = database.rawQuery(
//					SELECT_SQL + 
//					" WHERE "+KEY_IS_CHECKED+"=0" +
//					" ORDER BY " + KEY_START_LONG + " ASC ",
//					null);
//			try {
//				while (cursor.moveToNext()) {
//					//	assign it
//					assignValuesToClassification(cursor, reusableClassification);
//					//	return it
//					callback.onRetrieve(reusableClassification);
//				}
//			} finally {
//				cursor.close();
//			}
//		}
//	}
	
	
	/**
	 * Removes any extra data available in the database table,
	 * leaving only the period required as defined in {@link Constants#DURATION_KEEP_DB_ACTIVITY_DATA}
	 * and that hasn't been uploaded ({@value #KEY_IS_CHECKED});
	 */
	public void trim()
	{
		if (isDatabaseAvailable()) {
			long currentTime = System.currentTimeMillis();
			if (currentTime-lastTrim>Constants.DURATION_KEEP_DB_ACTIVITY_DATA) {
				long cutOffLimit = currentTime - Constants.DURATION_KEEP_DB_ACTIVITY_DATA;
				database.delete(TABLE_NAME, KEY_LAST_UPDATED_AT+"<"+cutOffLimit, null);
				lastTrim = currentTime;
			}
		}
	}
	
	/**
	 * Updates the given row's ({@value #KEY_IS_CHECKED}) to true
	 */
//	public void updateChecked(long startTime)
//	{
//		if (isDatabaseAvailable()) {
//			int rows = database.update(TABLE_NAME, updateCheckedContentValues, KEY_START_LONG+"="+startTime, null);
//			if (rows==0)
//				Log.w(Constants.TAG, "Warning: Update Failed: table='"+TABLE_NAME+"', "+KEY_START_LONG+"="+startTime+", "+KEY_IS_CHECKED+"=1");
//		}
//	}
	
	/**
	 * Loads a single classification.
	 * 
	 * @param start
	 * @return
	 */
	public Classification loadClassification(long start) {
		if (isDatabaseAvailable()) {
			Cursor cursor = database.rawQuery(
					SELECT_SQL + 
					" WHERE "+KEY_START_LONG+"="+start +
					" ORDER BY " + KEY_START_LONG + " ASC ",
					null);
			try {
				if (cursor.moveToNext()) {
					Classification result = new Classification();
					//	assign it
					assignValuesToClassification(cursor, result);
					//	return it
					return result;
				}
				return null;
			} finally {
				cursor.close();
			}
		} else {
			Log.e(Constants.TAG, "Database unavailable!");
			return null;
		}
	}
	
	/**
	 * A convenience function to assign values stored
	 * in this instance to the {@link #insertContentValues} field
	 * before it is used in saving to the database.
	 */
	private void assignValuesToInsertContentValues(Classification classification)
	{
		insertContentValues.put(KEY_START_LONG, classification.getStart());
		insertContentValues.put(KEY_END_LONG, classification.getEnd());
		insertContentValues.put(KEY_ACTIVITY, classification.getClassification());
		insertContentValues.put(KEY_NUM_OF_BATCHES, classification.getNumberOfBatches());
		//insertContentValues.put(KEY_TOTAL_EE_ACT, classification.getTotalEeAct());
		insertContentValues.put(KEY_MET, classification.getMet());
		insertContentValues.put(KEY_MYTRACKS_ID, classification.getMyTracksId());
		insertContentValues.put(KEY_LAST_UPDATED_AT, System.currentTimeMillis());
		
//		for (Map.Entry<String,Object> entry:insertContentValues.valueSet()) {
//			Log.d(Constants.TAG, "Insert: "+entry.getKey()+"="+entry.getValue());
//		}
	}
	
	/**
	 * A convenience function to assign values stored
	 * in this instance to the {@link #updateContentValues} field
	 * before it is used in saving to the database.
	 * 
	 * All but the {@link #KEY_START_STR} field of the classification is updated.
	 * The {@link #KEY_START_STR} field is not to change and serves as
	 * reference point to the activity being updated 
	 */
	private void assignValuesToUpdateContentValues(Classification classification)
	{
		updateContentValues.put(KEY_END_LONG, classification.getEnd());
		updateContentValues.put(KEY_ACTIVITY, classification.getClassification());
		updateContentValues.put(KEY_NUM_OF_BATCHES, classification.getNumberOfBatches());
		//updateContentValues.put(KEY_TOTAL_EE_ACT, classification.getTotalEeAct());
		updateContentValues.put(KEY_MET, classification.getMet());
		updateContentValues.put(KEY_MYTRACKS_ID, classification.getMyTracksId());
		updateContentValues.put(KEY_LAST_UPDATED_AT, System.currentTimeMillis());
	}
	
	/**
	 * A convenience function to assign values from the cursor
	 * to the {@link Classification}. This function assumes
	 * that the cursor was derived from a SELECT statement
	 * based on the SELECT statement described in {@link #SELECT_SQL}
	 * 
	 * @return
	 * a classification with all values set as from the cursor,
	 * and {@link Classification#withContext(Context)} function called.
	 */
	private void assignValuesToClassification(Cursor cursor, Classification classification)
	{
		classification.init(cursor.getString(cursor.getColumnIndex(KEY_ACTIVITY)),
				cursor.getLong(cursor.getColumnIndex(KEY_START_LONG)),
				cursor.getLong(cursor.getColumnIndex(KEY_END_LONG)));
		classification.setNumberOfBatches(cursor.getInt(cursor.getColumnIndex(KEY_NUM_OF_BATCHES)));
		//classification.setTotalEeAct(cursor.getFloat(cursor.getColumnIndex(KEY_TOTAL_EE_ACT)));
		classification.setMet(cursor.getFloat(cursor.getColumnIndex(KEY_MET)));
		classification.setMyTracksId(cursor.getLong(cursor.getColumnIndex(KEY_MYTRACKS_ID)));
		classification.setLastUpdate(cursor.getLong(cursor.getColumnIndex(KEY_LAST_UPDATED_AT)));
		classification.withContext(context);
	}
	
}
