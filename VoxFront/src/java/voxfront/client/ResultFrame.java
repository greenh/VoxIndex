package voxfront.client;

import java.util.Set;

import voxindex.shared.LookupResult;
import voxfront.client.Native;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.DeckLayoutPanel;
import com.google.gwt.user.client.ui.Frame;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HasVerticalAlignment;

public class ResultFrame extends Composite {
	
	public final LookupResult result;
	public final TabContext parent;
	
	final int frames; 
	
	final Frame[] viewFrame;
	
	int currentFrame = 0;
	private VerticalPanel outer;
	private HorizontalPanel npBar;
	private DeckLayoutPanel framePanel;
	private Button nextButton;
	private Button prevButton;
	private Label locLabel;

	public ResultFrame(TabContext parent, LookupResult result) {
		this.result = result;
		this.parent = parent;
		this.frames = result.indexables.length;
		viewFrame = new Frame[frames];
		
		outer = new VerticalPanel();
		initWidget(outer);
		outer.setSize("100%", "100%");
		outer.setStyleName("RFOuter");
		
		if (frames > 1) {
			npBar = new HorizontalPanel();
			npBar.setStyleName("RFbar");
			outer.add(npBar);
			npBar.setWidth("100%");
			npBar.setHeight("20px");
			
			prevButton = new Button("<");
			npBar.add(prevButton);
			prevButton.setSize("100%", "20");
			npBar.setCellHeight(prevButton, "20");
			npBar.setCellWidth(prevButton, "40%");
			prevButton.addClickHandler(new ClickHandler() {
				@Override public void onClick(ClickEvent event) {
					previousVariant();
				}
			});
			
			locLabel = new Label();
			npBar.add(locLabel);
			npBar.setCellVerticalAlignment(locLabel, HasVerticalAlignment.ALIGN_MIDDLE);
			npBar.setCellHorizontalAlignment(locLabel, HasHorizontalAlignment.ALIGN_CENTER);
			locLabel.setWidth("100%");
			
			nextButton = new Button(">");
			npBar.add(nextButton);
			npBar.setCellHeight(nextButton, "20");
			npBar.setCellWidth(nextButton, "40%");
			nextButton.setSize("100%", "20");
			nextButton.addClickHandler(new ClickHandler() {
				@Override public void onClick(ClickEvent event) {
					nextVariant();
				}
			});
		}
		framePanel = new DeckLayoutPanel();
		outer.add(framePanel);
		framePanel.setSize("100%", "100%");
		outer.setCellHeight(framePanel, "100%");
		outer.setCellWidth(framePanel, "100%");
		
		// For the nonce, mindlessly add all frames from the start. 
		for (int f = 0; f < frames; f++) {
			Frame frame =  new Frame();
			viewFrame[f] = frame;
			frame.setUrl(result.indexables[f].uri);
			frame.setSize("100%","100%");
			framePanel.add(frame);
		}
		currentFrame = 0;
		showFrame(currentFrame);
	}	// ResultFrame
	
	public boolean hasVariants() { return frames > 1; }
	public int variants() { return frames; }
	
	public void previousVariant() {
		if (frames > 1) {
			currentFrame = (currentFrame - 1 + frames) % frames;
			// ConLog.log("ResultFrame.prev: current " + currentFrame);
			showFrame(currentFrame);
		}
	}	// previousVariant
	
	public void nextVariant() {
		if (frames > 1) {
			currentFrame = (currentFrame + 1) % frames;
			// ConLog.log("ResultFrame.next: current " + currentFrame);
			showFrame(currentFrame);
		}
	}	// nextVariant
	
	public String getCurrentURI() { return viewFrame[currentFrame].getUrl(); }
	public Set<String> getCurrentIDs() { 
		return result.indexables[currentFrame].indexIDs; 
	}
	public String getCurrentTitle() {
		String s = result.indexables[currentFrame].title;
		return (s != null) ? s : "";
	}
	public String getCurrentSource() { return result.indexables[currentFrame].source; }
	public int getCurrentView() { return currentFrame; }
	public LookupResult getResult() { return result; }
	
	private void updateUI() {
		// parent.updateView(); 
	}

	private void showFrame(int frame) {
		framePanel.showWidget(currentFrame);
		if (hasVariants())
			locLabel.setText((currentFrame + 1) + " of " + frames); 
		updateUI();
	}	// showFrame
	
	
} // ResultFrame

