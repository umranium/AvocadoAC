package com.urremote.classifier.utils;

import java.util.Arrays;

import com.urremote.classifier.common.Constants;

import android.hardware.SensorManager;
import android.util.Log;

/**
 * 
 * An object of class RotateSamplesToVerticalHorizontal will rotate a set 
 * of sampled accelerometer data to vertical axis Z dominant horizontal 
 * axis Y and minor horizontal axis Y.  Numbers are passed in a 3D array 
 * of X,Y,Z. Methods are provided to return: 
 * The rotated array.
 *  
 * @author Ken Taylor
 */

public class RotateSamplesToVerticalHorizontal {
	
	//	derived horizontal vector
	private float[] horizontalVec = new float[Constants.ACCEL_DIM];
	
	//	computed rotation matrix
	private float[] rotationMat = new float[Constants.ACCEL_DIM*Constants.ACCEL_DIM];
	
	//	a temporary vector to hold values while doing matrix multiplication
	private float[] tempVec = new float[Constants.ACCEL_DIM];
	
	/**
	 * Rotates the accelerometer samples to world coordinates,
	 * using a gravity vector derived from the same samples.
	 * 
	 * Note that, a horizontal vector is then derived from the
	 * gravity vector, which is used to rotate the samples to the
	 * world coordinates. This makes the final samples' direction-less
	 * and hence only the magnitude of the horizontal component should
	 * be used.
	 * 
	 * Use {@link #rotateToWorldCoordinates(float[], float[])}
	 * in order to keep the direction of the horizontal component
	 * of the sampled vectors relative to magnetic north.
	 * 
	 * @param samples
	 * The samples to convert to world coordinates. The array will
	 * contain the world coordinates upon return. Unless the
	 * function returns false, which means that the samples haven't
	 * been altered. 
	 * 
	 * @param gravityVec
	 *	This array (which should be created using the function {@link #createVector()})
	 *	provides the gravity vector, which should be an array of
	 *	size {@link Constants.ACCEL_DIM}, with each value containing
	 *	the mean of it's respective dimension in the samples.
	 *	i.e. value 0 should contain the mean of samples[0..N][0],
	 *		value 1 should contain the mean of samples[0..N][1], etc. 
	 * 
	 * @return
	 * Returns false if the function is unable to compute the rotation
	 * matrix and hence unable to change the samples to world coordinates.
	 */
	public synchronized boolean rotateToWorldCoordinates(float[] gravityVec, float[][] samples, int numSamples)
	{
		convertToHorVec(gravityVec, horizontalVec);
		
		return internRotateToWorldCoordinates(samples, numSamples, gravityVec, horizontalVec);
	}
		
	private boolean internRotateToWorldCoordinates(float[][] samples, int numSamples, float[] gravityVec, float[] horVec)
	{
		Log.v(Constants.TAG, "gravity="+Arrays.toString(gravityVec)+", hor="+Arrays.toString(horVec));
		if (!SensorManager.getRotationMatrix(rotationMat, null, gravityVec, horVec)) {
			//	sometimes fails, according to the api
			return false;
		}
		
		//	apply to current samples
		applyRotation(samples,numSamples);
		
		return true;
	}
	
	
	/**
	 * Applies the current rotation matrix to the samples
	 * 
	 * @param samples
	 * samples to apply the current rotation matrix to
	 * 
	 * rotation matrix:
	 * [ 0 ][ 1 ][ 2 ]
	 * [ 3 ][ 4 ][ 5 ]
	 * [ 6 ][ 7 ][ 8 ]
	 *
	 */
	private void applyRotation(float[][] samples, int numSamples)
	{
		for (int s=0; s<numSamples; ++s) {
			for (int d=0; d<Constants.ACCEL_DIM; ++d) {
				tempVec[d] = 0.0f;
				
				for (int k=0; k<Constants.ACCEL_DIM; ++k) {
					tempVec[d] += rotationMat[(d*3)+k] * samples[s][k];
				}
			}
			
			for (int d=0; d<Constants.ACCEL_DIM; ++d) {
				samples[s][d] = tempVec[d];
			}
		}
	}
	
	private static float[] normalizedGravity = new float[Constants.ACCEL_DIM];
	
	/**
	 * 
	 * Source: <a>http://stackoverflow.com/questions/2096474/given-a-surface-normal-find-rotation-for-3d-plane</a>
	 * 
	 * @param inGravityVec
	 * the gravity vector(3 dimensions) to convert to a horizontal vector
	 * 
	 * @param outVec
	 * an array of 3 floats to save the final horizontal vector in
	 */
	synchronized
	private static void convertToHorVec(float[] inGravityVec, float[] outVec)
	{
		for (int i=0; i<Constants.ACCEL_DIM; ++i) {
			normalizedGravity[i] = inGravityVec[i];
			outVec[i] = 0.0f;
		}
		
		//	normalize gravity vector
		CalcStatistics.normalize(Constants.ACCEL_DIM, normalizedGravity);
		
		//	get the minimum axis
        int imin = 0;
		for (int i=0; i<Constants.ACCEL_DIM; ++i)
            if(normalizedGravity[i] < normalizedGravity[imin])
                imin = i;

        //	get the scaling value
        float dt = normalizedGravity[imin];

        //	subtract the scaled value from the unit vector of the minimum axis
        outVec[imin] = 1;
		for (int i=0; i<Constants.ACCEL_DIM; ++i)
        	outVec[i] -= dt*normalizedGravity[i];

        // now normalize the vector being returned
        CalcStatistics.normalize(Constants.ACCEL_DIM, outVec);
	}
	
	/**
	 * 
	 * Returns the index of the smallest value in the vector
	 * 
	 * @param vector
	 * The 3 dimensional vector to find the minimum value
	 * 
	 * @return
	 * The index of the minimum value in the vector
	 */
	private static int findIndexOfSmallest(float[] vector) {
		int index = -1;
		float value = Float.MAX_VALUE;
		float temp;
		for (int d=0; d<Constants.ACCEL_DIM; ++d) {
			temp = vector[d];
			if (temp<0.0f)
				temp = -temp;
			
			if (temp<value) {
				value = temp;
				index = d;
			}
		}
		return index;
	}
	
	/**
	 * 
	 * Returns the index of the largest value in the vector
	 * 
	 * @param vector
	 * The 3 dimensional vector to find the minimum value
	 * 
	 * @return
	 * The index of the minimum value in the vector
	 */
	private static int findIndexOfLargest(float[] vector) {
		int index = -1;
		float value = Float.NEGATIVE_INFINITY;
		float temp;
		for (int d=0; d<Constants.ACCEL_DIM; ++d) {
			temp = vector[d];
			if (temp<0.0f)
				temp = -temp;
			
			if (temp>value) {
				value = temp;
				index = d;
			}
		}
		return index;
	}
	
	private static String vec2str(float[] vec) {
		return String.format("{x=% 3.2f, y=% 3.2f, z=% 3.2f}", vec[Constants.ACCEL_X_AXIS], vec[Constants.ACCEL_Y_AXIS], vec[Constants.ACCEL_Z_AXIS]);
	}

	@SuppressWarnings("unused")
	private static String vec2str(float[] vec, int start) {
		return String.format("{x=% 3.2f, y=% 3.2f, z=% 3.2f}", vec[start+Constants.ACCEL_X_AXIS], vec[start+Constants.ACCEL_Y_AXIS], vec[start+Constants.ACCEL_Z_AXIS]);
	}
	
}
