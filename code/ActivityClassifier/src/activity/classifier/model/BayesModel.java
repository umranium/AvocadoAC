package activity.classifier.model;

import activity.classifier.utils.FeatureExtractor;

public class BayesModel {
	
	//	Please don't edit this:
	//**START-MODEL-DETAILS**//

	//	the names of the features contained in the model,
	//		used to make sure that the model and the FeatureExtractor
	//		match
	private static final String[] featureNames = new String[] { 
		"HOR RANGE",
		"VER RANGE",
		"HOR MEAN",
		"VER MEAN",
		"HOR SD",
		"VER SD",
	};

	//	the clusters required for classification
	private static final float POS_INF = Float.POSITIVE_INFINITY;
	private static final float NEG_INF = Float.NEGATIVE_INFINITY;
	private static final BayesCluster[] clusters = new BayesCluster[] {
		new BayesCluster(
			"CLASSIFIED/RUNNING", "CLASSIFIED/RUNNING",
			new float[]{ 17.41190f, 38.27187f,  7.40801f,  8.24138f,  3.62479f,  9.93255f,},
			new float[]{ 15.75436f, 14.08606f,  3.61213f,  2.40616f,  0.59537f,  0.93851f,}
		),
		new BayesCluster(
			"CLASSIFIED/STATIONARY", "CLASSIFIED/STATIONARY",
			new float[]{  0.61493f,  0.81961f,  0.22357f,  9.76362f,  0.12658f,  0.13403f,},
			new float[]{  0.25733f,  0.71528f,  0.03796f,  0.06622f,  0.01142f,  0.01765f,}
		),
		new BayesCluster(
			"CLASSIFIED/WALKING", "CLASSIFIED/WALKING",
			new float[]{  7.16251f, 15.72955f,  2.36616f,  9.98333f,  1.54711f,  3.77088f,},
			new float[]{  5.45455f, 17.39632f,  0.53311f,  0.34493f,  0.21350f,  1.00660f,}
		),
	};
	
	//**END-MODEL-DETAILS**//
	
	
	private static boolean featureCorrectnessChecked = false;
	
	/**
	 * Checks to make sure that the model has the same features
	 * as the FeatureExtractor
	 */
	private static void checkFeatures() {
		//	check to make sure the numbers of features are the same
		if (featureNames.length!=FeatureExtractor.NUM_FEATURES) {
			throw new RuntimeException(
					"Number of features in the model aren't equivalent to " +
					"number of features in the feature extractor ("+
					featureNames.length+"!="+FeatureExtractor.NUM_FEATURES+")");
		}
		
		//	check each feature to make sure each has the same name
		for (int i=0; i<featureNames.length; ++i) {
			if (!featureNames[i].equals(FeatureExtractor.FEATURE_NAMES[i])) {
				throw new RuntimeException(
						"The " + (i+1) +"th features in the model and " +
						"the feature extractor don't match ('" +
						featureNames[i]+"'!='"+FeatureExtractor.FEATURE_NAMES[i]+"')");
			}
		}
		
		//	check each cluster, to make sure each has the required number of features
		for (BayesCluster cluster:clusters) {
			if (cluster.featureMeans.length!=FeatureExtractor.NUM_FEATURES) {
				throw new RuntimeException(
						"The means of the '" + cluster.clusterName + "' cluster " +
						"doesn't have the required number of feature means ('" +
						cluster.featureMeans.length+"'!='"+FeatureExtractor.NUM_FEATURES+"')");
			}
			if (cluster.featureVariances.length!=FeatureExtractor.NUM_FEATURES) {
				throw new RuntimeException(
						"The variances of the '" + cluster.clusterName + "' cluster " +
						"doesn't have the required number of feature variances ('" +
						cluster.featureMeans.length+"'!='"+FeatureExtractor.NUM_FEATURES+"')");
			}
		}
		
		//	finally, and only finally, we set flag that we have checked the features
		featureCorrectnessChecked = true;
	}
	
	public static BayesCluster[] getClusters() {
		if (!featureCorrectnessChecked) {
			checkFeatures(); //	will throw an exception on any failure
		}
		
		return clusters;
	}
	
}
