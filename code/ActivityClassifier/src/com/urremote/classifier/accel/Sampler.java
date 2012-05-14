package com.urremote.classifier.accel;

import com.urremote.classifier.accel.async.AsyncAccelReader;
import com.urremote.classifier.common.Constants;

import android.util.Log;

/**
 * Collects samples from the Accelerometer Reader {@link AsyncAccelReader} over a period of
 * time. The samples collected over the period are saved in the {@link SampleBatch}.
 * 
 * @author Umran
 *
 */
public interface Sampler {

	/**
	 * Start sampling, save the sampled data to the batch given.
	 * The sampler will stop after sampling 1 whole batch.
	 * 
	 * @param currentBatch
	 * 		Batch to save the sampled data to.
	 * 
	 */
    public void start(SampleBatch currentBatch);
    
    /**
     * Forces the sampler to stop sampling.
     */
    public void stop();
    
    /**
     * 
     * @return
     * 	true if the sampler is still currently sampling,
     *	false if not sampling.
     */
	public boolean isSampling();
	
	
	/**
	 * Number of accelerometer faults counted 
	 * @return
	 */
	public int getSamplingHardwareErrorCount();
}
