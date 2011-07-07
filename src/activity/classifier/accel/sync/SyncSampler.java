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

import activity.classifier.accel.SampleBatch;
import activity.classifier.accel.Sampler;
import activity.classifier.common.Constants;
import activity.classifier.rpc.Classification;
import android.os.Handler;
import android.util.Log;

/**
 * A utility class which handles sampling accelerometer data from an
 * {@link SyncAccelReader}. The Sampler takes 128 samples with a 50ms delay between
 * each sample. When the Sampler has finished, it executes a runnable so that
 * the data may be retrieved and analysed.
 *
 * @author chris
 */
public class SyncSampler implements Sampler {
	
    private final SyncAccelReader reader;
    private final Runnable finishedRunnable;
    
    private SampleBatch currentBatch;
    private boolean sampling;
    
    //	called when the reader has finished sampling the whole batch
    private Runnable whenDone = new Runnable() {
		@Override
		public void run() {
			SyncSampler.this.sampling = false;
			Log.i(Constants.DEBUG_TAG, "Finished Sampling.");
	        finishedRunnable.run();
		}
	};
    
    public SyncSampler(final SyncAccelReader reader, final Runnable finishedRunnable) {
        this.reader = reader;
        this.finishedRunnable = finishedRunnable;
    }

    public void start(SampleBatch currentBatch) {
    	this.currentBatch = currentBatch;
    	this.currentBatch.reset();
    	this.currentBatch.sampleTime = System.currentTimeMillis();
    	
        reader.startSampling(Constants.DELAY_BETWEEN_SAMPLES, currentBatch, whenDone);
        this.sampling = true;
        
        Log.i(Constants.DEBUG_TAG, "Started Sampling.");
    }

    public SampleBatch getSampleBatch() {
    	return currentBatch;
    }
    
    public void stop() {
        reader.stopSampling();
    	SyncSampler.this.sampling = false;
        Log.i(Constants.DEBUG_TAG, "Stopped Sampling.");
    }

	public boolean isSampling() {
		return sampling;
	}

}
