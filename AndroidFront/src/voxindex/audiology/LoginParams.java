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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Idiot datatype for holding values used for the login process.
 */
public class LoginParams {
	public String serviceURI = "";
	public String userID = "";
	public String password = "";
	public boolean remember = true;
	public String session = null;

	public LoginParams() { }

	public LoginParams(String serviceURI, String session, String userID,
										String password, boolean remember) {
		this.serviceURI = serviceURI;
		this.session = session;
		this.userID = userID;
		this.password = password;
		this.remember = remember;
	}
	
	public LoginParams(LoginParams p) {
		this.serviceURI = p.serviceURI;
		this.session = p.session;
		this.userID = p.userID;
		this.password = p.password;
		this.remember = p.remember;
	}
	
	public LoginParams(JSONObject o) throws JSONException {
		this.serviceURI = o.getString("uri");
		this.session = o.getString("session");
		this.userID = o.getString("user");
		this.password = o.getString("password");
		this.remember = o.getBoolean("remember");
	}
	
	/**
	 * Returns true if a sufficient set of data is present to make a login
	 * attempt worthwhile.
	 * @return True if the LoginParams object is login-sufficient.
	 */
	public boolean isLoginSufficient() {
		return serviceURI != null && ! serviceURI.isEmpty()
				&& userID != null && ! userID.isEmpty()
				&& password != null && ! password.isEmpty();
	}
	
	public String repString() {
		return serviceURI + " as " + userID + " [" + session + "]"; 
	}
	
	/**
	 * Generates and returns JSONObject representation of the LoginParams object content
	 * @return The JSONObject. 
	 */
	public JSONObject toJSON() {
		JSONObject o = new JSONObject();
		try {
			o.put("uri", serviceURI);
			o.put("user", userID);
			o.put("password", password);
			o.put("session", session);
			o.put("remember", remember);
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return o;
	}	// toJSON
	
	public static String toJSON(Collection<LoginParams> lps) {
		JSONArray j = new JSONArray();
		for (LoginParams lp : lps)
			j.put(lp.toJSON());
		return j.toString();
	}	// toJSON
	
	public static List<LoginParams> fromJSON(String rep) throws JSONException {
		JSONArray j = new JSONArray(rep);
		ArrayList<LoginParams> lps = new ArrayList<LoginParams>();
		for (int i = 0; i < j.length(); i++) {
			lps.add(new LoginParams(j.getJSONObject(i)));
		}
		return lps;
	}	// fromJSON
	
	
} // LoginParams