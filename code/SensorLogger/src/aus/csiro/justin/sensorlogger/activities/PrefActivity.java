package aus.csiro.justin.sensorlogger.activities;

import android.os.Bundle;
import android.preference.*;
import aus.csiro.justin.sensorlogger.R;

public class PrefActivity extends PreferenceActivity{
	
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
    }
	

}
