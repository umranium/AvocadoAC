package com.urremote.classifier.fusiontables;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.WeakHashMap;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.urremote.classifier.auth.AuthManager;
import com.urremote.classifier.common.Constants;
import com.urremote.classifier.common.StringComparator;
import com.urremote.classifier.db.ActivitiesTable;
import com.urremote.classifier.db.ActivitiesTable.ClassificationDataCallback;
import com.urremote.classifier.db.OptionsTable;
import com.urremote.classifier.db.SqlLiteAdapter;
import com.urremote.classifier.rpc.Classification;

public class FusionTableActivitySync extends FusionTables {
	
	private static final String VIEW_NAME = "Activities_Summary";

	private static String[] DB_ACTIVITY_TABLE_COLS = new String[] {
		ActivitiesTable.KEY_ACTIVITY,
		ActivitiesTable.KEY_END_LONG,
		ActivitiesTable.KEY_LAST_UPDATED_AT,
		ActivitiesTable.KEY_MYTRACKS_ID,
		ActivitiesTable.KEY_NUM_OF_BATCHES,
		ActivitiesTable.KEY_START_LONG,
		//ActivitiesTable.KEY_TOTAL_EE_ACT,
		ActivitiesTable.KEY_MET
	};
	
	private static long ROWID_CACHE_DURATION = 24*60*60*1000L; // 24hrs
	private static long ROWID_CACHE_TRIM_PERIOD = 6*60*60*1000L; // 6hrs
	
	Handler handler;
	OptionsTable optionsTable;
	ActivitiesTable activitiesTable;
	Table table;
	long latestUpdateTime; // the latest update time
	final Object LATEST_UPLOAD_TIME_MUTEX = new Object();
	long latestStartTime; // the latest start time
	final Object LATEST_START_TIME_MUTEX = new Object();
	RowIdCache rowIdCache;
	FusionTableUpdater updater;
	boolean running;

	public FusionTableActivitySync(Context context) {
		super(context, new AuthManager(context, SERVICE_ID));
		this.handler = new Handler(context.getMainLooper());
		
		this.optionsTable = SqlLiteAdapter.getInstance(context).getOptionsTable();
		this.activitiesTable = SqlLiteAdapter.getInstance(context).getActivitiesTable();
		
		this.running = true;
		this.updater = new FusionTableUpdater();
		this.updater.start();
	}
	
	public void quit() {
		this.running = false;
	}

	private Table verifyTableExists() {
		
		String tableId = this.optionsTable.getFusionTableId();
		
		if (tableId!=null) {
			Log.i(TAG, "Previous Fusion Table Id found");
			
			List<Table> serverTables = retrieveTables();
			if (serverTables==null)
				return null;
			
			for (Table table:serverTables) {
				if (table.id.equals(tableId)) {
					List<Column> columns = retrieveTableColumns(table.id);
					if (columns==null)
						return null;
					table.columns = columns;
					
					if (ACTIVITY_TABLE.equals(table)) {
						return table;
					}
					
					break;
				}
			}
		}
		
		Table newTable = createTable(ACTIVITY_TABLE);
		if (newTable!=null) {
			createView(VIEW_NAME, newTable.id, ACTIVITY_TABLE);
		}
		
		return newTable;
	}
	
	private void updateLatestUpdateTime(long updateTime) {
		if (latestUpdateTime<updateTime) {
			synchronized (LATEST_UPLOAD_TIME_MUTEX) {
				if (latestUpdateTime<updateTime) {
					latestUpdateTime = updateTime;
				}
			}
		}
	}
	
	private void updateLatestStartTime(long startTime) {
		if (latestStartTime<startTime) {
			synchronized (LATEST_START_TIME_MUTEX) {
				if (latestStartTime<startTime) {
					latestStartTime = startTime;
				}
			}
		}
	}
	
	/**
	 * @param tableId ID of table to check in
	 * @return Either the latest update time or -1 if table is empty, or NULL if an error occurs
	 */
	private Long fetchLatestUploadTime(String tableId) {
		String query = "SELECT MAXIMUM('"+ACTIVITY_TABLE.getFusionColumn(ActivitiesTable.KEY_LAST_UPDATED_AT)+"') FROM "+tableId;
		List<String[]> results = new ArrayList<String[]>();
		if (runQuery(false, query, results)) {
			if (results.size()>=2) {
				return Long.parseLong(results.get(1)[0]);
			} else {
				return -1L;
			}
		}
		return null;
	}
	
