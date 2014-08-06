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

import com.gdevelop.gwt.syncrpc.SyncProxy;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import voxindex.shared.AuthenticationException;
import voxindex.shared.LoginRequiredException;
import voxindex.shared.VoxIndexService;

/**
 * Provides RPC session services running from a dedicated (non-UI) thread.
 * <p>We take the view here that the VoxIndex login/session establishment mechanism 
 * effectively gives rise to a "session layer", which we implement here in a fairly
 * direct way. <code>RPCSession</code> manages a session, doing session establishment
 * (login) and termination (logout) as needed. Additionally, it provides value-added 
 * services that allow arbitrary requestors to fire off requests that must execute
 * in a session context, and retries those requests should they fail for 
 * session-related reasons.
 * <p>Communication to the thread uses the Android Message/Handler/Looper paradigm.
 * This causes requests to be serialized on the thread, and that’s both good and bad news.
 * The good news aspect is that it's easy, the bad news is that we have to be
 * careful to not design in deadlocks. 
 * <p>Because session establishment also requires RPC ops, we have to be sure that, should
 * the need for session activity, that the thread will be available within a reasonable
 * amount of time, i.e. that all non-login requests are guaranteed to terminate within a
 * finite amount of time. To this end, <code>RPCSession</code> only retries requests that 
 * have failed for want of a valid session (denoted by LoginRequiredException as a result),
 * and there's at mode one retry, and that occurs only if session reestablishment succeeded. 
 * <p>When login succeeds, <code>RPCSession</code> tells the UI, which presumably
 * records the successful credentials for future reuse. In the event of login failure, 
 * <code>RPCSession</code> signals that fact to the UI, which presumably pops 
 * a login dialog for the user's benefit (or whatever) but ultimately signals for a 
 * restart, possibly with new credentials.
 * <p>Be aware that we are working across threads here!!
 */
public class RPCSession extends Thread {

	/**
	 * Interface implemented by RPC requestors. 
	 * <p>Note that its methods are invoked from the session thread!!
	 */
	public interface RPCRequest {
		/**
		 * Initiates an RPC request in the context of the current session.
		 * <p>When invoked, <code>request</code> should initiate a (synchronous) RPC request,
		 * using the supplied VoxIndexService and session ID parameters. Upon completion,
		 * it should then do whatever's needful to report the outcome to the caller.
		 * @param service The VoxIndexService object for the curent session.
		 * @param sessionID The currently active session ID.
		 * @throws LoginRequiredException Thrown if the RPC request fails because of
		 */
		void request(VoxIndexService service, String sessionID) throws LoginRequiredException;
		/**
		 * Reports that a request failed. 
		 * <p>This encompasses any any exceptions not handled in the <code>request</code>
		 * method, plus any exceptions generated by the session management code.
		 * @param t A Throwable indicating the cause of the failure.
		 */
		void failed(Throwable t);
	}	// RPCRequest
	
	/**
	 * Callback for session start notification. 
	 * <p>Note that its methods are invoked from the session thread!!
	 */
	public interface OnSessionStarted {
		public void onSuccess(LoginParams params);
		public void onFailure(String errorMessage);
	}	// OnSessionStarted
	
	/**
	 * Callback for session termination notification.
	 * <p>Note that its methods are invoked from the session thread!!
	 */
	public interface OnSessionTerminated {
		public void onSuccess();
	}
	
	/**
	 * Notification of session status change. 
	 * <p>Note that its methods are invoked from the session thread!!
	 */
	public interface OnSessionStatus {
		/**
		 * Invoked when the session thread has its act together, to a first approximation.
		 */
		public void onStarted();
		
		/**
		 * Invoked when a session has terminated and attempts to revive it have
		 * failed.
		 * @param errorMessage Message indicating cause of problem
		 */
		public void onFail(String errorMessage);
	}	// OnSessionStarted


	
	public static class NoSessionException extends Exception {
		public NoSessionException(String msg) { super(msg); }
	}

	public static final String Tag = "Session";
	
	String voxServiceRelativePath = "VoxLookup";
	
	final OnSessionStatus onSessionStatus;
	VoxIndexService voxService;
	Handler handler;
	
	LoginParams loginParams = null;
	
	
	public RPCSession(OnSessionStatus onSessionStatus) {
		this.onSessionStatus = onSessionStatus;
		this.start();
	}	// RPCSession
	
//	public LoginParams getParams() { return loginParams; }
//	public String getSessionID() { return loginParams.session; }
//	public boolean hasSession() { return loginParams.session != null; }
	
	/**
	 * Thread innards, using Looper to receive and process messages.
	 */
	@Override public void run() {
		Looper.prepare();
		handler = new Handler();
		onSessionStatus.onStarted();
		Looper.loop();
	}	// run	
	
