package com.urremote.classifier.fusiontables;

public interface DataConverter<T> {
	String fromDbToFusion(T val);
	T fromFusionToDb(String val);
}