	/**
	 * @param tableId ID of table to check in
	 * @return Either the latest start time or -1 if table is empty, or NULL if an error occurs
	 */
	private Long fetchLatestStartTime(String tableId) {
		//	maximum for dates doesn't seem to work very well
		String query = "SELECT MAXIMUM('"+ACTIVITY_TABLE.getFusionColumn(ActivitiesTable.KEY_START_LONG)+"') FROM "+tableId;
		List<String[]> results = new ArrayList<String[]>();
		if (runQuery(false, query, results)) {
			if (results.size()>=2) {
				return Long.parseLong(results.get(1)[0]);
			} else {
				return -1L;
			}
		}
		return null;
	}
	
	private void updateTable() {
		table = verifyTableExists();
		if (table!=null) {
			this.optionsTable.setFusionTableId(table.id);
			this.optionsTable.save();
			
//			processDefaultLatestStartAndUpdateTimes();
			
			rowIdCache = new RowIdCache(table.id);
			
			Long latestUpload = fetchLatestUploadTime(table.id);
			if (latestUpload!=null && latestUpload>0) {
				latestUpdateTime = latestUpload;
			}

			Long latestStart = fetchLatestStartTime(table.id);
			if (latestStart!=null && latestStart>0) {
				latestStartTime = latestStart;
			}
		}
	}
	
	private void extractDbData(Classification classification, Map<String,Object> outputMap) {
		outputMap.clear();
		outputMap.put(ActivitiesTable.KEY_START_LONG, classification.getStart());
		outputMap.put(ActivitiesTable.KEY_END_LONG, classification.getEnd());
		outputMap.put(ActivitiesTable.KEY_ACTIVITY, classification.getClassification());
		outputMap.put(ActivitiesTable.KEY_NUM_OF_BATCHES, (long)classification.getNumberOfBatches());
		//outputMap.put(ActivitiesTable.KEY_TOTAL_EE_ACT, (double)classification.getTotalEeAct());
		outputMap.put(ActivitiesTable.KEY_MET, (double)classification.getMet());
		outputMap.put(ActivitiesTable.KEY_MYTRACKS_ID, (long)classification.getMyTracksId());
		outputMap.put(ActivitiesTable.KEY_LAST_UPDATED_AT, (long)classification.getLastUpdate());
	}

	private void setDbData(Map<String,Object> inputMap, Classification classification) {
		if (inputMap.containsKey(ActivitiesTable.KEY_START_LONG)) {
			classification.setStart((Long)inputMap.get(ActivitiesTable.KEY_START_LONG));
		} else {
			classification.setStart(0L);
		}
		if (inputMap.containsKey(ActivitiesTable.KEY_END_LONG)) {
			classification.setEnd((Long)inputMap.get(ActivitiesTable.KEY_END_LONG));
		} else {
			classification.setEnd(0L);
		}
		if (inputMap.containsKey(ActivitiesTable.KEY_ACTIVITY)) {
			classification.setClassification((String)inputMap.get(ActivitiesTable.KEY_ACTIVITY));
		} else {
			classification.setClassification("");
		}
		if (inputMap.containsKey(ActivitiesTable.KEY_NUM_OF_BATCHES)) {
			classification.setNumberOfBatches((int)(long)(Long)inputMap.get(ActivitiesTable.KEY_NUM_OF_BATCHES));
		} else {
			classification.setNumberOfBatches(0);
		}
		//if (inputMap.containsKey(ActivitiesTable.KEY_TOTAL_EE_ACT)) {
		//	classification.setTotalEeAct((float)(double)(Double)inputMap.get(ActivitiesTable.KEY_TOTAL_EE_ACT));
		//} else {
		//	classification.setTotalEeAct(Float.NaN);
		//}
		if (inputMap.containsKey(ActivitiesTable.KEY_MET)) {
			classification.setMet((float)(double)(Double)inputMap.get(ActivitiesTable.KEY_MET));
		} else {
			classification.setMet(Float.NaN);
		}
		if (inputMap.containsKey(ActivitiesTable.KEY_MYTRACKS_ID)) {
			classification.setMyTracksId((Long)inputMap.get(ActivitiesTable.KEY_MYTRACKS_ID));
		} else {
			classification.setLastUpdate(0L);
		}
		if (inputMap.containsKey(ActivitiesTable.KEY_LAST_UPDATED_AT)) {
			classification.setLastUpdate((Long)inputMap.get(ActivitiesTable.KEY_LAST_UPDATED_AT));
		} else {
			classification.setLastUpdate(0L);
		}
	}
	
