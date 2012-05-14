package com.urremote.classifier.fusiontables;

import java.util.Map;

import com.urremote.classifier.rpc.Classification;

public class ColumnExtra {
	
	DataExtractor<Object> dataExtractor;
	DataConverter<Object> dataConverter;
	String dbWriteColumn;
	
	@SuppressWarnings("unchecked")
	public ColumnExtra(DataExtractor<?> dataExtractor, DataConverter<?> dataConverter, String dbWriteColumn) {
		super();
		
		if (dataExtractor==null)
			throw new RuntimeException("Data Extractor unspecified");
		
		this.dataExtractor = (DataExtractor<Object>)dataExtractor;
		this.dataConverter = (DataConverter<Object>)dataConverter;
		this.dbWriteColumn = dbWriteColumn;
	}
	
	public String extractData(Classification classification, Map<String,Object> classificationValueMap) {
		Object extracted = dataExtractor.extract(classification,classificationValueMap);
		if (dataConverter!=null) {
			return dataConverter.fromDbToFusion(extracted);
		} else {
			return extracted.toString();
		}
	}
	
	public void putData(String fusionTableValue, Map<String,Object> outDbValues) {
		if (dbWriteColumn!=null) {
			if (dataConverter!=null)
				outDbValues.put(dbWriteColumn, dataConverter.fromFusionToDb(fusionTableValue));
			else
				outDbValues.put(dbWriteColumn, fusionTableValue);
		}
	}
}