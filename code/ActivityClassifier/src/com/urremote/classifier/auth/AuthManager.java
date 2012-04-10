/*
 * Copyright 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.urremote.classifier.auth;

import static com.urremote.classifier.common.Constants.UPLOAD_ACCOUNT_TYPE;
import static com.urremote.classifier.common.Constants.TAG;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.urremote.classifier.R;
import com.urremote.classifier.activity.AccountChooser;
import com.urremote.classifier.activity.MainSettingsActivity;
import com.urremote.classifier.common.Constants;
import com.urremote.classifier.db.OptionUpdateHandler;
import com.urremote.classifier.db.OptionsTable;
import com.urremote.classifier.db.SqlLiteAdapter;
import com.urremote.classifier.repository.DbAdapter;

/**
 * AuthManager keeps track of the current auth token for a user. The advantage
 * over just passing around a String is that this class can renew the auth token
 * if necessary, and it will change for all classes using this AuthManager.
 */
public class AuthManager {
	/**
	 * Callback for authentication token retrieval operations.
	 */
	public interface AuthCallback {
		/**
		 * Indicates that we're done fetching an auth token.
		 * 
		 * @param success
		 *            if true, indicates we have the requested auth token
		 *            available to be retrieved using
		 *            {@link AuthManager#getAuthToken}
		 */
		void onAuthResult(boolean success);
	}
	
	/** The activity that will handle auth result callbacks. */
	private Context context;
	private Handler handler;

	private NotificationManager notificationManager;
	private Handler mainLooperHandler;

	private OptionsTable optionsTable;

	/** The name of the service to authorize for. */
	private String service;

	/** The most recently fetched auth token or null if none is available. */
	private String authToken;
	
	/** last time the authentication was attempted */
	private long lastAuthAttempt = 0;

	private AccountManager accountManager;
	
	private final Object LOGIN_MUTEX = new Object();
	
	private OptionUpdateHandler optionUpdateHandler = new OptionUpdateHandler() {
		public void onFieldChange(Set<String> updatedKeys) {
			if (updatedKeys.contains(OptionsTable.KEY_UPLOAD_ACCOUNT)) {
				doLogin(null, true);
			}
		}
	};

	/**
	 * @param service
	 *            The name of the service to authenticate as
	 */
	public AuthManager(Context context, String service) {
		this.context = context;
		this.notificationManager = (NotificationManager) this.context
				.getSystemService(Context.NOTIFICATION_SERVICE);
		this.mainLooperHandler = new Handler(this.context.getMainLooper());
		this.optionsTable = SqlLiteAdapter.getInstance(context)
				.getOptionsTable();
		this.optionsTable.registerUpdateHandler(optionUpdateHandler);
		this.service = service;
		this.accountManager = AccountManager.get(context);
		
		doLogin(null, true);
	}

	@Override
	protected void finalize() throws Throwable {
		this.optionsTable.unregisterUpdateHandler(optionUpdateHandler);
		super.finalize();
	}
	
	/**
	 * Attempts to login using the current selected account
	 */
	private void doLogin(final AuthCallback callback, final boolean initiateRetry) {
		if (authToken==null) {
			synchronized (LOGIN_MUTEX) {
				if (authToken==null) {
					String accountName = optionsTable.getUploadAccount();
					Account[] accounts = accountManager.getAccountsByType(Constants.UPLOAD_ACCOUNT_TYPE);
					Account account = null;
					for (Account ac:accounts) {
						if (ac.name.equals(accountName)) {
							account = ac;
							break;
						}
					}
					
					if (account==null) {
						showNoAccountNotification();
						if (initiateRetry) {
							new RetryAuthenticationRunnable(callback);
						} else {
							if (callback!=null)
								callback.onAuthResult(false);
						}
						return;
					}
					
					accountManager.getAuthToken(account, service, true,
							new AccountManagerCallback<Bundle>() {
								public void run(AccountManagerFuture<Bundle> future) {
									try {
										authToken = future.getResult().getString(
												AccountManager.KEY_AUTHTOKEN);
										Log.i(TAG, "Got auth token");
									} catch (OperationCanceledException e) {
										Log.e(TAG, "Auth token operation Canceled", e);
									} catch (IOException e) {
										Log.e(TAG, "Auth token IO exception", e);
									} catch (AuthenticatorException e) {
										Log.e(TAG, "Authentication Failed", e);
									}

									lastAuthAttempt = System.currentTimeMillis();
									if (authToken==null) {
										showAuthenticationFailureNotification();
										if (initiateRetry) {
											new RetryAuthenticationRunnable(callback);
										} else { 
											if (callback!=null) {
												callback.onAuthResult(false);
											}
										}
									} else {
										if (callback!=null) {
											callback.onAuthResult(true);
										}
									}
								}
							}, null);

				}
			}
		}
	}
	
