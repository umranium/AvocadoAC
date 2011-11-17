package com.urremote.classifier.utils;

import java.util.Arrays;
import java.util.Set;

import com.urremote.classifier.common.Constants;
import com.urremote.classifier.db.OptionUpdateHandler;

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
public class MetUtilFinal implements OptionUpdateHandler {
	
	private static final float AMP_FILTER_LOW = Constants.GRAVITY*0.05f;
	
	//	energy expenditure while resting, per kg mass
	private static final double RESTING_EE_PER_KG_PER_SEC = 0.418;
	
	public static final int GENDER_MALE = 1;
	public static final int GENDER_FEMALE = 2;
	
	//	group samples to obtain their mean, hence effectively
	//		reducing the sampling frequency
	//	e.g. while sampling at 20Hz, to reduce to 10Hz,
	//		we get the mean of every two samples
	//		to reduce to 5Hz, we get the mean of every 4 samples.
	private static final int MEAN_GROUP_SIZE = 4;
	
	private double mass;	//	mass in kg
	private double height;	//	height in cm
	private double age;		//	age in years
	private int gender;		// 1 male, 2 female
	
	private double param_a;
	private double param_b;
	private double param_p1;
	private double param_p2;
	
	private double restingEE;
	
	/**
	 * @param mass		in kilograms
	 * @param height	in cm
	 * @param age		in years
	 * @param gender	either {@link #GENDER_MALE} or {@link #GENDER_FEMALE}
	 */
	public MetUtilFinal(double mass, double height, double age, int gender) {
		this.mass = mass;
		this.height = height;
		this.age = age;
		this.gender = gender;
		
		computeRestingEE();
		computeParameters();
	}
	
	private void computeRestingEE() {
		double weightInLb = mass * 2.20462262;
		double heightInInches = height * 0.393700787; 
		if (gender==GENDER_MALE) {
			restingEE = 4.19 * ((473*weightInLb) + (971*heightInInches) - (513*age) + 4687) / 100000.0;
		} else {
			restingEE = 4.19 * ((331*weightInLb) + (352*heightInInches) - (353*age) + 49854) / 100000.0;
		}
	}
	
	private void computeParameters() {
		param_p1 = (2.66*mass + 146.72) / 1000.0;
		param_p2 = (-3.85*mass + 968.28) / 1000.0;
		param_a = (12.81*mass + 843.22) / 1000.0;
		param_b = (38.90*mass - 682.44*gender + 692.50) / 1000.0;
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
		
		double met = ee / restingEE;
		
		Log.i(Constants.DEBUG_TAG, "MET="+met);
		
		//	MET = EE / EEresting
		return met;
	}
	
	/**
	 * Computes the EEact given the two horizontal accelerations, and vertical accelerations.
	 * <p>
	 * <code>EEact = EE - EEresting</code>
	 * </p>
	 * @param counts		counts per second in all 3 accelerometer axi
	 * @return				the actual energy expenditure
	 */
	public double computeEEact(double counts[])
	{	
		double horCounts = Math.sqrt(counts[0]*counts[0] + counts[1]*counts[1]);
		double verCounts = counts[2];
		
		Log.i(Constants.DEBUG_TAG, "horCounts="+horCounts);
		Log.i(Constants.DEBUG_TAG, "verCounts="+verCounts);
		
		Log.i(Constants.DEBUG_TAG, "param_p1="+param_p1);
		Log.i(Constants.DEBUG_TAG, "param_p2="+param_p2);
		Log.i(Constants.DEBUG_TAG, "param_a ="+param_a);
		Log.i(Constants.DEBUG_TAG, "param_b ="+param_b);
		
		double eeAct = param_a * Math.pow(horCounts, param_p1) +
					param_b * Math.pow(verCounts, param_p2); 
		
		Log.i(Constants.DEBUG_TAG, "eeAct="+eeAct);
		
		Log.i(Constants.DEBUG_TAG, "restingEE="+restingEE);
		
		return	eeAct;
	}

	public void onFieldChange(Set<String> updatedKeys) {
		computeRestingEE();
		computeParameters();
	}
	
