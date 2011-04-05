package activity.classifier.utils;

import activity.classifier.common.Constants;
import activity.classifier.db.OptionsTable;
import activity.classifier.rpc.ActivityRecorderBinder;
import android.os.RemoteException;
import android.util.Log;

/**
 * Checks for periods where the phone stationary and based on the duration
 * 	  performs calibration and detects when the phone is uncarried.
 * 
 * @author Justin Lee
 * 
 */
public class Calibrator {
	
	//	values to go back to when calibration is cancelled (i.e. stable values)
	private int resetCount;
	private float[] resetSd;
	private float[] resetMean;
	private float resetValueOfGravity;
	
	//	current values being computed, these values might be changing so aren't stable 
	private int count;
	private float[] sd;
	private float[] mean;
	private float valueOfGravity;
	
	private ActivityRecorderBinder service;
	private float allowedMultiplesOfDeviation;
	private boolean isUncarried;
	private boolean isCalibrated;
	
	/**
	 * Holds instances of measurements to check if the phone has moved or not within a certain
	 * period of time
	 */
	private MeasurementsBuffer measurementsBuffer;
	
	/**
	 * Constructs the class
	 * 
	 * @param service
	 * The service this class is being used in
	 * 
	 * @param rawDataStatistics
	 * The instance used to compute various statistical aspects of the raw data
	 * from the accelerometer. The dimensions specified in this instance is
	 * expected to be equal to {@link Constants.ACCEL_DIM} value.
	 * 
	 * @param isCalibrated
	 * Whether or not calibration has occurred.
	 * 
	 * @param resetCount
	 * The number of counts to reset calibration statistical data to. 
	 * 
	 * @param resetSd
	 * The standard deviation to reset calibration statistical data to.
	 * 
	 * @param resetMean
	 * The mean to reset calibration statistical data to.
	 * 
	 * @param resetValueOfGravity
	 * The gravity value to reset calibration statistical data to.
	 */
	public Calibrator(
			ActivityRecorderBinder service,
			boolean isCalibrated,
			float allowedMultiplesOfDeviation,
			int resetCount,
			float[] resetSd,
			float[] resetMean,
			float resetValueOfGravity
			)
	{
		this.service = service;
		this.measurementsBuffer = new MeasurementsBuffer(
				(int)Math.round(
						//	twice the longest duration we expect to cater for
						(double)Math.max(Constants.DURATION_OF_CALIBRATION, Constants.DURATION_WAIT_FOR_UNCARRIED)*2.0 /
						//	divided by the duration of each sample batch
						(double)Constants.DELAY_SAMPLE_BATCH
						)
						+ 5 // have to give extra or else the threads will lock up
				);
		
		setResetValues(isCalibrated, allowedMultiplesOfDeviation, resetCount, resetSd, resetMean, resetValueOfGravity);
	}
	
	public void setResetValues(
			boolean isCalibrated,
			float allowedMultiplesOfDeviation,
			int resetCount,
			float[] resetSd,
			float[] resetMean,
			float resetValueOfGravity
		)
	{
		this.isCalibrated = isCalibrated;
		this.allowedMultiplesOfDeviation = allowedMultiplesOfDeviation;
		
		//	values used to reset respective local values
		//		whenever calibration is cancelled
		this.resetCount = resetCount;
		this.resetSd = resetSd;
		this.resetMean = resetMean;
		this.resetValueOfGravity = resetValueOfGravity;
		
		this.reset();
	}

	/**
	 * Resets the state of the class.
	 * 
	 * Please note that this function doesn't change the state of
	 * calibration, rather it resets the statistical data to their
	 * respective reset values, and clears the measurement buffer. 
	 */
	private void reset()
	{
		this.count = resetCount;
		this.mean = resetSd.clone();
		this.sd = resetSd.clone();
		this.valueOfGravity = resetValueOfGravity;
		
		try {
			//	return all previous gathered measurements
			while (measurementsBuffer.peekFilledInstance()!=null) {
				measurementsBuffer.returnEmptyInstance(measurementsBuffer.takeFilledInstance());
			}
		} catch (InterruptedException e) {
			Log.e(Constants.DEBUG_TAG, "Error while resting calibration class", e);
		}
	}
	
