package com.urremote.classifier.fusiontables;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import android.util.Log;

public class RowIdCache {
	String tableId;
	Map<Long,String> startToRowIdMap;
	Long minCachedStartTime;
	Long maxStartTime;
	long lastTrim;
//		long lastCheckServerUpdate;
	FusionTableActivitySync activitySync;
	
	public RowIdCache(String tableId, FusionTableActivitySync activitySync) {
		this.tableId = tableId;
		this.minCachedStartTime = System.currentTimeMillis()-FusionTableActivitySync.ROWID_CACHE_DURATION;
		this.startToRowIdMap = new HashMap<Long, String>(2048);
//			lastCheckServerUpdate = System.currentTimeMillis();
		lastTrim = System.currentTimeMillis();
		
//			Map<Long,String> map = fetchAllRowIds(tableId, minCachedStartTime);
//			for (Map.Entry<Long, String> item:map.entrySet()) {
//				put(item.getKey(), item.getValue());
//			}
	}
	
	private void fetchAndPut(Long start) {
		if (lastTrim<System.currentTimeMillis()-FusionTableActivitySync.ROWID_CACHE_TRIM_PERIOD) {
			trim();
		}
		
		Log.d(FusionTableActivitySync.TAG, "RowId cache is fetching the RowId of the row with start="+start);
		String rowId = activitySync.getRowId(tableId, start);
		if (rowId!=null && rowId.length()>0) {
			put(start, rowId);
		}
	}
	
	public boolean hasRow(Long start) {
		Log.d(FusionTableActivitySync.TAG, "RowId Cache check if row start="+start+" is in cache");
		if (maxStartTime==null || start>maxStartTime) {
			Log.d(FusionTableActivitySync.TAG, "RowId Cache: Row start="+start+" too new (latest is "+maxStartTime+")");
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
		Log.d(FusionTableActivitySync.TAG, "Trimming Row ID Cache");
		lastTrim = System.currentTimeMillis();
		minCachedStartTime = maxStartTime - FusionTableActivitySync.ROWID_CACHE_DURATION;
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