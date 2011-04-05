/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package aus.csiro.justin.sensorlogger.activities;

import android.app.Activity;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.widget.TextView;
import com.flurry.android.FlurryAgent;

import aus.csiro.justin.sensorlogger.R;

/**
 *
 * @author chris
 * modified by Justin
 */
public class ThanksActivity extends Activity {

    /** {@inheritDoc} */
    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);

        setContentView(R.layout.thanks);

        final String imei = ((TelephonyManager) getSystemService(TELEPHONY_SERVICE)).getDeviceId();
        //set URL for the web server
        final String code = "http://testingjungoo.appspot.com/read.jsp";
        ((TextView) findViewById(R.id.thankslink)).setText(code);
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

}