	private String getRowId(String tableId, Long startTime) {
		String query = "SELECT ROWID FROM "+tableId+
				" WHERE '"+ACTIVITY_TABLE.getFusionColumn(ActivitiesTable.KEY_START_LONG)+"'="+
				Long.toString(startTime)+"";
		List<String[]> results = new ArrayList<String[]>();
		if (runQuery(true, query.toString(), results)) {
			if (results.size()>=2)
				return results.get(results.size()-1)[0];
			else
				return "";
		}
		return null;
	}
	
	private Map<Long,String> fetchAllRowIds(String tableId, Long startTime) {
		String query = "SELECT '"+ACTIVITY_TABLE.getFusionColumn(ActivitiesTable.KEY_START_LONG)+"',ROWID FROM "+tableId+
				" WHERE '"+ACTIVITY_TABLE.getFusionColumn(ActivitiesTable.KEY_START_LONG)+"'>="+Long.toString(startTime)+"";
		List<String[]> results = new ArrayList<String[]>();
		if (runQuery(true, query.toString(), results)) {
			if (results.size()>=2) {
				Map<Long, String> map = new HashMap<Long, String>(results.size()-1);
				for (int i=1; i<results.size(); ++i) {
					map.put(Long.parseLong(results.get(i)[0]), results.get(i)[1]);
				}
				return map;
			}
			else
				return Collections.EMPTY_MAP;
		}
		return null;
	}
	
	private boolean update(String tableId, String rowId, Classification classification) {
		HashMap<String,Object> dbValueMap = new HashMap<String,Object>(9);
		extractDbData(classification, dbValueMap);
		
		Map<String,String> fusionValueMap = ACTIVITY_TABLE.extractFusionData(classification, dbValueMap);
		
		StringBuilder query = new StringBuilder(1024);
		query.append("UPDATE ").append(tableId).append(" SET ");
		boolean first = true;
		for (Map.Entry<String, String> value:fusionValueMap.entrySet()) {
			if (first) {
				first = false;
			} else {
				query.append(", ");
			}
			
			query.append("'").append(value.getKey()).append("'=");
			query.append(value.getValue());
		}
		query.append(" WHERE ROWID='").append(rowId).append("'");
		List<String[]> results = new ArrayList<String[]>();
		if (runQuery(true, query.toString(), results)) {
			updateLatestUpdateTime(classification.getLastUpdate());
			updateLatestStartTime(classification.getStart());
			return true;
		}
		return false;
	}
	
	private boolean insert(String tableId, List<Classification> classifications) {
		StringBuilder query = new StringBuilder(1024);
		
		HashMap<String,Object> dbValueMap = new HashMap<String,Object>(9);
		
		for (Classification classification:classifications) {
			extractDbData(classification, dbValueMap);
			if (dbValueMap.isEmpty())
				throw new RuntimeException("Database values can't be null");
			
			Map<String,String> fusionValueMap = ACTIVITY_TABLE.extractFusionData(classification, dbValueMap);
			if (fusionValueMap.isEmpty())
				throw new RuntimeException("Database values can't be null");
			
			query.append("INSERT INTO ").append(tableId).append(" (");
			
			{
				boolean first = true;
				for (Map.Entry<String, String> value:fusionValueMap.entrySet()) {
					if (first) {
						first = false;
					} else {
						query.append(", ");
					}
					
					query.append("'").append(value.getKey()).append("'");
				}
			}
			
			query.append(") VALUES (");
			
			{
				boolean first = true;
				for (Map.Entry<String, String> value:fusionValueMap.entrySet()) {
					if (first) {
						first = false;
					} else {
						query.append(", ");
					}
					
					query.append(value.getValue());
				}
			}
			
			query.append("); ");
		}
		
		List<String[]> results = new ArrayList<String[]>();
		if (runQuery(true, query.toString(), results)) {
			for (Classification classification:classifications) {
				updateLatestUpdateTime(classification.getLastUpdate());
				updateLatestStartTime(classification.getStart());
			}
			if (rowIdCache!=null && results.size()==classifications.size()+1) {
				for (int i=0; i<classifications.size(); ++i) {
					long start = classifications.get(i).getStart();
					String rowId = results.get(i+1)[0];
					rowIdCache.put(start, rowId);
				}
			}
			return true;
		}
		return false;
	}
	
	private class FusionTableUpdater extends Thread {
		
		private List<Classification> inserts = new ArrayList<Classification>();
		private Classification reusableClassification = new Classification();
		private boolean connectionError = false;
		
