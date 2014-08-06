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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import voxindex.shared.LoginRequiredException;
import voxindex.shared.LookupResult;
import voxindex.shared.VoxIndexService;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebBackForwardList;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TextView;
import android.widget.Toast;
import voxindex.audiology.R;

/**
 * Main application class.
 */
@SuppressLint({ "SimpleDateFormat", "DefaultLocale" }) 
public class Audiology extends Activity {

	public static final String Tag = "VoxIndex";

	// Keys for preferences
//	public static final String ServerNameKey = "server-name";
	public static final String RecognizerThresholdKey = "recognizer-threshold";
	public static final String SilenceThresholdKey = "silence-threshold";
	public static final String TextSizeKey = "text-size";
	public static final String LoginParamsKey = "login";

	TextView textView = null;
	ScrollView textScroller;

	Button startButton;
	Button stopButton;
	ImageView statusImage;
	ImageView resultImage;
	Button logButton;
	Button forwardButton;
	Button backButton;
	Button nextItemButton;
	Button prevItemButton;
	Button newTabButton;
	Button closeTabButton;
	Button indexButton;
	Button updateButton;
	TabHost tabHost;
	
	Dialog logDialog = null;
	Dialog serverNameDialog = null;
	Dialog recognitionThresholdDialog = null;
	Dialog belowThresholdDialog = null;
	Dialog silenceThresholdDialog = null;
	Dialog testDialog = null;
	Dialog textSizeDialog = null;

	InputMethodManager inputMethodManager;

	HashMap<String, TabContext> tabs = new HashMap<String, TabContext>();
	ArrayList<TabContext> tabSeq = new ArrayList<TabContext>();

	Audiator audiator;
	//VoxRequestor requestor;

	WakeLock wakeLock;

	Handler handler;

	StatusType audiatorState = StatusType.Idle;
	VoxOutcome voxResult = VoxOutcome.None;
	
	
	LoginParams loginParams = null;
	
	RPCSession session;

	LookupResult pendingResult;
	TextView voxMalText;
	Dialog voxMalDialog = null;
	Dialog showingDialog = null;
	LoginDialog loginDialog;
	Dialog busyDialog;

	boolean hexing = false;
	boolean saveToggleIsChecked = false;
//	String serviceURI = null;
	float recognizerThreshold;
	int silenceThreshold;
	// WebSettings.TextSize textSize;
	StringBuffer logText = new StringBuffer();

	HashMap<Integer, CharSequence> savedState = new HashMap<Integer, CharSequence>();
	WebBackForwardList savedWebView;

	public static final int MaxTabs = 8;

	String lastTabID = null;
	boolean tabUpdate = false;
	
	boolean createComplete = false;
	boolean sessionStarted = false;


	// TODO -- if we go to a non-foreground state, do a 'stop' if recording is in
	// progress

	/**
	 * Called by the application environment when the app is first fired up.
	 */
	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		Log.d(Tag, "onCreate");

		initializeResources();

		SharedPreferences pref = getPreferences(MODE_PRIVATE);
//		serviceURI = pref.getString(ServerNameKey, "");
		recognizerThreshold = pref.getFloat(RecognizerThresholdKey, (float) 0.66);
		silenceThreshold = pref.getInt(SilenceThresholdKey, 300);
		String rlp = pref.getString(LoginParamsKey, null);
		
		List<LoginParams> savedLoginParams;
		if (rlp == null) {
			savedLoginParams = new ArrayList<LoginParams>();
		} else {
			try {
				savedLoginParams = LoginParams.fromJSON(rlp);
			} catch (Exception e) {
				savedLoginParams = new ArrayList<LoginParams>();
			}
		}
		if (savedLoginParams.size() > 0)
			loginParams = savedLoginParams.get(0);
		else
			loginParams = new LoginParams();
		
