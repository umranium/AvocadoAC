
package activity.classifier.activity;



import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Formatter;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import activity.classifier.R;
import activity.classifier.activity.ChartHelper.ChartData;
import activity.classifier.common.ActivityNames;
import activity.classifier.common.Constants;
import activity.classifier.common.ExceptionHandler;
import activity.classifier.db.ActivitiesTable;
import activity.classifier.db.SqlLiteAdapter;
import activity.classifier.repository.ActivityQueries;
import activity.classifier.rpc.ActivityRecorderBinder;
import activity.classifier.rpc.Classification;
import activity.classifier.service.RecorderService;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ViewFlipper;

import com.flurry.android.FlurryAgent;

public class ActivityChartActivity extends Activity {


	/** Called when the activity is first created. */
	private ViewFlipper flipper;
	private ChartView chartview;
	private LinearLayout[] linearLayout = new LinearLayout[4];
	private TextView[] textView = new TextView[4];
	private int height,width;

	private SqlLiteAdapter sqlLiteAdapter;
	private ActivitiesTable activitiesTable;
	private final Handler handler = new Handler();
	private UpdateInterfaceRunnable updateInterfaceRunnable = new UpdateInterfaceRunnable();

	private ChartHelper chartHelper;
	private Map<String,Integer> activityIndexes;
	private Map<String,String> activityNiceNames;
	private volatile ChartHelper.ChartData chartData;
	

	public IBinder onBind(Intent intent) {
		return null;
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
		FlurryAgent.onEndSession(this);
	}
	
	private class UpdateInterfaceRunnable implements Runnable {

		//	avoids conflicts between scheduled updates,
		//		and once-off updates 
		private ReentrantLock reentrantLock = new ReentrantLock();
		
		//	last update time
		private long lastUpdateTime = 0;
		
		// the text being displayed on the screen for the current event
		//		and the event before that
		private String currentEventNiceText = null;
		private String currentPrevEventNiceText = null;

		//	starts scheduled interface updates
		public void start() {
			Log.v(Constants.DEBUG_TAG, "UpdateInterfaceRunnable started");
			lastUpdateTime = 0;
			handler.postDelayed(updateInterfaceRunnable, 1);
		}

		//	stops scheduled interface updates
		public void stop() {
			Log.v(Constants.DEBUG_TAG, "UpdateInterfaceRunnable stopped");
			handler.removeCallbacks(updateInterfaceRunnable);
		}

		//	performs a once-off unsynchronised (unscheduled) interface update
		//		please note that this can be called from another thread
		//		without interfering with the normal scheduled updates.
		public void updateNow() {
			if (reentrantLock.tryLock()) {

				try {
					updateUI();
				} catch (ParseException ex) {
					Log.e(Constants.DEBUG_TAG, "Error while performing scheduled UI update.", ex);
				}

				reentrantLock.unlock();
			}

		}

		public void run() {
			if (reentrantLock.tryLock()) {
				
				try {
					updateUI();
				} catch (ParseException e) {
					e.printStackTrace();
				}

				reentrantLock.unlock();
			}
			handler.postDelayed(updateInterfaceRunnable, Constants.DELAY_UI_GRAPHIC_UPDATE);
		}

		/**
		 * 
		 * changed from updateButton to updateUI
		 * 
		 * updates the user interface:
		 * 	the toggle button's text is changed.
		 * 	the classification list's entries are updated.
		 * 
		 * @throws ParseException
		 */
		@SuppressWarnings("unchecked")
		private void updateUI() throws ParseException {
			
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
			if (currentTime-lastUpdateTime<Constants.DELAY_UI_GRAPHIC_UPDATE) {
				//	avoid refreshing before the required time
				return;
			} else {
				lastUpdateTime = currentTime;
			}
			
			Log.v(Constants.DEBUG_TAG, "Update Chart UI");
			
			Classification latest = new Classification();
			Classification beforeLatest = new Classification();
			
			if (!activitiesTable.loadLatest(latest)) {
				latest = null;
			}
			if (latest==null || !activitiesTable.loadLatestBefore(latest.getStart(), beforeLatest)) {
				beforeLatest = null;
			}
			
			String newText = formatActivityText(latest);
			String beforeText = formatActivityText(beforeLatest);
			
			if (	currentEventNiceText==null ||
					!currentEventNiceText.equals(newText) ||
					currentPrevEventNiceText==null ||
					!currentPrevEventNiceText.equals(beforeText)) {
				for(int i=0;i<textView.length;i++){
					if(i==0 || i==2){
						textView[i].setText(" Now    : " + newText );
					}else if(i==1 || i==3){
						textView[i].setText(" Before : " + beforeText );
					}
				}

				flipper.startFlipping();
				flipper.stopFlipping();
			}

			//	load and recompute chart data, refresh if necessary
			if (chartHelper.computeData()) {
				ActivityChartActivity.this.chartData = chartHelper.getData();
				chartview.postInvalidate();
			}
		}

	}
	
