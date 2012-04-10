
package com.urremote.classifier.repository;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.locks.ReentrantLock;

import com.urremote.classifier.common.Constants;
import com.urremote.classifier.db.ActivitiesTable;
import com.urremote.classifier.db.SqlLiteAdapter;
import com.urremote.classifier.rpc.Classification;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
/**
 * class for creating SQLite database in the device memory.
 * and allow other classes {@link ActivityQueries} {@link OptionQueries} to use this functionality.
 * 
 * 
 * Updated by Umran:
 * This class is now only used to fetch activities for drawing the charts in
 * {@link com.urremote.classifier.activity.ActivityChartActivity}. Updates and inserts
 * are now being done though the class {@link com.urremote.classifier.db.ActivitiesTable}.
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
		this.mCtx = ctx;
		this.sqlLiteAdapter = SqlLiteAdapter.getInstance(this.mCtx);
		this.activitiesTable = this.sqlLiteAdapter.getActivitiesTable();
	}


}
