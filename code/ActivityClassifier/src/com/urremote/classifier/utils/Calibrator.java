package com.urremote.classifier.utils;

import com.urremote.classifier.common.Constants;
import com.urremote.classifier.db.OptionsTable;
import com.urremote.classifier.service.threads.ClassifierThread;

import com.urremote.classifier.rpc.ActivityRecorderBinder;
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
	public static OptionsTable optionsTable;



	/*
	 * XYCALIBRATION is the state where the phone is flat and 
	 * sd for x,y,z and mean/offset for x and y are calculated.
	 * 
	 *  ZCALIBRATION is the state where the phone is vertical
	 *  and z mean/offset is calculated.
	 */
	enum OrientationState {XYCALIBRATION,ZCALIBRATION};

	//	current values being computed, these values might be changing so aren't stable 
	private int count;
	private float[] sd;
	private float[] mean;
	private float valueOfGravity;

	private ActivityRecorderBinder service;
	private float allowedMultiplesOfDeviation;
	private boolean isUncarried;

	public static boolean isCalibrated;
	private OrientationState calibrationState;
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

	private void setToCalibratedStateBasedOnOrientation(OrientationState state)
	{
		switch (state) {
		case XYCALIBRATION:
			for (int i = 0; i < Constants.ACCEL_DIM-1; i++) {
				resetMean[i] = mean[i];
				resetSd[i] = sd[i];
			}
			resetSd[Constants.ACCEL_Z_AXIS] = sd[Constants.ACCEL_Z_AXIS];
			break;

		case ZCALIBRATION:
			resetMean[Constants.ACCEL_Z_AXIS] = mean[Constants.ACCEL_Z_AXIS];
			break;

		default:
			break;
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
	 * Computes the mean and the standard deviation given the sum of x square and
	 * the sum of x
	 * 
	 * @see <a href="http://en.wikipedia.org/wiki/Standard_deviation#Identities_and_mathematical_properties">Combining
	 *      Standard Deviation: Identities and mathematical properties</a>
	 * 
	 * @param sumOfX
	 *            the sum of the samples for the 3 axis
	 * @param sumOfXSqr
	 *            the sum of the square of samples for the 3 axis
	 * @param count
	 *            the total number of samples used to obtain the sum and sum square
	 */
	private void PerformeCalibration(float[] sumOfX, float[] sumOfXSqr,OrientationState state, int count) {
		switch (state) {
		case XYCALIBRATION:
			this.valueOfGravity = 0.0f;
			for (int i = 0; i < Constants.ACCEL_DIM; i++) {

				this.mean[i] = sumOfX[i] / count;

				this.sd[i] = (float)Math.sqrt( Math.abs(sumOfXSqr[i]/count - this.mean[i]*this.mean[i]));

				//	just in case...
				if (Float.isNaN(this.sd[i])) {
					this.sd[i] = 0.0f;
				}
				this.valueOfGravity += this.mean[i]*this.mean[i];
			}
			//reseting z mean since it is XY calibration.
			this.mean[Constants.ACCEL_Z_AXIS]  = this.resetMean[Constants.ACCEL_Z_AXIS];
			//this.sd[Constants.ACCEL_Z_AXIS] = (float)Math.sqrt( Math.abs(sumOfXSqr[Constants.ACCEL_Z_AXIS]/count 
			//		- Math.pow(this.mean[Constants.ACCEL_Z_AXIS],2)));

			//TODO: This was wrong. Should be done by the collected values of Z in flat orientation 
			// that is in the for loop.
			//this.valueOfGravity += this.mean[Constants.ACCEL_Z_AXIS]*this.mean[Constants.ACCEL_Z_AXIS];
			this.valueOfGravity = (float)Math.sqrt(this.valueOfGravity);
			break;

		case ZCALIBRATION:
			this.mean[Constants.ACCEL_Z_AXIS] = sumOfX[Constants.ACCEL_Z_AXIS] / count;
			/*this.sd[Constants.ACCEL_Z_AXIS] = 0;/*(float)Math.sqrt( Math.abs(sumOfXSqr[Constants.ACCEL_Z_AXIS]/count 
					- Math.pow(this.mean[Constants.ACCEL_Z_AXIS],2)));*/

			break;

		default:
			break;
		}


		this.count = count;
	}

	/**
	 * Computes the mean and the standard deviation given the sum of x square and
	 * the sum of x
	 * 
	 * @see <a href="http://en.wikipedia.org/wiki/Standard_deviation#Identities_and_mathematical_properties">Combining
	 *      Standard Deviation: Identities and mathematical properties</a>
	 * 
	 * @param sumOfX
	 *            the sum of the samples for the 3 axis
	 * @param sumOfXSqr
	 *            the sum of the square of samples for the 3 axis
	 * @param count
	 *            the total number of samples used to obtain the sum and sum square
	 */
	private void doCalibration(float[] sumOfX, float[] sumOfXSqr, int count) {
		this.valueOfGravity = 0.0f;

		for (int i = 0; i < Constants.ACCEL_DIM; i++) {

			this.mean[i] = sumOfX[i] / count;
			this.sd[i] = (float)Math.sqrt( sumOfXSqr[i]/count - this.mean[i]*this.mean[i]);

			//	just in case...
			if (Float.isNaN(this.sd[i])) {
				this.sd[i] = 0.0f;
			}

			this.valueOfGravity += this.mean[i]*this.mean[i];
		}

		this.valueOfGravity = (float)Math.sqrt(this.valueOfGravity);
		this.count = count;
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
	 * @return
	 * true if movement was detected, false if no movement was detected.
	 */
	private boolean hasMovement(Measurement measurement1, Measurement measurement2)
	{
		boolean movementDetected = false;
		for (int axis=0; axis<Constants.ACCEL_DIM; ++axis) {
			float meanDif = measurement1.axisMean[axis] - measurement2.axisMean[axis];
			if (meanDif<0.0)
				meanDif = -meanDif;

			if (meanDif>resetSd[axis]*allowedMultiplesOfDeviation &&
					meanDif>Constants.CALIBARATION_MIN_ALLOWED_BASE_DEVIATION*allowedMultiplesOfDeviation) {
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
	 * @return
	 * true if motion was detected, false if no motion was detected.
	 */
	private boolean hasMotion(Measurement measurement) {
		boolean motionDetected = false;
		for (int axis=0; axis<Constants.ACCEL_DIM; ++axis) {
			if (measurement.axisSd[axis]>resetSd[axis]*allowedMultiplesOfDeviation &&
					measurement.axisSd[axis]>Constants.CALIBARATION_MIN_ALLOWED_BASE_DEVIATION*allowedMultiplesOfDeviation
			) {
				Log.v(Constants.DEBUG_TAG, "Motion detected in current measurement, sd["+axis+"]="+measurement.axisSd[axis]+" max="+(resetSd[axis]*allowedMultiplesOfDeviation));
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

	private void emptyAllBeforeTheTimeStamp(long lTimeStamp)
	{
		try {
			//	peek at the last item in the queue
			Measurement last = measurementsBuffer.peekFilledInstance();
			while (last!=null) {	//	if an item was found
				//	check if the item is was taken in the past
				if (lTimeStamp-last.time>=0) {
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
					"taken before the period "+(lTimeStamp/1000)+"s", 
					e);
		}
	}
	private void ToastPhoneOrientation() throws RemoteException
	{
		switch (calibrationState) {
		case XYCALIBRATION:
			service.showServiceToast("Phone is in flat orientation. XY calibration finished.");
			break;

		default:
			service.showServiceToast("Phone is in vertical orientation. Z calibration finished.");
			break;
		}
	}
	public int GetLastOrientation()
	{
		switch (calibrationState) {
		case XYCALIBRATION:
			return 0;

		case ZCALIBRATION:
			return 1;

		default:
			return -1;
		}
	}

	public static int CalibrationAttempts = 0;
	synchronized
	public void MainForceCalibrationProcess(long sampleTime, float[] mean, float[] sd, float[] sum, float[] sumSqr, int count) throws InterruptedException, RemoteException
	{
		if(ClassifierThread.bForceCalibration)
		{
			Measurement currGravity = UpdateGravity(sampleTime,mean,sd,sum,sumSqr,count);
			//resetCalibrationOptionsBasedOnOrientation(calibrationState);
			//		if(CalibrationAttempts>3)
			//		{
			//			ClassifierThread.bForceCalibration = false;
			//			service.showServiceToast("Calibration failed due to phone not being still.");
			//			//	reset data
			//			reset();
			//
			//		}	

			//		if (hasMotion(currGravity)) {
			//
			//			//CalibrationAttempts++;
			//			service.showServiceToast(/*"Attempt " + Integer.toString(CalibrationAttempts) +*/ "Calibration is failed. Please hold the phone still.");
			//			//	return the current sample as unusable
			//			measurementsBuffer.returnEmptyInstance(currGravity);
			//			ClassifierThread.bForceCalibration = false;
			//
			//			////	reset data
			//			//reset();
			//		}
			//		else 
			//		{
			Measurement lastGravity = UpdateSecondSampleBatch(currGravity);
			//		}

			if(lastGravity != null)
			{//	get the mean and sd from the sum, sum square, and count based on phone orientation.
				calibrationState = PhoneOriantation(lastGravity);
				ToastPhoneOrientation();

				float sSum[] = new float[Constants.ACCEL_DIM];
				float sSumSqr[] = new float[Constants.ACCEL_DIM];
				int sCount = 0;

				sCount = CalculateMeanForASample(sSum,sSumSqr,lastGravity);

				PerformeCalibration(sSum, sSumSqr, calibrationState, count);

				setToCalibratedStateBasedOnOrientation(calibrationState);

				ClassifierThread.bForceCalibration = false;

				//	get rid of extra measurements...
				emptyAllBeforeTheTimeStamp(sampleTime);

			}
		}


	}
	/*
	 * Identifies the orientation of the phone for calibration.
	 */
	private OrientationState PhoneOriantation(Measurement gravity)
	{
		if (gravity.axisMean[Constants.ACCEL_Z_AXIS] < Constants.GRAVITY/2 && gravity.axisMean[Constants.ACCEL_Z_AXIS] > -Constants.GRAVITY/2)
			return OrientationState.ZCALIBRATION;

		return OrientationState.XYCALIBRATION;
	}

	private Measurement UpdateGravity(long sampleTime, float[] mean, float[] sd, float[] sum, float[] sumSqr, int count) throws InterruptedException
	{
		//	get rid of it

		//emptyAllBefore(sampleTime, Math.max(Constants.DURATION_OF_CALIBRATION, Constants.DURATION_WAIT_FOR_UNCARRIED)*2);

		Measurement Gravity = measurementsBuffer.takeEmptyInstance();

		//	assign mean and standard deviations to be returned
		for (int i = 0; i<Constants.ACCEL_DIM; ++i) {
			Gravity.sum[i] = sum[i];
			Gravity.sumSqr[i] = sumSqr[i];
			Gravity.axisSd[i] = sd[i];
			Gravity.axisMean[i] = mean[i];
			Gravity.count = count;
		}

		//	assign the time to the time the sample was taken
		Gravity.time = sampleTime;
		return Gravity;

	}
	private Measurement UpdateSecondSampleBatch(Measurement currGravity) throws InterruptedException
	{
		//	get the last item in the buffer
		Measurement lastGravity = measurementsBuffer.peekFilledInstance();

		// check if there is any movement detected
		while (lastGravity!=null && (hasMovement(lastGravity, currGravity) /* || !ClassifierThread.bForceCalibration*/)) {
			//	get rid of it
			measurementsBuffer.returnEmptyInstance(measurementsBuffer.takeFilledInstance());
			//	get the next
			lastGravity = measurementsBuffer.peekFilledInstance();
		}

		//	return the current sample as a usable sample
		measurementsBuffer.returnFilledInstance(currGravity);
		return lastGravity;

	}

	private Measurement UpdateWithTheLatestSampleBatch(Measurement currMeasurement) throws InterruptedException
	{
		Measurement lastGravity = measurementsBuffer.peekFilledInstance();

		// check if there is any movement detected
		while (lastGravity!=null && currMeasurement.time > lastGravity.time /* || !ClassifierThread.bForceCalibration*/)
		{
			//	get rid of it
			measurementsBuffer.returnEmptyInstance(measurementsBuffer.takeFilledInstance());
			//	get the next
			lastGravity = measurementsBuffer.peekFilledInstance();
		}

		//	return the latest sample as a usable sample
		measurementsBuffer.returnFilledInstance(currMeasurement);
		return lastGravity;

	}
	private int CalculateMeanForASample(float[] sSum, float[] sSumSqr,Measurement sample)
	{
		for (int d=0; d<Constants.ACCEL_DIM; ++d) {
			sSum[d] = sample.sum[d];
			sSumSqr[d] = sample.sumSqr[d];
		}

		return sample.count;


	}

	private int CalculateMeanForCollectedSamples(float[] sSum, float[] sSumSqr) throws InterruptedException
	{

		int sCount = 0;

		//	cycle through all the measurements and do calibration
		int filledCount = measurementsBuffer.getPendingFilledInstances();
		for (int i=0; i<filledCount; ++i) {
			Measurement temp = measurementsBuffer.takeFilledInstance();
			//	add to sum, sum square, and count
			for (int d=0; d<Constants.ACCEL_DIM; ++d) {
				sSum[d] += temp.sum[d];
				sSumSqr[d] += temp.sumSqr[d];
			}
			sCount += temp.count;
			measurementsBuffer.returnEmptyInstance(temp);
		}
		return sCount;


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
	public void processData(long sampleTime, float[] mean, float[] sd, float[] sum, float[] sumSqr, int count) throws InterruptedException
	{
		//		Log.v(Constants.DEBUG_TAG, "Calibration/Uncarried process start: total instances="+measurementsBuffer.getTotalSize()+"/"+measurementsBuffer.getCapacity()+", empty="+measurementsBuffer.getEmptySize()+", filled="+measurementsBuffer.getFilledSize());
		try {
			//	current found gravity
			Measurement currGravity = UpdateGravity(sampleTime,mean,sd,sum,sumSqr,count);

			//	this is supposed to be the last item in the buffer that gives a continous
			//		phone stationary period to the current sample. if null, then
			//		none was found.
			Measurement lastGravity = null;

			//	check if there is any motion in the current found data
			if (hasMotion(currGravity)) {

				//	return the current sample as unusable
				measurementsBuffer.returnEmptyInstance(currGravity);
				//	reset data
				reset();

			} else {
				boolean movementDetected = false;

				lastGravity = UpdateSecondSampleBatch(currGravity);

				if (movementDetected)
					Log.v(Constants.DEBUG_TAG, "Some movement detected in current measurement");
				else
					Log.v(Constants.DEBUG_TAG, "No movement detected in current measurement");
			}

			if (lastGravity!=null) {
				Log.d(Constants.DEBUG_TAG, "Stationary period: "+((sampleTime-lastGravity.time)/1000)+"s");

				//	check and perhaps do calibration
				//	is the stationary period more than the required stationary period for calibration
				if (!isCalibrated && (sampleTime-lastGravity.time>Constants.DURATION_OF_CALIBRATION /*|| ClassifierThread.bForceCalibration*/)) {
					Log.d(Constants.DEBUG_TAG, "Performing calibration.");
					/* TODO: Removed, because it is too late to show this toast here. The data is already collected here.
					 * Therefore keeping the phone still would help to get better a calibration.
					 */
					//					try {
					//						service.showServiceToast("Performing calibration. Please keep the phone still.");
					//					} catch (RemoteException e) {
					//					}

					float sSum[] = new float[Constants.ACCEL_DIM];
					float sSumSqr[] = new float[Constants.ACCEL_DIM];
					int sCount = 0;

					sCount = CalculateMeanForCollectedSamples(sSum,sSumSqr);

					//	get the mean and sd from the sum, sum square, and count
					doCalibration(sSum, sSumSqr, sCount);

					setToCalibratedState();
					ClassifierThread.bForceCalibration = false;
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
		public final float[] sumSqr;
		public final float[] sum;
		public int count;

		public Measurement() {
			this.time = 0;
			this.axisMean = new float[Constants.ACCEL_DIM];
			this.axisSd = new float[Constants.ACCEL_DIM];
			this.sumSqr = new float[Constants.ACCEL_DIM];
			this.sum = new float[Constants.ACCEL_DIM];
			this.count = 0; 
		}

	}

	private class MeasurementsBuffer extends TwoWayBlockingQueue<Measurement> {

		public MeasurementsBuffer(int totalBatchCount) {
			super(totalBatchCount);
		}

		@Override
		protected Measurement getNewInstance() {
			return new Measurement();
		}

	}

	public static void resetCalibrationOptionsBasedOnOrientation(OrientationState state)
	{
		float[] mean = new float[Constants.ACCEL_DIM];
		float[] sd = new float[Constants.ACCEL_DIM];
		float[] offset = new float[Constants.ACCEL_DIM];
		float[] scale = new float[Constants.ACCEL_DIM];

		switch (state) {
		case XYCALIBRATION:
			mean[Constants.ACCEL_Z_AXIS] = optionsTable.getMean()[Constants.ACCEL_Z_AXIS];
			break;

		case ZCALIBRATION:
			mean = optionsTable.getMean();
			sd = optionsTable.getSd();
			mean[Constants.ACCEL_Z_AXIS] = 0;
			break;
		default:
			break;
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

	public static void resetCalibrationOptionsForForceCalib(OptionsTable optionsTable) {
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
		//optionsTable.setSd(sd);
		//optionsTable.setMean(mean);
		//optionsTable.setOffset(offset);
		//optionsTable.setScale(scale);
		//optionsTable.setAllowedMultiplesOfSd(Constants.CALIBARATION_ALLOWED_MULTIPLES_DEVIATION);
		optionsTable.setCount(0);
	}

}