	private void setToCalibratedState() {
		for (int i=0; i<Constants.ACCEL_DIM; ++i) {
			resetMean[i] = mean[i];
			resetSd[i] = sd[i];
		}
		resetCount = count;
		resetValueOfGravity = valueOfGravity;
		isCalibrated = true;
	}
	
	
	/**
	 * @return
	 * The stable mean values
	 */
	public float[] getMean() {
		return resetMean;
	}

	/**
	 * @return
	 * The stable standard deviation values
	 */
	public float[] getSd() {
		return resetSd;
	}

	/**
	 * @return
	 * The stable count
	 */
	public int getCount() {
		return resetCount;
	}

	/**
	 * @return
	 * The stable gravity value
	 */
	public float getValueOfGravity() {
		return resetValueOfGravity;
	}

	public boolean isCalibrated() {
		return isCalibrated;
	}
	
	public boolean isUncarried() {
		return isUncarried;
	}

	/**
	 * Calculate standard deviation by Combining Standard Deviation method.
	 * 
	 * @see <a href="http://en.wikipedia.org/wiki/Standard_deviation">Combining
	 *      Standard Deviation method</a>
	 * 
	 * @param sampleMean
	 *            Acceleration mean values
	 * @param sampleSd
	 *            Acceleration standard deviation values
	 */
	private void doCalibration(float[] sampleMean, float[] sampleSd) {
		this.valueOfGravity = 0.0f;
		
		for (int i = 0; i < Constants.ACCEL_DIM; i++) {
			float newMean, newSd;
			
			newMean = (count*this.mean[i] + sampleMean[i]) / (count+1);
			
			newSd = (float) Math.sqrt(
					(count*sd[i]*sd[i] + sampleSd[i]*sampleSd[i]) / (count+1)
					+
					(count*(mean[i]-sampleMean[i])*(mean[i]-sampleMean[i])) / ((count+1)*(count+1))
					);
			
			this.mean[i] = newMean;
			this.sd[i] = newSd;
			
			this.valueOfGravity += this.mean[i]*this.mean[i];
		}
		
		this.valueOfGravity = (float)Math.sqrt(this.valueOfGravity);
		
		++this.count;
	}
	
	/**
	 * Detects whether there has been any motion between two measurements by checking the
	 * absolute difference between the means of the two measurements, and allowing a certain
	 * amount of difference between them.
	 * 
	 * @param measurement1
	 * First measurement
	 * 
	 * @param measurement2
	 * Second measurement
	 * 
	 * @param baseAllowedMeanDiff
	 * Allowed difference between the measurements
	 * 
	 * @return
	 * true if movement was detected, false if no movement was detected.
	 */
	private boolean hasMovement(Measurement measurement1, Measurement measurement2, float[] baseAllowedMeanDiff, float magnitude)
	{
		boolean movementDetected = false;
		for (int axis=0; axis<Constants.ACCEL_DIM; ++axis) {
			float meanDif = measurement1.axisMean[axis] - measurement2.axisMean[axis];
			if (meanDif<0.0)
				meanDif = -meanDif;
			
			if (meanDif>baseAllowedMeanDiff[axis]*magnitude &&
					meanDif>Constants.CALIBARATION_MIN_ALLOWED_BASE_DEVIATION*magnitude) {
				movementDetected = true;
				break;
			}
		}
		return movementDetected;
	}
	
	/**
	 * Determines if there is any motion in a given measurement, by checking its
	 * standard deviation, and allowing for a certain amount of deviation.
	 * 
	 * @param measurement
	 * The measurement to check
	 * 
	 * @param baseAllowedSdDev
	 * The deviation allowed
	 * 
	 * @return
	 * true if motion was detected, false if no motion was detected.
	 */
	private boolean hasMotion(Measurement measurement, float[] baseAllowedSdDev, float magnitude) {
		boolean motionDetected = false;
		for (int axis=0; axis<Constants.ACCEL_DIM; ++axis) {
			if (measurement.axisSd[axis]>baseAllowedSdDev[axis]*magnitude &&
					measurement.axisSd[axis]>Constants.CALIBARATION_MIN_ALLOWED_BASE_DEVIATION*magnitude
					) {
				Log.v(Constants.DEBUG_TAG, "Motion detected in current measurement, sd["+axis+"]="+measurement.axisSd[axis]+" max="+(baseAllowedSdDev[axis]*magnitude));
				motionDetected = true;
				break;
			}
		}
		if (!motionDetected) {
			Log.v(Constants.DEBUG_TAG, "No motion detected in current measurement");
		}
		return motionDetected;
	}
	
