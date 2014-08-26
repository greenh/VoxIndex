package voxfront.client;

import voxfront.client.webaudio.Audiator;
import voxindex.shared.LoginRequiredException;
import voxindex.shared.VoxIndexIDs;
import voxindex.shared.VoxIndexService;
import voxindex.shared.VoxIndexServiceAsync;
import voxindex.shared.LookupResult;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.media.client.Audio;
import com.google.gwt.typedarrays.client.ArrayBufferNative;
import com.google.gwt.typedarrays.client.DataViewNative;
import com.google.gwt.typedarrays.shared.DataView;
import com.google.gwt.user.client.Cookies;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.rpc.ServiceDefTarget;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HasVerticalAlignment;
import com.google.gwt.user.client.ui.Label;

import java.util.Date;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import elemental.html.Blob;


/**
 * Main web-based UI for VoxIndex.
 */
public class VoxFront implements EntryPoint {
	

	public static final VoxIndexServiceAsync voxIndexService = 
			(VoxIndexServiceAsync) GWT.create(VoxIndexService.class);

	public static enum OpState { Uninit, Idle, Running }
	
	public static final String SessionCookieName = "session";
	public static final String UserCookieName = "user";
	public static final int ExTime = 1000 * 60 * 60 * 24 * 2;  // i.e., 2 days in msec.
	


	String userID;
	String password;
	String sessionID;

	Button logoutButton;
	Label userIdLabel;
	VerticalPanel logPanel;
	private Button startButton;
	private Button stopButton;
	private HorizontalPanel horizontalPanel_1;
	private Image stateIndicator;
	private Image reqIndicator;
	private Label utterBox;
	private Button playButton;
	private Anchor downloadGiz;
	private Audio audio;
	private Front front;

	private Audiator audiator;
	
	/*
	 * The following represent the state of what's going on, and are used
	 * by updateUI() to set the UI appropriately. So... whenever any of these
	 * change value, updateUI() must be called!!
	 */
	private OpState opState = OpState.Uninit;
	private RequestStatus requestState = RequestStatus.None;
	private AudiatorStatus audioStatus = AudiatorStatus.Idle;
	private String lastText = "";
	
	private LoginDialog loginDialog;
	private BusyDialog busyDialog;


	private static Logger logger = Logger.getLogger("VoxFront");

