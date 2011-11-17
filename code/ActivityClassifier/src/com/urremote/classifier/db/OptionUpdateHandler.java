package com.urremote.classifier.db;

import java.util.Set;

/**
 * Allows the OptionsTable class to notify registered objects
 * about changes made to the settings. This allows
 * objects in the system to react to the changes without
 * requiring a system restart.
 * 
 * See: {@link OptionsTable#registerUpdateHandler(OptionUpdateHandler)}
 * See: {@link OptionsTable#unregisterUpdateHandler(OptionUpdateHandler)}
 * 
 * @author abd01c
 *
 */
public interface OptionUpdateHandler {
	
	void onFieldChange( Set<String> updatedKeys ); 

}