	/**
	 * Removes items from the filled measurement buffer until an
	 * item is met that was sampled before the calibration duration.
	 * 
	 * @param currentSampleTime
	 * The current sample time.
	 * 
	 * @param duration
	 * The duration to check.
	 */
	private void emptyAllBefore(long currentSampleTime, long duration)
	{
		try {
			//	peek at the last item in the queue
			Measurement last = measurementsBuffer.peekFilledInstance();
			while (last!=null) {	//	if an item was found
				//	check if the item is was taken more than [the calibration waiting period] ago 
				if (currentSampleTime-last.time>duration) {
					//	remove item
					measurementsBuffer.returnEmptyInstance(measurementsBuffer.takeFilledInstance());
				} else {
					break;
				}
				//	check the next item
				last = measurementsBuffer.peekFilledInstance();
			}
		} catch (InterruptedException e) {
			Log.e(Constants.DEBUG_TAG, 
					"Exception while attempting to find latest measurement " +
					"taken before the period "+(currentSampleTime/1000)+"s", 
					e);
		}
	}

	/**
	 * Processes the sample data, updating any necessary statistical attributes,
	 * performing calibration, and detecting uncarried states.
	 * 
	 * @param sampleTime
	 * The time the data sample was taken
	 * 
	 * @param data
	 * The data taken in the sampling window
	 *  
	 * @param size
	 * The number of samples taken in the sampling window.
	 * 
	 * @param returnedMeans
	 * The means of the different axi of the data taken in the sampling window.
	 * 
	 * @param returnedSd
	 * The standard deviation of the different axi of the data given 
	 * 
	 * @throws InterruptedException
	 */
	synchronized
	public void processData(long sampleTime, float[] mean, float[] sd) throws InterruptedException
	{
//		Log.v(Constants.DEBUG_TAG, "Calibration/Uncarried process start: total instances="+measurementsBuffer.getTotalSize()+"/"+measurementsBuffer.getCapacity()+", empty="+measurementsBuffer.getEmptySize()+", filled="+measurementsBuffer.getFilledSize());
		try {
			//	current found gravity
			Measurement currGravity = measurementsBuffer.takeEmptyInstance();
	
			//	assign mean and standard deviations to be returned
			for (int i=0; i<Constants.ACCEL_DIM; ++i) {
				currGravity.axisMean[i] = mean[i];
				currGravity.axisSd[i] = sd[i];
			}
			
			//	assign the time to the time the sample was taken
			currGravity.time = sampleTime;
			
			//	this is supposed to be the last item in the buffer that gives a continous
			//		phone stationary period to the current sample. if null, then
			//		none was found.
			Measurement lastGravity = null;
			
			//	check if there is any motion in the current found data
			if (hasMotion(currGravity, resetSd, allowedMultiplesOfDeviation)) {
	//			for (int i=0; i<data.length; ++i) {
	//				String s = "";
	//				for (int d=0; d<data[i].length; ++d)
	//					s += data[i][d] + ",";
	//				Log.v(Constants.DEBUG_TAG, "\t"+s);
	//			}
				
				//	return the current sample as unusable
				measurementsBuffer.returnEmptyInstance(currGravity);
				//	reset data
				reset();
			} else {
				boolean movementDetected = false;
				
				//	get the last item in the buffer
				lastGravity = measurementsBuffer.peekFilledInstance();
				
				// check if there is any movement detected
				while (lastGravity!=null && hasMovement(lastGravity, currGravity, resetSd, allowedMultiplesOfDeviation)) {
					movementDetected = true;
					//	get rid of it
					measurementsBuffer.returnEmptyInstance(measurementsBuffer.takeFilledInstance());
					//	get the next
					lastGravity = measurementsBuffer.peekFilledInstance();
				}
				
				//	return the current sample as a usable sample
				measurementsBuffer.returnFilledInstance(currGravity);
				
				if (movementDetected)
					Log.v(Constants.DEBUG_TAG, "Some movement detected in current measurement");
				else
					Log.v(Constants.DEBUG_TAG, "No movement detected in current measurement");
			}
						
			if (lastGravity!=null) {
				Log.d(Constants.DEBUG_TAG, "Stationary period: "+((sampleTime-lastGravity.time)/1000)+"s");
				
				//	check and perhaps do calibration
				//	is the stationary period more than the required stationary period for calibration
				if (!isCalibrated && sampleTime-lastGravity.time>Constants.DURATION_OF_CALIBRATION) {
					Log.d(Constants.DEBUG_TAG, "Performing calibration.");
					try {
						service.showServiceToast("Performing calibration. Please keep the phone still.");
					} catch (RemoteException e) {
					}
					
					//	cycle through all the measurements and do calibration
					int filledCount = measurementsBuffer.getPendingFilledInstances();
					for (int i=0; i<filledCount; ++i) {
						Measurement temp = measurementsBuffer.takeFilledInstance();
						doCalibration(temp.axisMean, temp.axisSd);
						//	the mean & sd will change after the calibration,
						//		so don't bother to keep the measurements
						measurementsBuffer.returnEmptyInstance(temp);
					}
					
					setToCalibratedState();
				}
				
				//	check uncarried state
				isUncarried = sampleTime-lastGravity.time>Constants.DURATION_WAIT_FOR_UNCARRIED;
				
				//	get rid of extra measurements...
				emptyAllBefore(sampleTime, Math.max(Constants.DURATION_OF_CALIBRATION, Constants.DURATION_WAIT_FOR_UNCARRIED)*2);
				
			} else {
				Log.d(Constants.DEBUG_TAG, "No stationary period found.");
				//	set uncarried state
				isUncarried = false;
			}
		} finally {
			measurementsBuffer.assertAllAvailable();
//			Log.v(Constants.DEBUG_TAG, "Calibration/Uncarried process total instances: "+measurementsBuffer.getTotalSize()+"/"+measurementsBuffer.getCapacity()+", empty="+measurementsBuffer.getEmptySize()+", filled="+measurementsBuffer.getFilledSize());
		}
		
	}
	
