package voxfront.client;

import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.VerticalPanel;

public class RetryDialog extends DialogBox {

	public RetryDialog() {
		setAnimationEnabled(true);
		setGlassEnabled(true);
		
		VerticalPanel verticalPanel = new VerticalPanel();
		setWidget(verticalPanel);
		verticalPanel.setSize("377px", "100%");
	}



}
