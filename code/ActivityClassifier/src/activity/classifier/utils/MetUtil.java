package activity.classifier.utils;

import java.util.Arrays;

import activity.classifier.common.Constants;
import android.util.Log;

/**
 * Assists in the computation of energy expenditure, and MET.
 * As explained on the paper:
 * <a href="http://jap.physiology.org/content/83/6/2112.full.pdf#page=1&view=FitH">
 * Improving energy expenditure estimation by using a triaxial accelerometer</a>
 * 
 * <p>
 * This class assumes that the data is being sampled at 20Hz,
 * hence some of the computations done, are purposefully done
 * for 20Hz only. One of the computations is by getting the mean
 * of every consequent group of 4 samples, hence effectively
 * reducing the sampling frequency to 5Hz. The max signal
 * frequency we can obtain from 5Hz sampling frequency this 
 * is 2.5Hz, which is our required upper limit for our
 * frequency filter. The lower limit is 0.25Hz, which since
 * we are sampling for too short a period, we can't detect.
 * </p>
 * 
 * @author abd01c
 *
 */
public class MetUtil {
	
	private static final float AMP_FILTER_LOW = Constants.GRAVITY*0.05f;
	private static final float AMP_FILTER_HIGH = Constants.GRAVITY*2.5f;
	
	//	energy expenditure while resting, per kg mass
	private static final double RESTING_EE_PER_KG = 0.418;
	
	public static final int GENDER_MALE = 1;
	public static final int GENDER_FEMALE = 2;
	
	//	group samples to obtain their mean, hence effectively
	//		reducing the sampling frequency
	//	e.g. while sampling at 20Hz, to reduce to 10Hz,
	//		we get the mean of every two samples
	//		to reduce to 5Hz, we get the mean of every 4 samples.
	private static final int MEAN_GROUP_SIZE = 4;
	
	private static final double[] DC_COMPONENTS = new double[] {
			0.0, 0.0, Constants.GRAVITY
		};
	
	private double param_a;
	private double param_b;
	private double param_p1;
	private double param_p2;
	
	private double[] counts = new double[3];
	
	private double restingEE;
	
	/**
	 * @param mass
	 * 	in kilograms
	 * 
	 * @param gender
	 * 	either {@link #GENDER_MALE} or {@link #GENDER_FEMALE}
	 */
	public MetUtil(double mass, int gender) {
		param_p1 = (2.66*mass + 146.72) / 1000.0;
		param_p2 = (-3.85*mass + 968.28) / 1000.0;
		param_a = (12.81*mass + 843.22) / 1000.0;
		param_b = (38.90*mass + 682.44*gender + 693.50) / 1000.0;
		
		restingEE = RESTING_EE_PER_KG * mass;
	}
	
	/**
	 * Computes the MET of the individual given the actual energy expenditure
	 * <p>
	 * <code>EEact = EE - EEresting</code>
	 * <code>MET = EE / EEresting</code>
	 * </p>
	 * @param eeAct
	 * 		The actual energy expenditure of the activity, computed using the function
	 * 		{@link #computeEEact(int, float[], float[], float[], long[])}
	 * 
	 * @return
	 * 		The MET of the activity
	 */
	public double computeMET(double eeAct)
	{
		//	EEact = EE - EEresting
		double ee = eeAct + restingEE;
		
		//	MET = EE / EEresting
		return ee / restingEE;
	}
	
	/**
	 * Computes the EEact given the two horizontal accelerations, and vertical accelerations.
	 * <p>
	 * <code>EEact = EE - EEresting</code>
	 * </p>
	 * @param len			number of samples in the signal
	 * @param rotatedData	samples from the accelerometer, after rotation
	 * @param timeStamps	the timestamps of the samples
	 * @return				the actual energy expenditure
	 */
	public double computeEEact(int len, float[][] rotatedData, long[] timeStamps)
	{
		computeCountsPerSecond(len, rotatedData, timeStamps, counts);
		
		Log.d(Constants.DEBUG_TAG, "Average Zero-Crossings (per second): "+Arrays.toString(counts));
		
		//	the computed counts are per second,
		//		convert to per minute
		for (int dim=0; dim<3; ++dim) {
			counts[dim] *= 60.0;
		}
		
		double horCounts = Math.sqrt(counts[0]*counts[0] + counts[1]*counts[1]);
		double verCounts = counts[2];
		
		return	param_a * Math.pow(horCounts, param_p1) +
				param_b * Math.pow(verCounts, param_p2);
	}

	/**
	 * Computes the average number of zero-crossings in the accelerometer
	 * samples given (after rotation) per second.
	 * 
	 * @param len				the number of accelerometer samples in each dimension
	 * @param rotatedData		the accelerometer samples (3 dimensions, after rotation)
	 * @param timeStamps		timestamps of the accelerometer samples
	 * @param results			an array of three doubles to return the average number of
	 * 								zero-crossings per second
	 */
	private void computeCountsPerSecond(int len, float[][] rotatedData, long[] timeStamps, double[] results) {
		for (int dim=0; dim<3; ++dim) {
			
			//	number of zero-crossings found
			double counts = 0.0;
			//	the duration of the signal (in seconds)
			double timePeriod = ((double)(timeStamps[len-1]-timeStamps[0])) / 1000.0; 
			
			float value;
			boolean beenToTheUpperBand = false;
			boolean beenToTheLowerBand = false;
			for (int iSample=0, iGroup=0; iSample+MEAN_GROUP_SIZE<len; iSample+=MEAN_GROUP_SIZE, ++iGroup) {
				//	get the mean of every group of samples samples..
				//		hence effectively reducing the sampling
				//		frequency to the required frequency
				value = 0.0f;
				for (int k=0; k<MEAN_GROUP_SIZE; ++k) {
					value += rotatedData[iSample+k][dim] - DC_COMPONENTS[dim];
				}
				value /= MEAN_GROUP_SIZE;
				
				boolean isInTheUpperBand = false;
				boolean isInTheLowerBand = false;
				
				isInTheUpperBand = (value>AMP_FILTER_LOW && value<AMP_FILTER_HIGH);
				isInTheLowerBand = (-value>AMP_FILTER_LOW && -value<AMP_FILTER_HIGH);
				
				if (beenToTheUpperBand && isInTheLowerBand) {
					++counts;
					
					beenToTheUpperBand = false;
					beenToTheLowerBand = true;
				}
				else
				if (beenToTheLowerBand && isInTheUpperBand) {
					++counts;
					
					beenToTheUpperBand = true;
					beenToTheLowerBand = false;
				}
				else {
					if (isInTheUpperBand)
						beenToTheUpperBand = true;
					if (isInTheLowerBand)
						beenToTheLowerBand = true;
				}
			}
			
			results[dim] = counts / timePeriod;
		}
	}
	
}
