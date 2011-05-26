/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package aus.csiro.justin.sensorlogger.activities;

import android.app.Activity;
import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import com.flurry.android.FlurryAgent;

import aus.csiro.justin.sensorlogger.R;

/**
 *
 * @author chris
 * modified by Justin
 */
public class ThanksActivity extends Activity implements OnClickListener {

   
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
	    if (keyCode == KeyEvent.KEYCODE_BACK
	             && event.getRepeatCount() == 0) {
	        event.startTracking();
	        return true;
	    }
	    else if (keyCode == KeyEvent.KEYCODE_HOME) {
			this.finish();
	    	return true;
	    }
	    return super.onKeyDown(keyCode, event);
	}
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
	    if (keyCode == KeyEvent.KEYCODE_BACK && event.isTracking()
	            && !event.isCanceled()) {
			startActivity(new Intent(this,IntroActivity.class));
	    	return true;
	    }
	    return super.onKeyUp(keyCode, event);
	}
	
	/** {@inheritDoc} */
    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);

        setContentView(R.layout.thanks);

        final String imei = ((TelephonyManager) getSystemService(TELEPHONY_SERVICE)).getDeviceId();
        //set URL for the web server
        final String code = "http://testingjungoo.appspot.com/read.jsp";
        ((TextView) findViewById(R.id.thankslink)).setText(code);
        ((Button) findViewById(R.id.Exit)).setOnClickListener(this);
        
        //Linkify.addLinks(((TextView) findViewById(R.id.viewcaption)), Linkify.WEB_URLS);
    }

    public String getCode(final String imei) {
        final String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-=_";
        final StringBuilder builder = new StringBuilder();

        long val = Long.decode(imei == null ? "0"
                : imei.matches("^[0-9]+$") ? imei : ("0x" + imei));

        while (val > 0) {
            final long bit = val % chars.length();
            val = val / chars.length();
            builder.insert(0, chars.charAt((int) bit));
        }

        while (builder.length() < 10) {
            builder.insert(0, "a");
        }

        return builder.toString();
    }

    /** {@inheritDoc} */
    @Override
    protected void onStart() {
        super.onStart();

        FlurryAgent.onStartSession(this, "TFBJJPQUQX3S1Q6IUHA6");
    }

    /** {@inheritDoc} */
    @Override
    protected void onStop() {
        super.onStop();

        FlurryAgent.onEndSession(this);
    }
	@Override
	public void onClick(View v) {
		this.finish();
		//System.exit(0);
	}

}
