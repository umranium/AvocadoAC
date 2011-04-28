/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package activity.classifier.utils;

import activity.classifier.common.Constants;
import android.util.Log;

/**
 *	Extracts features from a given sample window.
 *	The window could be already rotated to world-orientation.
 *	This class abstracts which features of the sampled data
 *	is extracted and later provided to the classifier.
 *
 *
 * @author Umran
 */
public class FeatureExtractor {

    public static final int NUM_FEATURES = 8;
    
    public static final String FEATURE_NAMES[] = new String[] {
        "HOR RANGE",
        "VER RANGE",
        "HOR MEAN",
        "VER MEAN",
        "HOR STD DEV",
        "VER STD DEV",
        "HOR MAX FAMP",//Maximum frequency amplitude value for horizontal axis
        "VER MAX FAMP" //Maximum frequency amplitude value for vertical axis
    };

    public static final int FEATURE_HOR_RANGE   = 0;
    public static final int FEATURE_VER_RANGE   = 1;
    public static final int FEATURE_HOR_MEAN   	= 2;
    public static final int FEATURE_VER_MEAN   	= 3;
    public static final int FEATURE_HOR_SD   	= 4;
    public static final int FEATURE_VER_SD   	= 5;
    public static final int FEATURE_HOR_MFA   	= 6;//Maximum frequency amplitude value for horizontal axis
    public static final int FEATURE_VER_MFA   	= 7;//Maximum frequency amplitude value for vertical axis

    private int windowSize;
    private RotateSamplesToVerticalHorizontal rotate;
    private float[][] samples;
    private float[][] twoDimSamples;
    private CalcStatistics sampleStats;
    private float[] features;
    
    private float[][] freqABSValues;
    private CalcStatistics fftStats;
    private FFTLib fftObj;

    
    

    public FeatureExtractor(int windowSize) {
        this.windowSize = windowSize;

        this.rotate = new RotateSamplesToVerticalHorizontal();
        this.samples = new float[windowSize][3];
        this.twoDimSamples = new float[windowSize][2];
        this.sampleStats = new CalcStatistics(2);
        this.features = new float[NUM_FEATURES];

        // FFT calculations:
        this.freqABSValues = new float[2][windowSize];
        this.fftStats = new CalcStatistics(2);
        this.fftObj = new FFTLib(windowSize);

    }

    synchronized
    public float[] extractRotated(float[][] input, int windowStart)
    {
        if (windowStart+windowSize>input.length) {
        	Log.w(Constants.DEBUG_TAG, "attempting to extract features past " +
                    "the end of samples (windowStart="+windowStart+", size="+samples.length+")");
            return null;
        }

        for (int j=0; j<windowSize; ++j) {
            twoDimSamples[j][0] = (float)Math.sqrt(
                    input[j][0]*input[j][0] +
                    input[j][1]*input[j][1]);
            twoDimSamples[j][1] = input[j][2];
        }

        return internExtract();
    }

    private float[] internExtract() {
        sampleStats.assign(twoDimSamples, windowSize);
        float[][] sampleTrasposed = transpose(twoDimSamples);

        float[] min = sampleStats.getMin();
        float[] max = sampleStats.getMax();
        float[] mean = sampleStats.getMean();
        float[] sd = sampleStats.getStandardDeviation();
        
        
        if (Constants.USE_FFT) {
	        //FFT calculations
	        float[] imagenary = new float[windowSize];
	        freqABSValues[0] = fftObj.fft(sampleTrasposed[0],imagenary);
	        imagenary = new float[windowSize];
	        freqABSValues[1] = fftObj.fft(sampleTrasposed[1],imagenary);
	        fftStats.assign(transpose(freqABSValues),windowSize);
        }


        features[FEATURE_HOR_RANGE] = max[0] - min[0];
		features[FEATURE_VER_RANGE] = max[1] - min[1];
		features[FEATURE_HOR_MEAN] = mean[0];
		features[FEATURE_VER_MEAN] = mean[1];
		features[FEATURE_HOR_SD] = sd[0];
		features[FEATURE_VER_SD] = sd[1];
		
		if (Constants.USE_FFT) {
			// The difference between MAX amp of freqs and the mean of the freqs ...
	        features[FEATURE_HOR_MFA] = fftStats.getMax()[0] - fftStats.getMean()[0]; 
	        features[FEATURE_VER_MFA] = fftStats.getMax()[1] - fftStats.getMean()[1];
		} else {
			Log.i(Constants.DEBUG_TAG, "FFT Ignored in Feature Extractor");
			features[FEATURE_HOR_MFA] = 0.0f;
			features[FEATURE_VER_MFA] = 0.0f;
		}

        
        return features;
    }

    public static float[][] transpose(float [][] a) {
        int r = a.length;
        int c = a[r-1].length;
        float[][] t = new float[c][r];
        for(int i = 0; i < r; ++i) {
            for(int j = 0; j < c; ++j) {
                t[j][i] = a[i][j];
            }
        }
        return t;
    }

}