	/**
	 * Starts a new session, firing off an attempt at login using the supplied 
	 * login parameters. 
	 * <b>Called from client (e.g. UI) thread.</b>
	 * @param params The initial login parameters (URI, user, password, session)
	 * @param callback Callback for session establishment notification.
	 */
	public void startSession(LoginParams params, final OnSessionStarted callback) {
		final LoginParams lp = new LoginParams(params);
		Message msg = Message.obtain(handler, new Runnable() {
			@Override public void run() {
				try {
					Log.i(Tag, "logging in to " + lp.serviceURI 
											+ " as " + lp.userID + " [" + lp.session + "]");
					voxService = 
							(VoxIndexService) SyncProxy.newProxyInstance(
															VoxIndexService.class, lp.serviceURI, voxServiceRelativePath);
					lp.session = voxService.login(lp.userID, lp.password, lp.session);
					Log.i(Tag, "login succeeded [" + lp.session + "]");
					loginParams = lp;
					callback.onSuccess(loginParams);
				} catch(AuthenticationException e) {
					String errorMessage = "Login failed: " + e.getMessage();
					Log.i(Tag, "login failed: " + errorMessage);
					callback.onFailure(errorMessage);
				} catch(Throwable t) {
					String errorMessage = "Exception on login: " + t.getMessage();
					Log.i(Tag, "login exception: " + errorMessage);
					callback.onFailure(errorMessage);
				}
			}});
		handler.sendMessage(msg);
	}	// startSession
	
	/**
	 * Logs out, firing off a request to the server if appropriate.
	 * <b>Called from client (e.g. UI) thread.</b>
	 * @param callback Completion callback.
	 */
	public void endSession(final OnSessionTerminated callback) {
		Message msg = Message.obtain(handler, new Runnable(){
			@Override public void run() {
				if (loginParams != null) {
					Log.i(Tag, "logging out [" + loginParams.session + "]");
					try {
						voxService.logout(loginParams.session);
						Log.i(Tag, "logout complete");
					} catch (Throwable t) {
						Log.i(Tag, "logout exception: " + t.getMessage());
					}
				} else 
						Log.i(Tag, "logout complete (not logged in)");
				loginParams = null;
				callback.onSuccess();
			}});
		handler.sendMessage(msg);
	}	// endSession
	
	/**
	 * Accepts an RPC request, and sends the request to the session thread for
	 * execution. <b>Called from any thread.</b>
	 * @param req The request to initiate
	 */
	public void doRPCRequest(final RPCRequest req) {
		Message msg = Message.obtain(handler, new Runnable() {
			@Override public void run() {
				runRPCRequest(req);
			}});
		handler.sendMessage(msg);
	}	// runRPCRequest
	
	/**
	 * Accepts an RPC request, and fires it off in the context of the current session.
	 * If the request reports a problem with the session, <code>runRPCRequest</code>
	 * takes appropriate action to restore the session and retry the request. 
	 * <b>Called only from the session thread!</p>
	 * @param req The request to fire off.
	 */
	private void runRPCRequest(RPCRequest req) {
		
		if (loginParams == null) {
			Log.i(Tag, "runRPCrequest no session");
			req.failed(new NoSessionException("No active session"));
		} else {
			int tries = 2;
			boolean retryable = true;
			while (loginParams != null && tries > 0) {
				Log.i(Tag, "runRPCrequest (" + tries + ")");
				try {
					tries--;
					req.request(voxService, loginParams.session);
					tries = 0;
					Log.i(Tag, "runRPCrequest complete");
				} catch(LoginRequiredException e) {
					try {
						Log.i(Tag, "re-logging in to " + loginParams.serviceURI 
												+ " as " + loginParams.userID + " [" + loginParams.session + "]");
						voxService = 
								(VoxIndexService) SyncProxy.newProxyInstance(
																VoxIndexService.class, loginParams.serviceURI, voxServiceRelativePath);
						loginParams.session = voxService.login(loginParams.userID, 
																										loginParams.password, 
																										loginParams.session);
						Log.i(Tag, "re-login succeeded [" + loginParams.session + "]");
					} catch(AuthenticationException ae) {
						String errorMessage = "Login error: " + ae.getMessage();
						Log.i(Tag, "re-login failed: " + errorMessage);
						loginParams = null;
						onSessionStatus.onFail(errorMessage);
					} catch(Throwable t) {
						String errorMessage = "Exception on login: " + t.getMessage();
						Log.i(Tag, "re-login exception: " + errorMessage);
						loginParams = null;
						onSessionStatus.onFail(errorMessage);
					}
				} catch (Throwable t) {
					Log.i(Tag, "runRPCrequest exception: " + t.getMessage());
					req.failed(t);
				}
			}
		}
	}	// runRPCRequest
	
}	// RPCSession
