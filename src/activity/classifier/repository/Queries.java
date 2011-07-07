package activity.classifier.repository;

import android.content.Context;

/**
 * Abstract class for database queries
 * @see OptionQueries
 * @see ActivityQueries
 * @author Justin Lee
 *
 */
public abstract class Queries {

	public DbAdapter dbAdapter;

	private Context context;

	/**
	 * 
	 * @param context
	 */
	public Queries(Context context){
		this.context = context;
		dbAdapter = new DbAdapter(context);
	}
}
