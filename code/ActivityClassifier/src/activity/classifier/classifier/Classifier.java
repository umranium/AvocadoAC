package activity.classifier.classifier;

public interface Classifier {

	/**
	 * Extracts required features and uses an algorithm to classify the data
	 * and return the activity.
	 * 
	 * @param data sampled data array
	 * @param size sampled data size 
	 * @return best classification name
	 */
	public abstract String classifyRotated(final float[][] data);

}