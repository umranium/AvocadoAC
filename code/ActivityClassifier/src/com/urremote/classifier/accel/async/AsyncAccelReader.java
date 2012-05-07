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

package com.urremote.classifier.accel.async;

import java.util.Set;

import com.urremote.classifier.accel.SampleBatch;
import com.urremote.classifier.common.Constants;
import com.urremote.classifier.db.OptionUpdateHandler;
import com.urremote.classifier.db.OptionsTable;
import com.urremote.classifier.db.SqlLiteAdapter;
import com.urremote.classifier.exception.HardwareFaultException;

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
public class AsyncAccelReader {
	
	public static final int SPECIFIC_SAMPLING_RATE = 1000000/Constants.RECOMMENDED_SAMPLING_FREQUENCY; // microseconds
	public static final int GENERIC_SAMPLING_RATE = SensorManager.SENSOR_DELAY_FASTEST; 
	
	/*
	 * Available sampling rates, from fastest to slowest
	 */
	public static final int[] AVAILABLE_SAMPLING_RATES = new int[] {
		SensorManager.SENSOR_DELAY_FASTEST,
		SensorManager.SENSOR_DELAY_GAME,
		SensorManager.SENSOR_DELAY_UI,
		SensorManager.SENSOR_DELAY_NORMAL,
	};

    private final SensorEventListener accelListener = new SensorEventListener() {
    	
        /** {@inheritDoc} */
//        @Override
        public void onSensorChanged(final SensorEvent event) {
        	//Log.d(Constants.TAG+"Sensor", Long.toString(event.timestamp));
        	
        	if (assigningValues && currentBatch!=null) {
        		if (currentBatch.hasSpace()) {
        			long currentSampleTime = event.timestamp/1000000;
        			float[] currentSample = event.values;
        			
        			if (currentBatch.hasLastSample()) {
        				long prevSampleTime = currentBatch.getLastSampleTime();
        				float[] prevSample = currentBatch.getLastSample();
        				
						/*
						 * Some phone's accelerometers aren't that good,
						 * sometimes, all of a sudden the accelerometer would
						 * return zeros in one or more accelerometer axis for a
						 * one or more samples. To detect and remove these
						 * anomalies (at the cost of sample timing), we check
						 * that the value is not zero, or if it is, then there
						 * were values close to zero before.
						 */
						for (int i = 0; i < Constants.ACCEL_DIM; ++i) {
							if (currentSample[i] == 0.0f) {
								double diffFromLast = currentSample[i] - prevSample[i];
								if (diffFromLast < 0.0f)
									diffFromLast = -diffFromLast;
								if (diffFromLast > 0.1f) {
									return;
								}
							}
						}
        				
        				double timeDiff = (currentSampleTime-prevSampleTime)/1000.0;
        				++samplingQualityCount;
        				samplingQualitySum += timeDiff;
        				samplingQualitySumSqr += timeDiff*timeDiff;
        			}

        			currentBatch.assignSample(currentSampleTime, currentSample);
        		}
        	}
        }

        /** {@inheritDoc} */
//        @Override
        public void onAccuracyChanged(final Sensor sensor, final int accuracy) {
            // Don't really care
        }

    };

    private int currentSamplingRate;
    private boolean assigningValues;
    private SampleBatch currentBatch;
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

    public AsyncAccelReader(final Context context) {
        manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        
        sqlLiteAdapter = SqlLiteAdapter.getInstance(context);
        optionsTable = sqlLiteAdapter.getOptionsTable();
        
        accelListenerRegistered = false;
        currentSamplingRate = SPECIFIC_SAMPLING_RATE; 
    }
    
    public int getCurrentSamplingRate() {
		return currentSamplingRate;
	}
    
    public void setCurrentSamplingRate(int currentSamplingRate) {
		this.currentSamplingRate = currentSamplingRate;
		
		if (accelListenerRegistered) {
			manager.unregisterListener(accelListener);
			manager.registerListener(
					accelListener,
                    manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                    currentSamplingRate
                    );
		}
	}
    