		// String ts = pref.getString(TextSizeKey, "NORMAL");
		// try {
		// textSize = Enum.valueOf(WebSettings.TextSize.class, ts);
		// } catch(Exception e) {
		// textSize = WebSettings.TextSize.NORMAL;
		// }

		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "Audiology");
		inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

		handler = new Handler();
		// requestor = new VoxRequestor(this);
		audiator = new Audiator(this);
		audiator.setSilenceThreshold(silenceThreshold);
		session = new RPCSession(new RPCSession.OnSessionStatus() {
			@Override public void onFail(String errorMessage) {
				sendSessionFailed();
			}

			@Override public void onStarted() {
				handler.sendMessage(Message.obtain(handler, new Runnable() {
					@Override public void run() {
						// See the complement to this below!!
						boolean start;
						synchronized(Audiology.this) {
							sessionStarted = true;
							start = createComplete;
						}
						if (start)
							startTheSession();
					}}));
			}});
		 
		busyDialog = new AlertDialog.Builder(this)
			.setMessage(R.string.loggingIn)
			.create();

		loginDialog = new LoginDialog(new LoginDialog.OnRequest() {
			@Override public void onLoginRequest(LoginParams params) {
				Log.d(Tag, "Got new login params for " + params.repString());
				loginParams = params;
				session.startSession(loginParams, new RPCSession.OnSessionStarted(){
					@Override public void onSuccess(LoginParams params) { sendLoggedIn(params); }
					@Override public void onFailure(String errorMessage) { sendLoginFailed(errorMessage); }
				});
				// TODO -- ought to display busy indicator here
			}});
		loginDialog.setLoginParams(loginParams);
		
		updateUI();
		Log.d(Tag, "onCreate complete");
		

	}	// onCreate
	
	@Override protected void onStart() {
		super.onStart();
		Log.i(Tag, "onStart");
		// See the complement to this above!!
		boolean start;
		synchronized(this) {
			createComplete = true;
			start = sessionStarted;
		}
		if (start)
			startTheSession();
	}	// onStart
	
	@Override public void onPause() {
		super.onPause();
		Log.i(Tag, "onPause");
	}	// onPause

	@Override public void onResume() {
		super.onPause();
		Log.i(Tag, "onResume");
	}	// onResume
	
	@Override public void onStop() {
		super.onStop();
		Log.i(Tag, "onStop");
	}	// onResume
	
	

	
	/**
	 * Stuff that gets invoked after onCreate has completed *and* 
	 */
	private void startTheSession() {
		Log.d(Tag, "firing up the session");
		/* 
		 * If we've got enough information, try a login. Otherwise, pop the login dialog.
		 */
		if (loginParams.isLoginSufficient()) {
			busyDialog.show();
			session.startSession(loginParams, new RPCSession.OnSessionStarted(){
				@Override public void onSuccess(LoginParams params) { sendLoggedIn(params); }
				@Override public void onFailure(String errorMessage) { sendLoginFailed(errorMessage); }
				});
		} else 
			showLoginDialog();
	}	// startTheSession
	
	/**
	 * Called by the app environment when the external 'configuration' changes.
	 */
	@Override public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		Log.d(Tag, "onConfigurationChanged");
		// XXX ought to wait for audiator stop response before proceeding
		audiator.stop();
		saveState();
		initializeResources();
		restoreState();
		updateUI();
	} // onConfigurationChanged

	private Dialog makeLogDialog() {
		Dialog dialog = new Dialog(this);
		dialog.setContentView(R.layout.log_dialog);
		dialog.setTitle(R.string.logView);
		textView = (TextView) dialog.findViewById(R.id.textView);
		textView.setText(logText);
		textScroller = (ScrollView) dialog.findViewById(R.id.textScroller);
		return dialog;
	} // makeLogDialog

