/*
 * To change this template, choose Tools | Templates and open the template in
 * the editor.
 */

package activity.classifier.activity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;

import activity.classifier.R;
import activity.classifier.common.Constants;
import activity.classifier.common.ExceptionHandler;
import activity.classifier.db.ActivitiesTable;
import activity.classifier.db.OptionsTable;
import activity.classifier.db.SqlLiteAdapter;
import activity.classifier.rpc.ActivityRecorderBinder;
import activity.classifier.rpc.Classification;
import activity.classifier.service.RecorderService;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.flurry.android.FlurryAgent;

/**
 * 
 * @author chris, modified Justin Lee
 * 
 */
public class ActivityListActivity extends Activity {
	
	private final static SimpleDateFormat DISPLAY_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	
	private static int SINGLE_DAY = (24*60*60*1000);

	public static boolean serviceIsRunning = false;

	private final Handler handler = new Handler();

	private SqlLiteAdapter sqlLiteAdapter;
	private OptionsTable optionsTable;
	private ActivitiesTable activitiesTable;
	
	/**
	 * Updates the user interface.
	 */
	private final UpdateInterfaceRunnable updateInterfaceRunnable = new UpdateInterfaceRunnable();


	/**
	 * 
	 * @param intent
	 * @return null
	 */
	public IBinder onBind(Intent intent) {
		return null;
	}



