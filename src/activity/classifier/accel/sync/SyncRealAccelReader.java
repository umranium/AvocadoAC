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

package activity.classifier.accel.sync;

import java.util.Arrays;

import activity.classifier.accel.SampleBatch;
import activity.classifier.common.Constants;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.util.Log;

/**
 * An accelerometer reader which reads real data from the device's
 * accelerometer.
 *
 * @author chris
 */
public class SyncRealAccelReader implements SyncAccelReader {
	
	private float temp[] = new float[Constants.ACCEL_DIM];
	
    private final SensorEventListener accelListener = new SensorEventListener() {

        /** {@inheritDoc} */
//        @Override
        public void onSensorChanged(final SensorEvent event) {
        	Log.v(Constants.DEBUG_TAG, "Accel Reader Event");
        	synchronized (temp) {
        		temp[Constants.ACCEL_X_AXIS] = event.values[SensorManager.DATA_X]; 
        		temp[Constants.ACCEL_Y_AXIS] = event.values[SensorManager.DATA_Y]; 
        		temp[Constants.ACCEL_Z_AXIS] = event.values[SensorManager.DATA_Z];
            	currentBatch.assignSample(temp);
            	//	finished sampling
            	if (!currentBatch.nextSample()) {
            		stopSampling();
            		if (finishedRunnable!=null) finishedRunnable.run();
            	}
			}
        }

        /** {@inheritDoc} */
//        @Override
        public void onAccuracyChanged(final Sensor sensor, final int accuracy) {
            // Don't really care
        }

    };
    
    private SensorManager manager;
    private SampleBatch currentBatch;
    private Runnable finishedRunnable;

    public SyncRealAccelReader(final Context context) {
        manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
    }

    public void startSampling(int interSampleDelay, SampleBatch currentBatch, Runnable finishedRunnable) {
    	this.currentBatch = currentBatch;
    	this.finishedRunnable = finishedRunnable;
    	
        manager.registerListener(accelListener,
                manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                interSampleDelay*1000);
                //20000);
    }

    public void stopSampling() {
        manager.unregisterListener(accelListener);
    }

    @Override
    protected void finalize() throws Throwable {
    }


}