		private void doSync() {
			if (table==null)
				return;
			
			connectionError = false;
			
			inserts.clear();
			Log.d(TAG, "Fetching all records either inserted after "+latestStartTime+
					" or updated after ("+latestUpdateTime+"+"+Constants.DURATION_SERVER_UPDATE_ACTIVITY+")");
			activitiesTable.loadUploadable(latestStartTime, latestUpdateTime+Constants.DURATION_SERVER_UPDATE_ACTIVITY,
					reusableClassification, new ClassificationDataCallback() {
				public void onRetrieve(Classification classification) {
					if (connectionError)
						return;
					
					Log.d(TAG, "\tObtained start="+classification.getStart()+" lastUpdate="+classification.getLastUpdate());
					if (rowIdCache.hasRow(classification.getStart())) {
						Log.d(TAG, "\t\tAttempting to update");
						if (!update(table.id, rowIdCache.getRowId(classification.getStart()), classification)) {
							connectionError = true;
						}
					} else {
						Log.d(TAG, "\t\tGoing to insert");
						inserts.add(new Classification(classification));
					}
				}
			});
			
			if (!connectionError && !inserts.isEmpty()) {
				Log.d(TAG, "\t\tAttempting inserts");
				for (int start=0; start<inserts.size(); start+=10) {
					int end = start + 10;
					if (end>inserts.size())
						end = inserts.size();
					
					if (insert(table.id, inserts.subList(start, end))) {
						Log.d(TAG, "\t\t\tInserts ["+start+","+end+") successfull");
					}
				}
			}
		}
		
		public void run() {
			while (running) {
				if (table==null)
					updateTable();
				
				if (table!=null) {
					doSync();
				}
				
				try {
					Thread.sleep(Constants.DELAY_UPLOAD_DATA);
				} catch (InterruptedException e) {
					//	ignore
				}
			}
		}
	}
	

	private interface DataConverter<T> {
		String fromDbToFusion(T val);
		T fromFusionToDb(String val);
	}
	
	private interface DataExtractor<T> {
		T extract(Classification classification, Map<String,Object> classificationValueMap);
	}
	
	private static final DataConverter<Long> TIMESTAMP_TO_DATETIME_CONVERTER = new DataConverter<Long>() {
		
		SimpleDateFormat simpleDateFormat;
		
		{
		   simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		   simpleDateFormat.setTimeZone(TimeZone.getDefault());
		}
		
		public String fromDbToFusion(Long val) {
			return "'"+simpleDateFormat.format(new Date(val))+"'";
		}

		public Long fromFusionToDb(String val) {
			try {
				return simpleDateFormat.parse(val).getTime();
			} catch (ParseException e) {
				Log.e(TAG, "Error parsing fusion table value: '"+val+"'", e);
				return null;
			}
		}
		
	};
	
	private static final DataConverter<Long> LONG_CONVERTER = new DataConverter<Long>() {

		public String fromDbToFusion(Long val) {
			return val.toString();
		}

		public Long fromFusionToDb(String val) {
			return Long.parseLong(val);
		}
		
	};
	
	private static final DataConverter<Double> DOUBLE_CONVERTER = new DataConverter<Double>() {

		public String fromDbToFusion(Double val) {
			return val.toString();
		}

		public Double fromFusionToDb(String val) {
			return Double.parseDouble(val);
		}
		
	};
	
	private static final DataConverter<String> STRING_QUOTE_CONVERTER = new DataConverter<String>() {

		public String fromDbToFusion(String val) {
			return "'"+val.toString()+"'";
		}

		public String fromFusionToDb(String val) {
			return val;
		}
		
	};
	
	private static final DataExtractor<Double> DURATION_EXTRACTOR = new DataExtractor<Double>() {
		public Double extract(Classification classification, Map classificationValueMap) {
			long duration = classification.getEnd() - classification.getStart();
			return (double)duration/(60*1000.0);
		}
	};
	
	private static final DataExtractor<String> ACTIVITY_NICE_NAME_EXTRACTOR = new DataExtractor<String>() {
		public String extract(Classification classification, Map classificationValueMap) {
			return classification.getNiceClassification();
		};
	};
	
	private static final DataExtractor<Double> MET_EXTRACTOR = new DataExtractor<Double>() {
		public Double extract(Classification classification, Map classificationValueMap) {
			return (double)classification.getMet() / classification.getNumberOfBatches();
		};
	};
	
	private static class ColumnExtractor implements DataExtractor<Object> {
		
