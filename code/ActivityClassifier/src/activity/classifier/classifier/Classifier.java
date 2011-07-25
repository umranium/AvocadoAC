package activity.classifier.classifier;

import java.util.Map.Entry;
import java.util.Set;

public interface Classifier {

	public abstract void setModel(final Set<Entry<Float[], Object[]>> model);
	
	/**
	 * Extracts required features and uses an algorithm to classify the data
	 * and return the activity.
	 * 
	 * @param data sampled data array
	 * @param size sampled data size 
	 * @return best classification name
	 */
	public abstract String classify(final float[] extractedData);

}