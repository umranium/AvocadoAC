package activity.classifier.activity;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import activity.classifier.common.ActivityNames;
import activity.classifier.common.Constants;
import activity.classifier.common.StringComparator;
import activity.classifier.db.ActivitiesTable;
import activity.classifier.db.SqlLiteAdapter;
import activity.classifier.rpc.Classification;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.util.Log;

public class ChartHelper {
	
	/**
	 * The durations of the columns displayed
	 * 
	 * PLEASE NOTE: THE DURATIONS SHOULD AT ALL TIMES BE SORTED
	 * IN AN INCREASING ORDER. I.E. THE SMALLEST ITEM FIRST,
	 * AND LARGEST LAST.
	 */
	private final static long COL_DURATIONS[] = new long[] {
		1*60*60*1000L,
		4*60*60*1000L,
		24*60*60*1000L,
	};
	
	public static class ChartData {
		
		public final int numOfActivities;
		public final int numOfDurations;
		public final float[][] percentageMatrix;
		
		public ChartData(int numOfActivities) {
			this.numOfDurations = COL_DURATIONS.length;
			this.numOfActivities = numOfActivities;
			this.percentageMatrix = new float[COL_DURATIONS.length][numOfActivities];
		}
		
	}
	
	private final int NUM_OF_DATA_SETS = 1;
	
	private ChartData[] dataSets = new ChartData[NUM_OF_DATA_SETS];
	
	// the index of the ChartData in the dataSets array, where the chart should load from
	private int currentLoadData = -1;
	
	//	an index of the activities available to the system
	private Map<String,Integer> activityIndexes;
	
	//	map of activity names to nice names
	private Map<String,String> activityNiceNames;
	
	//	the colors of different activities by their indexes
	private int[] activityColors;
	
	private Context context;
	private SqlLiteAdapter sqlLiteAdapter;
	private ActivitiesTable activitiesTable;
	private Set<String> allActivities;
	
	//	reusable instances
	private Classification classification = new Classification();
	public long[][] sumMatrix;
	
	private int col_size;
	private int row_size;
	
	public int getColumnSize(){
		return col_size;
	}
	public int getRowSize(){
		return row_size;
	}
	public ChartHelper(Context context) {
		this.context = context;
		this.sqlLiteAdapter = SqlLiteAdapter.getInstance(context);
		this.activitiesTable = this.sqlLiteAdapter.getActivitiesTable();
		this.allActivities = ActivityNames.getAllActivities(context);
		
		this.sumMatrix = new long[COL_DURATIONS.length][allActivities.size()];
		this.col_size = COL_DURATIONS.length;
		this.row_size = allActivities.size();
		
		this.activityIndexes = new TreeMap<String,Integer>(new StringComparator(false));
		{
			int index = 0;
			for (String activity:allActivities) {
				this.activityIndexes.put(activity, index);
				++index;
			}
		}
		
		this.activityNiceNames = new TreeMap<String,String>(new StringComparator(false));
		for (String activity:allActivities) {
			String niceName = Classification.getNiceName(context, activity);
			this.activityNiceNames.put(activity, niceName);
		}
		
		for (int i=0; i<NUM_OF_DATA_SETS; ++i)
			dataSets[i] = new ChartData(this.allActivities.size());
	}
	
	public Map<String, Integer> getActivityIndexes(){
		return activityIndexes;
	}
	
	public Map<String, String> getActivityNiceNames(){
		return activityNiceNames;
	}
	
	public ChartData getData() {
		return dataSets[currentLoadData];
	}
	
