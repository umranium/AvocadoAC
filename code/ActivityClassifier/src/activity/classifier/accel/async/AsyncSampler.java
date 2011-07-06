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
	private final Handler handler;
    private final AsyncAccelReader reader;
    private final Runnable finishedRunnable;
    
    private SampleBatch currentBatch;
    private boolean sampling;
    
    private Timer timer;
    private SamplerTimerTask timerTask;
    
    private class SamplerTimerTask extends TimerTask
    {
		@Override
		public void run() {
			//	make sure we're sampling...
			if (AsyncSampler.this.sampling) {
		    	try {
					reader.assignSample(currentBatch);
			    	if (!currentBatch.nextSample()) {
			            stop();
			            if (finishedRunnable != null)
			            	finishedRunnable.run();
			    	}
				} catch (HardwareFaultException e) {
					Log.e(Constants.DEBUG_TAG, "Hardware Fault While Sampling", e);
					stop();
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
    		final Handler handler,
    		final AsyncAccelReader reader,
            final Runnable finishedRunnable) {
    	this.service = service;
        this.handler = handler;
        this.reader = reader;
        this.finishedRunnable = finishedRunnable;
        this.timer = new Timer("Async Sampler Timer");
        this.timerTask = new SamplerTimerTask();
    }

    public void start(SampleBatch currentBatch) {
//    	try {
//			service.showServiceToast("Starting Sampling.");
//		} catch (RemoteException e) {
//			e.printStackTrace();
//		}
    	
    	this.currentBatch = currentBatch;
    	this.currentBatch.reset();
    	this.currentBatch.sampleTime = System.currentTimeMillis();
    	
        try {
			reader.startSampling();
	        this.sampling = true;
	        Log.i(Constants.DEBUG_TAG, "Starting Timer");
	        timer.scheduleAtFixedRate(this.timerTask, Constants.DELAY_BETWEEN_SAMPLES, Constants.DELAY_BETWEEN_SAMPLES);
	        Log.i(Constants.DEBUG_TAG, "Started Sampling.");
        } catch (HardwareFaultException e) {
			Log.e(Constants.DEBUG_TAG, "Hardware Fault While Starting Sampling", e);
			try {
				service.handleHardwareFaultException("Faulty Accelerometer", e.getMessage());
			} catch (RemoteException e1) {
				e1.printStackTrace();
			}
		}
        
    }

    public SampleBatch getSampleBatch() {
    	return currentBatch;
    }
    

    public void stop() {
//    	try {
//			service.showServiceToast("Stopping Sampling.");
//		} catch (RemoteException e) {
//			e.printStackTrace();
//		}
		
    	reader.stopSampling();
        this.sampling = false;
        
        Log.i(Constants.DEBUG_TAG, "Cancelling Timer");
    	timerTask.cancel();		//	cancel timer task
    	timer.purge();			//	purge timer task
    							//	replace timer task to make sure previous one is gc
        this.timerTask = new SamplerTimerTask();
        Log.i(Constants.DEBUG_TAG, "Finished Sampling.");
    }

	public boolean isSampling() {
		return sampling;
	}

}
