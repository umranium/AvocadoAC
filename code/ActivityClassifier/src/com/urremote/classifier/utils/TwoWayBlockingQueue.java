package com.urremote.classifier.utils;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 
 * A generic class that is stores items while they wait to be processed (filled),
 * as well as items that aren't ready to be processed (empty). The items are
 * pre-allocated and meant to be reused to avoid construction/destruction overhead
 * and the garbage collection overhead. The take and put functions of each of the
 * two queues are blocking and hence should be used with care not to cause a dead-lock.  
 * Also, an item taken should be returned in order to avoid dead-locks while taking.
 * 
 * @author Umran
 *
 * @param <InstanceType>
 * 	The instance to keep in the queue.
 * 
 */
public abstract class TwoWayBlockingQueue<InstanceType> {
	
	private static final long POLLING_TIMEOUT = 120000;	// 120 seconds
	
	private int capacity;
	private ArrayBlockingQueue<InstanceType> filledInstances;
	private ArrayBlockingQueue<InstanceType> emptyInstances;
	
	public TwoWayBlockingQueue(int capacity) {
		
		this.capacity = capacity;
		this.filledInstances = new ArrayBlockingQueue<InstanceType>(capacity, true);
		this.emptyInstances = new ArrayBlockingQueue<InstanceType>(capacity, true);
		
		for (int i=0; i<capacity; ++i)
			this.emptyInstances.add(getNewInstance());
	}
	
	protected abstract InstanceType getNewInstance();
	
	public int getCapacity() {
		return capacity;
	}
	
	public int getTotalSize() {
		return this.filledInstances.size()+this.emptyInstances.size();
	}
	
	public int getFilledSize() {
		return this.filledInstances.size();
	}
	
	public int getEmptySize() {
		return this.emptyInstances.size();
	}
	
	public InstanceType takeEmptyInstance() throws InterruptedException {
		InstanceType instance = emptyInstances.poll(POLLING_TIMEOUT, TimeUnit.MILLISECONDS);
		if (instance==null)
			throw new InterruptedException("Timeout occurred while retrieving an empty instance. Empty count = "+
					emptyInstances.size()+", filled count = "+filledInstances.size());
		return instance;
	}
	
	public void returnEmptyInstance(InstanceType instance) throws InterruptedException {
		emptyInstances.put(instance);
	}
	
	public InstanceType takeFilledInstance() throws InterruptedException {
		InstanceType instance = filledInstances.poll(POLLING_TIMEOUT, TimeUnit.MILLISECONDS);
		if (instance==null)
			throw new InterruptedException("Timeout occurred while retrieving a filled instance. Empty count = "+
					emptyInstances.size()+", filled count = "+filledInstances.size());
		return instance;
	}
	
	public InstanceType peekFilledInstance() {
		return filledInstances.peek();
	} 
	
	public void returnFilledInstance(InstanceType instance) throws InterruptedException {
		filledInstances.put(instance);
	}
	
	public int getPendingFilledInstances() {
		return filledInstances.size();
	}
	
	public int getPendingEmptyInstances() {
		return emptyInstances.size();
	}
	
	public void assertAllAvailable() {
		int filledInst = filledInstances.size();
		int emptyInst = emptyInstances.size();
		if (filledInst+emptyInst!=capacity) {
			throw new RuntimeException(
					"Non-matching queue counts: filled queue size="+filledInst+
					", empty queue size="+filledInst+
					", total expected size="+capacity);
		}
	}
	
}
