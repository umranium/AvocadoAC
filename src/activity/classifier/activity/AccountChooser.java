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
package activity.classifier.activity;

import activity.classifier.common.Constants;
import activity.classifier.db.OptionsTable;
import activity.classifier.db.SqlLiteAdapter;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;

/**
 * Choose which account to upload track information to.
 * 
 * @author Sandor Dornbush
 */
public class AccountChooser {

	private Context context;
	private SqlLiteAdapter sqlLiteAdapter;
	private OptionsTable optionsTable;

	/**
	 * The last selected account.
	 */
	private int selectedAccountIndex;
	
	
	public AccountChooser(Context context) {
		this.context = context;
		this.sqlLiteAdapter = SqlLiteAdapter.getInstance(context);
		this.optionsTable = sqlLiteAdapter.getOptionsTable();
	}

	/**
	 * Chooses the best account to upload to. If no account is found the user
	 * will be alerted. If only one account is found that will be used. If
	 * multiple accounts are found the user will be allowed to choose.
	 * 
	 * @param activity
	 *            The parent activity
	 */
	public void chooseAccount() {
		final Account[] accounts = AccountManager.get(context)
				.getAccountsByType(Constants.UPLOAD_ACCOUNT_TYPE);
		if (accounts.length < 1) {
			alertNoAccounts();
			return;
		}
		
		this.selectedAccountIndex = -1;
		String currentAccountName = optionsTable.getUploadAccount();
		if (currentAccountName!=null) {
			for (int i=0; i<accounts.length; ++i) {
				if (accounts[i].name.equalsIgnoreCase(currentAccountName)) {
					this.selectedAccountIndex = i;
					break;
				}
			}
		}

		// Let the user choose.
		Log.e(Constants.DEBUG_TAG, "Multiple matching accounts found.");
		final AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle("Please choose account");
		builder.setCancelable(false);
		builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				optionsTable.setUploadAccount(accounts[selectedAccountIndex].name);
				optionsTable.save();
			}
		});
		builder.setNegativeButton("Cancel",
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
					}
				});
		String[] choices = new String[accounts.length];
		for (int i = 0; i < accounts.length; i++) {
			choices[i] = accounts[i].name;
		}
		builder.setSingleChoiceItems(choices, selectedAccountIndex,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						selectedAccountIndex = which;
					}
				});
		builder.show();
	}

	/**
	 * Puts up a dialog alerting the user that no suitable account was found.
	 */
	private void alertNoAccounts() {
		Log.e(Constants.DEBUG_TAG, "No matching accounts found.");
		final AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle("No Account Found");
		builder.setMessage("Please set up a google account first.");
		builder.setCancelable(true);
		builder.setNegativeButton("Ok", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				optionsTable.setUploadAccount(null);
				optionsTable.save();
			}
		});
		builder.show();
	}
}
