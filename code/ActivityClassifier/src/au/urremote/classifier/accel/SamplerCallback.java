package au.urremote.classifier.accel;

public interface SamplerCallback {

	public void samplerFinished(SampleBatch batch);
	public void samplerStopped(SampleBatch batch);
	public void samplerError(SampleBatch batch, Exception e);
	
}