	private AtomicInteger retryAuthenticationRunnableCount = new AtomicInteger(0);
	
	private class RetryAuthenticationRunnable implements Runnable {
		
		private WeakReference<AuthCallback> callback;
		
		public RetryAuthenticationRunnable(AuthCallback callback) {
			this.callback = new WeakReference<AuthManager.AuthCallback>(callback);
			retryAuthenticationRunnableCount.incrementAndGet();
			mainLooperHandler.postDelayed(this, Constants.PERIOD_RETRY_AUTHENTICATION);
		}
		
		public void run() {
			if (callback!=null && callback.get()==null) {
				//	callback was already GC!
				//	make sure to leave at least one retry running
				if (retryAuthenticationRunnableCount.intValue()>1) {
					retryAuthenticationRunnableCount.decrementAndGet();
					return;
				}
			}
			
			if (System.currentTimeMillis()>=lastAuthAttempt+Constants.PERIOD_RETRY_AUTHENTICATION) {
				doLogin(null, false);
			}
			
			if (authToken==null) {
				mainLooperHandler.postDelayed(this, Constants.PERIOD_RETRY_AUTHENTICATION);
				return;
			} else {
				retryAuthenticationRunnableCount.decrementAndGet();
				if (callback!=null && callback.get()!=null) {
					callback.get().onAuthResult(true);
				}
				return;
			}
			
		}
	};
	
	/**
	 * Returns the current auth token. Response may be null if no valid auth
	 * token has been fetched.
	 * 
	 * @return The current auth token or null if no auth token has been fetched
	 */
	public String getAuthToken() {
		return authToken;
	}

	/**
	 * Invalidates the existing auth token and request a new one. 
	 */
	public void invalidateAndRefresh(final AuthCallback callback) {
		accountManager.invalidateAuthToken(UPLOAD_ACCOUNT_TYPE, authToken);
		authToken = null;

		doLogin(callback, true);
	}
	
	private void showAuthenticationFailureNotification() {
		final Notification notification = new Notification(
				R.drawable.icon,
				"Authentication Failure",
				System.currentTimeMillis());

		Intent startSettings = new Intent(context, MainSettingsActivity.class);

		PendingIntent pendingIntent = PendingIntent.getActivity(context, 0,
				startSettings, 0);

		notification.flags |= Notification.FLAG_ONLY_ALERT_ONCE;
		notification.flags |= Notification.FLAG_AUTO_CANCEL;
		notification.setLatestEventInfo(context,
				"Authentication Failure",
				"Unable to authenticate " + optionsTable.getUploadAccount(), 
				pendingIntent);

		mainLooperHandler.post(new Runnable() {
			public void run() {
				notificationManager.notify(
						Constants.NOTIFICATION_ID_AUTHENTICATION_FAILURE, notification);
			}
		});
	}

	private void showNoAccountNotification() {
		final Notification notification = new Notification(
				R.drawable.icon,
				"No Upload Account",
				System.currentTimeMillis());

		Intent startSettings = new Intent(context, MainSettingsActivity.class);

		PendingIntent pendingIntent = PendingIntent.getActivity(context, 0,
				startSettings, 0);
		
		notification.flags |= Notification.FLAG_ONLY_ALERT_ONCE;
		notification.flags |= Notification.FLAG_AUTO_CANCEL;
		notification.setLatestEventInfo(context,
				"No Upload Account",
				"Please select an account to upload to", 
				pendingIntent);

		mainLooperHandler.post(new Runnable() {
			public void run() {
				notificationManager.notify(
						Constants.NOTIFICATION_ID_NO_ACCOUNT, notification);
			}
		});
	}
	
}
