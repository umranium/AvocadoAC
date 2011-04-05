/*
 * Copyright (c) 2009-2010 Chris Smith
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package activity.classifier.accel.async;

import java.util.Set;

import activity.classifier.accel.SampleBatch;
import activity.classifier.common.Constants;
import activity.classifier.db.OptionUpdateHandler;
import activity.classifier.db.OptionsTable;
import activity.classifier.db.SqlLiteAdapter;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

/**
 * An accelerometer reader which reads real data from the device's
 * accelerometer.
 *
 * @author chris
 */
public class AsyncRealAccelReader implements AsyncAccelReader, OptionUpdateHandler {
	
	private static final int BUFFER_SIZE = 10;

    private final SensorEventListener accelListener = new SensorEventListener() {
    	
    	float diffFromLast;

        /** {@inheritDoc} */
//        @Override
        public void onSensorChanged(final SensorEvent event) {
        	int nextValues = (currentValues + 1) % BUFFER_SIZE;
        	synchronized (values[nextValues]) {
            	values[nextValues][Constants.ACCEL_X_AXIS] = event.values[SensorManager.DATA_X]; 
            	values[nextValues][Constants.ACCEL_Y_AXIS] = event.values[SensorManager.DATA_Y]; 
            	values[nextValues][Constants.ACCEL_Z_AXIS] = event.values[SensorManager.DATA_Z];
            	
            	/*
            	 * Some phone's accelerometers aren't that good, sometimes,
            	 * all of a sudden the accelerometer would return zeros in one
            	 * or more accelerometer axis for a one or more samples.
            	 * To detect and remove these anomalies (at the cost of
            	 * sample timing), we check that the value is not zero,
            	 * or if it is, then there were values close to zero before.
            	 */
            	for (int i=0; i<Constants.ACCEL_DIM; ++i) {
	            	if (values[nextValues][i]==0.0f) {
	            		diffFromLast = values[nextValues][i] - values[currentValues][i];
	            		if (diffFromLast<0.0f)
	            			diffFromLast = -diffFromLast;
	            		if (diffFromLast>0.1f) {
	            			return;
	            		}
	            	}
            	}
        	}
        	currentValues = nextValues;
        	valuesAssigned = true;
        }

        /** {@inheritDoc} */
//        @Override
        public void onAccuracyChanged(final Sensor sensor, final int accuracy) {
            // Don't really care
        }

    };

    float[][] values = new float[BUFFER_SIZE][3];
    boolean valuesAssigned = false;
    int currentValues = 0;
    private SensorManager manager;
    
    private SqlLiteAdapter sqlLiteAdapter;
    private OptionsTable optionsTable;
    
    private boolean keepAccelOn;
    private boolean accelListenerRegistered;

    public AsyncRealAccelReader(final Context context) {
        manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        
        sqlLiteAdapter = SqlLiteAdapter.getInstance(context);
        optionsTable = sqlLiteAdapter.getOptionsTable();
        
        accelListenerRegistered = false;
    }
    
    public void startSampling() {
    	if (!accelListenerRegistered) {
    		Log.i(Constants.DEBUG_TAG, "Turning accelerometer on");
            manager.registerListener(accelListener,
                    manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                    SensorManager.SENSOR_DELAY_FASTEST);
            accelListenerRegistered = true;
    	}
    }

    public void stopSampling() {
    	if (!optionsTable.getFullTimeAccel()) {
    		manager.unregisterListener(accelListener);
    		accelListenerRegistered = false;
    		Log.i(Constants.DEBUG_TAG, "Turning accelerometer off");
    	}
    }

	@Override
	public void onFieldChange(Set<String> updatedKeys) {
		if (updatedKeys.contains(OptionsTable.KEY_FULLTIME_ACCEL)) {
			this.keepAccelOn = optionsTable.getFullTimeAccel();
		}
	}
	
	public void assignSample(SampleBatch batch) {
    	//	sometimes values are requested before the first
    	//		accelerometer sensor change event has occurred,
    	//		so wait for the sensor to change.
    	if (!valuesAssigned) {
    		Log.v(Constants.DEBUG_TAG, "No values assigned yet from accelerometer, going to wait for values.");
    		long startWait = System.currentTimeMillis();
    		long current = startWait;
	    	while (!valuesAssigned) {
	    		Thread.yield();
	    		current = System.currentTimeMillis();
	    		if (current-startWait>Constants.DELAY_SAMPLE_BATCH) {
	    			throw new RuntimeException("Unable to start accelerometer. Faulty accelerometer.");
	    		}
	    	}
	    	Log.v(Constants.DEBUG_TAG, "Done, waited for "+((current-startWait)/1000)+"s");
    	}
    	int j = this.currentValues;
    	synchronized (this.values[j]) {
    		batch.assignSample(this.values[j]);
		}
    }

    @Override
    protected void finalize() throws Throwable {
    }


}
