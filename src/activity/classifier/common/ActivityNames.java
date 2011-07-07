package activity.classifier.common;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import activity.classifier.R;
import activity.classifier.model.ModelReader;
import activity.classifier.service.RecorderService;
import android.content.Context;
import android.graphics.Color;
import android.util.Log;

public class ActivityNames {
	
	//	system based activities
	public static final String OFF						= "OFF";
	public static final String END						= "END";
	public static final String UNKNOWN					= "UNKNOWN";
	public static final String UNCARRIED				= "CLASSIFIED/UNCARRIED";
	public static final String CHARGING					= "CLASSIFIED/CHARGING";
	public static final String CHARGING_traveling		= "CLASSIFIED/CHARGING/traveling";
	
	//	model based activities
	public static final String STATIONARY				= "CLASSIFIED/STATIONARY";
	public static final String TRAVELING				= "CLASSIFIED/traveling";
	public static final String WALKING					= "CLASSIFIED/WALKING";
	public static final String PADDLING					= "CLASSIFIED/PADDLING";
	public static final String ROWING					= "CLASSIFIED/ROWING";
	public static final String CYCLING					= "CLASSIFIED/CYCLING";
	public static final String RUNNING					= "CLASSIFIED/RUNNING";
	
	/**
	 * Check if the given activity is a system-based activity,
	 * activities such as END, are not there for the user but for the system.
	 */
	public static boolean isSystemActivity(String activity) {
		return END.equals(activity) || OFF.equals(activity);
	}
	
	public static Set<String> getAllActivities(Context context) {
		
		Map<Float[],Object[]> model = ModelReader.getModel(context, R.raw.basic_model);
		
		Set<String> allActivities = new TreeSet<String>(new StringComparator(false));
		
        for (Map.Entry<Float[], Object[]> entry : model.entrySet()) {
        	String activity = (String)entry.getValue()[0];
        	allActivities.add(activity);
        }
        
        allActivities.add(OFF);
        allActivities.add(END);
        allActivities.add(UNKNOWN);
        allActivities.add(UNCARRIED);
        allActivities.add(CHARGING);
        
        return allActivities;
	}

}
