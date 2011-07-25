package activity.classifier.classifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.TreeMap;

import activity.classifier.common.ActivityNames;
import activity.classifier.common.Constants;
import activity.classifier.common.StringComparator;
import activity.classifier.db.ActivitiesTable;
import activity.classifier.utils.CalcStatistics;
import activity.classifier.utils.FeatureExtractor;
import android.util.Log;

/**
 * 
 * @author abd01c
 */
public class NaiveBayesClassifier implements Classifier {

	private static final int ACTIVITY_FEATURE_CLASS_MEAN = 0;
	private static final int ACTIVITY_FEATURE_CLASS_VAR = 1;

	private int numOfActivities;
	private int numOfFeatures;
	private List<String> activityNames;
	private float[][][] model; // dimensions: activity, mean/var, feature
	private float[] priorProbActivity;

	public NaiveBayesClassifier() {

	}

	public void setModel(final Set<Entry<Float[], Object[]>> model) {
		initModel(model);
		this.priorProbActivity = new float[numOfActivities];

		// initial prior prob activity set equal to all
		for (int i = 0; i < numOfActivities; ++i) {
			this.priorProbActivity[i] = 1.0f / numOfActivities;
		}
	}

	public String classify(final float[] extractedData) {
		String bestActivity = "UNKNOWN";
		float bestActivityValue = Float.NEGATIVE_INFINITY;

		for (int a = 0; a < numOfActivities; ++a) {
			float posterior = priorProbActivity[a];

			// System.out.println("activity: "+activityNames.get(a));

			for (int f = 0; f < numOfFeatures; ++f) {
				float probFeatureClass = computeProbFeatureClass(a, f,
						extractedData);

				// System.out.println("\t\t"+probFeatureClass);

				posterior *= probFeatureClass;
			}

			// System.out.println("\t"+posterior);

			if (posterior > bestActivityValue) {
				bestActivityValue = posterior;
				bestActivity = activityNames.get(a);
			}
		}

		return bestActivity;
	}

	private void initModel(Set<Entry<Float[], Object[]>> model) {

		Map<String, List<float[]>> activityFeatures = new TreeMap<String, List<float[]>>(
				new StringComparator(false));

		this.numOfFeatures = Integer.MAX_VALUE;

		for (Entry<Float[], Object[]> entry : model) {
			String activityName = (String) entry.getValue()[0];
			Float[] features = entry.getKey();

			if (features.length < this.numOfFeatures) {
				this.numOfFeatures = features.length;
			}

			float[] featureArray = new float[features.length];
			for (int i = 0; i < features.length; ++i) {
				featureArray[i] = (Float) features[i];
			}

			List<float[]> featureArrayList = activityFeatures.get(activityName);

			if (featureArrayList == null) {
				featureArrayList = new ArrayList<float[]>();
				activityFeatures.put(activityName, featureArrayList);
			}

			featureArrayList.add(featureArray);
		}

		this.numOfActivities = activityFeatures.size();
		this.activityNames = new ArrayList<String>(activityFeatures.keySet());

		if (this.numOfFeatures == 0 || this.numOfFeatures == Integer.MAX_VALUE)
			throw new RuntimeException(
					"Unable to determine number of features, either no features, or no data");

		float[][][] newModel = new float[this.numOfActivities][][];
		CalcStatistics calcFeatureStatistics = new CalcStatistics(numOfFeatures);

		for (int len = this.activityNames.size(), a = 0; a < len; ++a) {

			String activityName = this.activityNames.get(a);
			List<float[]> featureList = activityFeatures.get(activityName);
			int numOfSamples = featureList.size();
			float[][] featureArray = new float[numOfSamples][];
			featureArray = featureList.toArray(featureArray);
			calcFeatureStatistics.assign(featureArray, numOfSamples);
			float[] mean = calcFeatureStatistics.getMean();
			float[] var = calcFeatureStatistics.getSampleVariance();

			float[][] classDetails = new float[2][];
			classDetails[ACTIVITY_FEATURE_CLASS_MEAN] = mean.clone();
			classDetails[ACTIVITY_FEATURE_CLASS_VAR] = var.clone();
			newModel[a] = classDetails;

			String debugOut = String.format("%-25s", activityName) + "\t";
			for (int f = 0; f < numOfFeatures; ++f) {
				debugOut += "\t" +
					FeatureExtractor.FEATURE_NAMES[f] + "=" + 
                       String.format("%8.5f(%8.5f)",
								classDetails[ACTIVITY_FEATURE_CLASS_MEAN][f],
								classDetails[ACTIVITY_FEATURE_CLASS_VAR][f]
								);
			}
			Log.i(Constants.DEBUG_TAG, debugOut);
		}

		// int walking = -1;
		// for (int i=0; i<this.activityNames.size(); ++i) {
		// if (this.activityNames.get(i).contains("WALKING")) {
		// walking = i;
		// break;
		// }
		// }
		// if (walking>=0) {
		// newModel[walking][ACTIVITY_FEATURE_CLASS_VAR][FeatureExtractor.FEATURE_VER_RANGE]
		// = 5.0f*5.0f;
		// System.out.println("CHANGED THE WALKING VARIANCE");
		// }

		this.model = newModel;
	}

	private float computeProbFeatureClass(int activity, int featureClass,
			float[] data) {
		float[][] activityFeatureClassDetails = this.model[activity];
		float mean = activityFeatureClassDetails[ACTIVITY_FEATURE_CLASS_MEAN][featureClass];
		float var = activityFeatureClassDetails[ACTIVITY_FEATURE_CLASS_VAR][featureClass];
		float value = data[featureClass];

		float val = (float) ((1.0 / (Math.sqrt(2.0 * Math.PI * var))) * Math
				.exp(-((value - mean) * (value - mean)) / (2 * var)));

		if (val == 0.0f)
			val = Float.MIN_VALUE;
		else if (Float.isInfinite(val))
			val = Float.MAX_VALUE;

		return val;
	}

}
