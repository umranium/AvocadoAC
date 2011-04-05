/*
 * Copyright (c) 2009-2010 Chris Smith
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package uk.co.md87.android.contextanalyser.locale;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;

import com.flurry.android.FlurryAgent;
import com.twofortyfouram.SharedResources;

import java.util.Arrays;
import java.util.List;

/**
 * Allows the user to edit their selected activity.
 *
 * @author chris
 */
public class EditActivity extends Activity {

    private static final int MENU_SAVE = 1;
    private static final int MENU_DONT_SAVE = 2;

    final List<String> objects = Arrays.asList(new String[]{
        "CLASSIFIED/IDLE/SITTING", "CLASSIFIED/IDLE/STANDING",
        "CLASSIFIED/WALKING", "CLASSIFIED/WALKING/STAIRS/UP",
        "CLASSIFIED/WALKING/STAIRS/DOWN", "CLASSIFIED/DANCING",
        "CLASSIFIED/VEHICLE/CAR", "CLASSIFIED/VEHICLE/BUS",
    });

    final List<String> friendly = Arrays.asList(new String[]{
        "Idle (sitting)", "Idle (standing)",
        "Walking", "Walking (up stairs)", "Walking (down stairs)",
        "Dancing", "Vehicle (car)", "Vehicle (bus)",
    });

    private boolean isCancelled;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FlurryAgent.onStartSession(this, "FRC4VR4CREJG3BTGSTLV");

        setContentView(R.layout.main);

        final String breadcrumbString = getIntent()
                .getStringExtra(com.twofortyfouram.Intent.EXTRA_STRING_BREADCRUMB);
        setTitle(String.format("%s%s%s", breadcrumbString,
                com.twofortyfouram.Intent.BREADCRUMB_SEPARATOR,
                getString(R.string.plugin_name)));
       
        ((LinearLayout) findViewById(R.id.frame))
                .setBackgroundDrawable(SharedResources.getDrawableResource(getPackageManager(),
                SharedResources.DRAWABLE_LOCALE_BORDER));
        
        final Spinner spinner = (Spinner) findViewById(R.id.spinner);
        spinner.setAdapter(new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, friendly));

        if (savedInstanceState == null) {
            final Bundle forwardedBundle = getIntent()
                    .getBundleExtra(com.twofortyfouram.Intent.EXTRA_BUNDLE);

            if (forwardedBundle != null) {
                spinner.setSelection(objects.indexOf(forwardedBundle.getString("activity")));
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void finish() {
        if (isCancelled) {
            FlurryAgent.onEvent("cancel");
            setResult(RESULT_CANCELED);
        } else {
            FlurryAgent.onEvent("save");
            final Intent returnIntent = new Intent();

            final Bundle storeAndForwardExtras = new Bundle();

            storeAndForwardExtras.putString("activity",
                    objects.get(((Spinner) findViewById(R.id.spinner)).getSelectedItemPosition()));
            returnIntent.putExtra(com.twofortyfouram.Intent.EXTRA_STRING_BLURB,
                    ((Spinner) findViewById(R.id.spinner)).getSelectedItem().toString());

            returnIntent.putExtra(com.twofortyfouram.Intent.EXTRA_BUNDLE, storeAndForwardExtras);
            setResult(RESULT_OK, returnIntent);
        }

        FlurryAgent.onEndSession(this);
        super.finish();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        super.onCreateOptionsMenu(menu);

        final PackageManager manager = getPackageManager();

        final Intent helpIntent = new Intent(com.twofortyfouram.Intent.ACTION_HELP);
        helpIntent.putExtra(com.twofortyfouram.Intent.EXTRA_STRING_HELP_URL,
                "http://chris.smith.name/android/contextanalyser/");

        helpIntent.putExtra(com.twofortyfouram.Intent.EXTRA_STRING_BREADCRUMB, getTitle());

        menu.add(SharedResources.getTextResource(manager, SharedResources.STRING_MENU_HELP))
                .setIcon(SharedResources.getDrawableResource(manager,
                SharedResources.DRAWABLE_MENU_HELP)).setIntent(helpIntent);
        menu.add(0, MENU_DONT_SAVE, 0, SharedResources.getTextResource(manager,
                SharedResources.STRING_MENU_DONTSAVE)).setIcon(SharedResources
                .getDrawableResource(manager, SharedResources.DRAWABLE_MENU_DONTSAVE)).getItemId();
        menu.add(0, MENU_SAVE, 0, SharedResources.getTextResource(manager,
                SharedResources.STRING_MENU_SAVE)).setIcon(SharedResources
                .getDrawableResource(manager, SharedResources.DRAWABLE_MENU_SAVE)).getItemId();

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onMenuItemSelected(final int featureId, final MenuItem item) {
        switch (item.getItemId()) {
            case MENU_SAVE: {
                finish();
                return true;
            }
            case MENU_DONT_SAVE: {
                isCancelled = true;
                finish();
                return true;
            }
        }

        return super.onOptionsItemSelected(item);
    }
}
