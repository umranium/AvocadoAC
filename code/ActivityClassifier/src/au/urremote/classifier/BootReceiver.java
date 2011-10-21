

package au.urremote.classifier;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import au.urremote.classifier.db.OptionsTable;
import au.urremote.classifier.db.SqlLiteAdapter;
import au.urremote.classifier.service.RecorderService;

/**
 * 
 * BootReceiver is the class, which extends BroadcastReceiver, for starting RecorderService class at the phone booting.
 * 
 * To receive the boot status, a permission should be registered in AndroidManifest.xml file
 * (<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />)
 * 
 * Changes by Umran:
 * 	if the service was started last time the phone went off
 * 		(or rather, the user didn't stop it)
 * 	then the service should start from boot
 * 	otherwise it should stay off.. 
 * 
 * @author Justin Lee
 * @see android.content.BroadcastReceiver
 */
public class BootReceiver extends BroadcastReceiver{

	private static final String BOOT_ACTION = "android.intent.action.BOOT_COMPLETED";


	@Override 
	public void onReceive(Context context, Intent intent) {
		if(intent.getAction().equals(BOOT_ACTION)) {
			SqlLiteAdapter sqlLiteAdapter = SqlLiteAdapter.getInstance(context);
			OptionsTable optionsTable = sqlLiteAdapter.getOptionsTable();
			
			if (optionsTable.isServiceUserStarted()) {
				Intent i = new Intent(context, RecorderService.class);   
				i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				context.startService(i); 
			}
		}
	}  
}
