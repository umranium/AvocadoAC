package activity.classifier.aggregator;

import java.util.Map;
import java.util.Set;

import activity.classifier.common.ActivityNames;
import android.content.Context;

public class HmmAggregator implements IAggregator {
	
	float[][] transitionProbNumerator;
	float[][] transitionProbDenomenator;

	@Override
	public void init(Context context) {
		Set<String> activities = ActivityNames.getAllActivities(context);
		
		int numberOfActivities = activities.size();
		transitionProbNumerator = new float[numberOfActivities][numberOfActivities];
		transitionProbDenomenator = new float[numberOfActivities][numberOfActivities];
	}

	@Override
	public void update(Map<String, Float> activityValues) {
		
		
		
	}

	@Override
	public String getActivity() {
		return null;
	}

}