	/**
	 * Computes the average number of zero-crossings in the accelerometer
	 * samples given (after rotation) per second.
	 * 
	 * @param len				the number of accelerometer samples in each dimension
	 * @param rotatedData		the accelerometer samples (3 dimensions, after rotation)
	 * @param rotatedDataMeans  the means of the rotated data, in each dimension
	 * @param timeStamps		timestamps of the accelerometer samples
	 * @param results			an array of three doubles to return the average number of
	 * 								zero-crossings per second
	 */
	public void computeCountsZeroCrossing(int len, float[][] rotatedData, float[] rotatedDataMeans, long[] timeStamps, double[] results) {
		//	the duration of the signal (in seconds)
		double timePeriod = ((double)(timeStamps[len-1]-timeStamps[0])) / 1000.0;
		
		for (int dim=0; dim<3; ++dim) {
			
			//	number of zero-crossings found
			double counts = 0.0;
			//	the duration of the signal (in seconds)
			
			float value;
			boolean beenToTheUpperBand = false;
			boolean beenToTheLowerBand = false;
			for (int iSample=0, iGroup=0; iSample+MEAN_GROUP_SIZE<len; iSample+=MEAN_GROUP_SIZE, ++iGroup) {
				//	get the mean of every group of samples samples..
				//		hence effectively reducing the sampling
				//		frequency to the required frequency
				value = 0.0f;
				for (int k=0; k<MEAN_GROUP_SIZE; ++k) {
					value += rotatedData[iSample+k][dim] - rotatedDataMeans[dim];
				}
				value /= MEAN_GROUP_SIZE;
				
				boolean isInTheUpperBand = false;
				boolean isInTheLowerBand = false;
				
				isInTheUpperBand = (value>AMP_FILTER_LOW);
				isInTheLowerBand = (-value>AMP_FILTER_LOW);
				
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
		
		Log.d(Constants.DEBUG_TAG, "Average Counts: "+Arrays.toString(results));
	}

	/**
	 * Computes the average number of counts per min.
	 * 
	 * Based on the square-root of the square of the area under the
	 * acceleration graph.
	 * 
	 * Note: 
	 * 1) you can't get zero counts using this method, because you
	 * 		 never get the DC component alone from the accelerometer,
	 * 		there are always fluctuations in the signals. 
	 * 2) this method is insensitive to high intensity activities (e.g. jogging)
	 * 
	 * @param len				the number of accelerometer samples in each dimension
	 * @param rotatedData		the accelerometer samples (3 dimensions, after rotation)
	 * @param rotatedDataMeans  the means of the rotated data, in each dimension
	 * @param timeStamps		timestamps of the accelerometer samples
	 * @param results			an array of three doubles to return the average number of
	 * 								zero-crossings per second
	 */
	public void computeCountsIntegration(int len, float[][] rotatedData, float[] rotatedDataMeans, long[] timeStamps, double[] results) {
		//	the duration of the signal (in seconds)
		double timePeriod = ((double)(timeStamps[len-1]-timeStamps[0])) / 1000.0;
		double duration, currentMean, area;
		double prevMean[] = new double[Constants.ACCEL_DIM];
		
//		Log.i(Constants.DEBUG_TAG, Arrays.toString(rotatedDataMeans));
//		for (int i=0; i<len; ++i) {
//			Log.i(Constants.DEBUG_TAG, Arrays.toString(rotatedData[i]));
//		}
		
		for (int dim=0; dim<Constants.ACCEL_DIM; ++dim) {
			results[dim] = 0.0;
			
			//	compute mean of the first data group
			prevMean[dim] = 0.0;
			for (int iSample=0; iSample<MEAN_GROUP_SIZE; ++iSample) {
				prevMean[dim] += rotatedData[iSample][dim];
			}			
			prevMean[dim] /= MEAN_GROUP_SIZE;
			
			//	subtract the DC
			prevMean[dim] -= rotatedDataMeans[dim];
		}
		
		for (int iSample=MEAN_GROUP_SIZE; iSample+MEAN_GROUP_SIZE<len; iSample+=MEAN_GROUP_SIZE) {
			//	duration between the samples
			duration = (timeStamps[iSample]-timeStamps[iSample-1]) / 1000.0;
			
			for (int dim=0; dim<Constants.ACCEL_DIM; ++dim) {
				//	compute area covered between the last sample
				//		and current sample, over the duration
				
				//	get the mean of current data group
				currentMean = 0.0;
				for (int k=0; k<MEAN_GROUP_SIZE; ++k) {
					currentMean += rotatedData[iSample+k][dim];
				}			
				currentMean /= MEAN_GROUP_SIZE;
				
				//	subtract the DC
				currentMean -= rotatedDataMeans[dim];
				
				//	calculate area under current group
				area = (currentMean + prevMean[dim])/2.0 * duration;
				
				//	increase sum by area squared
				results[dim] += area*area;
				
				//	save current mean for next computation
				prevMean[dim] = currentMean;
			}
		}
		
		
		for (int dim=0; dim<Constants.ACCEL_DIM; ++dim) {
			Log.d(Constants.DEBUG_TAG, "sum(area["+dim+"]^2)="+results[dim]);
			
			//	get the square-root of the sum square of the area
			results[dim] = Math.sqrt(results[dim]);
			//	get the counts per second
			results[dim] /= timePeriod;
			//	convert to per minute
			results[dim] *= 60.0;
		}
		
		Log.d(Constants.DEBUG_TAG, "Average Counts: "+Arrays.toString(results));
	}
}
