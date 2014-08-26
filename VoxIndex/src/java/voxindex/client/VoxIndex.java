package voxindex.client;

import voxindex.shared.VoxIndexService;
import voxindex.shared.VoxIndexServiceAsync;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Label;

/**
 * Entry point classes define <code>onModuleLoad()</code>.
 */
public class VoxIndex implements EntryPoint {
	public static final VoxIndexServiceAsync voxIndexService 
			= (VoxIndexServiceAsync) GWT.create(VoxIndexService.class);

	private Button sendButton;
	private TextBox valueBox;

		
	@Override public void onModuleLoad() {
		RootPanel rootPanel = RootPanel.get();
		
		VerticalPanel verticalPanel = new VerticalPanel();
		rootPanel.add(verticalPanel);
		verticalPanel.setSize("100%", "100%");
		
		HorizontalPanel horizontalPanel = new HorizontalPanel();
		horizontalPanel.setSpacing(4);
		verticalPanel.add(horizontalPanel);
		horizontalPanel.setSize("100%", "100%");
		verticalPanel.setCellWidth(horizontalPanel, "100%");
		
		valueBox = new TextBox();
		horizontalPanel.add(valueBox);
		valueBox.setWidth("192px");
		
		sendButton = new Button("Send");
		sendButton.setText("Send");
		horizontalPanel.add(sendButton);
		
		Label resultLabel = new Label("");
		horizontalPanel.add(resultLabel);
		horizontalPanel.setCellWidth(resultLabel, "100%");
		resultLabel.setWidth("100%");
		sendButton.addClickHandler(new ClickHandler() {

			@Override public void onClick(ClickEvent event) {
				String text = valueBox.getText();
				try {
					int val = Integer.parseInt(text);
					voxIndexService.square(val, new AsyncCallback<Integer>() {

						@Override public void onFailure(Throwable caught) {
							Window.alert("Error occurred: " + caught.getMessage());
						}

						@Override public void onSuccess(Integer result) {
							Window.alert("Result is " + result);
						}});
				} catch (Exception e) {
					Window.alert("Bad number!");
				}
			}});
	}
	
	public void msg(String s) {
//		msgText += s + "\n";
//		textArea.setText(msgText);
	}
}
