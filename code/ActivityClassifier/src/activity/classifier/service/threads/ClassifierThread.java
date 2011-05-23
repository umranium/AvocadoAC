/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package activity.classifier.service.threads;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import activity.classifier.R;
import activity.classifier.accel.SampleBatch;
import activity.classifier.accel.SampleBatchBuffer;
import activity.classifier.activity.MainTabActivity;
import activity.classifier.aggregator.Aggregator;
import activity.classifier.classifier.Classifier;
import activity.classifier.classifier.KnnClassifier;
import activity.classifier.common.ActivityNames;
import activity.classifier.common.Constants;
import activity.classifier.common.ExceptionHandler;
import activity.classifier.db.ActivitiesTable;
import activity.classifier.db.DebugDataTable;
import activity.classifier.db.OptionUpdateHandler;
import activity.classifier.db.OptionsTable;
import activity.classifier.db.SqlLiteAdapter;
import activity.classifier.model.ModelReader;
import activity.classifier.rpc.ActivityRecorderBinder;
import activity.classifier.service.RecorderService;
import activity.classifier.utils.CalcStatistics;
import activity.classifier.utils.Calibrator;
import activity.classifier.utils.RotateSamplesToVerticalHorizontal;
import android.R.string;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;
import android.util.Log;

/**
 * ClassifierService class is a Service analyse the sensor data to classify
 * activities. RecorderService class invokes this class when sampling is done,
 * and send parameters (data collection, size of data array,battery status, etc)
 * which is useful to determine the activities. After done with classification,
 * it notices RecorderService about what activity is classified.
 * 
 * 
 * Standard Deviation(sd) and Average values for accelerations(average) are used
 * to classify Uncarried state. chargingState(battery status) is used to
 * classify Charging state.
 * 
 * Other activities are classified through KNN algorithm (with K=1). (This KNN
 * classification is implemented in Aggregator.java)
 * 
 * Local database is used to store some meaningful information such as sd,
 * average, lastaverage (the average of acceleration values when the activity is
 * Uncarried, if the activity is not a Uncarried, then the values is 0.0).
 * 
 * <p>
 * Changes made by Umran: <br>
 * The class used to be called ClassifierService. Now changed to a thread.
 * Communication between {@link RecorderService} and this class is done through
 * the {@link SampleBatch} and {@link SampleBatchBuffer}.
 * <p>
 * Filled batches are posted into the buffer in {@link RecorderService} and
 * removed here, after analysis, the batches are posted back into the buffer as
 * empty batches where the recorder class removes them and fills them with
 * sampled data.
 * 
 * @author chris, modified by Justin Lee
 * 
 * 
 */
public class ClassifierThread extends Thread implements OptionUpdateHandler {

	private Context context;
	private ActivityRecorderBinder service;
	private SampleBatchBuffer batchBuffer;

	private Map<Float[],Object[]> model; 

	private SqlLiteAdapter sqlLiteAdapter;
	private OptionsTable optionsTable;
	private DebugDataTable debugDataTable;

	private CalcStatistics rawSampleStatistics = new CalcStatistics(Constants.ACCEL_DIM);

	private float[][] rotatedMergedSamples = new float[Constants.NUM_OF_SAMPLES_PER_BATCH][2];
	private CalcStatistics rotatedMergedSampleStatistics = new CalcStatistics(2);	

	private RotateSamplesToVerticalHorizontal rotateSamples = new RotateSamplesToVerticalHorizontal();
	private Classifier classifier;
	private Aggregator aggregator;

	public static boolean bForceCalibration;
	private boolean isCalibrated;
	private Calibrator calibrator;

	private volatile boolean shouldExit;

	public ClassifierThread(Context context, ActivityRecorderBinder service,
			SampleBatchBuffer sampleBatchBuffer) {
		super(ClassifierThread.class.getName());

		this.context = context;
		this.service = service;
		this.batchBuffer = sampleBatchBuffer;

		this.model = ModelReader.getModel(context, R.raw.basic_model);

		this.sqlLiteAdapter = SqlLiteAdapter.getInstance(context);
		this.optionsTable = sqlLiteAdapter.getOptionsTable();
		this.debugDataTable = sqlLiteAdapter.getDebugDataTable();

		this.classifier = new KnnClassifier(this.model.entrySet());
		this.aggregator = new Aggregator(null);

		this.isCalibrated = this.optionsTable.isCalibrated();
		this.calibrator = new Calibrator(
				service, 
				this.optionsTable.isCalibrated(),
				this.optionsTable.getAllowedMultiplesOfSd(),
				this.optionsTable.getCount(),
				this.optionsTable.getSd().clone(),
				this.optionsTable.getMean().clone(),
				this.optionsTable.getValueOfGravity()
		);

		this.shouldExit = false;
	}