//	private Dialog makeServerNameDialog() {
//		final EditText serverURIText = new EditText(this);
//		serverURIText
//				.setText((serviceURI == null || serviceURI.isEmpty()) ? "http://"
//						: serviceURI);
//		Dialog dialog = new AlertDialog.Builder(this)
//				.setTitle(R.string.serverURIDialogTitle)
//				.setMessage(R.string.serverURIMessage)
//				.setView(serverURIText)
//				.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
//					@Override public void onClick(DialogInterface dialog, int which) {
//						try {
//							String sn = serverURIText.getText().toString();
//							if (!sn.startsWith("http://"))
//								sn = "http://" + sn;
//							new URI(sn);
//							serviceURI = sn;
//							SharedPreferences pref = getPreferences(MODE_PRIVATE);
//							SharedPreferences.Editor ed = pref.edit();
//							ed.putString(ServerNameKey, serviceURI);
//							ed.commit();
//							dialog.dismiss();
//							requestor.setServiceURI(serviceURI);
//							msg("Service address set to " + serviceURI);
//						} catch (URISyntaxException e) {
//							dialog.cancel();
//							Toast t = Toast.makeText(getApplicationContext(),
//									R.string.errInvalidURI, Toast.LENGTH_SHORT);
//							t.show();
//						}
//					}
//				})
//				.setNegativeButton(R.string.cancel,
//						new DialogInterface.OnClickListener() {
//							@Override public void onClick(DialogInterface dialog, int which) {
//								dialog.cancel();
//							}
//						}).create();
//		return dialog;
//	} // makeServerNameDialog

	
	private Dialog makeRecognitionThresholdDialog() {
		final EditText recognitionText = new EditText(this);
		recognitionText.setInputType(InputType.TYPE_CLASS_NUMBER);
		recognitionText.setText("" + (int) (100 * recognizerThreshold));
		Dialog dialog = new AlertDialog.Builder(this)
				.setTitle(R.string.recognitionThresholdDialogName)
				.setMessage(R.string.recognitionThresholdDialogMessage)
				.setView(recognitionText)
				.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
					@Override public void onClick(DialogInterface dialog, int arg1) {
						try {
							int thr = Integer.parseInt(recognitionText.getText().toString());
							if (thr < 0 || thr > 100) {
								throw new Exception("Wrong.");
							}
							recognizerThreshold = ((float) thr) / 100.0f;
							SharedPreferences pref = getPreferences(MODE_PRIVATE);
							SharedPreferences.Editor ed = pref.edit();
							ed.putFloat(RecognizerThresholdKey, recognizerThreshold);
							ed.commit();
							dialog.dismiss();
							msg("Recognizer threshold set to " + recognizerThreshold);
						} catch (Exception e) {
							dialog.cancel();
							Toast t = Toast.makeText(getApplicationContext(),
									R.string.recognitionThresholdInvalid, Toast.LENGTH_LONG);
							t.setGravity(Gravity.CENTER, 0, 0);
							t.show();
						}
					}
				})
				.setNegativeButton(R.string.cancel,
						new DialogInterface.OnClickListener() {
							@Override public void onClick(DialogInterface dialog, int which) {
								dialog.cancel();
							}
						}).create();
		return dialog;
	} // makeRecognitionThresholdDialog

	private Dialog makeSilenceThresholdDialog() {
		final EditText silenceText = new EditText(this);
		silenceText.setInputType(InputType.TYPE_CLASS_NUMBER);
		silenceText.setText("" + silenceThreshold);
		Dialog dialog = new AlertDialog.Builder(this)
				.setTitle(R.string.silenceThresholdDialogName)
				.setMessage(R.string.silenceThresholdDialogMessage)
				.setView(silenceText)
				.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
					@Override public void onClick(DialogInterface dialog, int arg1) {
						try {
							int thr = Integer.parseInt(silenceText.getText().toString());
							if (thr < 0 || thr > 32000) {
								throw new Exception("Wrong.");
							}
							silenceThreshold = thr;
							audiator.setSilenceThreshold(silenceThreshold);
							SharedPreferences pref = getPreferences(MODE_PRIVATE);
							SharedPreferences.Editor ed = pref.edit();
							ed.putInt(SilenceThresholdKey, silenceThreshold);
							ed.commit();
							dialog.dismiss();
							msg("Silence threshold set to " + silenceThreshold);
						} catch (Exception e) {
							dialog.cancel();
							Toast t = Toast.makeText(getApplicationContext(),
									R.string.silenceThresholdInvalid, Toast.LENGTH_LONG);
							t.setGravity(Gravity.CENTER, 0, 0);
							t.show();
						}
					}
				})
				.setNegativeButton(R.string.cancel,
						new DialogInterface.OnClickListener() {
							@Override public void onClick(DialogInterface dialog, int which) {
								dialog.cancel();
							}
						}).create();
		return dialog;
	}

	private Dialog makeTestDialog() {
		final EditText testText = new EditText(this);
		testText.setInputType(InputType.TYPE_CLASS_NUMBER);
		Dialog dialog = new AlertDialog.Builder(this)
				.setTitle(R.string.testDialogName)
				.setMessage(R.string.testDialogMessage)
				.setView(testText)
				.setPositiveButton(R.string.test,
						new DialogInterface.OnClickListener() {
							@Override public void onClick(DialogInterface dialog, int arg1) {
								try {
									final int thr = Integer.parseInt(testText.getText().toString());
									session.doRPCRequest(new RPCSession.RPCRequest(){

										@Override public void request(VoxIndexService service, String sessionID) 
																throws LoginRequiredException {
											Log.v(Tag, "VoxRequestor Ensquare (" + thr + ")");
											int rslt = service.square(thr);
											ensquareResult(rslt);
											Log.v(Tag, "VoxRequestor Ensquare result (" + rslt + ")");
										}

										@Override public void failed(Throwable t) {
											Log.e(Tag, "VoxRequestor Ensquare exception: ", t);
											requestException(t);
										}});
									dialog.dismiss();
								} catch (Exception e) {
									dialog.cancel();
									Toast t = Toast.makeText(getApplicationContext(),
											"Invalid value", Toast.LENGTH_LONG);
									t.setGravity(Gravity.CENTER, 0, 0);
									t.show();
								}
							}
						})
				.setNegativeButton(R.string.cancel,
						new DialogInterface.OnClickListener() {
							@Override public void onClick(DialogInterface dialog, int which) {
								dialog.cancel();
							}
						}).create();
		return dialog;
	} // makeTestDialog
	
