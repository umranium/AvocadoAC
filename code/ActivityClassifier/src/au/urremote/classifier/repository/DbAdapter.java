
package au.urremote.classifier.repository;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.locks.ReentrantLock;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import au.urremote.classifier.common.Constants;
import au.urremote.classifier.db.ActivitiesTable;
import au.urremote.classifier.db.SqlLiteAdapter;
import au.urremote.classifier.rpc.Classification;
/**
 * class for creating SQLite database in the device memory.
 * and allow other classes {@link ActivityQueries} {@link OptionQueries} to use this functionality.
 * 
 * 
 * Updated by Umran:
 * This class is now only used to fetch activities for drawing the charts in
 * {@link au.urremote.classifier.activity.ActivityChartActivity}. Updates and inserts
 * are now being done though the class {@link au.urremote.classifier.db.ActivitiesTable}.
 * 
 * @author Justin Lee
 * 			
 */
public class DbAdapter {
	
	private final Context mCtx;
	
	//	we need to fetch instances of SqlLiteAdapter and ActivitiesTable
	//		to make sure that they create and initialize the tables
	private SqlLiteAdapter sqlLiteAdapter;
	private ActivitiesTable activitiesTable;
	

	/**
	 * Initialise Context
	 * @param ctx context from Activity or Service classes
	 */
	public DbAdapter(Context ctx) {
		isActivityBetweenToday=false;
		this.mCtx = ctx;
		this.sqlLiteAdapter = SqlLiteAdapter.getInstance(this.mCtx);
		this.activitiesTable = this.sqlLiteAdapter.getActivitiesTable();
	}


	//  ---------------------Start Activity Table----------------------------------
	private boolean isActivityBetweenToday;

	public synchronized  ArrayList<String[]> fetchTodayItemsFromActivityTable(Date todayTime, Date currDate) throws SQLException {
		
		Classification reusableClassification = new Classification();
		
		final ArrayList<String[]> todayItems = new ArrayList<String[]>();
		
		activitiesTable.loadAllBetween(
				todayTime.getTime(),
				currDate.getTime(),
				reusableClassification,
				new ActivitiesTable.ClassificationDataCallback() {
					@Override
					public void onRetrieve(Classification classification) {
						String[] tempPara = {
								Long.toString(classification.getStart()),
								classification.getClassification(),
								classification.getDbStartTime(),
								classification.getDbEndTime(),
								classification.isChecked()?"1":"0"
							};
						todayItems.add(tempPara);
					}
				}
				);
		
		isActivityBetweenToday=false;

		return todayItems;
	}
	
	//  ---------------------End Activity Table----------------------------------

}
