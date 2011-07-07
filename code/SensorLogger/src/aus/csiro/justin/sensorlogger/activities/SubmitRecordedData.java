/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package aus.csiro.justin.sensorlogger.activities;

import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.flurry.android.FlurryAgent;
import java.util.TimerTask;

import aus.csiro.justin.sensorlogger.R;
import aus.csiro.justin.sensorlogger.SensorLoggerService;
import aus.csiro.justin.sensorlogger.rpc.SensorLoggerBinder;

/**
 *
 * @author chris
 */
public class SubmitRecordedData extends BoundActivity implements OnClickListener {

	private final Handler handler = new Handler();

	/** {@inheritDoc} */
	@Override
	public void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		setContentView(R.layout.submit);

		((Button) findViewById(R.id.submitBTN)).setOnClickListener(this);
		//((Button) findViewById(R.id.resultsno)).setOnClickListener(noListener);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

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

	@Override
	public void onClick(View v) {
		try {
			String comment = ((EditText)findViewById(R.id.editTextComment)).getText().toString();
			service.setComment(comment);
			service.submit();
			startActivity(new Intent(this,ThanksActivity.class));
			finish();
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}// TODO Auto-generated method stub
		
	}
	
	@Override
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
	    	ResultsActivity.dataEntryStage = stage.ENTERING_PHONE_LOCATION;
			startActivity(new Intent(this,ResultsActivity.class));
			
			finish();
	    	return true;
	    }
	    return super.onKeyUp(keyCode, event);
	}

}
