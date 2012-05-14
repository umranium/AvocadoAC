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

import java.util.Timer;
import java.util.TimerTask;

import com.urremote.classifier.accel.SampleBatch;
import com.urremote.classifier.accel.Sampler;
import com.urremote.classifier.accel.SamplerCallback;
import com.urremote.classifier.common.Constants;
import com.urremote.classifier.exception.HardwareFaultException;
import com.urremote.classifier.rpc.Classification;

import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;
import com.urremote.classifier.rpc.ActivityRecorderBinder;

/**
 * A utility class which handles sampling accelerometer data from an
 * {@link AsyncAccelReader}. The Sampler takes 128 samples with a 50ms delay between
 * each sample. When the Sampler has finished, it executes a runnable so that
 * the data may be retrieved and analysed.
 * 
 * This class uses Timer and TimerTask classes to schedule sampling.
 * Even though the Timer class introduces a new Thread hence incurs
 * some performance penalty, but the Timer class's scheduleAtFixedRate()
 * function guarantees a more consistent scheduling rate than the alternative:
 * The Handler's postDelayed() function. The consistency is important for
 * any FFT computations undertaken later.
 *
 * @author chris
 */
public class AsyncSampler implements Sampler {
	
	private final ActivityRecorderBinder service;
    private final AsyncAccelReader reader;
    private final SamplerCallback callback;
    private final Handler handler; 
    
    private SampleBatch currentBatch;
    private boolean sampling;
    
    private int samplingHardwareErrorCount = 0; 
    
    private Runnable finishSampling = new Runnable() {
		public void run() {
			if (sampling) {
				Log.d(Constants.TAG, "Finished sampling: sampled "+currentBatch.getSize()+" samples.");
	    		finalizeSampling();
	            if (callback != null) {
	            	callback.samplerFinished(currentBatch);
	            	currentBatch = null;
	            }
			}
		}
	};
	
    public AsyncSampler(
    		final ActivityRecorderBinder service, 
    		final AsyncAccelReader reader,
            final SamplerCallback callback,
            final Handler handler) {
    	this.service = service;
        this.reader = reader;
        this.callback = callback;
        this.handler = handler;
    }
    
    public void start(SampleBatch currentBatch) {
    	this.currentBatch = currentBatch;
    	this.currentBatch.reset();
    	this.currentBatch.sampleTime = System.currentTimeMillis();
    	
        try {
			reader.startSampling(currentBatch, finishSampling);
	        Log.d(Constants.TAG, "Started sampling");
	        this.sampling = true;
	        
	        //	wait for either all samples to be gathered, or for the period between batches (~30s) to finish. 
	        long currentTime = System.currentTimeMillis();
	        while (sampling && currentBatch.getSize()<Constants.NUMBER_OF_SAMPLES && 
	        		currentTime-currentBatch.sampleTime<Constants.DELAY_SAMPLE_BATCH) {
	        	try {
					Thread.sleep(1000L);
				} catch (InterruptedException e) {
				}
	        }
	        
	        if (sampling && currentBatch.getSize()<Constants.NUMBER_OF_SAMPLES) {
	        	throw new HardwareFaultException("Accelerometer rate too slow.");
	        }
	        
			if (samplingHardwareErrorCount>0) {
				Log.d(Constants.TAG, "Resetting hardware error count to zero");
				samplingHardwareErrorCount = 0;
//				if (service.isHardwareNotificationOn()) {
//					Log.d(Constants.TAG, "Clearing Faulty Hardware Notification");
//					service.cancelHardwareNotification();
//				}
			}
	        
        } catch (HardwareFaultException e) {
        	++samplingHardwareErrorCount;
			Log.w(Constants.TAG, "Hardware Fault While Starting Sampling ("+samplingHardwareErrorCount+")", e);
			
            if (callback != null) {
            	callback.samplerError(currentBatch, e);
            	currentBatch = null;
            }
			
			if (samplingHardwareErrorCount>=3) {
				if (reader.getCurrentSamplingRate()>10) {
					reader.setCurrentSamplingRate(AsyncAccelReader.GENERIC_SAMPLING_RATE);
					samplingHardwareErrorCount = 0;
				} else {
					try {
//						if (!service.isHardwareNotificationOn())
						{
							service.handleHardwareFaultException("Faulty Accelerometer", e.getMessage());
						}
					} catch (RemoteException e1) {
						e1.printStackTrace();
					}
				}
			}
			
			return;
		} finally {
			
		}
        
    }
    
    public int getSamplingHardwareErrorCount() {
		return samplingHardwareErrorCount;
	}
    
    private void finalizeSampling() {
        this.sampling = false;
    	reader.stopSampling();
    }

    public void stop() {
    	if (sampling) {
	    	finalizeSampling();
	        if (callback != null) {
	        	callback.samplerStopped(currentBatch);
	        	currentBatch = null;
	        }
    	}
        Log.i(Constants.TAG, "Sampling Stopped.");
    }
    
	public boolean isSampling() {
		return sampling;
	}

}