	/**
	 * Main entry point.
	 */
	@Override public void onModuleLoad() {
		((ServiceDefTarget) voxIndexService)
				.setServiceEntryPoint("/vox-index/voxindex.VoxIndex/VoxLookup");

		RootPanel rootPanel = RootPanel.get();
		rootPanel.setSize("100%", "100%");

		VerticalPanel verticalPanel = new VerticalPanel();
		verticalPanel.setStyleName("item-panel");
		rootPanel.add(verticalPanel, 0, 0);
		verticalPanel.setSize("100%", "100%");

		HorizontalPanel topPanel = new HorizontalPanel();
		topPanel.setVerticalAlignment(HasVerticalAlignment.ALIGN_MIDDLE);
		topPanel.setSpacing(4);
		verticalPanel.add(topPanel);
		verticalPanel.setCellVerticalAlignment(topPanel, HasVerticalAlignment.ALIGN_MIDDLE);
		verticalPanel.setCellHorizontalAlignment(topPanel, HasHorizontalAlignment.ALIGN_RIGHT);
		topPanel.setWidth("100%");
		verticalPanel.setCellWidth(topPanel, "100%");

		horizontalPanel_1 = new HorizontalPanel();
		topPanel.add(horizontalPanel_1);
		horizontalPanel_1.setSize("100%", "1");
		topPanel.setCellWidth(horizontalPanel_1, "100%");

		userIdLabel = new Label("");
		userIdLabel.setWordWrap(false);
		topPanel.add(userIdLabel);
		topPanel.setCellVerticalAlignment(userIdLabel,
				HasVerticalAlignment.ALIGN_MIDDLE);
		topPanel.setCellWidth(userIdLabel, "100%");
		userIdLabel.setWidth("100%");

		logoutButton = new Button("logout");
		topPanel.add(logoutButton);

		logoutButton.addClickHandler(new ClickHandler() {
			@Override public void onClick(ClickEvent event) {
				doLogout();
				loginDialog.doLogin();
			}});

		HorizontalPanel controlPanel = new HorizontalPanel();
		controlPanel.setVerticalAlignment(HasVerticalAlignment.ALIGN_MIDDLE);
		controlPanel.setSpacing(4);
		verticalPanel.add(controlPanel);
		verticalPanel.setCellVerticalAlignment(controlPanel,
				HasVerticalAlignment.ALIGN_MIDDLE);
		controlPanel.setWidth("100%");

		startButton = new Button("Start");
		controlPanel.add(startButton);
		controlPanel.setCellHeight(startButton, "24");
		controlPanel.setCellWidth(startButton, "80");
		startButton.setSize("80", "24");
		startButton.setEnabled(false);

		startButton.addClickHandler(new ClickHandler() {
			@Override public void onClick(ClickEvent event) { audiator.start(); }});

		stopButton = new Button("Stop");
		controlPanel.add(stopButton);
		controlPanel.setCellHeight(stopButton, "24");
		controlPanel.setCellWidth(stopButton, "80");
		stopButton.setSize("80", "24");
		stopButton.setEnabled(false);

		stopButton.addClickHandler(new ClickHandler() {
			@Override public void onClick(ClickEvent event) { audiator.stop(); }});

		stateIndicator = new Image(Resources.R.white_dot());
		controlPanel.add(stateIndicator);
		controlPanel.setCellVerticalAlignment(stateIndicator,
				HasVerticalAlignment.ALIGN_BOTTOM);
		controlPanel.setCellHorizontalAlignment(stateIndicator,
				HasHorizontalAlignment.ALIGN_CENTER);
		stateIndicator.setSize("24", "24");
		controlPanel.setCellHeight(stateIndicator, "24");
		controlPanel.setCellWidth(stateIndicator, "30");

		reqIndicator = new Image(Resources.R.blank());
		controlPanel.add(reqIndicator);
		controlPanel.setCellVerticalAlignment(reqIndicator,
				HasVerticalAlignment.ALIGN_BOTTOM);
		controlPanel.setCellHorizontalAlignment(reqIndicator,
				HasHorizontalAlignment.ALIGN_CENTER);
		reqIndicator.setSize("24", "24");
		controlPanel.setCellHeight(reqIndicator, "24");
		controlPanel.setCellWidth(reqIndicator, "30");

		utterBox = new Label();
		controlPanel.add(utterBox);
		controlPanel.setCellHeight(utterBox, "24");
		controlPanel.setCellWidth(utterBox, "100%");
		utterBox.setSize("100%", "24");
		// utterBox.setReadOnly(true);
		
		audio = Audio.createIfSupported();
		audio.setControls(false);
		// audio.setEnabled(false);
		// bottomPanel.add(audio);
	
		downloadGiz = new Anchor("Download");
		downloadGiz.setEnabled(false);
		controlPanel.add(downloadGiz);
		downloadGiz.setHeight("");

		playButton = new Button("Play");
		playButton.setEnabled(false);
		playButton.addClickHandler(new ClickHandler() {
			@Override public void onClick(ClickEvent event) {
				audio.play();
			}});
		controlPanel.add(playButton);
	
		audiator = new Audiator(this);
		audiator.initialize();
		
		front = new Front();
		verticalPanel.add(front);
		verticalPanel.setCellWidth(front, "100%");
		verticalPanel.setCellHeight(front, "100%");
		
		loginDialog = new LoginDialog();
		loginDialog.setLoginCallback(
				new LoginDialog.LoginCallback() {
					@Override public void onCancel() { }
			
					@Override public void onLogin(String loginID, String pwd, 
													String session, boolean remember) {
						setLogin(loginID, pwd, session, remember);
					}
				});;
				
		busyDialog = new BusyDialog();
				
		
		/*
		 * Theory of operation for sessions/logins/credentials/etc.:
		 * 1) Nothing happens unless and until successful login has occurred,
		 * and a session is established.
		 * 2) The loginDialog associated with the instance of VoxFront retains 
		 * in-memory current credentials.
		 * 3) Session reestablishment occurs automagically if it's interrupted
		 * for the lifetime of the VoxFront instance.
		 * 4) User may choose to retain credentials on the system. This is done
		 * via a cookie, which is restored (right here, in fact) during startup,
		 * and created as needed post successful login.
		 */
		String uu = Cookies.getCookie(UserCookieName);
		String[] s = (uu != null) ? uu.split("/") : null;
		if (uu != null && s.length == 2) {
			userIdLabel.setText(s[0]);
			loginDialog.setCredentials(s[0], s[1]);
			String session = Cookies.getCookie(SessionCookieName);
			busyDialog.showBusy("Logging in...");
			logger.log(Level.INFO, "autologin using " + s[0] + " session " + session);
			voxIndexService.login(s[0], s[1], session, new AsyncCallback<String>() {
				@Override public void onFailure(Throwable caught) {
					busyDialog.hide();
					loginDialog.doLogin();
				}
				@Override public void onSuccess(String session) {
					busyDialog.hide();
					setSession(session);
				}
				});
		} else{
			loginDialog.setCredentials("", "");
			loginDialog.doLogin();
		}
	}	// onModuleLoad