		String columnName;
		
		public ColumnExtractor(String columnName) {
			this.columnName = columnName;
		}

		public Object extract(Classification classification,
				Map<String, Object> classificationValueMap) {
			Object val = classificationValueMap.get(columnName);
			return val;
		}
		
	}

	private static class ColumnExtra {
		DataExtractor<Object> dataExtractor;
		DataConverter<Object> dataConverter;
		String dbWriteColumn;
		
		@SuppressWarnings("unchecked")
		public ColumnExtra(DataExtractor<?> dataExtractor, DataConverter<?> dataConverter, String dbWriteColumn) {
			super();
			
			if (dataExtractor==null)
				throw new RuntimeException("Data Extractor unspecified");
			
			this.dataExtractor = (DataExtractor<Object>)dataExtractor;
			this.dataConverter = (DataConverter<Object>)dataConverter;
			this.dbWriteColumn = dbWriteColumn;
		}
		
		public String extractData(Classification classification, Map<String,Object> classificationValueMap) {
			Object extracted = dataExtractor.extract(classification,classificationValueMap);
			if (dataConverter!=null) {
				return dataConverter.fromDbToFusion(extracted);
			} else {
				return extracted.toString();
			}
		}
		
		public void putData(String fusionTableValue, Map<String,Object> outDbValues) {
			if (dbWriteColumn!=null) {
				if (dataConverter!=null)
					outDbValues.put(dbWriteColumn, dataConverter.fromFusionToDb(fusionTableValue));
				else
					outDbValues.put(dbWriteColumn, fusionTableValue);
			}
		}
	}
	
	private static class ActivityTable extends Table {
		
		Map<Column,ColumnExtra> columnExtraMap;
		Map<String,Column> dbToFusionColumnMapping;

		public ActivityTable() {
			super("", "Activities");
			this.columnExtraMap = new HashMap<FusionTables.Column, FusionTableActivitySync.ColumnExtra>(10);
			this.dbToFusionColumnMapping = new TreeMap<String, Column>(StringComparator.CASE_INSENSITIVE_INSTANCE);
			
			addColumn("Start", null, "NUMBER", new ColumnExtractor(ActivitiesTable.KEY_START_LONG), LONG_CONVERTER, ActivitiesTable.KEY_START_LONG);
			addColumn("End", null, "NUMBER", new ColumnExtractor(ActivitiesTable.KEY_END_LONG), LONG_CONVERTER, ActivitiesTable.KEY_END_LONG);
			addColumn("Start Time", "Start Time", "DATETIME", new ColumnExtractor(ActivitiesTable.KEY_START_LONG), TIMESTAMP_TO_DATETIME_CONVERTER, null);
			addColumn("End Time", null, "DATETIME", new ColumnExtractor(ActivitiesTable.KEY_END_LONG), TIMESTAMP_TO_DATETIME_CONVERTER, null);
			addColumn("Duration", "Duration", "NUMBER", DURATION_EXTRACTOR, DOUBLE_CONVERTER, null);
			addColumn("Activity", null, "STRING", new ColumnExtractor(ActivitiesTable.KEY_ACTIVITY), STRING_QUOTE_CONVERTER, ActivitiesTable.KEY_ACTIVITY);
			addColumn("Activity Nice Name", "Activity", "STRING", ACTIVITY_NICE_NAME_EXTRACTOR, STRING_QUOTE_CONVERTER, null);
			addColumn("Number of Batches", null, "NUMBER", new ColumnExtractor(ActivitiesTable.KEY_NUM_OF_BATCHES), LONG_CONVERTER, ActivitiesTable.KEY_NUM_OF_BATCHES);
			addColumn("MET", "MET", "NUMBER", new ColumnExtractor(ActivitiesTable.KEY_MET), DOUBLE_CONVERTER, ActivitiesTable.KEY_MET);
			addColumn("MyTracks ID", null, "NUMBER", new ColumnExtractor(ActivitiesTable.KEY_MYTRACKS_ID), LONG_CONVERTER, ActivitiesTable.KEY_MYTRACKS_ID);
			addColumn("Last Updated Time", null, "NUMBER", new ColumnExtractor(ActivitiesTable.KEY_LAST_UPDATED_AT), LONG_CONVERTER, ActivitiesTable.KEY_LAST_UPDATED_AT);
			
			for (String dbColumn:DB_ACTIVITY_TABLE_COLS) {
				if (!dbToFusionColumnMapping.containsKey(dbColumn)) {
					throw new RuntimeException("Database column '"+dbColumn+"' has no fusion table column, data will be lost.");
				}
			}

		}
		
