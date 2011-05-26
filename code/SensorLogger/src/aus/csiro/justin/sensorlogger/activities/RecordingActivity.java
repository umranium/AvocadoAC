/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package aus.csiro.justin.sensorlogger.activities;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.TimerTask;

import aus.csiro.justin.sensorlogger.R;
import aus.csiro.justin.sensorlogger.RecorderService;
import aus.csiro.justin.sensorlogger.SensorLoggerService;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.flurry.android.FlurryAgent;

/**
 *
 * @author chris
 * modified by Justin
 */
public class RecordingActivity extends BoundActivity implements OnClickListener, SensorEventListener{

	Intent intntRecorderService;

	private long startTime;
	int defTimeOut = 0;
	private boolean bStopClicked =false;
	private final Handler handler = new Handler();
	private final TimerTask tskUITimer = new TimerTask() {
		@Override
		public void run() {
			checkSamples();
			handler.postDelayed(tskUITimer, 1000);
		}
	};

	public String ElapsedTime()
	{
		long elapsedTime = System.currentTimeMillis() - startTime;
		elapsedTime = elapsedTime / 1000;

		String seconds = Integer.toString((int) (elapsedTime % 60));
		String minutes = Integer.toString((int) ((elapsedTime % 3600) / 60));
		String hours = Integer.toString((int) (elapsedTime / 3600));

		if (seconds.length() < 2) {
			seconds = "0" + seconds;
		}

		if (minutes.length() < 2) {
			minutes = "0" + minutes;
		}

		if (hours.length() < 2) {
			hours = "0" + hours;
		}
		return hours + ":" + minutes + ":" + seconds;

	}
	//When "Stop" button is clicked, 
	public void onClick(View arg0) {
		FlurryAgent.onEvent("process_stop_click");
		FlurryAgent.onEvent("countdown_to_results");
		bStopClicked = true;
		startActivity(new Intent(this, ResultsActivity.class));
		finish(); //function to finish the application
	}
	protected void onResume() {
		// Ideally a game should implement onResume() and onPause()
		// to take appropriate action when the activity looses focus
		super.onResume();
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}
	/** {@inheritDoc} */
	@Override
	public void onCreate(final Bundle icicle) {
		super.onCreate(icicle);
		//Keep screen on during collecting data
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.recording);
		//add stop button on the screen
		((Button) findViewById(R.id.stop)).setOnClickListener(this);

		startTime = System.currentTimeMillis();

		handler.post(tskUITimer);
		
		intntRecorderService = new Intent(this, RecorderService.class);

		startService(intntRecorderService);
	}

	public void onPause() {
		super.onPause();
		getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}
	@Override
	protected void onDestroy() {
		super.onDestroy();
		handler.removeCallbacks(tskUITimer);
		stopService(intntRecorderService);

		getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		//        Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, defTimeOut);
	}

	public void checkSamples() {
		if(!bStopClicked)
		{
			String time = ElapsedTime();

			((TextView) findViewById(R.id.recordingcount)).setText(time);
		}
	}

	/** {@inheritDoc} */
	@Override
	protected void onStart() {
		super.onStart();

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		FlurryAgent.onStartSession(this, "TFBJJPQUQX3S1Q6IUHA6");
	}

	/** {@inheritDoc} */
	@Override
	protected void onStop() {
		super.onStop();

		getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		FlurryAgent.onEndSession(this);
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub

	}
	@Override
	public void onSensorChanged(SensorEvent event) {
		// TODO Auto-generated method stub

	}

	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK
				&& event.getRepeatCount() == 0) {
			event.startTracking();
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && event.isTracking()
				&& !event.isCanceled()) {
			bStopClicked = true;
			startActivity(new Intent(this,IntroActivity.class));
			finish();
			return true;
		}
		return super.onKeyUp(keyCode, event);
	}



}
