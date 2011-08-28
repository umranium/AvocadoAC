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
 * @author Ken Taylor
 */
public class CalcStatistics {
	
	//	the lowest required accuracy, lower than which, is regarded as zero
	private final float EPSILON = 5.0e-4f;
	
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
	 * The population-based sd of the array data.
	 */
	private float sd_p[];
	
	/**
	 * The population-based variance of the array data.
	 */
	private float var_p[];
	
	/**
	 * The sample-based sd of the array data.
	 */
	private float sd_s[];
	
	/**
	 * The sample-based variance of the array data.
	 */
	private float var_s[];
	
	public CalcStatistics(int dimensions) {
		this.dimensions = dimensions;

		this.sum = new float[dimensions]; 
		this.sumSqr = new float[dimensions];
		this.max = new float[dimensions];
		this.min = new float[dimensions];
		this.mean = new float[dimensions];
		this.sd_p = new float[dimensions];
		this.var_p = new float[dimensions];
		this.sd_s = new float[dimensions];
		this.var_s = new float[dimensions];
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
            this.sd_p[i] = 0.0f;
            this.var_p[i] = 0.0f;
            this.sd_s[i] = 0.0f;
            this.var_s[i] = 0.0f;
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
		
		float sumSqrOverSamples, sumSqrOverSamplesLessOne, meanSqr;
			
		for (int j = 0; j < dimensions; j++) {
			mean[j] = sum[j] / (samples);
			
			sumSqrOverSamples = sumSqr[j] / samples;
			sumSqrOverSamplesLessOne = sumSqr[j] / (samples-1);
			meanSqr = mean[j] * mean[j];
			
			var_p[j] = sumSqrOverSamples - meanSqr;

			if (var_p[j]>-EPSILON && var_p[j]<EPSILON) {
				var_p[j] = 0.0f;
			}
			
			if (var_p[j]<0.0f) {
				throw new RuntimeException("Population Variance ("+var_p[j]+") found to be < 0.0; Sum(x^2)="+sumSqr[j]+", mean^2="+meanSqr);
			}
			if (Float.isNaN(var_p[j])) {
				throw new RuntimeException("Population Variance found to be NaN");
			}
			
			sd_p[j] = (float) Math.sqrt(var_s[j]);
			
			var_s[j] = sumSqrOverSamplesLessOne - samples * meanSqr / (samples-1);
			
			if (var_s[j]>-EPSILON && var_s[j]<EPSILON) {
				var_s[j] = 0.0f;
			}
			
			if (var_s[j]<0.0f) {
				throw new RuntimeException("Sample Variance ("+var_s[j]+") found to be < 0.0; Sum(x^2)="+sumSqr[j]+", mean^2="+meanSqr);
			}
			if (Float.isNaN(var_s[j])) {
				throw new RuntimeException("Sample Variance found to be NaN");
			}
			
			sd_s[j] = (float) Math.sqrt(var_s[j]);
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
			verticalAccel += mean[j] * mean[j];
		}
		verticalAccel = (float) Math.sqrt(verticalAccel);
		return verticalAccel;
	}
	
	/**
	 * 
	 * @return population-based variance of all the items that have been entered.
	 *			(i.e. assumes the data given is the whole population)
	 *			Value will be Double.NaN if count == 0.
	 */
	public float[] getPopVariance() {
		return var_p;
	}
	
	/**
	 * 
	 * @return population-based standard deviation of all the items that have been entered.
	 *			(i.e. assumes the data given is the whole population)
	 *			Value will be Double.NaN if count == 0.
	 */
	public float[] getPopStandardDeviation() {
		return sd_p;
	}
	
	/**
	 * 
	 * @return sample-based variance of all the items that have been entered.
	 *			(i.e. assumes the data given is a subset of the population)
	 *			Value will be Double.NaN if count == 0.
	 */
	public float[] getSampleVariance() {
		return var_s;
	}
	
	/**
	 * 
	 * @return sample-based standard deviation of all the items that have been entered.
	 *			(i.e. assumes the data given is a subset of the population)
	 *			Value will be Double.NaN if count == 0.
	 */
	public float[] getSampleStandardDeviation() {
		return sd_s;
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
