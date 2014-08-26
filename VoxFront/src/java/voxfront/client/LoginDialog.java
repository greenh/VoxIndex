package voxfront.client;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.Cookies;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.HasVerticalAlignment;
import com.google.gwt.user.client.ui.CheckBox;

public class LoginDialog extends DialogBox {
	
	public interface LoginCallback {
		public void onLogin(String loginID, String password, String sessionID, boolean remember);
		public void onCancel();
	}
	
	String userID = "";
	String password = "";
	boolean remember = true;
	
	WiredTextBox userBox;
	PasswordTextBox passwordBox;
	Button loginButton;
//	Button cancelButton;
	
	LoginCallback callback;
	private Label msgLabel;
	private HorizontalPanel horizontalPanel_1; 
	private CheckBox memoryBox;
	
	private Logger logger = Logger.getLogger("VoxFront/LoginDialog");
	
	public LoginDialog() {
		setAnimationEnabled(true);
		setGlassEnabled(true);
		
		VerticalPanel verticalPanel = new VerticalPanel();
		setWidget(verticalPanel);
		verticalPanel.setSize("377px", "100%");
		
		FlexTable flexTable = new FlexTable();
		flexTable.setCellSpacing(4);
		flexTable.setCellPadding(4);
		verticalPanel.add(flexTable);
		verticalPanel.setCellHeight(flexTable, "100%");
		flexTable.setSize("100%", "100%");
		verticalPanel.setCellWidth(flexTable, "100%");
		
		Label lblUserId = new Label("User ID");
		flexTable.setWidget(0, 0, lblUserId);
		
		userBox = new WiredTextBox();
		userBox.setText(userID);
		flexTable.setWidget(0, 1, userBox);
		userBox.setSize("100%", "");
		flexTable.getCellFormatter().setHeight(0, 1, "100%");
		flexTable.getCellFormatter().setWidth(0, 1, "100%");
		
		userBox.addKeyUpHandler(new KeyUpHandler() {
			@Override public void onKeyUp(KeyUpEvent event) {
				// ConLog.log("keyup " + userBox.getText());
				checkEnableLogin();
			}});
		
		userBox.addKeyPressHandler(new KeyPressHandler() {
			@Override public void onKeyPress(KeyPressEvent event) {
				if (event.getCharCode() == KeyCodes.KEY_ENTER && checkEnableLogin())
					startLogin();
			}});
				
		userBox.addValueChangeHandler(new ValueChangeHandler<String>() {

			@Override public void onValueChange(ValueChangeEvent<String> event) {
				// ConLog.log("valueChange "  + userBox.getText());
				checkEnableLogin();
			}});

		
		Label lblPassword = new Label("Password");
		flexTable.setWidget(1, 0, lblPassword);
		
		passwordBox = new PasswordTextBox();
		flexTable.setWidget(1, 1, passwordBox);
		passwordBox.setSize("100%", "100%");
		flexTable.getCellFormatter().setHeight(1, 1, "100%");
		flexTable.getCellFormatter().setWidth(1, 1, "100%");
		
		passwordBox.addKeyUpHandler(new KeyUpHandler() {
			@Override public void onKeyUp(KeyUpEvent event) {
				// ConLog.log("keyup " + userBox.getText());
				checkEnableLogin();
			}});

		passwordBox.addKeyPressHandler(new KeyPressHandler() {
			@Override public void onKeyPress(KeyPressEvent event) {
				if (event.getCharCode() == KeyCodes.KEY_ENTER && checkEnableLogin())
					startLogin();
			}});
		
		horizontalPanel_1 = new HorizontalPanel();
		verticalPanel.add(horizontalPanel_1);
		
		memoryBox = new CheckBox("Remember user name");
		horizontalPanel_1.add(memoryBox);
				

		HorizontalPanel horizontalPanel = new HorizontalPanel();
		verticalPanel.add(horizontalPanel);
		horizontalPanel.setWidth("100%");
		verticalPanel.setCellHeight(horizontalPanel, "28");
		verticalPanel.setCellWidth(horizontalPanel, "100%");
		
		msgLabel = new Label("");
		horizontalPanel.add(msgLabel);
		horizontalPanel.setCellVerticalAlignment(msgLabel, HasVerticalAlignment.ALIGN_MIDDLE);
		
		loginButton = new Button("Login");
		horizontalPanel.add(loginButton);
		loginButton.setWidth("100%");
		horizontalPanel.setCellWidth(loginButton, "60");
		
		loginButton.addClickHandler(new ClickHandler() {
			@Override public void onClick(ClickEvent event) {
				startLogin();
			}});		
		
//		cancelButton = new Button("Cancel");
//		horizontalPanel.add(cancelButton);
//		horizontalPanel.setCellWidth(cancelButton, "60");
//		cancelButton.setSize("60", "28");
//		
//		cancelButton.addClickHandler(new ClickHandler() {
//			@Override public void onClick(ClickEvent event) {
//				hide();
//				callback.onCancel();
//			}});
	}	// LoginDialog
	
