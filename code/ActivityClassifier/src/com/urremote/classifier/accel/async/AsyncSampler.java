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
    
    private int startSamplingHardwareErrorCount = 0; 
    
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
			reader.startSampling(currentBatch);
	        Log.d(Constants.TAG, "Started sampling");
	        this.sampling = true;
	        handler.postDelayed(finishSampling, Constants.SAMPLING_BATCH_DURATION);
	        try {
				if (startSamplingHardwareErrorCount>0) {
					startSamplingHardwareErrorCount = 0;
					if (service.isHardwareNotificationOn())
						service.cancelHardwareNotification();
				}
			} catch (RemoteException e) {
			}
        } catch (HardwareFaultException e) {
        	++startSamplingHardwareErrorCount;
			Log.w(Constants.TAG, "Hardware Fault While Starting Sampling ("+startSamplingHardwareErrorCount+")", e);
			
            if (callback != null) {
            	callback.samplerError(currentBatch, e);
            	currentBatch = null;
            }
			
			if (startSamplingHardwareErrorCount>=3) {
				if (reader.getCurrentSamplingRate()>10) {
					reader.setCurrentSamplingRate(AsyncAccelReader.GENERIC_SAMPLING_RATE);
					startSamplingHardwareErrorCount = 0;
				} else {
					try {
						if (!service.isHardwareNotificationOn()) {
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