	/**
	 * 
	 */
	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		
		//set exception handler
		Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler(this));

		this.sqlLiteAdapter = SqlLiteAdapter.getInstance(this);
		this.optionsTable = this.sqlLiteAdapter.getOptionsTable();
		this.activitiesTable = this.sqlLiteAdapter.getActivitiesTable();
		
		setContentView(R.layout.main);

		ListView listView = (ListView) findViewById(R.id.list); 
		listView.setAdapter(new ArrayAdapter<Classification>(this,R.layout.item));
	}

	/**
	 * 
	 */
	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	/**
	 * 
	 */
	protected void onResume() {
		super.onResume();
		updateInterfaceRunnable.start();
	}

	/**
	 * 
	 */
	protected void onPause() {
		super.onPause();
		updateInterfaceRunnable.stop();
	}

	/**
	 * 
	 */
	@Override
	protected void onStart() {
		super.onStart();
		FlurryAgent.onStartSession(this, Constants.FLURRY_SESSION_ID);
	}

	/**
	 * 
	 */
	@Override
	protected void onStop() {
		super.onStop();
		// wl.release();
		FlurryAgent.onEndSession(this);
	}
	
	/**
	 * Performs scheduled user interface updates, also allows
	 * other components to request the user interface to be updated,
	 * without interfering with normal scheduled updates.
	 * @author Umran
	 *
	 */
	private class UpdateInterfaceRunnable implements Runnable {

		//	avoids conflicts between scheduled updates,
		//		and once-off updates 
		private ReentrantLock reentrantLock = new ReentrantLock();

		//	last time the ui was updated
		private long lastUiUpdateTime;
		
		//	last time an item in the database was updated
		private long lastDbUpdateTime = 0;

		//	reusable items
		private Classification reusableClassification = new Classification();
		private Map<Long,Integer> currentlyDisplayedItems = new TreeMap<Long,Integer>();
		private List<Classification> filteredOut = new ArrayList<Classification>(200);
		private int itemsInserted;
		private int itemsUpdated;
		private long maxDbUpdateTime;
		
		//	starts scheduled interface updates
		public void start() {
			lastUiUpdateTime = 0;
			handler.postDelayed(this, 1);
		}

		//	stops scheduled interface updates
		public void stop() {
			handler.removeCallbacks(this);
		}

		//	performs a once-off unsynchronised (unscheduled) interface update
		//		please note that this can be called from another thread
		//		without interfering with the normal scheduled updates.
		public void updateNow() {
			if (reentrantLock.tryLock()) {
				updateUI();
				reentrantLock.unlock();
			}
		}

		public void run() {
			if (reentrantLock.tryLock()) {
				updateUI();
				reentrantLock.unlock();
			}
			
			handler.postDelayed(this, Constants.DELAY_UI_GRAPHIC_UPDATE);
		}
		
		/**
		 * updates the list,
		 * 
		 * removing any items that shouldn't be displayed,
		 * adding any new items recently inserted
		 * and updating any items recently updated
		 */
		private void updateUI() {
			
			//	please note:
			//		In the nexus s (not sure about other phones), there seems to be
			//		two events that occur about 5 seconds apart, even though
			//		its the same handler, and the sequence is started only once.
			//		You can use this code to check the stack trace.
			//
			//				try {
			//					throw new RuntimeException();
			//				} catch (Exception e) {
			//					Log.v(Constants.DEBUG_TAG, "Update Chart UI Exception", e);
			//				}
			//
			//		To avoid this, we make sure that the last call was at least
			//		the required interval ago.
			long currentTime = System.currentTimeMillis();
			if (currentTime-lastUiUpdateTime<Constants.DELAY_UI_GRAPHIC_UPDATE) {
				//	avoid refreshing before the required time
				return;
			} else {
				lastUiUpdateTime = currentTime;
			}

			@SuppressWarnings("unchecked")
			final ArrayAdapter<Classification> adapter = (ArrayAdapter<Classification>) ((ListView) findViewById(R.id.list)).getAdapter();
			
			//	define the required period in which we want items to appear on the list
			long periodStart = currentTime;
			long periodEnd = currentTime - SINGLE_DAY;
			
			//	remove all items older than the required period
			{
				filteredOut.clear();
				// also, obtain a list of items currently on the list, and their locations
				currentlyDisplayedItems.clear();
				int index = 0;
				//	go through each item in the list
				for (int len=adapter.getCount(), i=0; i<len; ++i) {
					Classification cl = adapter.getItem(i);
					long end = cl.getEnd();
					if (end<periodEnd) {
						filteredOut.add(cl);
					} else {
						currentlyDisplayedItems.put(cl.getStart(), index);
						++index;
					}
				}
				//	remove the items
				for (Classification cl:filteredOut)
					adapter.remove(cl);
				filteredOut.clear();
			}
			
			this.itemsInserted = 0;
			this.itemsUpdated = 0;
			this.maxDbUpdateTime = 0;
			
			//	fetch newly updated items between the period
			activitiesTable.loadUpdated(
					periodStart, periodEnd,
					lastDbUpdateTime,
					reusableClassification,
					new ActivitiesTable.ClassificationDataCallback() {
						@Override
						public void onRetrieve(Classification cl) {
							//	check if item is on the list (updated) or is new (inserted)
							if (currentlyDisplayedItems.containsKey(cl.getStart())) {
								int index = currentlyDisplayedItems.get(cl.getStart());
								//shift the original index by the number of items inserted
								Classification dst = adapter.getItem(itemsInserted+index);
								//	update the one on the list
								dst.assignFrom(cl);
								++itemsUpdated;
							} else {
								//	insert at the top
								Classification classification = new Classification();
								classification.assignFrom(cl);
								classification.withContext(ActivityListActivity.this);
								adapter.insert(classification, 0);
								++itemsInserted;
							}
							
							//	find the time the latest item was updated
							long lastUpdate = cl.getLastUpdate(); 
							if (lastUpdate>maxDbUpdateTime) {
								maxDbUpdateTime = lastUpdate;
							}
						}
					}
				);
			
			//	if any item has been updated or inserted
			if (itemsUpdated>0 || itemsInserted>0) {
				//Log.v(Constants.DEBUG_TAG, "List UI: updates="+itemsUpdated+", inserts="+itemsInserted+". Notifying list adapter that items have changed.");
				//	notify that the list data set has changed
				adapter.notifyDataSetChanged();
				
				//	set the time the list was last updated
				//		to the time the latest item was updated
				lastDbUpdateTime = Math.max(lastDbUpdateTime, maxDbUpdateTime);
			}
		}
	}
	
}