	private String getTimeText(long duration){
		String strDurationNew="";
		if(duration>=60){
			long sec = 0;
			sec = duration%60;
			duration = duration/60;
			if(duration>=60){
				long min = 0;
				min = duration%60;
				duration = duration/60;
				strDurationNew = String.format("%1$3d %2$-4s %3$3d %4$-4s %5$3d %6$-4s",duration,"hr",min,"min",sec,"s");
			}else{
				strDurationNew = String.format("%1$3d %2$-4s %3$3d %4$-4s",duration,"min",sec,"s");
			}

		}else{
			strDurationNew = " < 1 min";
		}
		return strDurationNew;
	}

	private String formatActivityText(Classification activity) {
		if (activity==null)
			return "";
		
		String text = "";
		String durationText = "";
	
		if (activity!=null && !ActivityQueries.isSystemActivity(activity.getClassification())) {
			text = activity.getNiceClassification();
			long period = activity.getEnd()-activity.getStart();
			period /= 1000;
			durationText = getTimeText(period);
		}
		
		return String.format("%1$-10s", text) +" "+durationText;
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler(this));
		chartHelper = new ChartHelper(this);
		activityIndexes = chartHelper.getActivityIndexes();
		activityNiceNames = chartHelper.getActivityNiceNames();
		
