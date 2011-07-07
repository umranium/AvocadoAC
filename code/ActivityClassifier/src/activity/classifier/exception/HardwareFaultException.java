package activity.classifier.exception;

public class HardwareFaultException extends Exception {

	public HardwareFaultException() {
		super();
	}

	public HardwareFaultException(String detailMessage, Throwable throwable) {
		super(detailMessage, throwable);
	}

	public HardwareFaultException(String detailMessage) {
		super(detailMessage);
	}

	public HardwareFaultException(Throwable throwable) {
		super(throwable);
	}

}
