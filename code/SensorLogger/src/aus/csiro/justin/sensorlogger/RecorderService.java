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
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import aus.csiro.justin.sensorlogger.R;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
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

    public static boolean STARTED = false;

    private float[] mags = new float[3];
    private float[] accels = new float[3];
    private float[] mGData = new float[3];
    private float[] mMData = new float[3];
    private float[] mOData = new float[3];
    private float[] mR = new float[16];
    private float[] mI = new float[16];
    private float[] mOrientation = new float[3];
    private SensorManager manager;
    private FileOutputStream stream;
    private OutputStreamWriter writer;
    private FileOutputStream tmpstream;
	private OutputStreamWriter tmpwriter;
    

    private Timer timer;

    private volatile int i = 0;
    private float[] accelValues = new float[3],
            magValues = new float[3], orientationValues = new float[3];

    private float[] data = new float[256];
    private volatile int nextSample = 0;

    public static Map<Float[], String> model;

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
            analyse(cache);
        }

        write();
    }

    public void analyse(float[] data) {
        final Intent intent = new Intent(this, ClassifierService.class);
        intent.putExtra("data", data);
        startService(intent);
    }

    public void write() throws RemoteException {
        try {

        	accels=accelValues;
        	mags=magValues;
        	if(mags != null && accels != null){
                SensorManager.getRotationMatrix(mR, mI, accels, mags);
//               
//                SensorManager.getOrientation(mR, mOrientation);
//                float incl = SensorManager.getInclination(mI);
                mGData[0]=mR[0]*accels[0]+mR[1]*accels[1]+mR[2]*accels[2];
                if(mGData[0]<0.00001 && mGData[0]>0 || mGData[0]>-0.00001 && mGData[0]<0) mGData[0]=0.0f;
                mGData[1]=mR[4]*accels[0]+mR[5]*accels[1]+mR[6]*accels[2];
                if(mGData[1]<0.00001 && mGData[1]>0 || mGData[0]>-0.00001 && mGData[0]<0) mGData[1]=0.0f;
                mGData[2]=mR[8]*accels[0]+mR[9]*accels[1]+mR[10]*accels[2];
                if(mGData[2]<0.00001 && mGData[2]>0 || mGData[0]>-0.00001 && mGData[0]<0) mGData[2]=0.0f;
        	}
        	writer.write(System.currentTimeMillis() + ":" +
                    accelValues[SensorManager.DATA_X] + "," +
                    accelValues[SensorManager.DATA_Y] + "," +
                    accelValues[SensorManager.DATA_Z] + "," +
                    magValues[SensorManager.DATA_X] + "," +
                    magValues[SensorManager.DATA_Y] + "," +
                    magValues[SensorManager.DATA_Z] + "," +
                    orientationValues[SensorManager.DATA_X] + "," +
                    orientationValues[SensorManager.DATA_Y] + "," +
                    orientationValues[SensorManager.DATA_Z] + "&" + 
//                    mGData[0]+","+mGData[1]+","+mGData[2]+","+
                    " ");
        	tmpwriter.write(System.currentTimeMillis() + ":" +
                    accelValues[SensorManager.DATA_X] + "," +
                    accelValues[SensorManager.DATA_Y] + "," +
                    accelValues[SensorManager.DATA_Z] + "," +
                    magValues[SensorManager.DATA_X] + "," +
                    magValues[SensorManager.DATA_Y] + "," +
                    magValues[SensorManager.DATA_Z] + "," +
                    orientationValues[SensorManager.DATA_X] + "," +
                    orientationValues[SensorManager.DATA_Y] + "," +
                    orientationValues[SensorManager.DATA_Z] + "&" + 
//                    mGData[0]+","+mGData[1]+","+mGData[2]+","+
                    " ");
            
        	if (++i % 50 == 0) {
            	writer.flush();
        	}
        	if (i % 50 == 0) {
            	
            	tmpwriter.flush();

            }
        	//if data file size is around 1Mb when assumed 800byte for each row(800*10000=800Kb)
        	//make another file and write on in.
        	if(i%10000==0){
        		File file = getFileStreamPath("tmpsensors"+index+".log");
        		index++;
                tmpstream = openFileOutput("tmpsensors"+index+".log", MODE_WORLD_READABLE);
    			tmpwriter = new OutputStreamWriter(tmpstream);
        	}

            if (service.getState()==20) {
            	
                finished();
                
            }
            
        } catch (IOException ex) {
            Log.e(TAG, "Unable to write", ex);
        }
    }

    public void finished() {
        stopSelf();

        try {
        	service.setIndex(index);
            service.setState(4);
        } catch (RemoteException ex) {
            Log.e(getClass().getName(), "Error changing state", ex);
        }
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
        	stream = openFileOutput("sensors.log", MODE_WORLD_READABLE);
            writer = new OutputStreamWriter(stream);
            tmpstream = openFileOutput("tmpsensors"+index+".log", MODE_WORLD_READABLE);
			tmpwriter = new OutputStreamWriter(tmpstream);
        } catch (FileNotFoundException ex) {
            return;
        }

        InputStream is = null;
        try {
            is = getResources().openRawResource(R.raw.basic_model);
            model = (Map<Float[], String>) new ObjectInputStream(is).readObject();
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

        manager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        manager.registerListener(accelListener,
                manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_FASTEST);
        manager.registerListener(magneticListener,
                manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                SensorManager.SENSOR_DELAY_FASTEST);
        manager.registerListener(orientationListener,
                manager.getDefaultSensor(Sensor.TYPE_ORIENTATION),
                SensorManager.SENSOR_DELAY_FASTEST);

        timer = new Timer("Data logger");

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {

                try {
					sample();
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
            }
        }, 500, 20);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        
        manager.unregisterListener(accelListener);
        manager.unregisterListener(magneticListener);
        manager.unregisterListener(orientationListener);

        timer.cancel();

        STARTED = false;
    }

}
