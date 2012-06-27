package com.urremote.classifier.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import com.urremote.classifier.db.Migrator;
import com.urremote.classifier.db.Migrator.MigrationException;
import com.urremote.classifier.db.SqlLiteAdapter;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQuery;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

public class DbFileUtil {

	public static void copyFile(File sourceFile, File destinationFile) throws IOException {
		InputStream lm_oInput = new FileInputStream(sourceFile);
		byte[] buff = new byte[128];
		FileOutputStream lm_oOutPut = new FileOutputStream(destinationFile);
		while (true) {
			int bytesRead = lm_oInput.read(buff);
			if (bytesRead == -1)
				break;
			lm_oOutPut.write(buff, 0, bytesRead);
		}

		lm_oInput.close();
		lm_oOutPut.close();
		lm_oOutPut.flush();
		lm_oOutPut.close();
	}
	
	public static boolean copyFileToSd(Context context, String fileName, SqlLiteAdapter sqlLiteAdapter, boolean background) {
		if (!DbFileUtil.hasStorage(false)) {
			out(context, "SD-card isn't plugged in!", background);
			return false;
		}
		
		if (!DbFileUtil.hasStorage(true)) {
			out(context, "Unable to write to SD-card!", background);
			return false;
		}
		
		String dbSrc = sqlLiteAdapter.getPath();
		File dbSrcFile = new File(dbSrc);
		if (dbSrc==null || !dbSrcFile.exists()) {
			out(context, "Database hasn't been created yet!", background);
			return false;
		} else {
			File dbDstFile = new File(Constants.PATH_SD_CARD_APP_LOC, fileName);
			if (!dbDstFile.getParentFile().exists()) {
				dbDstFile.getParentFile().mkdirs();
			}
			
			try {
				DbFileUtil.copyFile(dbSrcFile, dbDstFile);
				out(context, "Database copied into " + dbDstFile.getAbsolutePath(), background);
				return true;
			} catch (Exception e) {
				out(context, "Database could NOT be copied:\n" + e.getMessage(), background);
				Log.e(Constants.TAG, "Copy database to SD Card Error", e);
				return false;
			}
		}
	}
	
	public static boolean migrateDataFromSd(Context context, String fileName, SqlLiteAdapter sqlLiteAdapter, boolean background) {
		try {
			File dbSrcFile = new File(Constants.PATH_SD_CARD_APP_LOC, fileName);
			if (!dbSrcFile.exists()) {
				out(context, dbSrcFile.getAbsolutePath()+"\nDoesn't exist!", background);
				return false;
			}
			
			String dbDstFilePath = sqlLiteAdapter.getPath();
			sqlLiteAdapter.close();
			
			Migrator migrator = new Migrator(dbDstFilePath, dbSrcFile.getAbsolutePath());
			migrator.migrate();
			return true;
		} catch (MigrationException e) {
			Log.e(Constants.TAG, e.getMessage(), e);
			out(context, e.getMessage(), background);
			return false;
		} finally {
			if (!sqlLiteAdapter.isOpen())
				sqlLiteAdapter.open();
		}
		
	}
	
	public static boolean copyFileFromSd(Context context, String fileName, SqlLiteAdapter sqlLiteAdapter, boolean background) {
		String dbDst = sqlLiteAdapter.getPath();
		File dbDstFile = new File(dbDst);
		
		sqlLiteAdapter.close();
		SQLiteDatabase db = null;
		
		try {
			File dbSrcFile = new File(Constants.PATH_SD_CARD_APP_LOC, fileName);
			if (!dbSrcFile.exists()) {
				out(context, dbSrcFile.getAbsolutePath()+"\nDoesn't exist!", background);
				return false;
			}
			
			db = SQLiteDatabase.openDatabase(dbSrcFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
			if (db==null) {
				out(context, "The database on the SD card couldn't be openned!", background);
				return false;
			}
			
			if (db.getVersion()<SqlLiteAdapter.DATABASE_VERSION) {
				out(context, "The version of the database on the SD card\nis an older than this application requires!", background);
				return false;
			}
			
			db.close();
			db = null;
			
			try {
				DbFileUtil.copyFile(dbSrcFile, dbDstFile);
				out(context, "Database copied from " + dbSrcFile.getAbsolutePath(), background);
				return true;
			} catch (Exception e) {
				out(context, "Database could NOT be copied:\n" + e.getMessage(), background);
				Log.e(Constants.TAG, "Copy database to SD Card Error", e);
				return false;
			}
		} finally {
			sqlLiteAdapter.open();
			if (db!=null) {
				db.close();
			}
		}
	}
	
	private static void out(Context context, String msg, boolean background) {
		if (!background) {
			Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
		}
		Log.w(Constants.TAG, msg);
	}
	
	public static boolean hasStorage(boolean requireWriteAccess) {
	    //TODO: After fix the bug,  add "if (VERBOSE)" before logging errors.
	    String state = Environment.getExternalStorageState();

	    if (Environment.MEDIA_MOUNTED.equals(state)) {
	    	File externalStorage = Environment.getExternalStorageDirectory();
	        if (requireWriteAccess) {
	            boolean writable = externalStorage.canWrite();
	            Log.v(Constants.TAG, "storage writable is " + writable);
	            return writable;
	        } else {
	            return true;
	        }
	    } else if (!requireWriteAccess && Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
	        return true;
	    }
	    return false;
	}
	
	
}
