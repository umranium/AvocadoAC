package com.urremote.classifier.db;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.urremote.classifier.common.Constants;
import com.urremote.classifier.common.StringComparator;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public class Migrator {
	
	public static class MigrationException extends Exception {
		private static final long serialVersionUID = 1844428466584034087L;

		public MigrationException() {
			super();
		}

		public MigrationException(String detailMessage, Throwable throwable) {
			super(detailMessage, throwable);
		}

		public MigrationException(String detailMessage) {
			super(detailMessage);
		}

		public MigrationException(Throwable throwable) {
			super(throwable);
		}
		
	}
	
	public static enum SqliteType {
		NULL, INTEGER, LONG, REAL, TEXT, BLOB;
	}
	
	private static class NamedElement {
		public final String name;

		public NamedElement(String name) {
			this.name = name;
		}
	}

	public static class Column extends NamedElement {
		public final SqliteType type;
		public final boolean notNull;
		public final boolean isPrimaryKey;
		public final boolean hasDefaultVal;
		public final String defaultVal;
		
		public Column(String name, SqliteType type, boolean notNull,
				boolean isPrimaryKey, String defaultVal) {
			super(name);
			this.type = type;
			this.notNull = notNull;
			this.isPrimaryKey = isPrimaryKey;
			this.hasDefaultVal = defaultVal!=null && defaultVal.length()>0;
			this.defaultVal = defaultVal;
		}
		
	}
	
	public static class Table extends NamedElement {
		public final Map<String,Column> columnMap;
		
		public Table(String name, Map<String,Column> columnMap) {
			super(name);
			this.columnMap = columnMap;
		}
	}
	
	private static <E extends NamedElement> Map<String,E> convToMap(List<E> list) {
		TreeMap<String,E> map = new TreeMap<String,E>(StringComparator.CASE_INSENSITIVE_INSTANCE);
		for (E e:list) {
			map.put(e.name, e);
		}
		return map;
	}
	
	private static boolean canAssign(SqliteType dstType, SqliteType srcType) throws MigrationException {
		switch (dstType) {
		case NULL:
			switch (srcType) {
			case NULL:
				return true;
			default:
				return false;
			}
		case INTEGER:
		case LONG:
			switch (srcType) {
			case INTEGER:
			case LONG:
				return true;
			default:
				return false;
			}
		case REAL:
			switch (srcType) {
			case INTEGER:
			case LONG:
			case REAL:
				return true;
			default:
				return false;
			}
		case TEXT:
			switch (srcType) {
			case INTEGER:
			case LONG:
			case REAL:
			case TEXT:
				return true;
			default:
				return false;
			}
		case BLOB:
			switch (srcType) {
			case BLOB:
				return true;
			default:
				return false;
			}
		default:
			throw new MigrationException("Unsupported data type "+dstType);
		}
	}
	
	private static Object retrieve(Cursor c, Column col) throws MigrationException {
		int colIndex = c.getColumnIndex(col.name);
		switch (col.type) {
		case NULL:
			return null;
		case INTEGER:
		case LONG:
			return (Long)(long)c.getInt(colIndex);
		case REAL:
			return (Double)c.getDouble(colIndex);
		case TEXT:
			return c.getString(colIndex);
		case BLOB:
			return c.getBlob(colIndex);
		default:
			throw new MigrationException("Unsupported data type "+col.type);
		}
	}
	
	private static Object assign(SqliteType dstType, SqliteType srcType, Object srcVal) throws MigrationException {
		switch (dstType) {
		case NULL:
			switch (srcType) {
			case NULL:
				return null;
			default:
				return null;
			}
		case INTEGER:
		case LONG:
			switch (srcType) {
			case INTEGER:
			case LONG:
				return srcVal;
			default:
				return null;
			}
		case REAL:
			switch (srcType) {
			case INTEGER:
			case LONG:
				return (double)(long)(Long)srcVal;
			case REAL:
				return srcVal;
			default:
				return null;
			}
		case TEXT:
			switch (srcType) {
			case INTEGER:
			case LONG:
				return Long.toString((long)(Long)srcVal);
			case REAL:
				return Double.toString((double)(Double)srcVal);
			case TEXT:
				return srcVal;
			default:
				return null;
			}
		case BLOB:
			switch (srcType) {
			case BLOB:
				return srcVal;
			default:
				return null;
			}
		default:
			throw new MigrationException("Unsupported data type "+dstType);
		}
	}
	
	public static Table loadTable(SQLiteDatabase db, String tableName) throws MigrationException {
		Cursor c = db.rawQuery("pragma table_info("+tableName+")", new String[0]);
		if (c==null) {
			throw new MigrationException("Unable to load table info for table "+tableName);
		} else {
			try {
				//Log.d(Constants.TAG, "Structure of "+tableName+" in "+db.getPath());
				int colName = c.getColumnIndex("name");
				int colType = c.getColumnIndex("type");
				int colNotNull = c.getColumnIndex("notnull");
				int colIsPK = c.getColumnIndex("pk");
				int colDefVal = c.getColumnIndex("dflt_value");
				List<Column> columns = new ArrayList<Column>();
				while (c.moveToNext()) {
					//Log.d(Constants.TAG, c.getString(colName)+":"+c.getString(colType));
					Column col = new Column(
							c.getString(colName),
							SqliteType.valueOf(c.getString(colType).toUpperCase()),
							c.getInt(colNotNull)==0?false:true,
							c.getInt(colIsPK)==0?false:true,
							c.getString(colDefVal));
					columns.add(col);
				}
				return new Table(tableName, convToMap(columns));
			} finally {
				c.close();
			}
		}
	}
	
	public static List<String> loadTableNames(SQLiteDatabase db) throws MigrationException {
		Cursor c = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", new String[0]);
		if (c==null) {
			throw new MigrationException("Unable to load table names for db "+db.getPath());
		} else {
			try {
				int colName = c.getColumnIndex("name");
				List<String> tableNames = new ArrayList<String>();
				while (c.moveToNext()) {
					tableNames.add(c.getString(colName));
				}
				return tableNames;
			} finally {
				c.close();
			}
		}
	}
	
	public static Map<String,Table> loadTables(SQLiteDatabase db) throws MigrationException {
		List<String> tableNames = loadTableNames(db);
		List<Table> tables = new ArrayList<Table>();
		for (String tableName:tableNames) {
			tables.add(loadTable(db, tableName));
		}
		return convToMap(tables);
	}
	
	private String dstDbPath;
	private String srcDbPath;
	private Map<String,Table> srcTables;
	private Map<String,Table> dstTables;
	
	public Migrator(String dstDbPath, String srcDbPath) throws MigrationException {
		this.dstDbPath = dstDbPath;
		this.srcDbPath = srcDbPath;
		
		SQLiteDatabase dstDb = SQLiteDatabase.openDatabase(dstDbPath, null, SQLiteDatabase.OPEN_READWRITE);
		try {
			
			SQLiteDatabase srcDb = SQLiteDatabase.openDatabase(srcDbPath, null, SQLiteDatabase.OPEN_READONLY);
			try {
				this.srcTables = loadTables(srcDb);
				this.dstTables = loadTables(dstDb);
			
				for (String srcTableName:srcTables.keySet()) {
					if (dstTables.containsKey(srcTableName)) {
						Table srcTable = srcTables.get(srcTableName);
						Table dstTable = dstTables.get(srcTableName);
						
						checkMigratable(dstTable, srcTable);
					}
				}
				
				dstDb.close();
				dstDb = null;
				
				srcDb.close();
				srcDb = null;
				
			} finally {
				if (srcDb!=null) {
					srcDb.close();
				}
			}
			
		} finally {
			if (dstDb!=null) {
				dstDb.close();
			}
		}
		
	}
	
	private boolean isRequired(Column col) {
		return col.notNull==true && col.type!=SqliteType.NULL && col.hasDefaultVal==false;
	}
	
	private void checkCompartibility(Column dstCol, Column srcCol) throws MigrationException {
		if (!isRequired(dstCol))
			return;
		
		if (dstCol.notNull==true && srcCol.notNull==false && !dstCol.hasDefaultVal) {
			throw new MigrationException("Destination column "+dstCol.name+
					" has no default and does not allow for NULL values, while source column "+
					srcCol.name+" does.");
		}
		
		if (!canAssign(dstCol.type, srcCol.type)) {
			throw new MigrationException("Destination type "+dstCol.type+" can not be assigned source type "+srcCol.type);
		}
	}
	
	private void checkMigratable(Table dstTable, Table srcTable) throws MigrationException {
		for (String dstColName:dstTable.columnMap.keySet()) {
			Column dstCol = dstTable.columnMap.get(dstColName);
			
			if (!isRequired(dstCol)) {
				continue;
			}
			
			if (srcTable.columnMap.containsKey(dstColName)) {
				Column srcCol = srcTable.columnMap.get(dstColName);
				
				checkCompartibility(dstCol, srcCol);
			} else {
				throw new MigrationException("The required column "+dstColName+" was not found in the source table "+srcTable.name);
			}
		}
	}
	
	private void migrate(SQLiteDatabase jointDb, Table dstTable, Table srcTable) throws MigrationException {
		if (dstTable.columnMap.isEmpty())
			return;
		
		Log.i(Constants.TAG, "Migrating table: "+dstTable.name);
		
		StringBuilder insertSql = new StringBuilder();
		StringBuilder selectSql = new StringBuilder();
		
		insertSql.append("INSERT INTO "+dstTable.name+"(");
		selectSql.append("SELECT ");
		
		boolean firstDstCol = true;
		for (String dstColName:dstTable.columnMap.keySet()) {
			Column dstCol = dstTable.columnMap.get(dstColName);
			
			if (firstDstCol)
				firstDstCol = false;
			else {
				insertSql.append(",");
				selectSql.append(",");
			}
			
			if (srcTable.columnMap.containsKey(dstColName)) {
				insertSql.append(dstColName);
				selectSql.append(dstColName);
				//Log.d(Constants.TAG, dstColName+" assigned "+dstColName);
			} else {
//				if (dstCol.hasDefaultVal)
//					selectSql.append("("+dstCol.defaultVal+") as "+dstColName);
//				else
				{
					insertSql.append(dstColName);
					selectSql.append("NULL as "+dstColName);
					//Log.d(Constants.TAG, dstColName+" assigned NULL");
				}
			}
		}
		
		insertSql.append(")");
		selectSql.append(" FROM src."+srcTable.name);
		String finalSql = insertSql.toString() + " " + selectSql.toString();
		
		Log.d(Constants.TAG, "Final select query: "+finalSql);
		
		jointDb.execSQL("DELETE FROM "+dstTable.name);
		jointDb.execSQL(finalSql);
		
		displayNumOfRows(jointDb, dstTable.name);
		displayNumOfRows(jointDb, "src."+srcTable.name);
	}
	
	public void migrate() throws MigrationException {
		SQLiteDatabase jointDb = SQLiteDatabase.openDatabase(dstDbPath, null, SQLiteDatabase.OPEN_READWRITE);
		
		try {
			String attachSql = "ATTACH '"+srcDbPath+"' AS src";
			Log.d(Constants.TAG, "Attach SQL:"+attachSql);
			jointDb.execSQL(attachSql);
			
			displayDatabases(jointDb);
			
			for (String srcTableName:srcTables.keySet()) {
				if (dstTables.containsKey(srcTableName)) {
					Table srcTable = srcTables.get(srcTableName);
					Table dstTable = dstTables.get(srcTableName);
					
					migrate(jointDb, dstTable, srcTable);
				}
			}
			Log.d(Constants.TAG, "Finished migration");
		} finally {
			jointDb.close();
		}
	}
	
	public static void displayAll(SQLiteDatabase db, String tableName) {
		Cursor c = db.rawQuery("SELECT * FROM "+tableName, new String[0]);
		try {
			displayRows(c);
		} finally {
			c.close();
		}
	}
	
	public static void displayNumOfRows(SQLiteDatabase db, String tableName) {
		Log.d(Constants.TAG, "Number of rows of "+tableName);
		Cursor c = db.rawQuery("SELECT count(*) FROM "+tableName, new String[0]);
		try {
			displayRows(c);
		} finally {
			c.close();
		}
	}
	
	public static void displayDatabases(SQLiteDatabase db) {
		Cursor c = db.rawQuery("PRAGMA database_list", new String[0]);
		try {
			displayRows(c);
		} finally {
			c.close();
		}
	}
	
	public static void displayRows(Cursor c) {
		String cols[] = c.getColumnNames();
		
		Log.d(Constants.TAG, Arrays.toString(cols));
		
		String vals[] = new String[c.getColumnCount()];
		while (c.moveToNext()) {
			for (int i=0; i<c.getColumnCount(); ++i) {
				vals[i] = c.getString(i);
			}
			Log.d(Constants.TAG, Arrays.toString(vals));
		}
	}
		
}
