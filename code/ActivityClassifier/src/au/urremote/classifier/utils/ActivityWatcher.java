/**
 * 
 */
package au.urremote.classifier.utils;

import android.content.Context;
import android.util.Log;
import au.urremote.classifier.common.ActivityNames;
import au.urremote.classifier.common.Constants;
import au.urremote.classifier.db.OptionsTable;
import au.urremote.classifier.db.SqlLiteAdapter;
import au.urremote.classifier.rpc.Classification;

/**
 * Watches the latest recognised activity and it's duration
 * in order to invoke appropriate responses to the system, e.g.
 * starting the MyTracks application, or sending out broadcast
 * events. 
 * 
 * @author Umran
 *
 */
public class ActivityWatcher {
	
	private SqlLiteAdapter sqlLiteAdapter;
	private OptionsTable optionsTable;
	private Context context;
	private MyTracksIntergration myTracks;
	private boolean wasWalking;
	private String previousActivity;
	private WalkingActivity previousWalkingActivitiy;
	
	private TwoWayBlockingQueue<WalkingActivity> walkingActivityQueue;
	
	public ActivityWatcher(Context context) {
		this.context = context;
		this.myTracks = new MyTracksIntergration(context);
		this.sqlLiteAdapter = SqlLiteAdapter.getInstance(context);
		this.optionsTable = this.sqlLiteAdapter.getOptionsTable();
		
		//	initiate with twice the required number
		this.walkingActivityQueue = new TwoWayBlockingQueue<ActivityWatcher.WalkingActivity>(
				(int)(2*Constants.DURATION_MONITOR_MYTRACKS / Constants.DELAY_SAMPLE_BATCH)
				)
		{
			@Override
			protected WalkingActivity getNewInstance() {
				return new WalkingActivity();
			}
		};
	}
	
	public void init() {
		this.wasWalking = false;
	}
	
	public void done() {
		if (this.myTracks.isRecording())
			this.myTracks.stopRecording();
	}
	
	public void processLatest(Classification classification) {
		String currentActivity = classification.getClassification(); 
		boolean isWalking = currentActivity.equals(ActivityNames.WALKING);
		
		//	we might change the value of wasWalking,
		//		but that shouldn't effect our current processing
		boolean prevWasWalking = wasWalking;
		
		//	the start of our monitoring period
		//		the monitoring period moves forward in time with the classifications
		//		and extends a given duration back in time.
		long monitoringStart = classification.getEnd()-Constants.DURATION_MONITOR_MYTRACKS; 
		
		//	empty all activities that occurred more than
		//		[the monitoring period] ago
		emptyAllBefore(monitoringStart);
		
		if (isWalking) {
			//	check if this is the same as the last activity inserted
			if (previousWalkingActivitiy!=null && previousWalkingActivitiy.start==classification.getStart()) {
				//	if it is... change the end time
				previousWalkingActivitiy.end = classification.getEnd();
			} else {
				//	else, add new walking activity
				try {
					WalkingActivity activity = this.walkingActivityQueue.takeEmptyInstance();
					activity.start = classification.getStart();
					activity.end = classification.getEnd();
					this.walkingActivityQueue.returnFilledInstance(activity);
					previousWalkingActivitiy = activity;
				} catch (InterruptedException e) {
					Log.e(Constants.DEBUG_TAG, "Interrupted", e);
				}
			}
			
			//	we only turn on MyTracks, if there's consistent walking for a given duration 
			if (!wasWalking) {
				long walkDuration = classification.getEnd() - classification.getStart();
				if (walkDuration>=Constants.DURATION_BEFORE_START_MYTRACKS) {
					if (this.optionsTable.getInvokeMyTracks())
						this.myTracks.startRecording();
					wasWalking = true;
				}
			}
		}
		
		if (prevWasWalking) {
			long durationOfWalking = 0;
			try {
				int numberOfFilled = walkingActivityQueue.getFilledSize();
				for (int i=0; i<numberOfFilled; ++i) {
					WalkingActivity activity = this.walkingActivityQueue.takeFilledInstance();
					// if the activity started before the monitoring period...
					if (activity.start<monitoringStart) {
						//	only use the period after the monitoring started
						durationOfWalking += activity.end - monitoringStart;
					}
					else {
						durationOfWalking += activity.end - activity.start;
					}
					this.walkingActivityQueue.returnFilledInstance(activity);
				}
			} catch (InterruptedException e) {
				Log.e(Constants.DEBUG_TAG, "Interrupted", e);
			}
			
			if (durationOfWalking<Constants.DURATION_MIN_KEEP_MYTRACKS) {
				Log.i(Constants.DEBUG_TAG, "Turning off MyTracks");
				if (this.myTracks.isRecording())
					this.myTracks.stopRecording();
				wasWalking = false;
			}
		}
		
		this.myTracks.isConnectedToSensor();
		
		if (this.myTracks.isRecording() && previousActivity!=null && !previousActivity.equals(currentActivity)) {
			this.myTracks.insertMarker(classification.getNiceClassification(), classification.toString());
			this.myTracks.insertStatistics(classification.getNiceClassification(), classification.toString());
		}
		
		if (this.myTracks.isRecording())
			classification.setMyTracksId(this.myTracks.getCurrentTrackId());
		else
			classification.setMyTracksId(null);
		
		this.previousActivity = currentActivity;
	}
	
	private void emptyAllBefore(long time)
	{
		try {
			//	peek at the last item in the queue
			WalkingActivity last = walkingActivityQueue.peekFilledInstance();
			while (last!=null) {	//	if an item was found
				//	check if the item is was taken more than [the calibration waiting period] ago 
				if (last.end<time) {
					//	remove item
					WalkingActivity instance = walkingActivityQueue.takeFilledInstance();
					walkingActivityQueue.returnEmptyInstance(instance);
				} else {
					break;
				}
				//	check the next item
				last = walkingActivityQueue.peekFilledInstance();
			}
		} catch (InterruptedException e) {
			Log.e(Constants.DEBUG_TAG, "Interrupted", e);
		}
	}
	
	class WalkingActivity {
		long start;
		long end;
	}
	
}
