package com.urremote.classifier.aggregator;

import java.util.Map;
import java.util.Set;

import com.urremote.classifier.common.ActivityNames;

import android.content.Context;

public class HmmAggregator implements IAggregator {
	
	float[][] transitionProbNumerator;
	float[][] transitionProbDenomenator;

	public void init(Context context) {
		Set<String> activities = ActivityNames.getAllActivities(context);
		
		int numberOfActivities = activities.size();
		transitionProbNumerator = new float[numberOfActivities][numberOfActivities];
		transitionProbDenomenator = new float[numberOfActivities][numberOfActivities];
	}

	public void update(Map<String, Float> activityValues) {
		
		
		
	}

	public String getActivity() {
		return null;
	}

}
