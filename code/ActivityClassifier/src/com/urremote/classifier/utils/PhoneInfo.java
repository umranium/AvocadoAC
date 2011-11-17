package com.urremote.classifier.utils;

import com.urremote.classifier.common.Constants;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Service;
import android.content.Context;
import android.telephony.TelephonyManager;

/**
 * a utility class which read user and device information and pass them to other classes to use.
 * 
 * @author Justin Lee
 *
 */
public class PhoneInfo {
	
	private Context context;
	private AccountManager accountManager;
	
    public PhoneInfo(Context context) {
    	this.context = context;
    	this.accountManager = AccountManager.get(context.getApplicationContext());
	}

	/**
     * Returns the device model name
     * @return device model name
     */
    public String getModel() {
        return android.os.Build.MODEL;
    }
    
    /**
     * Returns the device IMEI number
     * 
     * @return device IMEI number
     */
    public String getIMEI() {
    	return ((TelephonyManager) context.getSystemService(Service.TELEPHONY_SERVICE)).getDeviceId();
    }
    
    /**
     * Returns the user's first Google account name available
     * 
     * @return user's first Google account name available
     */
    public String getFirstAccountName() {
        Account[] accounts = accountManager.getAccountsByType(Constants.UPLOAD_ACCOUNT_TYPE);
        return (accounts.length!=0 ? accounts[0].name : null);
    }
    	
	
}
