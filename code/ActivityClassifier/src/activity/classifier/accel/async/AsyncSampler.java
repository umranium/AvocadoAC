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

import java.util.Timer;
import java.util.TimerTask;

import activity.classifier.accel.SampleBatch;
import activity.classifier.accel.Sampler;
import activity.classifier.accel.SamplerCallback;
import activity.classifier.common.Constants;
import activity.classifier.exception.HardwareFaultException;
import activity.classifier.rpc.ActivityRecorderBinder;
import activity.classifier.rpc.Classification;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;

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
    
    private SampleBatch currentBatch;
    private boolean sampling;
    
    private Timer timer;
    private SamplerTimerTask timerTask;
    
    private int startSamplingHardwareErrorCount = 0; 
    
    private class SamplerTimerTask extends TimerTask
    {
		@Override
		public void run() {
			//	make sure we're sampling...
			if (AsyncSampler.this.sampling) {
		    	try {
					reader.assignSample(currentBatch);
			    	if (!currentBatch.nextSample()) {
			            if (callback != null)
			            	callback.samplerFinished(currentBatch);
			    		finalizeSampling();
			    	}
				} catch (HardwareFaultException e) {
					Log.e(Constants.DEBUG_TAG, "Hardware Fault While Sampling", e);
		            if (callback != null)
		            	callback.samplerError(currentBatch, e);
					finalizeSampling();
					try {
						service.handleHardwareFaultException("Faulty Accelerometer", e.getMessage());
					} catch (RemoteException e1) {
						e1.printStackTrace();
					}
				}
				
			}
		}
	};
    
    public AsyncSampler(
    		final ActivityRecorderBinder service, 
    		final AsyncAccelReader reader,
            final SamplerCallback callback) {
    	this.service = service;
        this.reader = reader;
        this.callback = callback;
        this.timer = new Timer("Async Sampler Timer");
        this.timerTask = new SamplerTimerTask();
    }

    public void start(SampleBatch currentBatch) {
    	this.currentBatch = currentBatch;
    	this.currentBatch.reset();
    	this.currentBatch.sampleTime = System.currentTimeMillis();
    	
        try {
			reader.startSampling();
	        startSamplingHardwareErrorCount = 0;
        } catch (HardwareFaultException e) {
        	++startSamplingHardwareErrorCount;
			Log.e(Constants.DEBUG_TAG, "Hardware Fault While Starting Sampling ("+startSamplingHardwareErrorCount+")", e);
			
            if (callback != null)
            	callback.samplerError(currentBatch, e);
			
			if (startSamplingHardwareErrorCount>=3) {
				try {
					service.handleHardwareFaultException("Faulty Accelerometer", e.getMessage());
				} catch (RemoteException e1) {
					e1.printStackTrace();
				}
			}
			
			return;
		}
        
        this.sampling = true;
        Log.i(Constants.DEBUG_TAG, "Starting Timer");
        timer.scheduleAtFixedRate(this.timerTask, Constants.DELAY_BETWEEN_SAMPLES, Constants.DELAY_BETWEEN_SAMPLES);
        Log.i(Constants.DEBUG_TAG, "Started Sampling.");
    }
    
    private void finalizeSampling() {
        this.sampling = false;
    	reader.stopSampling();
        
        Log.i(Constants.DEBUG_TAG, "Cancelling Timer");
    	timerTask.cancel();		//	cancel timer task
    	timer.purge();			//	purge timer task
    							//	replace timer task to make sure previous one is gc
        this.timerTask = new SamplerTimerTask();
    }

    public void stop() {
    	if (this.isSampling()) {
	        if (callback != null)
	        	callback.samplerStopped(currentBatch);
	    	finalizeSampling();
    	}
        Log.i(Constants.DEBUG_TAG, "Sampling Stopped.");
    }

	public boolean isSampling() {
		return sampling;
	}

}