	public void setLoginCallback(LoginCallback cb) { callback = cb; }
	
	public void doLogin() {
		userBox.setText(userID != null ? userID : "");
		passwordBox.setText(password != null ? password : "");
		memoryBox.setValue(remember);
		checkEnableLogin();
		center();
		show();
		Scheduler.get().scheduleDeferred(new ScheduledCommand() {
			@Override public void execute() {
				if (userBox.getText().isEmpty())
					userBox.setFocus(true);
				else if (passwordBox.getText().isEmpty()) 
					passwordBox.setFocus(true);
			}
		});
	}	// doLogin
	
	public String getUserID() { return userID; }
	
	public void setCredentials(String userID, String password) { 
		this.userID = userID; 
		this.password = password;
		checkEnableLogin();
	} 
	
	boolean checkEnableLogin() {
		boolean enabled = ! (userBox.getText().isEmpty() || passwordBox.getText().isEmpty());
		loginButton.setEnabled(enabled);
		return enabled;
	}
	
	void startLogin() {
		msgLabel.setText("");
		userID = userBox.getText();
		password = passwordBox.getText();
		logger.log(Level.INFO, "login using " + userID);
		VoxFront.voxIndexService.login(userBox.getText(), passwordBox.getText(), null,
				new AsyncCallback<String>(){

					@Override public void onFailure(Throwable caught) {
						msgLabel.setText("Error contacting server!!");
					}

					@Override public void onSuccess(String result) {
						if (result != null && ! result.isEmpty()) {
							logger.log(Level.INFO, "logged in as " + userID + " session " + result);
							callback.onLogin(userID, password, result, memoryBox.getValue());
							hide();
						} else
							msgLabel.setText("Invalid credentials!!");
					}});
	}	// startLogin
	
//	void startLoginJSON() {
//		msgLabel.setText("");
//		userID = userBox.getText();
//		String text = 
//				"{ \"user\": \"" + userID 
//					+ "\", \"pwd\": \"" +  passwordBox.getText() +  "\"}";
//		
//		RequestBuilder rb = new RequestBuilder(RequestBuilder.POST, loginURL);
//		rb.setHeader("Content-type", "application/json");
//		try {
//			rb.sendRequest(text, new RequestCallback() {
//		
//				@Override public void onResponseReceived(Request request, Response response) {
//					if (response.getStatusCode() == Response.SC_OK) {
//						try {
//							String sessionID = JSONParser.parseStrict(response.getText())
//															.isObject()
//															.get("sessionId")
//															.isString()
//															.stringValue();  // ...yeeesh.
//							callback.onLogin(userID, sessionID, memoryBox.getValue());
//							hide();
//						} catch (Exception e) {
//							msgLabel.setText("Exception occurred while processing response!");
//						}
//					} else {
//						msgLabel.setText("Invalid credentials!!");
//					}
//				}
//		
//				@Override public void onError(Request request, Throwable exception) {
//					msgLabel.setText("Error contacting server!!");
//					
//				}});
//		} catch (RequestException e) {
//			msgLabel.setText("Exception occurred during request!");
//		}
//	}
	

}