//	private Dialog makeTextSizeDialog() {
//		LayoutInflater inflater = LayoutInflater.from(this);
//		final View tsview = inflater.inflate(R.layout.text_size, null);
//		final RadioGroup group = (RadioGroup) tsview
//				.findViewById(R.id.textSizeGroup);
//		WebSettings.TextSize ts = getCurrentTabContext().getTextSize();
//		RadioButton rb;
//		switch (ts) {
//		case SMALLEST:
//			rb = (RadioButton) tsview.findViewById(R.id.tinyText);
//			break;
//		case SMALLER:
//			rb = (RadioButton) tsview.findViewById(R.id.smallText);
//			break;
//		case NORMAL:
//			rb = (RadioButton) tsview.findViewById(R.id.normalText);
//			break;
//		case LARGER:
//			rb = (RadioButton) tsview.findViewById(R.id.largeText);
//			break;
//		case LARGEST:
//			rb = (RadioButton) tsview.findViewById(R.id.hugeText);
//			break;
//		default:
//			rb = (RadioButton) tsview.findViewById(R.id.normalText);
//			break;
//		}
//		rb.setChecked(true);
//		Dialog dialog = new AlertDialog.Builder(this)
//				.setTitle(R.string.textSizeDialogName)
//				.setView(tsview)
//				.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
//					@Override public void onClick(DialogInterface dialog, int which) {
//						WebSettings.TextSize tsize;
//						switch (group.getCheckedRadioButtonId()) {
//						case R.id.tinyText:
//							tsize = WebSettings.TextSize.SMALLEST;
//							break;
//						case R.id.smallText:
//							tsize = WebSettings.TextSize.SMALLER;
//							break;
//						case R.id.normalText:
//							tsize = WebSettings.TextSize.NORMAL;
//							break;
//						case R.id.largeText:
//							tsize = WebSettings.TextSize.LARGER;
//							break;
//						case R.id.hugeText:
//							tsize = WebSettings.TextSize.LARGEST;
//							break;
//						default:
//							tsize = WebSettings.TextSize.NORMAL;
//							break;
//						}
//						getCurrentTabContext().setTextSize(tsize);
//						// if (tsize != textSize) {
//						// // audiator.setTextSize(silenceThreshold);
//						// SharedPreferences pref = getPreferences(MODE_PRIVATE);
//						// SharedPreferences.Editor ed = pref.edit();
//						// ed.putString(TextSizeKey, tsize.toString());
//						// ed.commit();
//						// dialog.dismiss();
//						// msg("text size set to " + tsize);
//						// }
//					}
//				})
//				.setNegativeButton(R.string.cancel,
//						new DialogInterface.OnClickListener() {
//							@Override public void onClick(DialogInterface dialog, int which) {
//								dialog.cancel();
//							}
//						}).create();
//		return dialog;
//	} // makeTextSizeDialog

	private Dialog makeVoxMalDialog(View v) {
		return new AlertDialog.Builder(this)
				.setTitle(R.string.belowThresholdDialogTitle)
				.setMessage(R.string.belowThresholdRecognitionMessage).setView(v)
				.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
					@Override public void onClick(DialogInterface dialog, int arg1) {
						dialog.dismiss();
						processLookupResult(pendingResult);
					}
				})
				.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
					@Override public void onClick(DialogInterface dialog, int arg1) {
						dialog.cancel();
					}
				}).create();
	} // makeVoxMalDialog



	@Override public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.options, menu);
		return true;
	}

	/**
	 * Options menu item selection processing. Called by the app environment when
	 * the user selects an item in the options menu.
	 */
	@Override public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
//		case R.id.dumpVoice:
//			item.setChecked(!item.isChecked());
//			hexing = item.isChecked();
//			break;
//
//		case R.id.saveSpeech:
//			item.setChecked(!item.isChecked());
//			break;

//		case R.id.serverName:
//			if (serverNameDialog == null)
//				serverNameDialog = makeServerNameDialog();
//			serverNameDialog.show();
//			break;

		case R.id.recognitionThreshold:
			if (recognitionThresholdDialog == null)
				recognitionThresholdDialog = makeRecognitionThresholdDialog();
			recognitionThresholdDialog.show();
			break;

		case R.id.silenceThreshold:
			if (silenceThresholdDialog == null)
				silenceThresholdDialog = makeSilenceThresholdDialog();
			silenceThresholdDialog.show();
			break;

		case R.id.commTest:
			if (testDialog == null)
				testDialog = makeTestDialog();
			testDialog.show();
			break;

		case R.id.exeunt:
			finish();
			break;

		case R.id.jsEnable:
			item.setChecked(!item.isChecked());
			enableJavaScript(getCurrentTabContext(), item.isChecked());
			break;

//		case R.id.textSize:
//			if (textSizeDialog == null)
//				textSizeDialog = makeTextSizeDialog();
//			textSizeDialog.show();
//			break;

