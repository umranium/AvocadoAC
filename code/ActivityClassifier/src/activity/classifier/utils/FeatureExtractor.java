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

    public static final int NUM_FEATURES = 6;
    
    public static final String FEATURE_NAMES[] = new String[] {
        "HOR RANGE",
        "VER RANGE",
        "HOR MEAN",
        "VER MEAN",
        "HOR STD DEV",
        "VER STD DEV"
    };

    public static final int FEATURE_HOR_RANGE   = 0;
    public static final int FEATURE_VER_RANGE   = 1;
    public static final int FEATURE_HOR_MEAN   	= 2;
    public static final int FEATURE_VER_MEAN   	= 3;
    public static final int FEATURE_HOR_SD   	= 4;
    public static final int FEATURE_VER_SD   	= 5;

    private int windowSize;
    private RotateSamplesToVerticalHorizontal rotate;
    private float[][] samples;
    private float[][] twoDimSamples;
    private CalcStatistics sampleStats;
    private float[] features;

    public FeatureExtractor(int windowSize) {
        this.windowSize = windowSize;

        this.rotate = new RotateSamplesToVerticalHorizontal();
        this.samples = new float[windowSize][3];
        this.twoDimSamples = new float[windowSize][2];
        this.sampleStats = new CalcStatistics(2);
        this.features = new float[NUM_FEATURES];
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

        float[] min = sampleStats.getMin();
        float[] max = sampleStats.getMax();
        float[] mean = sampleStats.getMean();
        float[] sd = sampleStats.getStandardDeviation();
        
		features[FEATURE_HOR_RANGE] = max[0] - min[0];
		features[FEATURE_VER_RANGE] = max[1] - min[1];
		features[FEATURE_HOR_MEAN] = mean[0];
		features[FEATURE_VER_MEAN] = mean[1];
		features[FEATURE_HOR_SD] = sd[0];
		features[FEATURE_VER_SD] = sd[1];
        
        return features;
    }

}
