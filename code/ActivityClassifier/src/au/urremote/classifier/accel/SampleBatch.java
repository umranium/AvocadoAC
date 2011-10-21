package au.urremote.classifier.accel;

import android.util.Log;
import au.urremote.classifier.common.Constants;

/**
 * 
 * @author Umran
 * 
 * <p>
 * Represents a batch of consecutive samples taken within one sampling period.
 * 
 * <p>
 * By placing the samples taken within a single sampling period in one class,
 * it is possible to move a reference to the batch instead of copying the
 * batch. In addition, we can place statistical information extracted from
 * the batch, in the same class.
 *
 */
public class SampleBatch {
	
	public long sampleTime;
	public final long[] timeStamps;
	public final float[][] data;
	private int currentSample;
	private boolean charging;
	
	public SampleBatch() {
		data = new float[Constants.NUM_OF_SAMPLES_PER_BATCH][Constants.ACCEL_DIM];
		timeStamps = new long[Constants.NUM_OF_SAMPLES_PER_BATCH];
		currentSample = 0;
	}
	
	public void assignSample(float[] data) {
		timeStamps[currentSample] = System.currentTimeMillis();
		for (int d=0; d<Constants.ACCEL_DIM; ++d)
			this.data[currentSample][d] = data[d];
	}
	
	public boolean nextSample() {
		++currentSample;
		
		if (currentSample>=Constants.NUM_OF_SAMPLES_PER_BATCH) {
			currentSample = Constants.NUM_OF_SAMPLES_PER_BATCH;
			return false;
		}
		else {
			return true;
		}
	}
	
	public void reset() {
		currentSample = 0;
	}
	
	public int getSize() {
		return currentSample;
	}

	public boolean isCharging() {
		return charging;
	}

	public void setCharging(boolean charging) {
		this.charging = charging;
	}
	
	
}
