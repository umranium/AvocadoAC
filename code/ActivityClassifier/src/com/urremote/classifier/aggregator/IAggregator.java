package com.urremote.classifier.aggregator;

import java.util.Map;

import android.content.Context;

public interface IAggregator {
	
	void init(Context context);
	void update(Map<String, Float> activityValues);
	String getActivity();

}
