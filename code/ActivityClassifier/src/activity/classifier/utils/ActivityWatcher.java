/**
 * 
 */
package activity.classifier.utils;

import activity.classifier.common.ActivityNames;
import activity.classifier.common.Constants;
import activity.classifier.db.OptionsTable;
import activity.classifier.db.SqlLiteAdapter;
import activity.classifier.rpc.Classification;
import android.content.Context;

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
	private long previousActivityDuration;
	
	public ActivityWatcher(Context context) {
		this.context = context;
		this.myTracks = new MyTracksIntergration(context);
		this.sqlLiteAdapter = SqlLiteAdapter.getInstance(context);
		this.optionsTable = this.sqlLiteAdapter.getOptionsTable();
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
		long duration = classification.getEnd() - classification.getStart();
		
		if (isWalking) {
			if (!wasWalking && duration>=Constants.DURATION_BEFORE_START_MYTRACKS) {
				if (this.optionsTable.getInvokeMyTracks())
					this.myTracks.startRecording();
				wasWalking = isWalking;
			}
		} else {
			if (wasWalking) {
				if (duration>=Constants.DURATION_BEFORE_STOP_MYTRACKS) {
					this.myTracks.stopRecording();
					wasWalking = isWalking;
				}
			}
		}
		
		if (this.myTracks.isRecording() && previousActivity!=null && !previousActivity.equals(currentActivity)) {
			this.myTracks.insertMarker(classification.getNiceClassification(), classification.toString());
			this.myTracks.insertStatistics(classification.getNiceClassification(), classification.toString());
		}
		
		if (this.myTracks.isRecording())
			classification.setMyTracksId(this.myTracks.getCurrentTrackId());
		else
			classification.setMyTracksId(null);
		
		this.previousActivity = currentActivity;
		this.previousActivityDuration = duration;
	}
	
	
}