	public void doLogout() {
		voxIndexService.logout(sessionID, new AsyncCallback<Void>() {
			@Override public void onFailure(Throwable caught) {
				resetLogin();
			}

			@Override public void onSuccess(Void result) {
				resetLogin();
			}});
	}	// doLogout
	
	public void setSession(String sessionID) {
		logger.log(Level.INFO, "using session " + sessionID);
		this.sessionID = sessionID;
		Cookies.setCookie(SessionCookieName, sessionID, 
					new Date(System.currentTimeMillis() + ExTime));
	}
	
	/**
	 * Simple dumb cheap representation for a request that's subject to retry.
	 * <p>This is notionally used for requests that can be failed due to session
	 * failure. If a new session can be reestablished automatically, this provides
	 * the wherewithal to restart the request, or to report failure if the session
	 * can't be reestablished.
	 */
	public interface RetryableRequest {
		void request(String sessionID);
		void failed(Throwable t);
	}

	/**
	 * Attempts a login from retained credentials, and then attempts
	 * to fire off a request.
	 * <p>This is used as a mechanism for retrying requests that fail due
	 * to session failure. It's called by an async request when it detects a 
	 * login-required exception.
	 * <p><prog>doLoginThen</prog> tries to reestablish a session by repeating 
	 * the login based on retained credentials (the assumption here being that
	 * we always have retained credentials that have been verified at least once,
	 * else we wouldn't be firing off requests!). If the login works, we get
	 * a new session ID, and <prog>doLoginThen</prog> retries the request, handing
	 * it the new session ID.
	 * <p>If login fails, <prog>doLoginThen</prog> fails the request and pops the 
	 * login dialog.
	 * @param request The request to retry post-login.
	 */
	public void doLoginThenRetry(final RetryableRequest request) {
		logger.log(Level.INFO, "login for retry " + userID + " (no session)");
		voxIndexService.login(userID, password, null, new AsyncCallback<String>(){

			@Override public void onFailure(Throwable caught) {
				logger.log(Level.WARNING, "login for retry failed -- " + caught.getMessage());
				request.failed(caught);
				loginDialog.doLogin();
			}

			@Override public void onSuccess(String result) {
				logger.log(Level.INFO, "login for retry OK, session " + result);
				setSession(result);
				request.request(result);
			}});
	}	// doLoginThenRetry
	
	/**
	 * Fires off a retryable request based on the current session ID.
	 * @param req The request to fire off.
	 */
	public void runRetryableRequest(RetryableRequest req) {
		req.request(sessionID);
	}

	/**
	 * Voice input handler... and the main voice lookup mechanism, too.
	 * <p><code>deliverWave<code> gets called when the audio input stream 
	 * processing worker code has accumulated a blob of putative voice
	 * data (really anything that's distinguishable from noise), and has
	 * formatted it into a WAVE structure. Here, we laboriously package the
	 * WAVE data up with context information and fire it all off in a request 
	 * for voice recognition at the server.
	 * 
	 * @param wave
	 * @param bytes
	 */
	public void deliverWave(ArrayBufferNative wave, int bytes) {
		Blob blob = Native.makeBlob(wave, bytes, "audio/wav");
		String blobURL = Native.createObjectURL(blob);
		downloadGiz.setHref(blobURL);
		downloadGiz.setEnabled(true);
		audio.setSrc(blobURL);
		playButton.setEnabled(true);
		
		DataView dv = DataViewNative.create(wave);
		final byte[] b = new byte[bytes];
		for (int i = 0; i < bytes; i++) b[i] = (byte) dv.getUint8(i);   // ugh to the max
		
		requestState = RequestStatus.InProgress;
		updateUI();
		runRetryableRequest(new RetryableRequest() {
			byte[] bytes = b;
			String tag = front.getSelectedTag();
			Set<String> ids = front.getSelectedIDs();
			RetryableRequest request = this;
			
			@Override public void request(String session) {
				logger.log(Level.INFO, "requesting voxLookup session " + session + " tag " + tag);
				voxIndexService.voxLookup(session, tag, ids, bytes,
						new AsyncCallback<LookupResult>() {
							@Override public void onFailure(Throwable caught) {
								logger.log(Level.WARNING, "voxLookup failed: " + caught.getMessage());
								if (caught instanceof LoginRequiredException) 
									doLoginThenRetry(request);
							}	// onFailure
							
							@Override public void onSuccess(LookupResult result) {
								logger.log(Level.INFO, "lookup succeeded");
								lookupResponse(result);
							}	// onSuccess
				});
			}	// request

			@Override public void failed(Throwable t) {
				logger.log(Level.WARNING, "Lookup request was failed", t);
			}	// failed
		});
		
//		voxIndexService.voxLookup(sessionID, front.getSelectedTag(), 
//				front.getSelectedIDs(), b, null, // null -> old filename thing
//				new AsyncCallback<LookupResult>() {
//					@Override public void onFailure(Throwable caught) {
//						if (caught instanceof LoginRequiredException)
//						
//						
//						logger.log(Level.WARNING, "Lookup request failed", caught);
//					}
//					@Override public void onSuccess(LookupResult result) {
//						lookupResponse(result);
//					}});
	}	// deliverWave
	
