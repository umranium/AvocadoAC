/*
 * Copyright (c) 2009-2010 Chris Smith
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package au.urremote.classifier.classifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import android.util.Log;
import au.urremote.classifier.common.ActivityNames;
import au.urremote.classifier.common.Constants;
import au.urremote.classifier.common.StringComparator;
import au.urremote.classifier.utils.CalcStatistics;
import au.urremote.classifier.utils.FeatureExtractor;

/**
 * Extracts basic features and applies a K-Nearest Network algorithm to an
 * array of data in order to determine the classification. The data consists
 * of two interleaved data sets, and each set has two features extracted -
 * the range and the mean.
 * 
 * @author chris
 */
public class KnnClassifier implements Classifier {
	
    private Set<Map.Entry<Float[], Object[]>> model;
	
	private int kValue;
    
    //	the distances of each of the samples found in the model to the newly sampled data
    private ClassificationDist[] distances;
    //	a comparator that compares between two ClassificationDist instances
    private ClassificationDistComparator distanceComparator = new ClassificationDistComparator();
    //	used to obtain the counts of different activities within the K-nearest neighbours in the KNN
    private Map<String,Integer> activityCounts = new TreeMap<String,Integer>(new StringComparator(false));
    
    /**
     * Set the clustered data set for classification.
     * @param model clustered data set
     */
    public KnnClassifier(int kValue) {
		this.kValue = kValue;
    }

	public void setModel(final Set<Entry<Float[], Object[]>> model) {
		//	statistics about each feature selected
		Map<String,CalcStatistics> activityFeatureStatistics;

		activityFeatureStatistics = new TreeMap<String,CalcStatistics>(new StringComparator(false));
		
		int numOfFeatures = Integer.MAX_VALUE;
		
		for (Entry<Float[],Object[]> entry:model) {
			if (entry.getKey().length<numOfFeatures) {
				numOfFeatures = entry.getKey().length;
			}
		}
		
		if (numOfFeatures==0 || numOfFeatures==Integer.MAX_VALUE)
			throw new RuntimeException("Unable to determine number of features, either no features, or no data");

		for (Entry<Float[], Object[]> sample:model) {
			String activity = (String)sample.getValue()[0];
			if (!activityFeatureStatistics.containsKey(activity)) {
				activityFeatureStatistics.put(activity, new CalcStatistics(numOfFeatures));
			}
		}

		for (String activity:activityFeatureStatistics.keySet()) {
			CalcStatistics features = activityFeatureStatistics.get(activity);

			ArrayList<float[]> featureData = new ArrayList<float[]>(model.size());
			for (Entry<Float[], Object[]> sample:model) {
				String activityName = (String)sample.getValue()[0];
				if (activity.equalsIgnoreCase(activityName)) {
					Float[] origSampleData = sample.getKey();
					float[] sampleData = new float[origSampleData.length];
					for (int i=0; i<origSampleData.length; ++i)
						sampleData[i] = origSampleData[i];
					featureData.add(sampleData);
				}
			}

			features.assign(featureData.toArray(new float[featureData.size()][]), featureData.size());
		}

        this.model = model;
        
        this.distances = new ClassificationDist[model.size()];
        for (int i=0; i<model.size(); ++i)
        	this.distances[i] = new ClassificationDist();
	}
	
	

    /* (non-Javadoc)
	 * @see au.urremote.classifier.classifier.Classifier#classifyRotated(float[][])
	 */
	synchronized
    public String classify(final float[] extractedData) {
    	return internClassify(extractedData);
    }
    
    private String internClassify(float[] features) {
    	float temp;
    	
        /*
         *  Compare between the points from the sample data and the points from the clustered data set.
         *  Get the closest points in the clustered data set, and classify the activity.
         */
        //	TODO: This doesn't have to be iterative (linear). It can be optimized using windows
        //			to cut out most of the checking.
    	
    	{	// compute distances
	        int i = 0;
	        
	        for (Map.Entry<Float[], Object[]> entry : model) {
	        	Object[] values = entry.getValue();
	        	String activity = (String)values[0];
	        	Integer identity = (Integer)values[1];
	        	Float[] activityFeatures = entry.getKey();
	        	
	            float distance = 0;
	
	            for (int f = 0; f < features.length; f++) {
					temp = features[f] - activityFeatures[f];
					distance += temp*temp;
				}
	            
	            distances[i].classification = activity;
	            distances[i].distance = distance;
	            distances[i].entry = entry;
	            ++i;
	        }
    	}
    	
    	// sort distances
        Arrays.sort(distances, distanceComparator);
        activityCounts.clear();

        String bestActivity = "UNKNOWN";
        int bestCount = 0;
        
        for (int l = model.size(), i=0; i<kValue && i<l; ++i) {
        	Integer count = activityCounts.get(distances[i].classification);
        	if (count==null)
        		count = 0;
        	count = count + 1;
        	activityCounts.put(distances[i].classification, count);
        	
        	if (count>bestCount) {
        		bestCount = count;
        		bestActivity = distances[i].classification;
        	}
        }
        

        return bestActivity;
    }
    
    private static class ClassificationDist {
    	String classification;
    	float distance;
    	Map.Entry<Float[], Object[]> entry;
    	
		public ClassificationDist() {
			this.classification = "";
			this.distance = 0.0f;
			this.entry = null;
		}

    }
    
    private static class ClassificationDistComparator implements Comparator<ClassificationDist> {

		@Override
		public int compare(ClassificationDist arg0, ClassificationDist arg1) {
			return Double.compare(arg0.distance, arg1.distance);
		}
    	
    }
}
