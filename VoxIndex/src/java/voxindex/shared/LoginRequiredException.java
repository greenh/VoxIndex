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
package voxindex.shared;

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * Exception thrown when an RPC request comes along that hasn't got a valid
 * session.
 */
public class LoginRequiredException extends Exception implements IsSerializable {
		public LoginRequiredException() { }

	public LoginRequiredException(String message) {
		super(message);
	}

	public LoginRequiredException(Throwable cause) {
		super(cause);
	}

	public LoginRequiredException(String message, Throwable cause) {
		super(message, cause);
	}

}
