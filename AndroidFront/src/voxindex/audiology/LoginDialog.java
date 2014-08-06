/*
 *  Copyright (c) 2011 - 2014 Howard Green. All rights reserved.
 *  The use and distribution terms for this software are covered by the
 *  Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 *  which can be found in the file epl-v10.html at the root of this distribution.
 *  By using this software in any fashion, you are agreeing to be bound by
 *  the terms of this license.
 *  
 *  You must not remove this notice, or any other, from this software.
 */
package voxindex.audiology;

import java.net.URI;
import java.net.URISyntaxException;

import android.app.DialogFragment;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Dialog for handling login-related input. 
 */
public class LoginDialog extends DialogFragment {
	public static final String Tag = "Login";
	
	public interface OnRequest {
		public void onLoginRequest(LoginParams params);
	}

	TextView loginMessage;
	EditText loginURI;
	EditText loginUserID;
	EditText loginPassword;
	Button loginButton;
	CheckBox loginRemember;
	
	OnRequest callback;
	
	URI theURI = null;
	String theUser = null;
	String thePassword = null;
	
	LoginParams params = new LoginParams();
	
	boolean created = false;
	String messageText = "";
	
	public LoginDialog(OnRequest callback) {
		this.callback = callback;
	}

//	public LoginDialog(LoginParams params, OnRequest callback) {
//		this.params = params;
//		this.callback = callback;
//	}
//

	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}	// onCreate
	
	@Override public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             					Bundle savedInstanceState) {
		Log.d(Tag, "login creating view");
		View v = inflater.inflate(R.layout.login_layout, container, false);
		loginButton = (Button) v.findViewById(R.id.loginButton);
		loginButton.setEnabled(false);
		
		loginMessage = (TextView) v.findViewById(R.id.loginMessage);
		loginMessage.setText(messageText);
		
		loginURI = (EditText) v.findViewById(R.id.loginServerURI);
		loginURI.addTextChangedListener(new TextWatcher() {
			@Override public void afterTextChanged(Editable s) {
				try {
					String sn = loginURI.getText().toString().trim();
					if (sn.isEmpty())
						theURI = null;
					else {
						if (! (sn.startsWith("http://") || sn.startsWith("https://")))
							sn = "http://" + sn;
						theURI = new URI(sn);
					}
					loginMessage.setText("");
					checkEnable();
				} catch (URISyntaxException e) {
					loginMessage.setText(R.string.errInvalidURI);
					theURI = null;
				} 
			}

			@Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override public void onTextChanged(CharSequence s, int start, int before, int count) {
			}});

		loginUserID = (EditText) v.findViewById(R.id.loginUserID);
		loginUserID.addTextChangedListener(new TextWatcher(){
			@Override public void afterTextChanged(Editable s) {
				String u = s.toString().trim();
				if (! u.isEmpty()) {
					theUser = u;
					checkEnable();
				} else 
					theUser = null;
			}

			@Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
			@Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
			});
		
		loginPassword = (EditText) v.findViewById(R.id.loginPassword);
		loginPassword.addTextChangedListener(new TextWatcher(){
			@Override public void afterTextChanged(Editable s) {
				String u = s.toString().trim();
				if (! u.isEmpty()) {
					thePassword = u;
					checkEnable();
				}
				else 
					thePassword = null;
			}

			@Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
			@Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
			});

		loginRemember = (CheckBox) v.findViewById(R.id.loginRemember);
		loginRemember.setChecked(params.remember);
		
		loginButton.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) { 
				params = new LoginParams(
						theURI.toString(), params.session, 
						theUser, thePassword, loginRemember.isChecked());
				callback.onLoginRequest(params); 
				} });
		
		loginURI.setText(params.serviceURI);
		loginUserID.setText(params.userID);
		loginPassword.setText(params.password);
		checkEnable();
		
		getDialog().setTitle(R.string.loginDialogName);
		this.setCancelable(false);
		created = true;
		return v;
	}
	
	@Override public void onPause() {
		super.onPause();
	}
	
	void checkEnable() {
		boolean en = theURI != null && theUser != null && thePassword != null;
		loginButton.setEnabled(en);
		// Log.d(Tag, "login enabled = " + en);
	}
	
	public void setLoginParams(LoginParams params) {
		this.params = params;
		if (created) {
			loginURI.setText(params.serviceURI);
			loginUserID.setText(params.userID);
			loginPassword.setText(params.password);
			loginRemember.setChecked(params.remember);
			checkEnable();
		}
	}	// setLoginParams
	
	public void setMessage(String msg) {
		messageText = msg;
		if (created)
			loginMessage.setText(messageText);
	}	// setMessage
	
	/**
	 * Simple-minded 
	 */
	public void hide() {
		if (created)
			dismiss();
	}

}
