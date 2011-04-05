/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package aus.csiro.justin.sensorlogger.activities;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.TimerTask;

import aus.csiro.justin.sensorlogger.R;
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

    private SensorManager sensors = null;

    private SensorManager mSensorManager;
    private float[] mags = new float[3];
    private float[] accels = new float[3];
    private float[] mGData = new float[3];
    private float[] mMData = new float[3];
    private float[] mOData = new float[3];
    private float[] mR = new float[16];
    private float[] mI = new float[16];
    private FloatBuffer mVertexBuffer;
    private FloatBuffer mColorBuffer;
    private ByteBuffer mIndexBuffer;
    private float[] mOrientation = new float[3];
    private int mCount;
	private static final int DELAY = 30000;
	int defTimeOut =0;
    private final Handler handler = new Handler();
    int state = 3;
    int phase = 3;
    static int sec = 0;
    static int min = 0;
    static int hour = 0;
    static int check=1;
    private final TimerTask task = new TimerTask() {
            @Override
            public void run() {
                checkSamples();
            }
    };

    	//When "Stop" button is clicked, 
        public void onClick(View arg0) {
            FlurryAgent.onEvent("process_stop_click");
            try {
                service.setState(20);
                //Start ResultsActicity
                startActivity(new Intent(this, ResultsActivity.class));
                finish(); //function to finish the application
            } catch (RemoteException ex) {
                Log.e(getClass().getName(), "Unable to submit correction", ex);
            }
        }
        protected void onResume() {
            // Ideally a game should implement onResume() and onPause()
            // to take appropriate action when the activity looses focus
            super.onResume();
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            Sensor gsensor = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            Sensor msensor = sensors.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            sensors.registerListener(this, gsensor, SensorManager.SENSOR_DELAY_GAME);
            sensors.registerListener(this, msensor, SensorManager.SENSOR_DELAY_GAME);
        }
    /** {@inheritDoc} */
    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        //Keep screen on during collecting data
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.recording);
        //add stop button on the screen
        ((Button) findViewById(R.id.stop)).setOnClickListener(this);;

        sensors = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        
        handler.removeCallbacks(task);
        handler.postDelayed(task, 500);
  
    }
