package com.urremote.classifier.utils;

import com.urremote.classifier.common.Constants;

import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

public abstract class TimedEvent implements Runnable {
	
	//	if we attempt to adjust too fast, the function might never converge
	private static final double TIME_ADJUSTMENT_COEFF = 0.25;
	
	private Handler handler;
	
	private boolean isRunning;
	
	private long startUpTime = 0;
	private long startUtcTime = 0;
	
	private long procStartUpTime = 0;
	private int adjustment;
	private int interCycleDelay = 100;
	private int adjustedInterCycleDelay = interCycleDelay;
	private long requiredNextScheduledTime = 0;
	private long actualNextScheduledTime = 0;
	private int cyclesSkipped;
		
	public TimedEvent(Handler handler) {
		this.handler = handler;
	}
	
	public Handler getHandler() {
		return handler;
	}
	
	public boolean isRunning() {
		return isRunning;
	}

	public long getStartUpTime() {
		return startUpTime;
	}

	public long getStartUtcTime() {
		return startUtcTime;
	}

	protected long getCurrentTime() {
		return procStartUpTime;
	}

	public void start(int initialDelay, int interCycleDelay) {
		if (this.isRunning) {
			stop();
		}
		this.interCycleDelay = this.adjustedInterCycleDelay = interCycleDelay;
		this.isRunning = true;
		this.startUtcTime = System.currentTimeMillis();
		this.startUpTime = requiredNextScheduledTime = SystemClock.uptimeMillis() + initialDelay;
		handler.postAtTime(this, requiredNextScheduledTime);
		
		onStart(SystemClock.uptimeMillis());
	}
	
	public void stop() {
		if (this.isRunning) {
			handler.removeCallbacks(this);		
			this.isRunning = false;
			onEnd(SystemClock.uptimeMillis());
		}
	}
	
	//	function called when the timed event is started, and before the onEvent starts.
	//		use this to initialise.
	protected abstract void onStart(long currentTime);
	
	//	be careful to do as little as possible here,
	//		other-wise increase your inter-sample delay.
	protected abstract void onEvent(long currentTime);
	
	//	called after the last onEvent
	//		use this to destroy any initialized resources.
	protected abstract void onEnd(long currentTime);
	
	
	public void run() {
		if (!isRunning) return;
		
		this.procStartUpTime = SystemClock.uptimeMillis();
		
		onEvent(procStartUpTime);
		
		adjustment = (int)((procStartUpTime - requiredNextScheduledTime)*TIME_ADJUSTMENT_COEFF);
		adjustedInterCycleDelay -= adjustment;
		actualNextScheduledTime = procStartUpTime + adjustedInterCycleDelay - adjustment;
		requiredNextScheduledTime += interCycleDelay;
		//	to avoid problems, if the next scheduled time has actually passed,
		//		skip the next cycle, and move to the next cycle.
		cyclesSkipped = 0; 
		while (actualNextScheduledTime<SystemClock.uptimeMillis()) {
			actualNextScheduledTime += interCycleDelay;
			requiredNextScheduledTime += interCycleDelay;
			++cyclesSkipped;
		}
		if (cyclesSkipped>0) {
			Log.d(Constants.TAG, cyclesSkipped+" cycles skipped. Consider optimizing your code, or increasing the inter-cycle delay.");
		}
		handler.postAtTime(this, actualNextScheduledTime);
	}
	

}