	/**
	 * Processes a response to a voice lookup request. 
	 * <P>We look at the result we've been handed, and, in the case
	 * that something was actually recognized, pull out several command
	 * cases (e.g., "stop") that are related to
	 * the audio processing business. If there's nothing recognized 
	 * here, forward the result on to the browser part of the UI
	 * for further dissection.
	 * @param result The result object from the recognizer.
	 */
	public void lookupResponse(LookupResult result) {
		if (result.recognized) {
			logger.log(Level.INFO, "Lookup recognized --" 
							+ " confidence: " + result.confidence
							+ " index name: " + result.indexName
							+ " recognized text: " + result.recognizedText
							+ " command URI: " + result.commandURI
							+ " indexables: (" + result.indexables.length + ")");
			for (LookupResult.Indexable ix : result.indexables) {
				logger.log(Level.INFO, "indexable: "
						+ " iid: " + ix.indexIDs
						+ " source: " + ix.source
						+ " title: " + ix.title
						+ " uri: " + ix.uri);
			}
			requestState = RequestStatus.Accepted;
			lastText = result.recognizedText;
			updateUI();
			
			if (VoxIndexIDs.isCmdURN(result.commandURI, "stop"))
				audiator.stop();
			else
				front.lookupResult(result);
		} else {
			logger.log(Level.INFO, "Lookup result: not recognized");
			requestState = RequestStatus.Rejected;
			updateUI();
		}
	}	// lookupResponse

	public void audiatorFailed(int code) {
		Window.alert("Audio initialization failed: " + code);
	}	// audiatorFailed
	
	public void audiatorInitialized() { 
		opState = OpState.Idle;
		logger.log(Level.INFO, "audiatorInitialized");
		updateUI();
	}	// audiatorInitialized
	
	public void audiatorRunning() {
		opState = OpState.Running;
		logger.log(Level.INFO, "audiatorRunning");
		updateUI();
	}
	
	public void audiatorStopped() {
		opState = OpState.Idle;
		logger.log(Level.INFO, "audiatorStopped");
		updateUI();		
	}
	
	public void audiatorStatus(String stat) {
		audioStatus = AudiatorStatus.statusMap.get(stat);
		if (audioStatus == null) {
			logger.log(Level.INFO, "Eeek! Unknown status from audiator -- " + stat);
			audioStatus = AudiatorStatus.Unknown;
		}
		updateUI();
	}	// audiatorStatus

	/**
	 * One and only authorized location for setting the state of all the thingies in
	 * the main window. This needs to be called each time any significate state change
	 * is detected.
	 * @p Here, we look at all of the state variables present in the app, and set 
	 * the various UI elements to reflect what's going on.
	 */
	public void updateUI() {
		switch (opState) {
		case Uninit:
			startButton.setEnabled(false);
			stopButton.setEnabled(false);
			break;
		case Idle:
			startButton.setEnabled(true);
			stopButton.setEnabled(false);
			break;
		case Running:
			startButton.setEnabled(false); 
			stopButton.setEnabled(true);
			break;
		}
		stateIndicator.setResource(audioStatus.image());
		reqIndicator.setResource(requestState.image());
		if (requestState == RequestStatus.Accepted) 
			utterBox.setText(lastText);
		else
			utterBox.setText("");
	}	// updateUI

	public void resetLogin() {
		// logoutButton.setText("login");
		userIdLabel.setText("");
		Cookies.removeCookie(UserCookieName);
		Cookies.removeCookie(SessionCookieName);
	}	// resetLogin
	
	public void setLogin(String user, String password, String session, boolean remember) {
		logger.log(Level.INFO, "setLogin " + user + " " + password + " " + remember + " " + session);

		userID = user;
		setSession(session);
		// logoutButton.setText("logout");
		userIdLabel.setText(userID);
		if (remember) {
			Cookies.setCookie(UserCookieName, userID + "/" + password, 
					new Date(System.currentTimeMillis() + ExTime));
		} else {
			Cookies.removeCookie(UserCookieName);
			Cookies.removeCookie(SessionCookieName);
		}
	}	// setLogin
	

}
