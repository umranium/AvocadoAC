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
import activity.classifier.exception.HardwareFaultException;
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
public class AsyncRealAccelReader implements AsyncAccelReader {
	
	private static final int BUFFER_SIZE = 10;

    private final SensorEventListener accelListener = new SensorEventListener() {
    	
    	float diffFromLast;

        /** {@inheritDoc} */
//        @Override
        public void onSensorChanged(final SensorEvent event) {
        	int nextValues = (currentValueIndex + 1) % BUFFER_SIZE;
        	synchronized (valueBuff[nextValues]) {
        		valueBuff[nextValues].timeStamp = System.currentTimeMillis(); 
            	valueBuff[nextValues].values[Constants.ACCEL_X_AXIS] = event.values[SensorManager.DATA_X]; 
            	valueBuff[nextValues].values[Constants.ACCEL_Y_AXIS] = event.values[SensorManager.DATA_Y]; 
            	valueBuff[nextValues].values[Constants.ACCEL_Z_AXIS] = event.values[SensorManager.DATA_Z];
            	
            	/*
            	 * Some phone's accelerometers aren't that good, sometimes,
            	 * all of a sudden the accelerometer would return zeros in one
            	 * or more accelerometer axis for a one or more samples.
            	 * To detect and remove these anomalies (at the cost of
            	 * sample timing), we check that the value is not zero,
            	 * or if it is, then there were values close to zero before.
            	 */
            	for (int i=0; i<Constants.ACCEL_DIM; ++i) {
	            	if (valueBuff[nextValues].values[i]==0.0f) {
	            		diffFromLast = valueBuff[nextValues].values[i] - valueBuff[currentValueIndex].values[i];
	            		if (diffFromLast<0.0f)
	            			diffFromLast = -diffFromLast;
	            		if (diffFromLast>0.1f) {
	            			return;
	            		}
	            	}
            	}

            	previousValueIndex = currentValueIndex;
            	currentValueIndex = nextValues;
            	++valuesAssigned;
        	}
        }

        /** {@inheritDoc} */
//        @Override
        public void onAccuracyChanged(final Sensor sensor, final int accuracy) {
            // Don't really care
        }

    };
    
    class Values {
    	long timeStamp;
    	float[] values = new float[3];
    }

    private Values[] valueBuff;
    
    private int valuesAssigned;
    private int previousValueIndex = -1;
    private int currentValueIndex = 0;
    private SensorManager manager;
    
    private SqlLiteAdapter sqlLiteAdapter;
    private OptionsTable optionsTable;
    
    private boolean accelListenerRegistered;
    
    //	used to compute sampling quality
    private double samplingQualitySum;
    private double samplingQualitySumSqr;
    private double samplingQualityCount;
    
    private double samplingQualityMean;
    private double samplingQualityStdDev;

    public AsyncRealAccelReader(final Context context) {
        manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        
        sqlLiteAdapter = SqlLiteAdapter.getInstance(context);
        optionsTable = sqlLiteAdapter.getOptionsTable();
        
        this.valueBuff = new Values[BUFFER_SIZE];
        for (int i=0; i<BUFFER_SIZE; ++i)
        	this.valueBuff[i] = new Values();
        
        accelListenerRegistered = false;
    }
    
    public void startSampling() {
    	if (!accelListenerRegistered) {
    		Log.i(Constants.DEBUG_TAG, "Turning accelerometer on");
            manager.registerListener(accelListener,
                    manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                    SensorManager.SENSOR_DELAY_FASTEST);
            accelListenerRegistered = true;
            valuesAssigned = 0;
            samplingQualitySum = 0.0;
            samplingQualitySumSqr = 0.0;
            samplingQualityCount = 0.0;
    	}
    }

    public void stopSampling() {
    	if (!optionsTable.getFullTimeAccel()) {
    		manager.unregisterListener(accelListener);
    		accelListenerRegistered = false;
    		Log.i(Constants.DEBUG_TAG, "Turning accelerometer off");
    		
    		samplingQualityMean = samplingQualitySum / samplingQualityCount;
    		samplingQualityStdDev = Math.sqrt(
    				samplingQualitySumSqr / samplingQualityCount - 
    				samplingQualityMean * samplingQualityMean
				);
    		
    		Log.d(Constants.DEBUG_TAG, String.format("Sampling Quality: Delay: Mean=%.2f ms, S.D=%.2f ms", samplingQualityMean, samplingQualityStdDev));
    	}
    }
	
	public void assignSample(SampleBatch batch) throws HardwareFaultException {
    	//	sometimes values are requested before the first
    	//		accelerometer sensor change event has occurred,
    	//		so wait for the sensor to change.
    	if (valuesAssigned<2) {
    		Log.v(Constants.DEBUG_TAG, "No values assigned yet from accelerometer, going to wait for values.");
    		long startWait = System.currentTimeMillis();
    		long current = startWait;
	    	while (valuesAssigned<2) {
	    		Thread.yield();
	    		current = System.currentTimeMillis();
	    		if (current-startWait>Constants.DELAY_SAMPLE_BATCH)
	    		{
	    			throw new HardwareFaultException("Unable to start accelerometer");
	    		}
	    	}
	    	Log.v(Constants.DEBUG_TAG, "Done, waited for "+((current-startWait)/1000)+"s");
    	}
    	
    	//	lock the previous sample, hence locking the current one too
    	int prevValueIndex = this.previousValueIndex;
    	synchronized (this.valueBuff[previousValueIndex]) {
    		//	get the next one after the previous.. i.e. the current
        	int currentValueIndex = (prevValueIndex+1) % BUFFER_SIZE;
        	
        	//	we sample 1 interval before...
        	long now = System.currentTimeMillis() - Constants.DELAY_BETWEEN_SAMPLES;
        	
        	int useIndex = currentValueIndex; // default to the latest sample        	
        	if (Math.abs(now - this.valueBuff[prevValueIndex].timeStamp) < 
        			Math.abs(now - this.valueBuff[currentValueIndex].timeStamp)) {
        		//	unless the closer one is actually the one before that
        		useIndex = prevValueIndex;
        	}
        	
    		double delay = Math.abs(now - this.valueBuff[useIndex].timeStamp);
    		
    		samplingQualitySum += delay;
    		samplingQualitySumSqr += delay*delay;
    		samplingQualityCount += 1.0;
    		
    		//Log.i("ACCEL SAMPLE DELAY", Double.toString(delay));
    		batch.assignSample(this.valueBuff[useIndex].values);
		}
    }
	
    /**
	 * @return the mean delay between the arrival of a sample, and the time it is 
	 */
	public double getSamplingQualityMean() {
		return samplingQualityMean;
	}

	/**
	 * @return the samplingQualityStdDev
	 */
	public double getSamplingQualityStdDev() {
		return samplingQualityStdDev;
	}

	@Override
    protected void finalize() throws Throwable {
    }


}