	public boolean computeData()
	{
		final long periodStart = System.currentTimeMillis();
		
		long maxDuration = COL_DURATIONS[COL_DURATIONS.length-1];
		
		final long periodEnd = periodStart - maxDuration;
		
		for (int i=0; i<COL_DURATIONS.length; ++i) {
			for (int j=0; j<allActivities.size(); ++j) {
				sumMatrix[i][j] = 0;
			}
		}
		activitiesTable.loadAllBetween(periodStart, periodEnd, classification,
				new ActivitiesTable.ClassificationDataCallback() {
					@Override
					public void onRetrieve(Classification classification) {
						if (!activityIndexes.containsKey(classification.getClassification())) {
							Log.e(Constants.DEBUG_TAG, "ERROR: UNKNOWN ACTIVITY '"+classification.getClassification()+"' FOUND");
							return;
						}
						int index = activityIndexes.get(classification.getClassification());
						long howLongAgoStarted = periodStart - classification.getStart();
						long howLongAgoEnded = periodStart - classification.getEnd();
						for (int i=0; i<COL_DURATIONS.length; ++i) {
							long colDuration = COL_DURATIONS[i];
							long activityDuration=0;
							if (colDuration>=howLongAgoStarted) {
								activityDuration = classification.getEnd() - classification.getStart();
							}else{
								if(colDuration>=howLongAgoEnded){
									activityDuration = colDuration - howLongAgoEnded;
								}
							}
							sumMatrix[i][index] +=  activityDuration;
						}
					}
				}
			);
		
		
		for (int i=0; i<COL_DURATIONS.length; ++i) {
			long totalNonSystem = 0;
			for (String activity:allActivities) {
				if (!ActivityNames.OFF.equals(activity)) {
					int index = activityIndexes.get(activity);
					totalNonSystem += sumMatrix[i][index];
				}
			}
			
			int indexOff = activityIndexes.get(ActivityNames.OFF);
			sumMatrix[i][indexOff] = COL_DURATIONS[i] - totalNonSystem;
		}
		
		int currentComputeData = (currentLoadData+1)%NUM_OF_DATA_SETS;
		
		ChartData data = dataSets[currentComputeData];
		
		for (int i=0; i<COL_DURATIONS.length; ++i) {
			for (int j=0; j<allActivities.size(); ++j) {
				data.percentageMatrix[i][j] = 100.0f * (float)sumMatrix[i][j] / (float)COL_DURATIONS[i];
			}
		}
		
		currentLoadData = currentComputeData;
		return true;
	}
	
	
	/**
	 * Size of footer texts
	 */
	//TODO: Need to CHANGE BASED ON PHONE! Should be fixed later.
	//Different footer text sizes works for different phones.
	//Wildfire and Xperia 
	public final static int TEXT_SIZE_IN_LOWRES_PHONES = 10;
	//Such as NEXUS S
	public final static int TEXT_SIZE_IN_HIGHRES_PHONES = 17;


	
	/**
	 * Number of footer texts
	 */
	public final static int NUMBER_OF_FOOTERS = 3;
	
	/**
	 * The names of footers
	 */
	public final static String[] FOOTER_NAMES = { "Last Hour", "Last 4Hours", "Today",}; 
	
	/**
	 * Color of line
	 */
	public final static int COLOR_LINE = Color.argb(255, 32, 33, 38);
	
	/**
	 * Colors of activities
	 */
	@SuppressWarnings("serial")
	public final static Map<String,Integer> COLOR_ACTIVITIES = new TreeMap<String,Integer>(new StringComparator(true)) {
		{
			this.put(ActivityNames.OFF,					Color.argb(255, 128, 128, 128));
			this.put(ActivityNames.END,					Color.argb(255, 255, 255, 255));
			this.put(ActivityNames.UNKNOWN,				Color.argb(255, 255, 255, 255));
			this.put(ActivityNames.UNCARRIED,			Color.argb(255, 109, 206, 250));
			this.put(ActivityNames.CHARGING,			Color.argb(255, 122, 181, 204));
			this.put(ActivityNames.CHARGING_TRAVELLING, Color.argb(255, 153, 102, 255));
			this.put(ActivityNames.STATIONARY,			Color.argb(255,   1, 190, 171));
			this.put(ActivityNames.TRAVELING,			Color.argb(255,   0, 102, 255));
			this.put(ActivityNames.WALKING,				Color.argb(255,   0, 191,  48));
			this.put(ActivityNames.PADDLING,			Color.argb(255, 181,  48,   0));
			this.put(ActivityNames.ROWING,				Color.argb(255, 181,  48,   0));
			this.put(ActivityNames.CYCLING,				Color.argb(255, 191, 134,   0));
			this.put(ActivityNames.RUNNING,				Color.argb(255, 187, 191,   0));
			
//		//Charging Colour
//		Color.argb(255, 153, 153, 153),
//		//Orange
//		Color.argb(255, 255, 97, 0),
//		//Not carried
//		Color.argb(255, 109, 206, 250),
//		//Stationary
//		Color.argb(255, 0, 102, 0),
//		//Stationary
//		Color.argb(255, 0, 102, 255),
//		//Walking
//		Color.argb(255, 244, 141, 62),
//		//Running
//		Color.argb(255, 181, 40, 65),
//		//Off, light green
//		Color.argb(255, 181, 204, 122),
//		Color.argb(255, 181, 204, 0),
		}
	};
	
}
