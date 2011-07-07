/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package aus.csiro.justin.sensorlogger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import aus.csiro.justin.sensorlogger.R;
import aus.csiro.justin.sensorlogger.activities.RecordingActivity;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

/**
 *
 * @author chris
 * modified by Justin
 */
public class RecorderService extends BoundService {

    private static final String TAG = "SensorLoggerService";

	private static final int ONGOING_NOTIFICATION_ID = 1;

    public static boolean STARTED = false;
	public static boolean bStopRecording = false;

    public boolean hasGyro = false;

    private float[] mags = new float[3];
    private float[] accels = new float[3];
    private float[] gyro = new float[3];
    private float[] mGData = new float[3];
    private float[] mR = new float[16];
    private float[] mI = new float[16];

    private SensorManager manager;
    private FileOutputStream stream;
	private OutputStreamWriter writer;
    

    private Timer timerSensorSampler;
    private Handler handlerSnsrSampler;
    

    private volatile int i = 0;
    private float[] accelValues = new float[3],gyroValues = new float[3],
            magValues = new float[3], orientationValues = new float[3];

    private float[] data = new float[256];
    private volatile int nextSample = 0;

    public static Map<Float[], String> model;
    
    private final SensorEventListener gyroListener = new SensorEventListener() {
		
		@Override
		public void onSensorChanged(SensorEvent event) {
			setGyrolValues(event.values);
		}

		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
			//Do nothing!
		}
		
	};

    private final SensorEventListener accelListener = new SensorEventListener() {

        /** {@inheritDoc} */
        @Override
        public void onSensorChanged(final SensorEvent event) {
            setAccelValues(event.values);
        }

        /** {@inheritDoc} */
        @Override
        public void onAccuracyChanged(final Sensor sensor, final int accuracy) {
            // Don't really care
        }

    };

    private final SensorEventListener magneticListener = new SensorEventListener() {

        /** {@inheritDoc} */
        @Override
        public void onSensorChanged(final SensorEvent event) {
            setMagValues(event.values);
        }

        /** {@inheritDoc} */
        @Override
        public void onAccuracyChanged(final Sensor sensor, final int accuracy) {
            // Don't really care
        }

    };

    private final SensorEventListener orientationListener = new SensorEventListener() {

        /** {@inheritDoc} */
        @Override
        public void onSensorChanged(final SensorEvent event) {
            setOrientationValues(event.values);
        }

        /** {@inheritDoc} */
        @Override
        public void onAccuracyChanged(final Sensor sensor, final int accuracy) {
            // Don't really care
        }

    };

    public void setGyrolValues(float[] gyroValues) {
        this.gyroValues = gyroValues;
    }
    public void setAccelValues(float[] accelValues) {
        this.accelValues = accelValues;
    }

    public void setMagValues(float[] magValues) {
        this.magValues = magValues;
    }

    public void setOrientationValues(float[] orientationValues) {
        this.orientationValues = orientationValues;
    }

    public void sample() throws RemoteException {
        data[(nextSample * 2) % 256] = accelValues[SensorManager.DATA_Y];
        data[(nextSample * 2 + 1) % 256] = accelValues[SensorManager.DATA_Z];
        
        if (++nextSample % 64 == 0 && nextSample >= 128) {
            float[] cache = new float[256];
            System.arraycopy(data, 0, cache, 0, 256);
            //analyse(cache);
        }

        write();
    }

    public void analyse(float[] data) {
        final Intent intent = new Intent(this, ClassifierService.class);
        intent.putExtra("data", data);
        startService(intent);
    }

    public void write() throws RemoteException {
    	String recordedData;
        try {

        	accels=accelValues;
        	mags=magValues;
        	if(mags != null && accels != null){
                SensorManager.getRotationMatrix(mR, mI, accels, mags);

                mGData[0]=mR[0]*accels[0]+mR[1]*accels[1]+mR[2]*accels[2];
                if(mGData[0]<0.00001 && mGData[0]>0 || mGData[0]>-0.00001 && mGData[0]<0) mGData[0]=0.0f;
                mGData[1]=mR[4]*accels[0]+mR[5]*accels[1]+mR[6]*accels[2];
                if(mGData[1]<0.00001 && mGData[1]>0 || mGData[0]>-0.00001 && mGData[0]<0) mGData[1]=0.0f;
                mGData[2]=mR[8]*accels[0]+mR[9]*accels[1]+mR[10]*accels[2];
                if(mGData[2]<0.00001 && mGData[2]>0 || mGData[0]>-0.00001 && mGData[0]<0) mGData[2]=0.0f;
        	}
        	
        	//TODO: Change when justin updates his web app.
        	
        	recordedData = accelValues[SensorManager.DATA_X] + "," +
            accelValues[SensorManager.DATA_Y] + "," +
            accelValues[SensorManager.DATA_Z] + "," +
            magValues[SensorManager.DATA_X] + "," +
            magValues[SensorManager.DATA_Y] + "," +
            magValues[SensorManager.DATA_Z] + "," +
            orientationValues[SensorManager.DATA_X] + "," +
            orientationValues[SensorManager.DATA_Y] + "," +
            orientationValues[SensorManager.DATA_Z];
        	
        	if (hasGyro)
        		recordedData += "," + gyroValues[SensorManager.DATA_X] + "," +
        		gyroValues[SensorManager.DATA_Y] + "," +
        		gyroValues[SensorManager.DATA_Z] + "& ";
        	else
        		recordedData += ",0,0,0& ";

        	writer.write(System.currentTimeMillis() + ":" + recordedData);
        	
            
        	if (++i % 50 == 0) {
            	writer.flush();
        	}
        	
           
        } catch (IOException ex) {
            Log.e(TAG, "Unable to write", ex);
        }
    }

    public void finished() {
        stopSelf();
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return new Binder();
    }

    @Override
    public void onStart(final Intent intent, final int startId) {
    	
        super.onStart(intent, startId);

        STARTED = true;

        init();
    }
    private int index =0;

	
    public void init() {
        try {
            stream = openFileOutput("tmpsensors"+index+".log", MODE_WORLD_READABLE);
			writer = new OutputStreamWriter(stream);
        } catch (FileNotFoundException ex) {
            return;
        }

        InputStream is = null;
        try {
            is = getResources().openRawResource(R.raw.basic_model);
            
            //A super slow process.
            //model = (Map<Float[], String>) new ObjectInputStream(is).readObject();
        } catch (Exception ex) {
            Log.e(TAG, "Unable to load model", ex);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ex) {
                    
                }
            }
        }

        RegisterSensors();
        
        timerSensorSampler = new Timer("Data logger");

        timerSensorSampler.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {

                try {
                	//if(!bStopRecording)
                		sample();
                	//else
                    //    stopSelf();
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
            }
        }, 500, 20);
        
		//	put on-going notification to show that service is running in the background
		NotificationManager notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

		int icon = R.drawable.icon_logger;
		CharSequence tickerText = "Avocado Logger is recording";
		long when = System.currentTimeMillis();
		Notification notification = new Notification(icon, tickerText, when);
		
		notification.defaults = 0;
		notification.flags = Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
		
		Context context = getApplicationContext();
		CharSequence contentTitle = "Avocado Logger";
		CharSequence contentText = "Avocado Logger service recording";
		Intent notificationIntent = new Intent(this, RecordingActivity.class);
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
		notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
		
		//notificationManager.notify(ONGOING_NOTIFICATION_ID, notification);
		this.startForeground(ONGOING_NOTIFICATION_ID, notification);
        
    }
    
    public void RegisterSensors()
    {
        manager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        
        List<Sensor> typedSensors = manager.getSensorList(Sensor.TYPE_GYROSCOPE);
        if(typedSensors.size() != 0)
        {
        	manager.registerListener(gyroListener, typedSensors.get(0),
        			SensorManager.SENSOR_DELAY_FASTEST);
        	hasGyro = true;
        	Log.d("Sensor Logger", "Gyroscope available");
        } else {
        	Log.d("Sensor Logger", "Gyroscope not available");
        }
        
        manager.registerListener(accelListener,
                manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_FASTEST);
        manager.registerListener(magneticListener,
                manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                SensorManager.SENSOR_DELAY_FASTEST);
        manager.registerListener(orientationListener,
                manager.getDefaultSensor(Sensor.TYPE_ORIENTATION),
                SensorManager.SENSOR_DELAY_FASTEST);

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        
        manager.unregisterListener(accelListener);
        manager.unregisterListener(magneticListener);
        manager.unregisterListener(orientationListener);
		this.stopForeground(true);
        bStopRecording  = true;
        timerSensorSampler.cancel();
        timerSensorSampler.purge();
        STARTED = false;
    }

}
