package com.urremote.classifier.fusiontables;

import java.util.Map;

import com.urremote.classifier.rpc.Classification;

public class ColumnExtractor implements DataExtractor<Object> {
	
	String columnName;
	
	public ColumnExtractor(String columnName) {
		this.columnName = columnName;
	}

	public Object extract(Classification classification,
			Map<String, Object> classificationValueMap) {
		Object val = classificationValueMap.get(columnName);
		return val;
	}
	
}