		private void addColumn(String fusionCol, String viewName, String typeName, DataExtractor<?> extractor, DataConverter<?> converter, String dbWriteColumn) {
			Column column = new Column("", fusionCol, typeName, viewName);
			ColumnExtra extra = new ColumnExtra(extractor, converter, dbWriteColumn);
			
			columns.add(column);
			columnExtraMap.put(column, extra);
			
			if (dbWriteColumn!=null)
				this.dbToFusionColumnMapping.put(dbWriteColumn, column);
		}
		
		public Map<String,String> extractFusionData(Classification classification, Map<String,Object> classificationValueMap) {
			Map<String,String> results = new HashMap<String, String>(10);
			for (Column column:columns) {
				ColumnExtra extra = columnExtraMap.get(column);
				results.put(column.name, extra.extractData(classification, classificationValueMap));
			}
			return results;
		}
		
		public Map<String,Object> extractDbData(Map<String,String> fusionData) {
			Map<String,Object> results = new HashMap<String, Object>(10);
			for (Column column:columns) {
				ColumnExtra extra = columnExtraMap.get(column);
				extra.putData(fusionData.get(column.name), results);
			}
			return results;
		}
		
		public String getFusionColumn(String dbColumn) {
			return dbToFusionColumnMapping.get(dbColumn).name;
		}
	}
	
	private static final ActivityTable ACTIVITY_TABLE = new ActivityTable();
	
	private class RowIdCache {
		String tableId;
		Map<Long,String> startToRowIdMap;
		Long minCachedStartTime;
		Long maxStartTime;
		long lastTrim;
//		long lastCheckServerUpdate;
		
		public RowIdCache(String tableId) {
			this.tableId = tableId;
			this.minCachedStartTime = System.currentTimeMillis()-ROWID_CACHE_DURATION;
			this.startToRowIdMap = new HashMap<Long, String>(2048);
//			lastCheckServerUpdate = System.currentTimeMillis();
			lastTrim = System.currentTimeMillis();
			
//			Map<Long,String> map = fetchAllRowIds(tableId, minCachedStartTime);
//			for (Map.Entry<Long, String> item:map.entrySet()) {
//				put(item.getKey(), item.getValue());
//			}
		}
		
		private void fetchAndPut(Long start) {
			if (lastTrim<System.currentTimeMillis()-ROWID_CACHE_TRIM_PERIOD) {
				trim();
			}
			
			Log.d(TAG, "RowId cache is fetching the RowId of the row with start="+start);
			String rowId = FusionTableActivitySync.this.getRowId(tableId, start);
			if (rowId!=null && rowId.length()>0) {
				put(start, rowId);
			}
		}
		
		public boolean hasRow(Long start) {
			Log.d(TAG, "RowId Cache check if row start="+start+" is in cache");
			if (maxStartTime==null || start>maxStartTime) {
				Log.d(TAG, "RowId Cache: Row start="+start+" too new (latest is "+maxStartTime+")");
				return false;
			}
			boolean found = startToRowIdMap.containsKey(start);
			if (!found && start<minCachedStartTime) {
				fetchAndPut(start);
				return startToRowIdMap.containsKey(start);
			} else
				return found;
		}
		
		/**
		 * Make sure to call {@link #hasRow(Long)} first.
		 * @param start starting time
		 * @return RowId of row with start
		 */
		public String getRowId(Long start) {
			return startToRowIdMap.get(start);
		}
		
		public void put(Long start, String rowId) {
			startToRowIdMap.put(start, rowId);
			if (maxStartTime==null || start>maxStartTime) {
				maxStartTime = start;
			}
		}
		
		public void trim() {
			Log.d(TAG, "Trimming Row ID Cache");
			lastTrim = System.currentTimeMillis();
			minCachedStartTime = maxStartTime - ROWID_CACHE_DURATION;
			Iterator<Long> it = startToRowIdMap.keySet().iterator();
			while (it.hasNext()) {
				Long val = it.next();
				if (val<minCachedStartTime) {
					it.remove();
				}
			}
		}
		
//		public void checkServerUpdates() {
//			Log.d(TAG, "RowId cache is checking for any server table updates");
//			Map<Long,String> updates = fetchAllRowIds(tableId, maxStartTime);
//			for (Map.Entry<Long, String> update:updates.entrySet()) {
//				put(update.getKey(), update.getValue());
//			}
//		}
		
	}
	
}
