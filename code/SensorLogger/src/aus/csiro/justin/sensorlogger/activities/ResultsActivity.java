/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package aus.csiro.justin.sensorlogger.activities;

import android.accounts.Account;
import android.accounts.AccountManager;
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
enum stage {
	CANCELED, // The cancel option is selected from the list
	ENTERING_DATA_LABEL, // The list of activities is presented for user to choose
	DATA_LABELED, // The activity label is clicked by the user
	ENTERING_PHONE_LOCATION, // The list of locations is presented
	TASK_FINISHED, // The location of the phone is specified.
	ACTIVITY_STOPPED // finish method is called and the activity is destroyed.
	};
public class ResultsActivity extends ListActivity {

	private final Handler handler = new Handler();
	private AutoCompleteTextView input;
	private ProgressDialog dialog;

	private final TimerTask task = new TimerTask() {
		@Override
		public void run() {
			handler.postDelayed(task, 50);
			checkStage();
			
		}
	};

	private ServiceConnection connection = new ServiceConnection() {

		public void onServiceConnected(ComponentName arg0, IBinder arg1) {
			service = SensorLoggerBinder.Stub.asInterface(arg1);
			serviceBound();
		}

		public void onServiceDisconnected(ComponentName arg0) {
			//Toast.makeText(, R.string.error_disconnected, Toast.LENGTH_LONG);
			setResult(RESULT_CANCELED);
			finish();
		}
	};
	SensorLoggerBinder service = null;

	protected void serviceBound() {

//		try {
//			String name = "activity_" + service.getClassification().substring(11)
//			.replace("/", "_").toLowerCase();
//
//			//int res = getResources().getIdentifier(name, "string", "aus.csiro.justin.sensorlogger");
//			//((TextView) findViewById(R.id.resultsresultaaa)).setText(res);
//		} catch (RemoteException ex) {
//			Log.e(getClass().getName(), "Unable to get classification", ex);
//		}

		//handler.postDelayed(task, 500);
	}

	// The activity has three data entry stages:
	//	1. Activity Label
	//  2. Handset location
	//  3. Possible comment
	public static stage dataEntryStage = stage.ENTERING_DATA_LABEL;

	static String strCategory = "";
	static String strLocation = "";
	static String strComment = "";
	static ListView lv;
	String[] countries;
	String[] locations;
	AccountManager accountManager;
	Account[] acntaUserNames;
	String[] straUsrs;


	public void FillUserNames()
	{
		int i = 0;
		accountManager = (AccountManager)getSystemService(ACCOUNT_SERVICE);
		try {

			acntaUserNames = accountManager.get(this).getAccounts();
		} catch (Exception e) {
			int ii = 1;
			ii++;
			// TODO: handle exception
		}
		straUsrs = new String[acntaUserNames.length];

		for (Account acnt : acntaUserNames) {
			straUsrs[i++] = acnt.name;

		}
	}
	/** {@inheritDoc} */
	@Override
	public void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		// It is not ideal, but we have to set content first because the super.OnCreate
		// calls the service bind initially that then calls serviceBound here and expects
		// the layout.
		//setContentView(R.layout.list_item);
		countries = getResources().getStringArray(R.array.activity_list);
		locations = getResources().getStringArray(R.array.handset_location);
		//FillUserNames();

		startService(new Intent(this, SensorLoggerService.class));
		bindService(new Intent(this, SensorLoggerService.class), connection,
				BIND_AUTO_CREATE);

		lv = getListView();
		lv.setTextFilterEnabled(true);

		handler.removeCallbacks(task);
		handler.postDelayed(task, 50);

		switch (dataEntryStage) {
		case ENTERING_DATA_LABEL:
			
			setListAdapter(new ArrayAdapter<String>(this, R.layout.list_item, countries));
			break;
		case ENTERING_PHONE_LOCATION:
			setListAdapter(new ArrayAdapter<String>(this, R.layout.list_item, locations));
			
			break;

		// This is the case when the dataEntryStage is set to ACTIVITY_STOPPED 
		default:
			setListAdapter(new ArrayAdapter<String>(this, R.layout.list_item, countries));
			dataEntryStage = stage.ENTERING_DATA_LABEL;
			break;

		}

		





		lv.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				try {
					switch (dataEntryStage) {
					case ENTERING_DATA_LABEL:
						strCategory = ((TextView) view).getText().toString().toUpperCase();
						if(strCategory.compareTo("CANCEL") == 0)
						{
							Toast.makeText(getApplicationContext(), "Canceled recording, logger goes to intro",
									Toast.LENGTH_SHORT).show();
							setResult(RESULT_CANCELED);
							dataEntryStage = stage.CANCELED;

						}
						else 
						{
							String message = "Labaled as: " + ((TextView) view).getText().toString().toUpperCase();
							Toast.makeText(getApplicationContext(), message,
									Toast.LENGTH_SHORT).show();

							service.setClassfication("CLASSIFIED/" + strCategory);
							dataEntryStage = stage.DATA_LABELED;
						}

						break;
					case ENTERING_PHONE_LOCATION:
						strLocation = ((TextView) view).getText().toString().toUpperCase();
						service.setLocation(strLocation);
						dataEntryStage = stage.TASK_FINISHED;
						break;

					default:
						break;
					}


				} catch (RemoteException e) {
					Log.e(getClass().getName(), "Unable to set state", e);
				}
			}
		});

	}

	@Override
	protected void onDestroy() {

		handler.removeCallbacks(task);
		super.onDestroy();

		unbindService(connection);

	}

	void checkStage() {
			switch (dataEntryStage) {
			case DATA_LABELED:
				dataEntryStage = stage.ENTERING_PHONE_LOCATION;
				startActivity(new Intent(this,ResultsActivity.class));
				finish();
				break;

			case TASK_FINISHED:
				dataEntryStage = stage.ACTIVITY_STOPPED;
				startActivity(new Intent(this,SubmitRecordedData.class));
				finish();
				break;
				
			case CANCELED:
				dataEntryStage = stage.ENTERING_PHONE_LOCATION;
				startActivity(new Intent(this, IntroActivity.class));
				finish();
				break;
			default:
				break;
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
			switch (dataEntryStage) {
			case ENTERING_PHONE_LOCATION:
				dataEntryStage = stage.ENTERING_DATA_LABEL;
				startActivity(new Intent(this,ResultsActivity.class));
				finish();
				break;
			case ENTERING_DATA_LABEL:
				startActivity(new Intent(this, IntroActivity.class));
				finish();
				break;
				
			}
			return true;
		}
		return super.onKeyUp(keyCode, event);
	}


}
