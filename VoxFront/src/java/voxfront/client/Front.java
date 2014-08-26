package voxfront.client;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Set;

import voxindex.shared.LookupResult;
import voxindex.shared.VoxIndexIDs;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.TabLayoutPanel;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.ui.HasVerticalAlignment;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.TextArea;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 
 */
public class Front extends Composite {
	
	public static final String BackCmd = VoxIndexIDs.voxCmdURN("back");
	public static final String ForwardCmd = VoxIndexIDs.voxCmdURN("forward");
	public static final String PrevCmd = VoxIndexIDs.voxCmdURN("prev");
	public static final String NextCmd = VoxIndexIDs.voxCmdURN("next");
	public static final String NewCmd = VoxIndexIDs.voxCmdURN("new");
	public static final String CloseCmd = VoxIndexIDs.voxCmdURN("close");
	public static final String StopCmd = VoxIndexIDs.voxCmdURN("stop");
	
	private TabLayoutPanel tabs;
	private HashMap<String,TabContext> tagMap = new HashMap<String,TabContext>();
	private Button prevButton;
	private Button nextButton;
	private TextBox labelBox;
	private Button backButton;
	private Button forwardButton;
	private Button newButton;
	private Button closeButton;
	
	private static Logger logger = Logger.getLogger("Front");

	public Front() {
		VerticalPanel verticalPanel = new VerticalPanel();
		verticalPanel.setSize("100%", "100%");
		initWidget(verticalPanel);
		
		HorizontalPanel topPanel = new HorizontalPanel();
		verticalPanel.add(topPanel);
		verticalPanel.setCellWidth(topPanel, "100%");
		topPanel.setWidth("100%");
		
		backButton = new Button("Back");
		topPanel.add(backButton);
		topPanel.setCellWidth(backButton, "50");
		backButton.addClickHandler(new ClickHandler() {
			@Override public void onClick(ClickEvent event) { navigate(Navigation.Back); }});
		
		forwardButton = new Button("Fwd");
		topPanel.add(forwardButton);
		forwardButton.addClickHandler(new ClickHandler() {
			@Override public void onClick(ClickEvent event) { navigate(Navigation.Forward); }});
		
		prevButton = new Button("Prev");
		topPanel.add(prevButton);
		prevButton.addClickHandler(new ClickHandler() {
			@Override public void onClick(ClickEvent event) { navigate(Navigation.Prev); }});
		
		nextButton = new Button("Next");
		topPanel.add(nextButton);
		nextButton.addClickHandler(new ClickHandler() {
			@Override public void onClick(ClickEvent event) { navigate(Navigation.Next); }});
				
		newButton = new Button("New");
		newButton.setText("New");
		topPanel.add(newButton);
		newButton.addClickHandler(new ClickHandler() {
			@Override public void onClick(ClickEvent event) {
				newTab();
			}});
		
		closeButton = new Button("Close");
		topPanel.add(closeButton);
		closeButton.addClickHandler(new ClickHandler() {
			@Override public void onClick(ClickEvent event) {
				closeTab();
			}});
		
		HorizontalPanel horizontalPanel = new HorizontalPanel();
		topPanel.add(horizontalPanel);
		topPanel.setCellWidth(horizontalPanel, "100%");
				
		tabs = new TabLayoutPanel(20, Unit.PX);
		verticalPanel.add(tabs);
		tabs.setSize("100%", "100%");
		verticalPanel.setCellHeight(tabs, "100%");
		verticalPanel.setCellWidth(tabs, "100%");
		tabs.addSelectionHandler(new SelectionHandler<Integer>() {
			@Override public void onSelection(SelectionEvent<Integer> event) {
				updateTabState();
			}});

		newTab();
	}	// TabContext
	
	public Set<String> getSelectedIDs() { return getSelected().getIDs(); }
	public String getSelectedTag() { return getSelected().getTag(); }
	
	public void newTab() {
		TabContext context = new TabContext(this);
		tabs.add(context, "New Tab");
		tagMap.put(context.getTag(), context);
		tabs.selectTab(context);
	}	// newTab
	
	public void closeTab() {
		TabContext tc = getSelected();
		tabs.remove(tc);
		tagMap.remove(tc.getTag());
		if (tabs.getWidgetCount() == 0)
			newTab();
	}	// closeTab
	
	public static enum Navigation { Back, Forward, Next, Prev }
	
	public void navigate(Navigation where) {
		int tab = tabs.getSelectedIndex();
		TabContext context = (TabContext) tabs.getWidget(tab);
		context.navigate(where);
	}	// navigate
	
	/**
	 * Called whenever there's a change in any of the tabs, and updates the 
	 * top-level UI to match.
	 */
	public void updateTabState() {
		int tab = tabs.getSelectedIndex();
		TabContext tc = (TabContext) tabs.getWidget(tab);
		// XXX need to get indexable description displayed somewhere
		if (tc.hasView()) {
			LookupResult res = tc.viewResult();
			tabs.setTabText(tab, res.indexName);
			forwardButton.setEnabled(tc.tabCanFwd());
			backButton.setEnabled(tc.tabCanBack());
			prevButton.setEnabled(tc.tabHasVariants());
			nextButton.setEnabled(tc.tabHasVariants());
		} else {
			forwardButton.setEnabled(false);
			backButton.setEnabled(false);
			prevButton.setEnabled(false);
			nextButton.setEnabled(false);
		}
	}	// updateTabState
	
	/**
	 * Process a result from a voice lookup operation.
	 * @param result
	 */
	public void lookupResult(LookupResult result) {
		String cmd = result.commandURI;
		if (VoxIndexIDs.isCmd(cmd, ForwardCmd))
			navigate(Navigation.Forward);
		else if (VoxIndexIDs.isCmd(cmd, BackCmd))
			navigate(Navigation.Back);
		else if (VoxIndexIDs.isCmd(cmd, PrevCmd))
			navigate(Navigation.Prev);
		else if (VoxIndexIDs.isCmd(cmd, NextCmd))
			navigate(Navigation.Next);
		else if (VoxIndexIDs.isCmd(cmd, NewCmd))
			newTab();
		else if (VoxIndexIDs.isCmd(cmd, CloseCmd))
			closeTab();
		else if (result.indexables.length > 0) {
			/* We conclude that it's really real content, and donate it to its lucky 
			 * TabContext recipient. 
			 */
			int tab = tabs.getSelectedIndex();
			TabContext tc = (TabContext) tabs.getWidget(tab);
			tc.newResult(result);
		} else 
			logger.log(Level.INFO, "Unrecognized result");
	}	// lookupResult
	
	private TabContext getSelected() {
		int tab = tabs.getSelectedIndex();
		return (TabContext) tabs.getWidget(tab);
	}	// getTab
	
}	// Front
