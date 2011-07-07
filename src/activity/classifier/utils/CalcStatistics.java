package activity.classifier.utils;

import activity.classifier.common.Constants;
import android.util.Log;

/**
 * An object of class CalcStatistics can be used to compute several simple
 * statistics for a set of numbers. Numbers are passed in a 3D array of X,Y,Z.
 * Methods are provided to return the following statistics for the set of
 * numbers that have been entered: The number of items, the sum of the items,
 * the average, the standard deviation, the maximum, the minimum and
 * VerticalAccel. The vertical acceleration would normally equal gravity. If
 * higher than gravity then device was accelerated during the sampling interval
 * most likely in a car. If it is less than gravity then device was rotated
 * during sampling.
 * 
 *	<p>
 *	Changes made by Umran: <br>
 *	Class can now work on any given vector size.
 *	And to be re-usable with another set of samples. Call {@link #assign(float[][], int)} to initiate another set of samples.
 *
 * <p>
 * 
 * 
 * @author Ken Taylor
 */
public class CalcStatistics {
	
	/**
	 * Number of dimensions we're computing
	 */
	private final int dimensions;
	
	
	/**
	 * Number of numbers in the array.
	 */
	private int count;

	/**
	 * The sum of all the items in the array.
	 */
	private float sum[];

	/**
	 * The sum of the squares of all the items.
	 */
	private float sumSqr[];

	/**
	 * Largest item seen.
	 */
	private float max[];

	/**
	 * Smallest item seen.
	 */
	private float min[];

	/**
	 * The mean of the array data.
	 */
	private float mean[];
	
	/**
	 * The sd of the array data.
	 */
	private float sd[];
	
	public CalcStatistics(int dimensions) {
		this.dimensions = dimensions;

		this.sum = new float[dimensions]; 
		this.sumSqr = new float[dimensions];
		this.max = new float[dimensions];
		this.min = new float[dimensions];
		this.mean = new float[dimensions];
		this.sd = new float[dimensions];
	}
	
	/**
	 * Initiates computation of statistical attributes from 
	 * the array of vectors given.
	 * 
	 * @param arrayIn
	 *            an array of vectors
	 * @param samples
	 *            number of vectors used from the array
	 */
	public void assign(float[][] arrayIn, int samples) {
		
		assert(samples>0);
		
		this.count = samples;
		
		for (int i=0; i<dimensions; ++i) {
            this.sum[i] = 0.0f;
            this.sumSqr[i] = 0.0f;
            this.min[i] = Float.POSITIVE_INFINITY;
            this.max[i] = Float.NEGATIVE_INFINITY;
            this.mean[i] = 0.0f;
        }
		
		
		float val;
		// step through the array
		for (int s = 0; s < samples; ++s) {

			for (int j = 0; j < dimensions; j++) {
				val = arrayIn[s][j];
				sum[j] += val;
				sumSqr[j] += val * val;
				if (val > max[j])
					max[j] = val;
				if (val < min[j])
					min[j] = val;
			}
		}
		
		for (int j = 0; j < dimensions; j++) {
			mean[j] = sum[j] / (samples);
			sd[j] = (float) Math.sqrt(
					sumSqr[j] / count - mean[j] * mean[j]
				);
		}
	}

	/**
	 * 
	 * @return number of items in array as passed in.
	 */
	public int getCount() {
		return count;
	}

	/**
	 * 
	 * @return the sum of all the items that have been entered.
	 */
	public float[] getSum() {
		return sum;
	}

	/**
	 * @return the sumSqr
	 */
	public float[] getSumSqr() {
		return sumSqr;
	}
	
	/**
	 * 
	 * @return average of all the items that have been entered. Value is
	 *         Float.NaN if count == 0.
	 */
	public float[] getMean() {
		return mean;
	}

	/**
	 * 
	 * @return
	 */
	public float getVerticalAccel() {
		float verticalAccel = 0;
		for (int j = 0; j < dimensions; j++) {
			Log.i("sd", "count" + count + " sum_sqr " + sumSqr[j] + " mean "
					+ mean[j] + " ");
			verticalAccel += mean[j] * mean[j];
		}
		verticalAccel = (float) Math.sqrt(verticalAccel);
		return verticalAccel;
	}
	
	/**
	 * 
	 * @return standard deviation of all the items that have been entered. Value
	 *         will be Double.NaN if count == 0.
	 */
	public float[] getStandardDeviation() {
		return sd;
	}

	/**
	 * 
	 * @return the smallest item that has been entered. Value will be - infinity
	 *         if no items in array.
	 */
	public float[] getMin() {
		return min;
	}

	/**
	 * 
	 * @return the largest item that has been entered. Value will be -infinity
	 *         if no items have been entered.
	 */
	public float[] getMax() {
		return max;
	}
	
	/**
	 * Computes the magnitude of a vector.
	 * 
	 * @param vec
	 * A vector of dimensions as given when the instance is constructed
	 * using {@link #CalcStatistics(int)}
	 * 
	 * @return
	 * the magnitude of the vector
	 */
	public float calcMag(float[] vec) {
		return calcMag(dimensions, vec);
	}
	
	/**
	 * Creates a vector of the dimensions given when this instance is
	 * initialised.
	 * 
	 * @return
	 * A vector of given dimensions
	 */
	public float[] createVector() {
		return new float[dimensions];
	}	
	
	public static float calcMag(int dimensions, float[] vec) {
		double mag = 0.0f;
		for (int i=0; i<dimensions; ++i)
			mag += vec[i]*vec[i];
		return (float)Math.sqrt(mag);
	}
	
	public static void normalize(int dimensions, float[] vec) {
        float length = calcMag(dimensions, vec);
        if (length != 0) {
    		for (int i=0; i<dimensions; ++i)
    			vec[i] /= length;
        }
	}

	
}
