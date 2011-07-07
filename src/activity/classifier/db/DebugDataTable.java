package activity.classifier.db;

import java.util.Date;

import activity.classifier.common.Constants;
import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public class DebugDataTable extends DbTableAdapter {

	public static final String TABLE_NAME = "debug_data";
	
	/**
	 * Column names in startinfo Table
	 */
	public static final String KEY_ID = "id";
	public static final String KEY_STARTED_AT = "startedAt";
	public static final String KEY_MEAN_X = "mean_x";
	public static final String KEY_MEAN_Y = "mean_y";
	public static final String KEY_MEAN_Z = "mean_z";
	public static final String KEY_SD_X = "sd_x";
	public static final String KEY_SD_Y = "sd_y";
	public static final String KEY_SD_Z = "sd_z";
	public static final String KEY_RANGE_X = "range_x";
	public static final String KEY_RANGE_Y = "range_y";
	public static final String KEY_RANGE_Z = "range_z";
	public static final String KEY_HOR_MEAN = "mean_hor";
	public static final String KEY_VER_MEAN = "mean_ver";
	public static final String KEY_HOR_RANGE = "range_hor";
	public static final String KEY_VER_RANGE = "range_ver";
	public static final String KEY_HOR_SD = "sd_hor";
	public static final String KEY_VER_SD = "sd_ver";
	public static final String KEY_COUNTS_X = "counts_x";
	public static final String KEY_COUNTS_Y = "counts_y";
	public static final String KEY_COUNTS_Z = "counts_z";
	public static final String KEY_EE_ACT = "ee_act";
	public static final String KEY_MET = "met";
	public static final String KEY_CLASSIFIER_ALGO_OUTPUT = "classifier_algo_output";
	public static final String KEY_AGGREGATOR_ALGO_OUTPUT = "aggregator_algo_output";
	public static final String KEY_FINAL_CLASSIFIER_OUTPUT = "final_classifier_output";
	public static final String KEY_FINAL_SYSTEM_OUTPUT = "final_system_output";
	
	//	reusable
	private ContentValues insertContentValues;
	private ContentValues updateContentValues;
	
	private long sampleTime;
	private Date startedAtDt = new Date();
	private Float meanX;
	private Float meanY;
	private Float meanZ;
	private Float sdX;
	private Float sdY;
	private Float sdZ;
	private Float rangeX;
	private Float rangeY;
	private Float rangeZ;
	private Float horMean;
	private Float verMean;
	private Float horRange;
	private Float verRange;
	private Float horSd;
	private Float verSd;
	private Float countsX;
	private Float countsY;
	private Float countsZ;
	private Float eeAct;
	private Float met;
	private String classifierAlgoOutput;
	private String aggregatorAlgoOutput;
	private String finalClassifierOutput;

	protected DebugDataTable(Context context) {
		super(context);
		this.insertContentValues = new ContentValues();
		this.updateContentValues = new ContentValues();
	}
	
	@Override
	protected void createTable(SQLiteDatabase database) {
		//	create the create sql
		String sql = 
			"CREATE TABLE "+TABLE_NAME+" (" +
			KEY_ID+" LONG PRIMARY KEY, " +
			KEY_STARTED_AT+" TEXT NOT NULL, " +
			KEY_MEAN_X + " REAL NULL, " +
			KEY_MEAN_Y + " REAL NULL, " +
			KEY_MEAN_Z + " REAL NULL, " +
			KEY_SD_X + " REAL NULL, " +
			KEY_SD_Y + " REAL NULL, " +
			KEY_SD_Z + " REAL NULL, " +
			KEY_RANGE_X + " REAL NULL, " +
			KEY_RANGE_Y + " REAL NULL, " +
			KEY_RANGE_Z + " REAL NULL, " +
			KEY_HOR_MEAN + " REAL NULL, " +
			KEY_VER_MEAN + " REAL NULL, " +
			KEY_HOR_RANGE + " REAL NULL, " +
			KEY_VER_RANGE + " REAL NULL, " +
			KEY_HOR_SD + " REAL NULL, " +
			KEY_VER_SD + " REAL NULL, " +
			KEY_COUNTS_X + " REAL NULL, " + 
			KEY_COUNTS_Y + " REAL NULL, " + 
			KEY_COUNTS_Z + " REAL NULL, " + 
			KEY_EE_ACT + " REAL NULL, " +
			KEY_MET + " REAL NULL, " +
			KEY_CLASSIFIER_ALGO_OUTPUT + " TEXT NULL, " +
			KEY_AGGREGATOR_ALGO_OUTPUT + " TEXT NULL, " + 
			KEY_FINAL_CLASSIFIER_OUTPUT + " TEXT NULL, " +
			KEY_FINAL_SYSTEM_OUTPUT + " TEXT NULL " +
			")";
		//	run the sql
		database.execSQL(sql);
	}
	
	@Override
	protected void dropTable(SQLiteDatabase database) {
		//	drop existing table if it exists
		database.execSQL("DROP TABLE IF EXISTS "+TABLE_NAME);
	}
	
	@Override
	protected boolean init(SQLiteDatabase database) {
		super.init(database);
		
		return true;
	}
	
	@Override
	protected void done() {
		super.done();
	}
	
	/**
	 * A convenience function to assign values stored
	 * in this instance to the {@link #insertContentValues} field
	 * before it is used in saving to the database.
	 */
	private void assignValuesToInsertContentValues()
	{
		this.startedAtDt.setTime(this.sampleTime);
		
		insertContentValues.put(KEY_ID, this.sampleTime);
		insertContentValues.put(KEY_STARTED_AT, Constants.DB_DATE_FORMAT.format(this.startedAtDt));
		insertContentValues.put(KEY_MEAN_X, this.meanX); 
		insertContentValues.put(KEY_MEAN_Y, this.meanY);
		insertContentValues.put(KEY_MEAN_Z, this.meanZ);
		insertContentValues.put(KEY_SD_X, this.sdX); 
		insertContentValues.put(KEY_SD_Y, this.sdY);
		insertContentValues.put(KEY_SD_Z, this.sdZ);
		insertContentValues.put(KEY_RANGE_X, this.rangeX); 
		insertContentValues.put(KEY_RANGE_Y, this.rangeY);
		insertContentValues.put(KEY_RANGE_Z, this.rangeZ);
		insertContentValues.put(KEY_HOR_MEAN, this.horMean);
		insertContentValues.put(KEY_VER_MEAN, this.verMean);
		insertContentValues.put(KEY_HOR_RANGE, this.horRange);
		insertContentValues.put(KEY_VER_RANGE, this.verRange);
		insertContentValues.put(KEY_HOR_SD, this.horSd);
		insertContentValues.put(KEY_VER_SD, this.verSd);
		insertContentValues.put(KEY_COUNTS_X, this.countsX);
		insertContentValues.put(KEY_COUNTS_Y, this.countsY);
		insertContentValues.put(KEY_COUNTS_Z, this.countsZ);
		insertContentValues.put(KEY_EE_ACT, this.eeAct);
		insertContentValues.put(KEY_MET, this.met);
		insertContentValues.put(KEY_CLASSIFIER_ALGO_OUTPUT, this.classifierAlgoOutput);
		insertContentValues.put(KEY_AGGREGATOR_ALGO_OUTPUT, this.aggregatorAlgoOutput);
		insertContentValues.put(KEY_FINAL_CLASSIFIER_OUTPUT, this.finalClassifierOutput);
	}
	
	/**
	 * Saves the cached values to the database
	 */
	public void insert()
	{
		if (isDatabaseAvailable()) {
			synchronized (insertContentValues) {
				assignValuesToInsertContentValues();
				database.insertOrThrow(TABLE_NAME, null, insertContentValues);
			}
		}
	}
	
	/**
	 * Updates the final system's output ({@value #KEY_FINAL_SYSTEM_OUTPUT}),
	 * of the sample given by @param sampleTime.
	 */
	public void updateFinalSystemOutput(long sampleTime, String finalSystemOutput)
	{
		if (isDatabaseAvailable()) {
			synchronized (updateContentValues) {
				updateContentValues.put(KEY_FINAL_SYSTEM_OUTPUT, finalSystemOutput);
				int rows = database.update(TABLE_NAME, updateContentValues, KEY_ID+"="+sampleTime, null);
				if (rows==0)
					Log.w(Constants.DEBUG_TAG, "Warning: Update Failed: table='"+TABLE_NAME+"', "+KEY_ID+"="+sampleTime+", "+KEY_FINAL_SYSTEM_OUTPUT+"='"+finalSystemOutput+"'");
			}
		}
	}
	
	/**
	 * Removes any extra data available in the database table,
	 * leaving only the period required as defined in {@link Constants#DURATION_KEEP_DB_DEBUG_DATA}
	 */
	public void trim()
	{
		if (isDatabaseAvailable()) {
			long cutOffLimit = System.currentTimeMillis() - Constants.DURATION_KEEP_DB_DEBUG_DATA; 
			database.delete(TABLE_NAME, KEY_ID+"<"+cutOffLimit, null);
		}
	}
	
	/**
	 * Resets the data cached in this class so as to start
	 * inserting data of the given sample time.
	 */
	public void reset(long sampleTime) {
		this.sampleTime = sampleTime;
		this.meanX = null;
		this.meanY = null;
		this.meanZ = null;
		this.sdX = null;
		this.sdY = null;
		this.sdZ = null;
		this.rangeX = null;
		this.rangeY = null;
		this.rangeZ = null;
		this.horMean = null;
		this.verMean = null;
		this.horRange = null;
		this.verRange = null;
		this.horSd = null;
		this.verSd = null;
		this.countsX = null;
		this.countsY = null;
		this.countsZ = null;
		this.eeAct = null;
		this.met = null;
		this.classifierAlgoOutput = null;
		this.aggregatorAlgoOutput = null;
		this.finalClassifierOutput = null;
	}
	
	/**
	 * Sets the unrotated means and sd of the axis x, y, and z. 
	 */
	public void setUnrotatedStats(
			float meanX, float meanY, float meanZ,
			float sdX, float sdY, float sdZ,
			float rangeX, float rangeY, float rangeZ
			)
	{
		this.meanX = meanX;
		this.meanY = meanY;
		this.meanZ = meanZ;
		this.sdX = sdX;
		this.sdY = sdY;
		this.sdZ = sdZ;
		this.rangeX = rangeX;
		this.rangeY = rangeY;
		this.rangeZ = rangeZ;
	}
	
	/**
	 * Sets the rotated horizontal and vertical means, ranges and 
	 * standard deviations.
	 */
	public void setRotatedStats(
			float horMean, float verMean,
			float horRange, float verRange,
			float horSd, float verSd
			)
	{
		this.horMean = horMean;
		this.verMean = verMean;
		this.horRange = horRange;
		this.verRange = verRange;
		this.horSd = horSd;
		this.verSd = verSd;
	}
	
	public void setMetStats(float countsX, float countsY, float countsZ, float eeAct, float met)
	{
		this.countsX = countsX;
		this.countsY = countsY;
		this.countsZ = countsZ;
		this.eeAct = eeAct;
		this.met = met;
	}
	
	/**
	 * Sets the result of the classifier algorithm
	 */
	public void setClassifierAlgoOutput(String classifierAlgoOutput)
	{
		this.classifierAlgoOutput = classifierAlgoOutput;
	}
	
	/**
	 * @param aggregatorAlgoOutput the aggregatorAlgoOutput to set
	 */
	public void setAggregatorAlgoOutput(String aggregatorAlgoOutput) {
		this.aggregatorAlgoOutput = aggregatorAlgoOutput;
	}

	/**
	 * Sets the final result of the classifier
	 */
	public void setFinalClassifierOutput(String finalClassifierOutput)
	{
		this.finalClassifierOutput = finalClassifierOutput;
	}
	
}
