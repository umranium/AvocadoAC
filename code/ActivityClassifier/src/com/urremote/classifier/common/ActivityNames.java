package com.urremote.classifier.common;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.urremote.classifier.model.ModelReader;
import com.urremote.classifier.service.RecorderService;

import com.urremote.classifier.R;
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
	public static final String CHARGING_TRAVELLING		= "CLASSIFIED/CHARGING/TRAVELLING";
	
	//	model based activities
	public static final String STATIONARY				= "CLASSIFIED/STATIONARY";
	public static final String TRAVELING				= "CLASSIFIED/TRAVELLING";
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
		
		Set<String> declaredActivities = new TreeSet<String>(StringComparator.CASE_INSENSITIVE_INSTANCE);
		
		try {
			Field[] fields = ActivityNames.class.getFields();
			for (Field f:fields) {
				//	find all public static final String declared fields 
				if (f.getType().equals(String.class) &&
					(f.getModifiers() & (Modifier.STATIC | Modifier.FINAL | Modifier.PUBLIC)) != 0) {
					String value = (String)f.get(null);
					declaredActivities.add(value);
				}
			}
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		Set<String> modelActivities = new TreeSet<String>(StringComparator.CASE_INSENSITIVE_INSTANCE);
		
        for (Map.Entry<Float[], Object[]> entry : model.entrySet()) {
        	String activity = (String)entry.getValue()[0];
        	modelActivities.add(activity);
        }
        
        for (String activity:modelActivities) {
        	if (!declaredActivities.contains(activity)) {
        		throw new RuntimeException("Undeclared activity found in the model:'"+activity+"'");
        	}
        }
        
        declaredActivities.add(OFF);
        declaredActivities.add(END);
        declaredActivities.add(UNKNOWN);
        declaredActivities.add(UNCARRIED);
        declaredActivities.add(CHARGING);
        
        return declaredActivities;
	}

}
