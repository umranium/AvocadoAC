package com.urremote.classifier.fusiontables;

import java.util.Map;

import com.urremote.classifier.rpc.Classification;

public interface DataExtractor<T> {
	T extract(Classification classification, Map<String,Object> classificationValueMap);
}