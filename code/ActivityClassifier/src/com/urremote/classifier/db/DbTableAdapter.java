package com.urremote.classifier.db;

import java.io.File;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

abstract class DbTableAdapter {
	
	protected Context context;
	protected SQLiteDatabase database;
	protected File databaseFile;
	
	protected DbTableAdapter(Context context) {
		this.context = context;
	}
	
	//	used by the DbAdapter when creating the table
	//		or dropping the table is needed
	protected abstract void createTable(SQLiteDatabase database);
	protected abstract void dropTable(SQLiteDatabase database);

	protected boolean init(SQLiteDatabase database) {
		this.database = database;
		this.databaseFile = new File(database.getPath());
		return true;
	}
	
	protected void done() {
		this.database = null;
		this.databaseFile = null;
	}
	
	
	synchronized
	protected boolean isDatabaseAvailable() {
		return internIsDatabaseAvailable(5);
	}
	
	private boolean internIsDatabaseAvailable(int countDown) {
		//	check if database is open
		if (database!=null && databaseFile!=null) {
			if (databaseFile.exists() && database.isOpen()) {
				return true;
			} else {
				if (countDown==0)
					return false;
				SqlLiteAdapter.getInstance(context).open();
				return internIsDatabaseAvailable(countDown-1);
			}
		} else {
			if (countDown==0)
				return false;
			SqlLiteAdapter.getInstance(context).open();
			return internIsDatabaseAvailable(countDown-1);
		}
	}
	
}