		flipper = new ViewFlipper(this);
		sqlLiteAdapter = SqlLiteAdapter.getInstance(this);
		activitiesTable = sqlLiteAdapter.getActivitiesTable();
		chartview = new ChartView(this);
		//		update = new updateTimeThread();
		//		update.start();
		LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT,
				LayoutParams.WRAP_CONTENT);

		for(int i=0;i<linearLayout.length;i++){
			linearLayout[i] = new LinearLayout(this);
			linearLayout[i].setOrientation(LinearLayout.VERTICAL);
			if(i==0 || i==1){
				linearLayout[i].setMinimumHeight(height/7);
			}else if(i==2){
				linearLayout[i].setMinimumHeight(height-height/7);
			}
		}

		for(int i=0; i<textView.length;i++){
			textView[i] = new TextView(this);
			// +2: Needs to look bigger than the footer.
			textView[i].setTextSize(TEXT_SIZE_HEADER);
			if(i==0 || i==2){
				textView[i].setText(" Now    : ");
			}else if(i==1 || i==3){
				textView[i].setText(" Before : ");
			}
		}

		for(int i=0;i<2;i++){
			for(int j=0;j<2;j++){
				linearLayout[i].addView(textView[(2*i)+j], params);
			}
		}
		linearLayout[2].addView(chartview);

		flipper.addView(linearLayout[0]);
		flipper.addView(linearLayout[1]);
		flipper.setInAnimation(AnimationUtils.loadAnimation(this,
				R.anim.push_up_out));
		flipper.setOutAnimation(AnimationUtils.loadAnimation(this,
				R.anim.push_up_in));

		linearLayout[3].addView(flipper);
		linearLayout[3].addView(linearLayout[2]);

		setContentView(linearLayout[3]);
	}
	static int gScreenDensity = 0; 
	int TEXT_SIZE_FOOTER;
	int TEXT_SIZE_HEADER;
	int TEXT_SIZE_ACTIVITYNAME;


	private class ChartView extends View{
		boolean densityMeasured = false;
		boolean fontSizesSet = false;
		
		//TODO: There must be multiple paint objects for different parts of the canvas
		// to save on painting task in the Avocado.
		Paint paint;
		
		public ChartView(Context context){
			super(context);
			paint = new Paint();


		}
		@Override protected void onDraw(Canvas canvas) {
			super.onDraw(canvas);
			
			if (chartData==null)
				return;
			// Measures the density of the screen to adjust the fonts.
			// Density value is 240 for nexus s and 120 for wildfire and Xpreia.
			// if is here is for performance reasons.
			if (1 == 1)
			{	
				gScreenDensity = canvas.getDensity();
				Log.d("saltfactory", ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>gScreenDensity" + gScreenDensity);

				densityMeasured = true;
			}
			
			if(!fontSizesSet && gScreenDensity>150)
			{
				TEXT_SIZE_HEADER = ChartHelper.TEXT_SIZE_IN_HIGHRES_PHONES;
				TEXT_SIZE_FOOTER = ChartHelper.TEXT_SIZE_IN_HIGHRES_PHONES+4;
				TEXT_SIZE_ACTIVITYNAME = ChartHelper.TEXT_SIZE_IN_HIGHRES_PHONES+2;
				fontSizesSet = true;
				
			}	
			else if (!fontSizesSet && gScreenDensity<150)
			{
				TEXT_SIZE_HEADER  = ChartHelper.TEXT_SIZE_IN_LOWRES_PHONES + 4;
				TEXT_SIZE_FOOTER = ChartHelper.TEXT_SIZE_IN_LOWRES_PHONES;
				TEXT_SIZE_ACTIVITYNAME = ChartHelper.TEXT_SIZE_IN_LOWRES_PHONES - 2;
				fontSizesSet = true;
				
			}
			

			
			height = getHeight();
			width = getWidth();
			// custom drawing code here
			// remember: y increases from top to bottom
			// x increases from left to right
			int x = 0;
			int y = 0;
			//			DisplayMetrics displayMatrics = new DisplayMetrics();


			//			Log.i("saltfactory", "width : " + width +", height : " + height);

			paint.setStyle(Paint.Style.FILL);

			//			Log.i("DB","onDraw");


			// make the entire canvas white
			paint.setColor(Color.WHITE);
			canvas.drawPaint(paint);
			// another way to do this is to use:
			// canvas.drawColor(Color.WHITE);


			paint.setARGB(255, 53, 57, 64);
			canvas.drawRect(new RectF(0,height-height/6,width,height), paint);

			paint.setColor(Color.WHITE);
			paint.setAntiAlias(true);
			paint.setTextSize(TEXT_SIZE_FOOTER);
			float[] sizeOfFooters = new float[ChartHelper.NUMBER_OF_FOOTERS];
			for(int i=0;i<ChartHelper.NUMBER_OF_FOOTERS;i++){
				sizeOfFooters[i] = paint.measureText(ChartHelper.FOOTER_NAMES[i]);
				canvas.drawText(ChartHelper.FOOTER_NAMES[i], i*width/3+((width/3)-sizeOfFooters[i])/2, height-(((height/6)-17)/2), paint);
			}
			
			String[] tempActivityNames = new String[activityIndexes.keySet().toArray().length];
			for(int i=0;i<activityIndexes.keySet().toArray().length;i++){
				tempActivityNames[i] = ""+activityIndexes.keySet().toArray()[i];
			}
			String[] activityNames = new String[tempActivityNames.length];
			float[][] sizeOfActivityNames = new float[chartData.numOfDurations][chartData.numOfActivities];
			
			
			for(int i=0;i<tempActivityNames.length;i++){
				activityNames[i] = activityNiceNames.get(tempActivityNames[i]);
			}

			
			paint.setTextSize(TEXT_SIZE_ACTIVITYNAME);
			
			for(int i=0;i<chartData.numOfDurations;i++){
				float percentageStack = 0;
				for(int j=0;j<chartData.numOfActivities;j++){
					paint.setStyle(Paint.Style.FILL);
					paint.setColor(ChartHelper.COLOR_ACTIVITIES[j]);
					canvas.drawRect((new RectF(i*width/3,percentageStack,(i+1)*width/3,(percentageStack+(chartData.percentageMatrix[i][j]*(height-height/6)/100)))),paint);
					
					paint.setColor(ChartHelper.COLOR_LINE);
					paint.setStrokeWidth((float) 1.5);
					paint.setStyle(Paint.Style.STROKE);
					canvas.drawRect((new RectF(i*width/3,percentageStack,(i+1)*width/3,(percentageStack+(chartData.percentageMatrix[i][j]*(height-height/6)/100)))),paint);
					
					
					String niceDisplayName = activityNames[j]+"("+(int)chartData.percentageMatrix[i][j]+"%)";
					sizeOfActivityNames[i][j] = paint.measureText(niceDisplayName);
					
					paint.setColor(Color.WHITE);
					paint.setStyle(Paint.Style.FILL);
					canvas.drawText(niceDisplayName, i*(width/3)+((width/3)-sizeOfActivityNames[i][j])/2, (((chartData.percentageMatrix[i][j]*(height-height/6)/100))<=27)?5000:percentageStack+27, paint);
					
					percentageStack += chartData.percentageMatrix[i][j]*(height-height/6)/100;
				}
			}
			
			paint.setColor(ChartHelper.COLOR_LINE);
			paint.setStrokeWidth((float) 1.5);
			canvas.drawLine(0, 0, width, 0, paint);
			canvas.drawLine(width, 0, width, height, paint);
			canvas.drawLine(0, height-height/6, width, height-height/6, paint);
			paint.setStrokeWidth((float) 7.0);
			canvas.drawLine(0, 0, 0, height, paint);
			canvas.drawLine(0, height, width, height, paint);
			canvas.drawLine(width/3, 0, width/3, height, paint);
			canvas.drawLine(width-width/3, 0, width-width/3, height, paint);
			paint.setStrokeWidth((float) 1.5);
			
			

		}
	}

}
