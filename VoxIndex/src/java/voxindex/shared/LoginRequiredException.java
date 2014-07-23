package voxindex.shared;

import com.google.gwt.user.client.rpc.IsSerializable;

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
