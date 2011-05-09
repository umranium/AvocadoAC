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
import activity.classifier.rpc.Classification;
import android.os.Handler;
import android.util.Log;

/**
 * A utility class which handles sampling accelerometer data from an
 * {@link AsyncAccelReader}. The Sampler takes 128 samples with a 50ms delay between
 * each sample. When the Sampler has finished, it executes a runnable so that
 * the data may be retrieved and analysed.
 *
 * @author chris
 */
public class AsyncSampler implements Sampler {

    private final Handler handler;
    private final AsyncAccelReader reader;
    private final Runnable finishedRunnable;
    
    private SampleBatch currentBatch;
    private boolean sampling;
    /*
    private long lastTime;
    private long currTimeDelay;
    private long minTimeDelay;
    private long maxTimeDelay;
    private long cummTimeError;
    */
    private Timer timer;
    
    private TimerTask timerTask = new TimerTask() {
		@Override
		public void run() {
	    	/*
	    	long thisTime = System.currentTimeMillis();
	    	
	    	currTimeDelay = (thisTime-lastTime) - Constants.DELAY_BETWEEN_SAMPLES;
	    	if (currTimeDelay<0) {
	    		currTimeDelay = -currTimeDelay;
	    	}
	    	cummTimeError += currTimeDelay;
	    	if (minTimeDelay>currTimeDelay)
	    		minTimeDelay = currTimeDelay;
	    	if (maxTimeDelay<currTimeDelay)
	    		maxTimeDelay = currTimeDelay;
	    	*/
	    	reader.assignSample(currentBatch);
	    	//Log.i("accel",currentBatch.getCurrentSample()[0]+" "+currentBatch.getCurrentSample()[1]+" "+currentBatch.getCurrentSample()[2]);
	    	if (!currentBatch.nextSample()) {
	    		/*
	    		Log.v(Constants.DEBUG_TAG, "Cummulative Absolute Sampling Time Error: "+cummTimeError);
	    		Log.v(Constants.DEBUG_TAG, "Min Absolute Sampling Time Error: "+minTimeDelay);
	    		Log.v(Constants.DEBUG_TAG, "Max Absolute Sampling Time Error: "+maxTimeDelay);
	    		*/
	            reader.stopSampling();
	            AsyncSampler.this.sampling = false;
	            stop();
	            finishedRunnable.run();
	    	}
	    	
	    	//lastTime = thisTime;
		}
	};
    
    public AsyncSampler(final Handler handler, final AsyncAccelReader reader,
            final Runnable finishedRunnable) {
        this.handler = handler;
        this.reader = reader;
        this.finishedRunnable = finishedRunnable;
        this.timer = new Timer("Async Sampler");
    }

    public void start(SampleBatch currentBatch) {
    	this.currentBatch = currentBatch;
    	this.currentBatch.reset();
    	this.currentBatch.sampleTime = System.currentTimeMillis();
    	
        reader.startSampling();
        this.sampling = true;
        /*
        cummTimeError = 0L;
        minTimeDelay = Long.MAX_VALUE;
        maxTimeDelay = Long.MIN_VALUE;
        lastTime = System.currentTimeMillis();
        */
        //handler.postDelayed(this, Constants.DELAY_BETWEEN_SAMPLES);
        timer.scheduleAtFixedRate(timerTask, Constants.DELAY_BETWEEN_SAMPLES, Constants.DELAY_BETWEEN_SAMPLES);
        
        Log.i(Constants.DEBUG_TAG, "Started Sampling.");
    }

    public SampleBatch getSampleBatch() {
    	return currentBatch;
    }
    

    public void stop() {
    	timer.cancel();
        Log.i(Constants.DEBUG_TAG, "Finished Sampling.");
    }

	public boolean isSampling() {
		return sampling;
	}

}
