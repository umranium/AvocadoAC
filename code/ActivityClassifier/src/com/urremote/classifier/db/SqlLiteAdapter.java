
package com.urremote.classifier.db;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import com.urremote.classifier.common.Constants;
import com.urremote.classifier.repository.DbAdapter;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * class for creating SQLite database in the device memory.
 * 
 * @author Justin Lee
 * 
 * <p>
 * Please note:<br/>
 * This class is private within its package and should not be used
 * outside the package.<br/>
 * Many changes have gone into this class to make is as straight-forward
 * as possible, while maintaining db-access high efficiency in the system. <br/>
 * This class, by design should only be available to the classes in
 * the same package.
 * </p>
 * 
 * @author Umran Azziz
 */
public class SqlLiteAdapter {
	
	//	only one instance created, despite the context
	private static SqlLiteAdapter instance = null;
	
	//	only way to obtain that instance
	public static SqlLiteAdapter getInstance(Context context) {
		//	first check if the instance exists already...
		if (instance==null) {
			//	enter a lock waiting..
			synchronized (SqlLiteAdapter.class) {
				//	if the instance is still not created
				//		(maybe another thread bet us to it!)
				if (instance==null) {
					instance = new SqlLiteAdapter(context);
				}
			}
		}
		return instance;
	}
	
	private static final int DATABASE_VERSION = 7;
	
	private Context context;
	private SQLiteOpenHelper helper;
	private SQLiteDatabase database;
	private OptionsTable optionsTable;
	private DebugDataTable debugDataTable;
	private ActivitiesTable activitiesTable;
	private DbTableAdapter[] tableAdapters;
	
	//	make the constructor private so that no other classes can create an instance of this class
	private SqlLiteAdapter(Context context)
	{		
		this.context = context;
		this.optionsTable = new OptionsTable(context);
		this.debugDataTable = new DebugDataTable(context);
		this.activitiesTable = new ActivitiesTable(context);
		
		this.tableAdapters = new DbTableAdapter[] {
			this.optionsTable,
			this.debugDataTable,
			this.activitiesTable,
		};
		
		this.helper = new SQLiteOpenHelper(context, Constants.RECORDS_FILE_NAME, null, DATABASE_VERSION) {
			@Override
			public void onOpen(SQLiteDatabase db) {
				for (DbTableAdapter tableAdapter:SqlLiteAdapter.this.tableAdapters) {
					if (!tableAdapter.init(db)) {
						throw new RuntimeException("Failed to initialize Table Adapter: "+tableAdapter.getClass().getSimpleName());
					}
				}
			}

			@Override
			public void onCreate(SQLiteDatabase db) {
				for (DbTableAdapter tableAdapter:SqlLiteAdapter.this.tableAdapters) {
					tableAdapter.createTable(db);
				}
			}

			@Override
			public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
				for (DbTableAdapter tableAdapter:SqlLiteAdapter.this.tableAdapters) {
					tableAdapter.dropTable(db);
				}
				for (DbTableAdapter tableAdapter:SqlLiteAdapter.this.tableAdapters) {
					tableAdapter.createTable(db);
				}
			}
		};
		this.database = helper.getWritableDatabase();
	}

	/**
	 * Returns the only valid instance of {@link OptionsTable}
	 */
	public OptionsTable getOptionsTable() {
		return optionsTable;
	}
	
	/**
	 * Returns the only valid instance of {@link DebugDataTable}
	 */
	public DebugDataTable getDebugDataTable() {
		return debugDataTable;
	}
	
	/**
	 * Returns the only valid instance of {@link ActivitiesTable}
	 */
	public ActivitiesTable getActivitiesTable() {
		return activitiesTable;
	}
	
	public void close() {
		if (this.database!=null) {
			if (this.database.isOpen())
				helper.close();
			this.database = null;
		}
	}
	
	public void open() {
//		if (this.database!=null && this.database.isOpen())
//			helper.close();
		this.database = helper.getWritableDatabase();
	}
	
	public boolean isOpen() {
		return this.database!=null && this.database.isOpen();
	}
	
	public String getPath() {
		return this.database.getPath();
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#finalize()
	 */
	@Override
	protected void finalize() throws Throwable {
		for (DbTableAdapter tableAdapter:this.tableAdapters) {
			tableAdapter.done();
		}
		helper.close();
		super.finalize();
	}
	
}
