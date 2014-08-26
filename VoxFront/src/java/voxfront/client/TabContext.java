package voxfront.client;

import java.util.ArrayList;
import java.util.Set;

import voxfront.client.Native;
import voxfront.client.Front.Navigation;
import voxindex.shared.LookupResult;

import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.TabPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.Frame;
import com.google.gwt.user.client.ui.DeckPanel;

public class TabContext extends Composite {
	
	ArrayList<ResultFrame> resultFrames = new ArrayList<ResultFrame>();
	int current = -1;
	
	public final String tag = "" + System.currentTimeMillis();
	
	Front front;
	private Label goopLabel;
	private VerticalPanel basePanel;
	private DeckPanel deckPanel;

	public TabContext(Front front) {
		this();
		this.front = front;
	}
	public TabContext() {
		
		basePanel = new VerticalPanel();
		basePanel.setSize("100%", "100%");
		basePanel.setStyleName("TCBasePanel");
		
		HorizontalPanel horizontalPanel = new HorizontalPanel();
		basePanel.add(horizontalPanel);
		basePanel.setCellHeight(horizontalPanel, "20");
		horizontalPanel.setStyleName("TCBaseTopPanel");
		horizontalPanel.setSize("100%", "20");
		
				basePanel.setCellWidth(horizontalPanel, "100%");
				
				goopLabel = new Label("");
				horizontalPanel.add(goopLabel);
				horizontalPanel.setCellWidth(goopLabel, "100%");
				goopLabel.setWidth("100%");
		
		deckPanel = new DeckPanel();
		basePanel.add(deckPanel);
		deckPanel.setSize("100%", "100%");
		basePanel.setCellHeight(deckPanel, "100%");
		basePanel.setCellWidth(deckPanel, "100%");
		deckPanel.addStyleName("TCDeckPanel");
		
		initWidget(basePanel);
	}
	
	public String getTag() { return tag; }
	public Set<String> getIDs() {
		if (hasView()) {
			Set<String> s = resultFrames.get(current).getCurrentIDs();
			s.addAll(IndexIDs.defaultIndexIDs);
			return s;
		} else
			return IndexIDs.defaultIndexIDs;
	}	// getIDs
	
	public void newResult(LookupResult result) {
		ResultFrame newFrame = new ResultFrame(this, result);
		Native.log("TabContext.newResult: current " + current  + " size " + resultFrames.size());
		for (int h = resultFrames.size() - 1; h > current; h--) {  
			Native.log("TabContext.newResult: removing " + h);
			deckPanel.remove(resultFrames.get(h));
			resultFrames.remove(h);
		}
		Native.log("TabContext.newResult: current " + current);
		current++;
		resultFrames.add(newFrame);
		deckPanel.add(newFrame);
		deckPanel.showWidget(current);
		Native.log("TabContext.newResult: current " + current + " size " + resultFrames.size());
		updateView();
	}	// newResult
	
	public void moveBack() {
		if (current > 0) {
			current--;
			deckPanel.showWidget(current);
			updateView();
		}
	}	// moveBack
	
	public void moveForward() {
		if (current < resultFrames.size() - 1) {
			current++;
			deckPanel.showWidget(current);
			updateView();
		}
	}	// moveForward
		
	public void navigate(Navigation where) {
		switch (where) {
		case Back: 
			moveBack();
			break;
		case Forward:
			moveForward();
			break;
		case Next: 
			resultFrames.get(current).nextVariant();
			break;
		case Prev:
			resultFrames.get(current).nextVariant();
			break;
		}
	}	// navigate
	
	private void updateView() {
		String uri = resultFrames.get(current).getCurrentURI();
		goopLabel.setText(uri);
		front.updateTabState();
	}	// updateView
	
	public LookupResult viewResult() {
		return resultFrames.get(current).getResult();
	}
	
	public boolean hasView() { return current >= 0; }
	public boolean tabHasVariants() { return resultFrames.get(current).hasVariants(); }
	public boolean tabCanFwd() { return current < resultFrames.size() - 1; }
	public boolean tabCanBack() { return current > 0; } 

}	// resultTabContext