//		case R.id.login:
//			login.loginRequest(false, null, new LoginHorror.LoginCompleteCallback() {
//				@Override public void succeded(String sessionID) {
//				}
//
//				@Override public void failed() {
//				}
//			});
//			break;

		case R.id.logout:
			break;

		default:
			return super.onOptionsItemSelected(item);
		}
		return true;
	} // onOptionsItemSelected

	/**
	 * Returns the RPC session in use by the app.
	 * @return The RPC session object.
	 */
	public RPCSession getSession() { return session; }
	
	private void showLoginDialog() {
		showLoginDialog("");
	}	// showLoginDialog
	
	private void showLoginDialog(String what) {
		loginDialog.setMessage(what);
		loginDialog.show(getFragmentManager(), "fragment_login_dialog");
	}	// showLoginDialog

	
	private void hideLoginDialog() {
		/*
		 * Note that we use the homegrown hide() method as opposed to dismiss()
		 * to allow the dialog to be 'hidden' when it hasn't been displayed ever.
		 */
		loginDialog.hide();
	}	// hideLoginDialog

	private void sessionFailed() {
		busyDialog.dismiss();
		showLoginDialog();
	}	// sessionFailed
	
	/**
	 * Called upon successful login to save winning login parameters, etc.
	 * @param params Successful login parameters
	 */
	private void loginSucceeded(LoginParams params) {
		Log.i(Tag, "logged in to " + params.repString());	
		busyDialog.dismiss();
		String s = LoginParams.toJSON(Collections.singletonList(params));
		SharedPreferences pref = getPreferences(MODE_PRIVATE);
		SharedPreferences.Editor ed = pref.edit();
		if (params.remember) 
			ed.putString(LoginParamsKey, s);
		else
			ed.remove(LoginParamsKey);
		ed.commit();
		// Log.v(Tag, (params.remember ? "saved" : "did not save") + " login " + s);
		hideLoginDialog();
	}	// loginSucceeded

	private void loginFailed(String msg) {
		showLoginDialog();
		// TODO -- need to stop audiator if it isn't already
	}	// loginFailed
	
	private void loggedOut() {
		// TODO -- audiology logout
	}	// loggedOut 

	/**
	 * Handles the results of a speech recognition request made to the server.
	 * <p>
	 * The initial stages of processing occur here, and the rest in
	 * {@link processLookupResult}. This division occurs to deal with the case
	 * where a low-confidence recongition occurs, in which case we flash a dialog
	 * asking the user if the recognizer guessed right... and if the answer is
	 * affirmative, processing continues from the dialog.
	 * 
	 * @param result
	 *          The {@link LookupResult} returned from the server.
	 */
	public void lookupResult(final LookupResult result) {
		Message msg = Message.obtain(handler, new Runnable() {
			@Override public void run() {
				if (result == null) {
					voxResult = VoxOutcome.None;
					msg("Lookup result null");
				} else if (!result.recognized) {
					if (result.belowThreshold) {
						// we shouldn't see this any more
						voxResult = VoxOutcome.LowConfidence;
						msg("Lookup result: below threshold -- " + result.confidence);
					} else {
						voxResult = VoxOutcome.Rejected;
						msg("Lookup result: not recognized");
					}
				} else if (result.confidence < recognizerThreshold) {
					msg("Lookup result: below threshold -- " + result.confidence
							+ " text: " + result.recognizedText);
					pendingResult = result;
					voxMalDialog.setTitle(getString(R.string.belowThresholdDialogTitle)
							+ " -- "
							+ String.format("%2.2f%%", 100 * pendingResult.confidence));
					voxMalText.setText(pendingResult.recognizedText);
					voxMalDialog.show();
					showingDialog = voxMalDialog;
				} else
					processLookupResult(result);
				updateUI();
			}
		});
		handler.sendMessage(msg);
	}

	/**
	 * Processes a (possibly deferred) lookup result.
	 * <p>
	 * In ideal circumstances, this is called directly upon receiving a
	 * recognition result, by {@link #lookupResult(LookupResult)}. However, if the
	 * confidence for the recognition is below threshold, the result is passed to
	 * the BelowThresholdDialog to get user approval.
	 * 
	 * @param result
	 *          A {@link LookupResult} that's been accepted for further
	 *          processing.
	 */
	private void processLookupResult(LookupResult result) {
		voxResult = VoxOutcome.Accepted;
		if (result.commandURI != null) {
			if (RemoteCommand.isCommand(result.commandURI)) {
				msg("Lookup result --> command: " + result.commandURI);
				RemoteCommand.Command cmd = RemoteCommand.getCommand(result.commandURI);
				TabContext tab = tabs.get(result.tag);

				if (cmd != null) {
					switch (cmd) {
					case Back:
						if (tab != null)
							tab.goBack();
						break;
					case Forward:
						if (tab != null)
							tab.goForward();
						break;
					case Top:
						if (tab != null)
							tab.toTop();
						break;
					case Bottom:
						if (tab != null)
							tab.toBottom();
						break;
					case ScrollDown:
						if (tab != null)
							tab.scrollDown();
						break;
					case ScrollUp:
						if (tab != null)
							tab.scrollUp();
						break;
					case Move: {
						String post = RemoteCommand.getPostCommand(result.commandURI);
						int off = 1;
						try {
							if (post != null)
								off = Integer.parseInt(post);
						} catch (Exception e) {
						}
						if (off > 0)
							tab.pageDown(off);
						else if (off < 0)
							tab.pageUp(-off);
					}
						break;

					case Log:
						if (logDialog == null)
							logDialog = makeLogDialog();
						logDialog.show();
						break;
					case CloseLog:
						break;

					case Accept:
						break;
					case Reject:
						break;

					case Index: {
						String indexURI = RemoteCommand.getPostCommand(result.commandURI);
						if (indexURI != null && tab != null) {
							result.commandURI = null;
							result.indexName = tab.getIndexName();
							result.indexables = new LookupResult.Indexable[] { new LookupResult.Indexable(
									(indexURI.matches(".*\\?.*") ? "Active indexes"
											: "Index of indexes"), tab.getSource(), indexURI,
									tab.getIndexIDs()) };
							processLookupResult(result);
						}
					}
						break;

					case Tab: {
						String pc = RemoteCommand.getPostCommand(result.commandURI);
						if (pc != null) {
							int index = -1;
							try {
								index = Integer.parseInt(pc);
							} catch (Exception e) {
							}
							if (index > 0 && index <= MaxTabs) {
								tabHost.setCurrentTab(index - 1);
							}
						}
					}
						break;

					case Use: {
						String ix = RemoteCommand.getPostCommand(result.commandURI);
						if (ix != null) {
							Iterator<TabContext> tcs = tabs.values().iterator();
							TabContext tc = null;
							while (tcs.hasNext()
									&& !((tc = tcs.next()).getIndexIDs().contains(ix)))
								tc = null;
							if (tc != null)
								tabHost.setCurrentTabByTag(tc.getTag());
						}
					}
						break;

					case NewTab:
						newTab();
						break;
					case CloseTab:
						closeTab(tab);
						break;

					case PreviousVariant:
						tab.showVariant(-1);
						break;
					case NextVariant:
						tab.showVariant(1);
						break;

					case Stop:
						audiator.stop();
						break;
					case Exit:
						break;

					default:
						msg("Unrecognized command processed: " + result.commandURI);
					}
				}
			} else
				msg("Unrecognized command URI: " + result.commandURI);
		} else if (result.indexables != null && result.indexables.length > 0) {
			/*
			 * At this point, we believe we actually have something that's 
			 * an actual recognition of some set of indexables, and not a 
			 * command or rejection or any other funky thing.
			 */
			msg("Lookup result --> text: " + result.recognizedText + " tab: "
					+ result.tag + " " + result.indexName + " ("
					+ result.indexables.length + ")");
			int n = 0;
			for (LookupResult.Indexable ind : result.indexables) {
				msg("    (" + n++ + ") " + ind.source + " " + ind.title + " --> "
						+ ((ind.uri == null) ? "(null)" : ind.uri));
			}

			TabContext tab = tabs.get(result.tag);
			if (tab != null) {
				tab.newResult(result);
			} else
				msg("No tab found!!");
		} else {
			msg("Empty result!");
		}
	} // processLookupResult

	/**
	 * Handles an exception to a request made to the server. Just posts a Toast,
	 * and merrily goes on as though nothing had happened.
	 * 
	 * @param t
	 *          The throwable returned from the server request.
	 */
	public void requestException(final Throwable t) {
		Message msg = Message.obtain(handler, new Runnable() {
			@Override public void run() {
				msg("Request exception -- " + t.getMessage());
				Toast toast = Toast.makeText(getApplicationContext(),
						R.string.requestException + " -- \n" + t.getMessage(),
						Toast.LENGTH_SHORT);
				toast.show();
			}
		});
		handler.sendMessage(msg);
	}

	/**
	 * Handles a result from an "ensquare" test operation.
	 * 
	 * @param result
	 */
	public void ensquareResult(final int result) {
		Message msg = Message.obtain(handler, new Runnable() {
			@Override public void run() {
				msg("Squared result is " + result);
				Toast toast = Toast.makeText(getApplicationContext(), "Test result: "
						+ result, Toast.LENGTH_LONG);
				toast.show();
			}
		});
		handler.sendMessage(msg);
	} // ensquareResult

	/**
	 * Processes the result from a lookup request based on a link access in some
	 * TabContext.
	 * <p>
	 * We make a good-faith try to find the appropriate context and do the update,
	 * but if things don't line up precisely, the world won't end as a result.
	 * 
	 * @param result
	 *          The {@link LookupResult} object returned by the server.
	 */
	public void linkResult(final LookupResult result) {
		Message msg = Message.obtain(handler, new Runnable() {
			@Override public void run() {
				if (result != null && result.tag != null) {
					String[] ss = result.tag.split("\\:");
					if (ss.length == 2) {
						TabContext tc = tabs.get(ss[0]);
						if (tc != null) {
							tc.updateResult(result);
						}
					}
				}
			}
		});
		handler.sendMessage(msg);
	} // linkResult

	/**
	 * Creates a new tab.
	 * 
	 * @return The TabContext for the new tab.
	 */
	private TabContext newTab() {
		TabContext tc = new TabContext(Audiology.this, tabHost);
		tabHost.addTab(tc.getTabSpec());
		tabs.put(tc.getTag(), tc);
		tabSeq.add(tc);
		tabHost.setCurrentTabByTag(tc.getTag());
		return tc;
	} // newTab

	/**
	 * Closes the tab associated with the specified TabContext.
	 * 
	 * @param tab
	 *          The TabContext to close.
	 */
	private void closeTab(TabContext tab) {
		tabUpdate = true;
		if (tab != null && tabSeq.remove(tab)) {
			tabHost.setCurrentTab(0);
			tabHost.clearAllTabs();
			for (TabContext tc : tabSeq)
				tabHost.addTab(tc.getTabSpec());
		}
		tabUpdate = false;
	} // closeTab

	/**
	 * Relabels a tab's indicator.
	 * <p>
	 * This relies on the semi-hokey mechanism of changing the TabContext's
	 * TabSpec's indicator label, then doing a "refresh" by deleting all of the
	 * tabs from the TabHost, then re-adding them.
	 * <p>
	 * Also note: we set tabUpdate to true for the duration, as all this produces
	 * all sorts of state changes to the TabHost state, which gives rise to lots
	 * of tab-changed events. tabUpdate is a crummy mechanism that inhibits action
	 * by the tab-change event handler.
	 * 
	 * @param tab
	 *          The TabContext to change
	 * @param s
	 *          The new value of the tab indicator's label.
	 */
	private void labelTab(TabContext tab, String s) {
		tabUpdate = true;
		if (tab != null) {
			tab.getTabSpec().setIndicator(s);
			tabHost.setCurrentTab(0);
			tabHost.clearAllTabs();
			for (TabContext t : tabSeq)
				tabHost.addTab(t.getTabSpec());
			tabHost.setCurrentTabByTag(tab.getTag());
		}
		tabUpdate = false;
	}

	private void goBack(TabContext tab) {
		if (tab != null)
			tab.goBack();
	}

	private void goForward(TabContext tab) {
		if (tab != null)
			tab.goForward();
	}

	private void nextItem(TabContext tab) {
		if (tab != null)
			tab.showVariant(1);
	}

	private void previousItem(TabContext tab) {
		if (tab != null)
			tab.showVariant(-1);
	}

	private void enableJavaScript(TabContext tab, boolean enabled) {
		if (tab != null)
			tab.setJSEnabled(enabled);
	}

	@SuppressWarnings("unused") 
	private void indexRequest(TabContext tc, boolean active) {
		if (tc != null) {
			final String tag = tc.getTag();
			final Set<String> indexIDs = active ? tc.getIndexIDs() : null;
			session.doRPCRequest(new RPCSession.RPCRequest() {
					@Override public void request(VoxIndexService service, String sessionID) 
																		throws LoginRequiredException {
						Log.v(Tag, "VoxRequestor indexRequest (" + indexIDs + ")");
						LookupResult rslt =
								service.indexLookup(sessionID, tag, indexIDs);
						lookupResult(rslt);
					}	
					@Override public void failed(Throwable t) {
	    				Log.e(Tag, "VoxRequestor LinkRequest exception: ", t);
	    				requestException(t);
					}});
		}		
	}	// indexRequest

	/*--------------------------------------------------------------------------------
	 	Resource management things
	 --------------------------------------------------------------------------------*/

	private void initializeResources() {
		Configuration config = getResources().getConfiguration();
		if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) 
			setContentView(R.layout.landscape_tabbed);
		else
			setContentView(R.layout.portrait_tabbed);

		startButton = (Button) findViewById(R.id.startButton);
		stopButton = (Button) findViewById(R.id.stopButton);
		statusImage = (ImageView) findViewById(R.id.statusImage);
		resultImage = (ImageView) findViewById(R.id.resultImage);
		forwardButton = (Button) findViewById(R.id.forwardButton);
		backButton = (Button) findViewById(R.id.backButton);
		nextItemButton = (Button) findViewById(R.id.nextVariant);
		prevItemButton = (Button) findViewById(R.id.prevVariant);
		newTabButton = (Button) findViewById(R.id.newTab);
		closeTabButton = (Button) findViewById(R.id.closeTab);
		indexButton = (Button) findViewById(R.id.indexView);
		updateButton = (Button) findViewById(R.id.updateButton);
		logButton = (Button) findViewById(R.id.logButton);

		tabHost = (TabHost) findViewById(R.id.tabHost);
		tabHost.setup();

		/*
		 * We want to create the low-confidence dialog here instead of in onCreateDialog()
		 * so that we have access to its text box from the outset. 
		 */
		View v = getLayoutInflater().inflate(R.layout.vox_mal, null);
		voxMalText = (TextView) v.findViewById(R.id.voxMalText);
		voxMalDialog = makeVoxMalDialog(v);

		logDialog = makeLogDialog();

		getWindow().setSoftInputMode(
				WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

		startButton.setOnClickListener(new OnClickListener() {
			@Override public void onClick(View arg0) {
//				if (serviceURI.length() == 0) {
//					Toast.makeText(getApplicationContext(), R.string.noURI, Toast.LENGTH_SHORT).show();
//				} else
					audiator.start();
			}
		});
		stopButton.setOnClickListener(new OnClickListener() {
			@Override public void onClick(View arg0) { audiator.stop(); } });

		forwardButton.setOnClickListener(new OnClickListener() {
			@Override public void onClick(View arg0) { goForward(getCurrentTabContext()); } });

		backButton.setOnClickListener(new OnClickListener() {
			@Override public void onClick(View v) { goBack(getCurrentTabContext()); } });

		nextItemButton.setOnClickListener(new OnClickListener() {
			@Override public void onClick(View v) { nextItem(getCurrentTabContext()); }});

		prevItemButton.setOnClickListener(new OnClickListener() {
			@Override public void onClick(View v) { previousItem(getCurrentTabContext()); } });

		newTabButton.setOnClickListener(new OnClickListener() {
			@Override public void onClick(View v) { newTab(); } });

		closeTabButton.setOnClickListener(new OnClickListener() {
			@Override public void onClick(View v) { closeTab(getCurrentTabContext()); }});

		updateButton.setOnClickListener(new OnClickListener() {
			@Override public void onClick(View v) { 
				session.doRPCRequest(new RPCSession.RPCRequest() {
					@Override public void request(VoxIndexService service, String sessionID) 
															throws LoginRequiredException {
	    				Log.v(Tag, "refresh requested");
	    				service.refresh(sessionID);
	    				msg("VoxRequestor refresh complete");
	    				Log.v(Tag, "refresh complete");
					}
					@Override public void failed(Throwable t) {
	    				Log.e(Tag, "refresh exception: ", t);
	    				requestException(t);
					}});
				 }});

		logButton.setOnClickListener(new OnClickListener() {
			@Override public void onClick(View v) { logDialog.show(); }});

		if (lastTabID == null) {
			TabContext tc = newTab();
			lastTabID = tc.getTag();
		} else {
			Log.d(Tag, "Rehosting tabs (" + tabs.size() + ")");
			tabUpdate = true;
			tabHost.setCurrentTab(0);
			for (TabContext tc : tabSeq) {
				tc.rehost(tabHost);
				tabHost.addTab(tc.getTabSpec());
			}
			tabHost.setCurrentTabByTag(lastTabID);
			tabUpdate = false;
		}

		tabHost.setOnTabChangedListener(new OnTabChangeListener() {
			@Override public void onTabChanged(String tabId) {
				if (!tabUpdate) {
					InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
					imm.hideSoftInputFromWindow(tabHost.getApplicationWindowToken(), 0);
					if (!lastTabID.equals(tabId)) {
						TabContext tc = tabs.get(lastTabID);
						if (tc != null)
							tc.unselect();
						lastTabID = tabId;
					}
					updateTabState();
				}
			}
		});

		getWindow().setSoftInputMode(
				WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
	} // initializeResources

	private void saveState() {
		// savedState.put(R.id.textView, textView.getText());
	}

	private void restoreState() {
		// textView.setText(savedState.get(R.id.textView));
	}

	private boolean nonempty(String s) {
		return s != null && !s.isEmpty();
	}

	public void updateTabState() {
		String tag = tabHost.getCurrentTabTag();
		TabContext tc = tabs.get(tag);
		if (tc != null) {
			nextItemButton.setEnabled(tc.hasVariants());
			prevItemButton.setEnabled(tc.hasVariants());
			backButton.setEnabled(tc.hasBack());
			forwardButton.setEnabled(tc.hasForward());
			audiator.setRequestContext(tc.getTag(), tc.getIndexIDs());
			String name = tc.getIndexName();
			String source = tc.getSource();
			String title = tc.getTitle();
			int index = tc.getNth();
			int of = tc.getN();
			if (nonempty(name) && nonempty(title) && nonempty(source)) {
				setTitle(getString(R.string.app_name) + " -- " + source + " | " + title
						+ ((of > 1) ? ("  --  " + (index + 1) + " of " + of) : ""));
				labelTab(tc, source);
			} else {
				setTitle(getString(R.string.app_name));
				labelTab(tc, "[...]");
			}
			tc.select();
		}
	}

	private void updateUI() {
		Log.v(Tag, "UpdateUI state is " + audiatorState);
		startButton.setEnabled(audiatorState.startEnabled());
		startButton.setFocusable(audiatorState.startEnabled());
		stopButton.setEnabled(audiatorState.stopEnabled());
		stopButton.setFocusable(audiatorState.stopEnabled());
		statusImage.setImageResource(audiatorState.imageID());
		resultImage
				.setImageResource((audiatorState == StatusType.VoiceDetected) ? VoxOutcome.None
						.imageID() : voxResult.imageID());
		updateTabState();
	} // updatteUI

	private TabContext getCurrentTabContext() {
		String tag = tabHost.getCurrentTabTag();
		return tabs.get(tag);
	} // getCurrentTabContext

	/*--------------------------------------------------------------------------------
	 	Misc. utilities & etc.
	 --------------------------------------------------------------------------------*/

	public boolean getHexDump() {
		return hexing;
	}

	private static SimpleDateFormat dateFormat = new SimpleDateFormat(
			"MM-dd HH:mm:ss.SSS");

	/**
	 * Adds a message to the log.
	 * <p>
	 * Note that this can only be called by functionalities that are running from
	 * the main app thread... other threads must use {@link sendDisplayMessage}.
	 * 
	 * @param message
	 *          The message to log.
	 */
	public void msg(String message) {
		String s = dateFormat.format(new Date()) + " :: " + message + "\n";
		logText.append(s);
		if (textView != null) {
			textView.append(s);
			textScroller.fullScroll(View.FOCUS_DOWN);
		}
	}	// msg
	
	/*--------------------------------------------------------------------------------
	 	Inter-thread signalling support glop
	 --------------------------------------------------------------------------------*/
	public void signalVoiceAcquired() { sendSignal(StatusType.VoiceAcquired); }
	public void signalVoiceOverflow() { sendSignal(StatusType.VoiceOverflow); }
	public void signalVoiceDetected() { sendSignal(StatusType.VoiceDetected); }
	public void signalIdle() { sendSignal(StatusType.Idle); }
	public void signalRecording() { sendSignal(StatusType.Recording); }
	public void signalFailed() { sendSignal(StatusType.Failed); }

	private void sendSignal(final StatusType what) {
		Message msg = Message.obtain(handler, new Runnable() {
			@Override public void run() {
				audiatorState = what;
				msg("Got message: " + audiatorState.toString());
				updateUI();
			}
		});
		handler.sendMessage(msg);
	}

	public void sendDisplayMessage(final String string) {
		Message msg = Message.obtain(handler, new Runnable() {
			@Override public void run() { msg(string); } });
		handler.sendMessage(msg);
	}
	
	public void sendSessionFailed() {
		Message msg = Message.obtain(handler, new Runnable() {
			@Override public void run() { sessionFailed(); } });
		handler.sendMessage(msg);
	}	// sendSessionFailed

	public void sendLoggedIn(final LoginParams params) {
		Message msg = Message.obtain(handler, new Runnable() {
			@Override public void run() { loginSucceeded(params); }});
		handler.sendMessage(msg);
	}	// sendLoginOK
	
	public void sendLoginFailed(final String message) {
		Message msg = Message.obtain(handler, new Runnable() {
			@Override public void run() { loginFailed(message); }});
		handler.sendMessage(msg);
	}	// sendLoginFailed
	
	public void sendLoggedOut() {
		Message msg = Message.obtain(handler, new Runnable() { 
			@Override public void run() { loggedOut(); }});
		handler.sendMessage(msg);
	}	// sendLoginFailed
	
 
}