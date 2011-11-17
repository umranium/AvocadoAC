package com.urremote.classifier.model;

public class BayesCluster {

	public final String activityName;
	public final String clusterName;
	public final float[] featureMeans;
	public final float[] featureVariances;
	
	public BayesCluster(String activityName, String clusterName,
			float[] featureMeans, float[] featureVariances) {
		this.activityName = activityName;
		this.clusterName = clusterName;
		this.featureMeans = featureMeans;
		this.featureVariances = featureVariances;
	}
	
}
