package au.urremote.classifier.accel;

import au.urremote.classifier.utils.TwoWayBlockingQueue;

/**
 * 
 * @author Umran
 * 
 * <p>
 * This buffer ensures that there is always a fixed number of {@link SampleBatch} instances
 * at any one time. The buffer contains all empty and filled batches waiting to be processed.
 * 
 * <p>
 *  Empty batches are waiting to be filled (by {@link au.urremote.classifier.service.RecorderService}), while
 *  Filled batches are waiting to be processed (by {@link au.urremote.classifier.service.ClassifierService})
 *
 */
public class SampleBatchBuffer extends TwoWayBlockingQueue<SampleBatch> {
	
	public static final int TOTAL_BATCH_COUNT = 20;

	public SampleBatchBuffer() {
		super(TOTAL_BATCH_COUNT);
	}

	@Override
	protected SampleBatch getNewInstance() {
		return new SampleBatch();
	}

}
