/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package aus.csiro.justin.sensorlogger.activities;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.RemoteException;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import aus.csiro.justin.common.ExceptionHandler;
import com.google.android.googlelogin.*;

import com.flurry.android.FlurryAgent;

import aus.csiro.justin.sensorlogger.R;

/**
 *
 * @author chris
 */
public class IntroActivity extends BoundActivity implements OnClickListener {

	Button btnBegine; 	
	Button btnPref;

	/** {@inheritDoc} */
	@Override
	public void onCreate(final Bundle icicle) {
		super.onCreate(icicle);

		Thread.setDefaultUncaughtExceptionHandler(
				new ExceptionHandler("SensorLogger",
						"http://chris.smith.name/android/upload", getVersionName(), getIMEI()));

		setContentView(R.layout.intro);


		btnBegine = ((Button) findViewById(R.id.introstart));
		btnBegine.setOnClickListener(this);

		//btnPref = ((Button) findViewById(R.id.Preferences));
		//btnPref.setOnClickListener(this);
	}

	public String getVersionName() {
		try {
			return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
		} catch (NameNotFoundException ex) {
			return "Unknown";
		}
	}

	public String getIMEI() {
		return ((TelephonyManager) getSystemService(TELEPHONY_SERVICE)).getDeviceId();
	}


	/** {@inheritDoc} */
	@Override
	public void onClick(final View v) {
		try {
			FlurryAgent.onEvent("intro_to_countdown");

			if(v.getId() == btnBegine.getId()){
				service.setState(2);
				startActivity(new Intent(this, CountdownActivity.class));
				finish();
			}
			else
			{
				startActivity(new Intent(this,PrefActivity.class));
				finish();
			}
		} catch (RemoteException ex) {
			Log.e(getClass().getName(), "Error setting state", ex);
		}
	}

	/** {@inheritDoc} */
	@Override
	protected void onStart() {
		super.onStart();

		FlurryAgent.onStartSession(this, "TFBJJPQUQX3S1Q6IUHA6");

	}

	/** {@inheritDoc} */
	@Override
	protected void onStop() {
		super.onStop();

		FlurryAgent.onEndSession(this);
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
			finish();
			return true;
		}
		return super.onKeyUp(keyCode, event);
	}



}