	private static class Measurement {
		public long time;
		public final float[] axisMean;
		public final float[] axisSd;
		
		public Measurement(float[] axisMean, float[] axisSd) {
			this.time = 0;
			this.axisMean = axisMean;
			this.axisSd = axisSd;
		}
		
	}
	
	private class MeasurementsBuffer extends TwoWayBlockingQueue<Measurement> {

		public MeasurementsBuffer(int totalBatchCount) {
			super(totalBatchCount);
		}

		@Override
		protected Measurement getNewInstance() {
			return new Measurement(
					new float[Constants.ACCEL_DIM],
					new float[Constants.ACCEL_DIM]
					);
		}
		
	}
	
	/**
	 * A centralised way of reseting calibration values in the options 
	 * {@link OptionsTable}.
	 * 
	 * @param optionsTable
	 */
	public static void resetCalibrationOptions(OptionsTable optionsTable) {
		float[] mean = new float[Constants.ACCEL_DIM];
		float[] sd = new float[Constants.ACCEL_DIM];
		float[] offset = new float[Constants.ACCEL_DIM];
		float[] scale = new float[Constants.ACCEL_DIM];
		
		for (int i=0; i<Constants.ACCEL_DIM; ++i) {
			mean[i] = 0.0f;
			sd[i] = Constants.CALIBARATION_ALLOWED_BASE_DEVIATION;
			offset[i] = 0.0f;
			scale[i] = 1.0f;
		}
		
		optionsTable.setCalibrated(false);
		optionsTable.setValueOfGravity(Constants.GRAVITY);
		optionsTable.setSd(sd);
		optionsTable.setMean(mean);
		optionsTable.setOffset(offset);
		optionsTable.setScale(scale);
		optionsTable.setAllowedMultiplesOfSd(Constants.CALIBARATION_ALLOWED_MULTIPLES_DEVIATION);
		optionsTable.setCount(0);
	}
}
