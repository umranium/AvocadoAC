package com.urremote.classifier.utils;

@SuppressWarnings("serial")
public class TimeOutException extends Exception {

	public TimeOutException() {
		super();
	}

	public TimeOutException(String detailMessage, Throwable throwable) {
		super(detailMessage, throwable);
	}

	public TimeOutException(String detailMessage) {
		super(detailMessage);
	}

	public TimeOutException(Throwable throwable) {
		super(throwable);
	}

}