	/**
	 * Stops the thread cautiously
	 */
	public synchronized void exit() {
		// signal the thread to exit
		this.shouldExit = true;

		Log.v(Constants.DEBUG_TAG, "About to interrupt classifier thread");

		// if the thread is blocked waiting for a filled batch
		// interrupt the thread
		this.interrupt();
	}

	/*
	 * Calibrates the sensor based on the input data. This will be called instead of the classification process.
	 */
	public void StartCalibration(SampleBatch batch) throws InterruptedException, RemoteException
	{
		int size = batch.getSize();
		long sampleTime = batch.sampleTime;
		float[][] data = batch.data;


		if(!calibrator.isCalibrated())
		{
			rawSampleStatistics.assign(data, size);
			calibrator.processData(sampleTime, 
					rawSampleStatistics.getMean(), 
					rawSampleStatistics.getStandardDeviation(), 
					rawSampleStatistics.getSum(), 
					rawSampleStatistics.getSumSqr(), 
					rawSampleStatistics.getCount());
			
			float[] sd = calibrator.getSd();
			float[] mean = calibrator.getMean();

			optionsTable.setCalibrated(true);
			optionsTable.setMean(mean);
			optionsTable.setSd(sd);
			optionsTable.setCount(calibrator.getCount());
			optionsTable.setValueOfGravity(calibrator.getValueOfGravity());

			//mean[Constants.ACCEL_Z_AXIS] -= Constants.GRAVITY;
			mean[Constants.ACCEL_Z_AXIS] = 0.0f;
			optionsTable.setOffset(mean);

			optionsTable.save();

			this.isCalibrated = true;
			//bForceCalibration = false;
			
			String ns = this.context.NOTIFICATION_SERVICE;
			NotificationManager notificationManager = (NotificationManager)this.context.getSystemService(ns);

			int icon = R.drawable.icon;
			CharSequence tickerText = "Avocado AC Calibration";
			long when = System.currentTimeMillis();
			Notification notification = new Notification(icon, tickerText, when);
			
			notification.defaults = 0;
			notification.flags = Notification.DEFAULT_SOUND | Notification.FLAG_ONLY_ALERT_ONCE;
			
			Context context = this.context.getApplicationContext();
			CharSequence contentTitle = "Avocado AC";
			CharSequence contentText = "Avocado AC colibration is finished.";
			Intent notificationIntent = new Intent(this.context, MainTabActivity.class);
			PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, 0);
			notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
			
			notificationManager.notify(notification.flags, notification);
			//service.s.startForeground(1, notification);

		}

	}
	/**
	 * Classification start
	 */
	public void run() {
		try {
			Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler(context));
			Log.v(Constants.DEBUG_TAG, "Classification thread started.");
			this.optionsTable.registerUpdateHandler(this);
			while (!this.shouldExit) {
				try {
					// in case of too sampling too fast, or too slow CPU, or the
					// classification taking too long
					// check how many batches are pending
					int pendingBatches = batchBuffer.getPendingFilledInstances();
					if (pendingBatches == SampleBatchBuffer.TOTAL_BATCH_COUNT) {
						// issue an error if too many
						service.showServiceToast("Unable to classify sensor data fast enough!");
					}

					// this function blocks until a filled sample batch is obtained
					SampleBatch batch = batchBuffer.takeFilledInstance();

					Log.v(Constants.DEBUG_TAG, "Classifier thread received batch");

					// process the sample batch to obtain the classification
					long sampleTime = batch.sampleTime;
					if(bForceCalibration)
						StartCalibration(batch);
					else
					{
						String classification = processData(batch);


						//	submit the classification (if any)
						if (classification!=null && classification.length()>0) {
							Log.v(Constants.DEBUG_TAG, "Classification found: '"+classification+"'");
							// submit the classification
							service.submitClassification(sampleTime, classification);
						}
					}
					
					// return the sample batch to the buffer as an empty batch
					batchBuffer.returnEmptyInstance(batch);

				} catch (RemoteException ex) {
					Log.e(Constants.DEBUG_TAG,
					"Exception error occured in connection in ClassifierService class");
				} catch (InterruptedException e) {
					//					Log.d(Constants.DEBUG_TAG,
					//							"Interruption occured while performing classification", e);
				}
			}
			Log.d(Constants.DEBUG_TAG, "Classifier thread exitting");
		} finally {
			this.optionsTable.unregisterUpdateHandler(this);
			Log.d(Constants.DEBUG_TAG, "Classification thread exiting.");
		}

	}

	@Override
	public void onFieldChange(Set<String> updatedKeys) {
		if (updatedKeys.contains(OptionsTable.KEY_IS_CALIBRATED)) {
			if (!this.optionsTable.isCalibrated()) {
				Log.v(Constants.DEBUG_TAG, "Calibration Values Reset! Classifier Thread resetting calibration.");
				this.isCalibrated = this.optionsTable.isCalibrated();
				this.calibrator.setResetValues(
						this.optionsTable.isCalibrated(),
						this.optionsTable.getAllowedMultiplesOfSd(),
						this.optionsTable.getCount(),
						this.optionsTable.getSd().clone(),
						this.optionsTable.getMean().clone(),
						this.optionsTable.getValueOfGravity()
				);
			}
		}
	}

	private String processData(SampleBatch batch) throws InterruptedException, RemoteException {
		Log.v(Constants.DEBUG_TAG, "Processing batch.");

		long start = System.currentTimeMillis();

		try {
			//	get local copies of the data
			float[][] data = batch.data;
			String classification = ActivityNames.UNKNOWN;

			int size = batch.getSize();
			long sampleTime = batch.sampleTime;

			boolean chargingState = !Constants.IS_DEBUGGING && batch.isCharging();

			if (Constants.OUTPUT_DEBUG_INFO) {
				debugDataTable.reset(sampleTime);
			}
			{
				//	take out accelerometer axis offsets
				if (calibrator.isCalibrated()) {
					float[] offset = optionsTable.getOffset();
					float[] scale = optionsTable.getScale();

					for (int i=0; i<size; ++i) {
						data[i][Constants.ACCEL_X_AXIS] -= offset[Constants.ACCEL_X_AXIS]; 
						data[i][Constants.ACCEL_X_AXIS] /= scale[Constants.ACCEL_X_AXIS]; 

						data[i][Constants.ACCEL_Y_AXIS] -= offset[Constants.ACCEL_Y_AXIS];
						data[i][Constants.ACCEL_Y_AXIS] /= scale[Constants.ACCEL_Y_AXIS];

						data[i][Constants.ACCEL_Z_AXIS] -= offset[Constants.ACCEL_Z_AXIS];
						data[i][Constants.ACCEL_Z_AXIS] /= scale[Constants.ACCEL_Z_AXIS];
					}
				}

				rawSampleStatistics.assign(data, size);

				float[] dataMin = rawSampleStatistics.getMin();
				float[] dataMax = rawSampleStatistics.getMax();
				float[] dataMeans = rawSampleStatistics.getMean();
				float[] dataSd = rawSampleStatistics.getStandardDeviation();

				if (Constants.OUTPUT_DEBUG_INFO) {
					debugDataTable.setUnrotatedStats(
							dataMeans[Constants.ACCEL_X_AXIS],
							dataMeans[Constants.ACCEL_Y_AXIS],
							dataMeans[Constants.ACCEL_Z_AXIS],
							dataSd[Constants.ACCEL_X_AXIS],
							dataSd[Constants.ACCEL_Y_AXIS],
							dataSd[Constants.ACCEL_Z_AXIS],
							dataMax[Constants.ACCEL_X_AXIS]-dataMin[Constants.ACCEL_X_AXIS],
							dataMax[Constants.ACCEL_Y_AXIS]-dataMin[Constants.ACCEL_Y_AXIS],
							dataMax[Constants.ACCEL_Z_AXIS]-dataMin[Constants.ACCEL_Z_AXIS]
					);
				}

				//if(!isCalibrated)
					calibrator.processData(sampleTime, dataMeans, dataSd, rawSampleStatistics.getSum(), rawSampleStatistics.getSumSqr(), rawSampleStatistics.getCount());

				/*else*/ if (!isCalibrated && calibrator.isCalibrated()) {
					Log.v(Constants.DEBUG_TAG, "Calibration just finished. Saving values to DB.");

					float[] sd = calibrator.getSd();
					float[] mean = calibrator.getMean();

					optionsTable.setCalibrated(true);
					optionsTable.setMean(mean);
					optionsTable.setSd(sd);
					optionsTable.setCount(calibrator.getCount());
					optionsTable.setValueOfGravity(calibrator.getValueOfGravity());

					//mean[Constants.ACCEL_Z_AXIS] -= Constants.GRAVITY;
					mean[Constants.ACCEL_Z_AXIS] = 0.0f;
					optionsTable.setOffset(mean);

					optionsTable.save();
					this.isCalibrated = true;
				}


				//	check the current gravity, rotate and perform classification
				{
					float gravity = calibrator.getValueOfGravity();
					float calcGravity = rawSampleStatistics.calcMag(dataMeans);
					float minGravity = gravity - gravity*Constants.MIN_GRAVITY_DEV;
					float maxGravity = gravity + gravity*Constants.MIN_GRAVITY_DEV;

					if (calcGravity>=minGravity && calcGravity<=maxGravity) {
						// first rotate samples to world-orientation
						if (rotateSamples.rotateToWorldCoordinates(dataMeans, data)) {
							classification = classifier.classifyRotated(data);

							Log.v(Constants.DEBUG_TAG, "Classifier Algorithm Output: "+classification);

							if (Constants.OUTPUT_DEBUG_INFO) {
								logRotatedValues(dataMeans, data, size);
								debugDataTable.setClassifierAlgoOutput(classification);
							}

							if (optionsTable.getUseAggregator()) {
								aggregator.addClassification(classification);
								String aggrClassification = aggregator.getClassification();
								if (aggrClassification!=null && aggrClassification.length()>0 &&
										!ActivityNames.isSystemActivity(aggrClassification)) {
									classification = aggrClassification;
								}

								Log.v(Constants.DEBUG_TAG, "Aggregator Output: "+aggrClassification);

								if (Constants.OUTPUT_DEBUG_INFO) {
									debugDataTable.setAggregatorAlgoOutput(aggrClassification);
								}
							} else {
								if (Constants.OUTPUT_DEBUG_INFO) {
									debugDataTable.setAggregatorAlgoOutput("NOT USING AGGREGATOR");
								}
							}

						} else {
							Log.v(Constants.DEBUG_TAG, "Unable to perform classification, data could not be rotated!");
							if (Constants.OUTPUT_DEBUG_INFO) {
								debugDataTable.setClassifierAlgoOutput("ERROR: Unable to rotate gravity "+Arrays.toString(dataMeans));
							}
						}
					} else {
						if (Constants.OUTPUT_DEBUG_INFO) {
							debugDataTable.setClassifierAlgoOutput("ERROR: Gravity "+calcGravity+" not within limits: ["+minGravity+","+maxGravity+"]");
						}
					}
				}
			}
			if (chargingState) {
				if (classification.contains("TRAVELLING"))
					classification = ActivityNames.CHARGING_TRAVELLING;
				else
					classification = ActivityNames.CHARGING;
			} else {
				if (calibrator.isUncarried()) {
					classification = ActivityNames.UNCARRIED;
				}
			}

			if (Constants.OUTPUT_DEBUG_INFO) {
				debugDataTable.setFinalClassifierOutput(classification);
			}
			Log.v(Constants.DEBUG_TAG, "Final Classifier Output: "+classification);

			return classification;
		} finally {
			if (Constants.OUTPUT_DEBUG_INFO) {
				debugDataTable.trim();
				debugDataTable.insert();
			}
			Log.i(Constants.DEBUG_TAG, "Processing Batch Took: "+(System.currentTimeMillis()-start)+"ms");	        
		}
	}

	private void logRotatedValues(float[] dataMeans, float[][] rotatedData, int dataSize)
	{
		for (int j=0; j<Constants.NUM_OF_SAMPLES_PER_BATCH; ++j) {
			rotatedMergedSamples[j][0] = (float)Math.sqrt(
					rotatedData[j][0]*rotatedData[j][0] +
					rotatedData[j][1]*rotatedData[j][1]);
			rotatedMergedSamples[j][1] = rotatedData[j][2];
		}

		rotatedMergedSampleStatistics.assign(rotatedMergedSamples, dataSize);

		float[] rotatedMin = rotatedMergedSampleStatistics.getMin();
		float[] rotatedMax = rotatedMergedSampleStatistics.getMax();
		float[] rotatedMeans = rotatedMergedSampleStatistics.getMean();
		float[] rotatedSd = rotatedMergedSampleStatistics.getStandardDeviation();

		debugDataTable.setRotatedStats(
				rotatedMeans[0],
				rotatedMeans[1],

				rotatedMax[0]-rotatedMin[0],
				rotatedMax[1]-rotatedMin[1],

				rotatedSd[0],
				rotatedSd[1]
		);

	}

}