    public void startSampling(SampleBatch batch) throws HardwareFaultException {
    	boolean initSuccess = false;
    	try {
	    	if (!accelListenerRegistered) {
	    		Log.i(Constants.TAG, "Turning accelerometer on");
	            manager.registerListener(accelListener,
	                    manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
	                    currentSamplingRate
	                    );
	            accelListenerRegistered = true;
	    	}
	    	
	        samplingQualitySum = 0.0;
	        samplingQualitySumSqr = 0.0;
	        samplingQualityCount = 0.0;
	        currentBatch = batch;
	    	assigningValues = true;
	    	
	    	//	sometimes values are requested before the first
	    	//		accelerometer sensor change event has occurred,
	    	//		so wait for the sensor to change.
	    	if (currentBatch.getSize()<2)
	    	{
	    		Log.v(Constants.TAG, "No values assigned yet from accelerometer, going to wait for values.");
	    		long startWait = System.currentTimeMillis();
	    		long current = startWait;
		    	while (currentBatch.getSize()<2)
		    	{
		    		Thread.yield();
		    		current = System.currentTimeMillis();
		    		if (current-startWait>Constants.DELAY_SAMPLE_BATCH)
		    		{
		    			throw new HardwareFaultException("Unable to start accelerometer.");
		    		}
		    	}
	//	    	Log.v(Constants.TAG, "Done, waited for "+((current-startWait)/1000)+"s");
	    	}
	    	
	    	initSuccess = true;
    	} finally {
    		if (!initSuccess) {
    			assigningValues = false;
    			currentBatch = null;
    		}
    	}
    }

    public void stopSampling() {
    	if (currentBatch.hasLastSample()) {
	    	int batchDurationSec = (int)((currentBatch.getLastSampleTime()-currentBatch.getFirstSampleTime())/1000L);
	    	int size = batchDurationSec*Constants.RECOMMENDED_SAMPLING_FREQUENCY;
    		Log.d(Constants.TAG, "Downsampling sample batch from "+currentBatch.getSize()+" to "+size);
	    	currentBatch.downSample(size);
    	}
    	
    	assigningValues = false;
    	currentBatch = null;
		samplingQualityMean = samplingQualitySum / samplingQualityCount;
		samplingQualityStdDev = Math.sqrt(
				samplingQualitySumSqr / samplingQualityCount - 
				samplingQualityMean * samplingQualityMean
			);
		Log.d(Constants.TAG, String.format("Sampling Quality: Delay: Mean=%.2f ms, S.D=%.2f ms", samplingQualityMean, samplingQualityStdDev));
		
		if (!optionsTable.getFullTimeAccel()) {
    		manager.unregisterListener(accelListener);
    		accelListenerRegistered = false;
    		Log.i(Constants.TAG, "Turning accelerometer off");
    	}
    }
	
//	public void assignSample(SampleBatch batch) throws HardwareFaultException 
//	{
//    	//	lock the previous sample, hence locking the current one too
//    	int prevValueIndex = this.previousValueIndex;
//    	synchronized (this.valueBuff[prevValueIndex]) {
//    		//	get the next one after the previous.. i.e. the current
//        	int currValueIndex = (prevValueIndex+1) % BUFFER_SIZE;
//        	
//        	//	we sample 1 interval before...
//        	long now = System.currentTimeMillis() - Constants.DELAY_BETWEEN_SAMPLES;
//        	
//    		double delay;
//    		
//    		int timeFromPrev = (int)(now - this.valueBuff[prevValueIndex].timeStamp);
//    		int timeFromCurr = (int)(now - this.valueBuff[currValueIndex].timeStamp);
//    		
//    		//	if they are both of the same sign, that means our
//    		//		now time is after both.. pick the closest
//    		if ((timeFromPrev>0)==(timeFromCurr>0)) {
//            	int useIndex = currValueIndex; // default to the latest sample        	
//            	if (Math.abs(now - this.valueBuff[prevValueIndex].timeStamp) < 
//            			Math.abs(now - this.valueBuff[currValueIndex].timeStamp)) {
//            		//	unless the closer one is actually the one before that
//            		useIndex = prevValueIndex;
//            	}
//            	
//            	delay = Math.abs(now - this.valueBuff[useIndex].timeStamp);
//            	batch.assignSample(this.valueBuff[useIndex].values);
//
//    		} else {	// otherwise our now, is right in between
//        		float prev, curr;
//        		
//        		timeFromCurr = -timeFromCurr;
//        		
//        		resultantValues.timeStamp = now;
//        		for (int i=0; i<Constants.ACCEL_DIM; ++i) {
//        			prev = this.valueBuff[prevValueIndex].values[i]; 
//        			curr = this.valueBuff[currValueIndex].values[i]; 
//        			//	estimate the value at the now time...
//        			resultantValues.values[i] =
//        				(((curr-prev)*timeFromPrev)/(timeFromPrev+timeFromCurr)) + prev;
//        		}
//
//            	delay = Math.abs(now - resultantValues.timeStamp);
//            	batch.assignSample(resultantValues.values);
//    		}
//    	}
//    }
	
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
