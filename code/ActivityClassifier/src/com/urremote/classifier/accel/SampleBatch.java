package com.urremote.classifier.accel;

import com.urremote.classifier.common.Constants;

import android.util.Log;

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
		data = new float[Constants.NUMBER_OF_SAMPLES][Constants.ACCEL_DIM];
		timeStamps = new long[Constants.NUMBER_OF_SAMPLES];
		currentSample = 0;
	}
	
	public boolean hasLastSample() {
		return currentSample>0;
	}
	
	public float[] getLastSample() {
		return this.data[currentSample-1];
	}
	
	public long getFirstSampleTime() {
		return this.timeStamps[0];
	}
	
	public long getLastSampleTime() {
		return this.timeStamps[currentSample-1];
	}
	
	public void assignSample(long time, float[] data) {
		timeStamps[currentSample] = time;
		for (int d=0; d<Constants.ACCEL_DIM; ++d)
			this.data[currentSample][d] = data[d];
		++currentSample;
	}
	
	public boolean hasSpace() {
		return currentSample<Constants.NUMBER_OF_SAMPLES;
	}
	
	public void downSample(int size) {
		if (size>currentSample) {
			return;
		}
		
		int step = currentSample / size;
		int write = 0;
		for (int read=0; read<currentSample; read+=step) {
			for (int d=0; d<Constants.ACCEL_DIM; ++d)
				data[write][d] = data[read][d];
			++write;
		}
		
		currentSample = write;
	}
	
//	public boolean nextSample() {
//		++currentSample;
//		
//		if (currentSample>=Constants.MAXIMUM_SUPPORTED_SAMPLES_PER_BATCH) {
//			currentSample = Constants.MAXIMUM_SUPPORTED_SAMPLES_PER_BATCH;
//			return false;
//		}
//		else {
//			return true;
//		}
//	}
	
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