//--------------------------testing for how the data is collecting well---------------------
//-----------------------------mainly just display data on the screen-----------------------
//    public void onSensorChanged(SensorEvent event) {
//    	TextView status = (TextView) findViewById(R.id.status);
//    	TextView status1 = (TextView) findViewById(R.id.status_1);
//        float[] data;
//
//        String allSensor = "Sensor = \n";
//        String allSensor1 = "\nSensor = \n";
//        
//        switch (event.sensor.getType()) {
//        case Sensor.TYPE_ACCELEROMETER:
//        	data = mGData;
//        	accels = event.values.clone();
//        	float value;
//        	
//            for (int i=0;i<3;i++) {
//            	value=event.values[i];
//            	
//                allSensor += "accel val = " + value + "\n";
//            }
//
//            break;
// 
//        case Sensor.TYPE_MAGNETIC_FIELD:
//        	data = mMData;
//        	mags = event.values.clone();
//            for (int i=0;i<3;i++) {
//            	value=event.values[i];
//            	
//                allSensor += "magnetic val = " + value + "\n";
//            }
//
//            break;
//
//
//        }
//
//        status.setText(allSensor);
//        if(mags != null && accels != null){
//        SensorManager.getRotationMatrix(mR, mI, accels, mags);
//        allSensor1=(float)(mR[0]) +" " + (float)(mR[1]) +" " + (float)(mR[2]) +" " + (float)(mR[3]) +"\n"+
//		(float)(mR[4]) +" " + (float)(mR[5]) +" " + (float)(mR[6]) +" " + (float)(mR[7]) +"\n"+
//		(float)(mR[8]) +" " + (float)(mR[9]) +" " + (float)(mR[10]) +" " + (float)(mR[11]) +"\n"+
//		(float)(mR[12]) +" " + (float)(mR[13]) +" " + (float)(mR[14]) +" " + (float)(mR[15]) +"\n";
////        allSensor1+=(int)(mI[0]) +" " + (int)(mI[1]) +" " + (int)(mI[2]) +" " + (int)(mI[3]) +"\n"+
////		(int)(mI[4]) +" " + (int)(mI[5]) +" " + (int)(mI[6]) +" " + (int)(mI[7]) +"\n"+
////		(int)(mI[8]) +" " + (int)(mI[9]) +" " + (int)(mI[10]) +" " + (int)(mI[11]) +"\n"+
////		(int)(mI[12]) +" " + (int)(mI[13]) +" " + (int)(mI[14]) +" " + (int)(mI[15]) +"\n";
//
//        SensorManager.getOrientation(mR, mOrientation);
//        float incl = SensorManager.getInclination(mI);
////        mGData[0]=mR[0]*accels[0]+mR[1]*accels[1]+mR[2]*accels[2];
////        if(mGData[0]<0.00001 && mGData[0]>0 || mGData[0]>-0.00001 && mGData[0]<0) mGData[0]=0.0f;
////        mGData[1]=mR[4]*accels[0]+mR[5]*accels[1]+mR[6]*accels[2];
////        if(mGData[1]<0.00001 && mGData[1]>0 || mGData[0]>-0.00001 && mGData[0]<0) mGData[1]=0.0f;
////        mGData[2]=mR[8]*accels[0]+mR[9]*accels[1]+mR[10]*accels[2];
////        if(mGData[2]<0.00001 && mGData[2]>0 || mGData[0]>-0.00001 && mGData[0]<0) mGData[2]=0.0f;
////        allSensor1 += "accel val = " + mGData[0] + "\n";
////        allSensor1 += "accel val = " + mGData[1] + "\n";
////        allSensor1 += "accel val = " + mGData[2] + "\n";
//        
//            final float rad2deg = (float)(180.0f/Math.PI);
//            mCount = 0;
//            allSensor1="After rotating: =\n"+"yaw: " + (int)(mOrientation[0]*rad2deg) +"\n"+
//                    "  pitch: " + (int)(mOrientation[1]*rad2deg) +"\n"+
//                    "  roll: " + (int)(mOrientation[2]*rad2deg) +"\n"+
//                    "  incl: " + (int)(incl*rad2deg)+"\n";
//////                    
////            Log.d("Compass", "yaw: " + (int)(mOrientation[0]*rad2deg) +
////                    "  pitch: " + (int)(mOrientation[1]*rad2deg) +
////                    "  roll: " + (int)(mOrientation[2]*rad2deg) +
////                    "  incl: " + (int)(incl*rad2deg)
////                    );
//            status1.setText(allSensor1);
//        }else{
//        	status1.setText("nothing");
//        }
//        
//    }

    public void onPause() {
        if (sensors != null) {
            sensors.unregisterListener(this);
        }
        super.onPause();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        sec = 0;
        min = 0;
        hour = 0;
        check=1;
        handler.removeCallbacks(task);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
//        Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, defTimeOut);
    }

    public void checkSamples() {
        try {
            phase = (phase + 1) % 4;
            check = (check+1) % 2;
            String time = "";
            String text = "?";
            switch (phase) {
                case 0: text = ".  "; break;
                case 1: text = " . "; break;
                case 2: text = "  ."; break;
                case 3: text = " . "; break;
            }
            switch(check){
            	case 0: break;
            	case 1: sec++;
            			if(sec==60){
            				sec=0;
            				min++;
            			}
            			if(min==60){
            				min=0;
            				hour++;
            			}
            			break;
            			
            }
            time = "0"+hour+" : "+min+" : "+sec;

            ((TextView) findViewById(R.id.recordingcount)).setText(time);

            final int serviceState = service.getState();

            if (serviceState > state) {
                state = serviceState;

                if (state == 4) {
                    setTitle("Sensor Logger > Analysing");
                    sec = 0;
                    min = 0;
                    hour = 0;
                    check=1;
                    ((TextView) findViewById(R.id.recordingheader)).setTag(R.string.analysingheader);
                }
            }

            if (serviceState > 4) {
            	sec = 0;
                min = 0;
                hour = 0;
                check=1;
                FlurryAgent.onEvent("countdown_to_results");
                startActivity(new Intent(this, ResultsActivity.class));
                finish();
            } else {
                handler.postDelayed(task, 500);
            }
        } catch (RemoteException ex) {
            Log.e(getClass().getName(), "Error getting countdown", ex);
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


}
