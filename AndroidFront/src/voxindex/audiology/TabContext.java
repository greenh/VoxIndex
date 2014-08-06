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

import java.util.HashSet;
import java.util.Set;

import voxindex.audiology.R;
import voxindex.audiology.RPCSession.RPCRequest;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebBackForwardList;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TabHost;
import android.widget.TextView;
import voxindex.audiology.TabContext.ResultFrame.ViewFrame;
import voxindex.shared.LoginRequiredException;
import voxindex.shared.LookupResult;
import voxindex.shared.LookupResult.Indexable;
import voxindex.shared.VoxIndexService;

/**
 * Provides the resources and contextual information needed within a tab
 * within the main display. Each TabContext gives rise to an independent process 
 * of lookup of vocal input, and display and manipulation of the results.
 * to this end, a TabContext maintains a WebView to display whatever content 
 * is implied by the lookup process, and lookup and viewing history
 * to facilitate various user-driven functions on the results.
 */
public class TabContext {
	
	public static final String Tag = "TabContext";

	public static final String ControlIndexID = "000000000000000000000001";
	public static final String AudiologyIndexID = "000000000000000000000002";
	public static final HashSet<String> defaultIndexIDs = new HashSet<String>();
	static {
		defaultIndexIDs.add(ControlIndexID);
		defaultIndexIDs.add(AudiologyIndexID);
		};
	
	public static final String blankHTML = 
		"<!DOCTYPE html><html><head>"
			+ "<meta charset=\"ISO-8859-1\">"
			+ "<title></title>"
			+ "<STYLE type=\"text/css\">body { background-color: black }</STYLE>"
			+ "</head><body/></html>";
	
	/**
	 * Describes the state of affairs resulting from a recognition request.
	 * <p>A successful lookup request made on behalf of a tab to the server 
	 * can result in any 
	 * of many kinds of results being returned, such as commands for
	 * manipulating the client in various ways. The nominal result,
	 * however, is a collection of indexables, each of which represents
	 * a specific chunk of content that can potentially be downloaded
	 * and displayed. In many (or even most) cases, only one indexable
	 * is returned; however, in the general case, a specific term can
	 * give rise to multiple indexables. 
	 * <p>A ResultFrame records this potential multiplicity of results,
	 * and provides the basis for    
	 * 
	 */
	public class ResultFrame {
		final String resultTag = tag + ":" + System.currentTimeMillis();
		LookupResult result;
		
		/**
		 * Describes a state corresponding to the display of a specific 
		 * chunk of content belonging to the parent ResultFrame within 
		 * the containing Tab.  
		 */
		public class ViewFrame { 
			
			int index;
			ViewFrame previousView = null;
			ViewFrame nextView = null;
			int mark = 0;
			
			public ViewFrame(int index) {
				this.index = index;
			}
			
			public void engage(ViewFrame previous) {
				this.previousView = previous;
				previous.nextView = this;
			}
			
			public String statusInfo() {
				return 
					tag 
					+ " Index: " + getIndexName()
					+ " Title: " + getTitle() 
					+ " Source: " + getSource() 
					+ "\nURI: " + getURI()
					+ "\nIDs: " + getIDs();
			}
						
			public ResultFrame getResultFrame() { return ResultFrame.this; }
			
			public LookupResult getResult() { return result; }
			
			public ViewFrame getPreviousView() { return previousView; }
			public ViewFrame getNextView() { return nextView; }
			
			public int getIndex() { return index; }
			public int getN() { return result.indexables.length; }
			
			public String getSource() { return result.indexables[index].source; }
			public String getTitle() { 
				String s = result.indexables[index].title;
				return (s != null) ? s : "";
			}
			public String getURI() { return result.indexables[index].uri; }
			public Set<String> getIDs() { return result.indexables[index].indexIDs; }
			public String getIndexName() { return result.indexName; }
			
			public void setMark(int m) { mark = m; }
			public int getMark() { return mark; }
			
			/**
			 * Creates a new ViewFrame for the indexable relative to this.
			 * @param delta Offset (plus or minus) to the desired indexable. 
			 * A value of +1 gets the circular-next indexable in the sequence, 
			 * -1 the circular-previous, etc.
			 * @return The new ViewFrame, or null if the new ViewFrame would 
			 * have the same content as this one.
			 */
			public ViewFrame relativeView(int delta) {
				if (result.indexables.length <= 1)
					return null;
				int nx = (index + delta) % result.indexables.length;
				if (nx <  0)
					nx = result.indexables.length + nx;
				if (nx == index)
					return null;
				ViewFrame newView = new ViewFrame(nx);
				nextView = newView;
				return newView;
			}	// relativeView

			/**
			 * Generates a new ViewFrame for the indexable with the 
			 * specified index within the parent request's indexable sequence.
			 * @param inx The index of the indexable of interest.
			 * @return The new ViewFrame, or null if the index is invalid, 
			 * or would result in the same indexable as this one.
			 */
			public ViewFrame indexableView(int inx) {
				if (inx > result.indexables.length || inx == index)
					return null;
				ViewFrame newView = new ViewFrame(inx);
				nextView = newView;
				return newView;
			}	// indexableView

		}	// ViewFrame
		
		
		public ResultFrame(LookupResult result) { this.result = result; }
		
		public ViewFrame initialView() { return new ViewFrame(0); }
		
		public boolean hasVariants() { return result.indexables.length > 1; }
		
		public void updateResult(LookupResult newResult) { result = newResult; }
		
	} 	// ResultFrame
	
	String tag = "" + System.currentTimeMillis();
	
	final Audiology app;
	TabHost tabHost;
	TextView tabDataView;
	WebView webView;
	TabHost.TabSpec tabSpec;
	
	ViewFrame currentView;
	
	WebViewClient wvClient = 
		new WebViewClient() {
			/*
			 * Here we override default link processing for the WebViewClient. The
			 * idea here is that when a user manually clicks a link, we take the 
			 * resulting URL and pass it off to the server for lookup. If the 
			 * server recognizes it, it returns context information appropriate
			 * to the page, so the net effect is just as if the user had done a voice lookup. 
			 */
			@Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
				Log.i(Tag, "TabContext shouldOverride " + url);
				LookupResult r = new LookupResult(
						getTag(), true, false, 1.0f, "", null, "", 
						new Indexable[] { new Indexable("", "", url, defaultIndexIDs) });
				ResultFrame rf = newResult(r);
				
				final String tag = rf.resultTag;
				final String link = url;
						
				TabContext.this.app.getSession().doRPCRequest(new RPCRequest(){
					@Override public void request(VoxIndexService service, String sessionID) 
												throws LoginRequiredException {
	    				Log.v(Tag, "LinkRequest (" + link + ")");
	    				LookupResult rslt = service.linkLookup(sessionID, tag, null, link);
						TabContext.this.app.linkResult(rslt);
					}
					@Override public void failed(Throwable t) {
	    				Log.e(Tag, "VoxRequestor LinkRequest exception: ", t);
	    				TabContext.this.app.requestException(t);
					}});
				return true;
			}	// shouldOverrideUrlLoading
			
			@Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
				Log.i(Tag, "TabContext page start " + url);
			}
			
			@Override public void onReceivedError(WebView view, int errorCode, 
													String description, String url) {
				Log.w(Tag, "TabContext receive error: " + description + " on " + url);
			}
		};

	
	public TabContext(Audiology app, TabHost tabHost) {
		this.app = app;
		uiInit(tabHost, app.getResources().getString(R.string.newTab));
		webView.loadData(blankHTML, "text/html", "UTF-8");
		Log.d(Tag, "Created tab " + tag);
	}	// Tab
	
	private void uiInit(TabHost tabHost, String label) {
		LayoutInflater inflater = LayoutInflater.from(app);
		final View layout = inflater.inflate(R.layout.tab_context_layout,
				(ViewGroup) app.findViewById(R.layout.landscape_tabbed)); // XXX
		
		tabDataView = (TextView) layout.findViewById(R.id.tabDataView);
		webView = (WebView) layout.findViewById(R.id.webView);
		webView.setWebViewClient(wvClient);
        webView.getSettings().setBuiltInZoomControls(true);
        /*************************************************************************
         *************************************************************************
     			DO NOT TURN JAVASCRIPT ON!!!!!!!!
        // webSettings.setJavaScriptEnabled(true);
         ************************************************************************* 
         *************************************************************************/
        this.tabHost = tabHost;
		tabSpec = tabHost.newTabSpec(tag)
					.setIndicator(label)
					.setContent(new TabHost.TabContentFactory() {
						@Override public View createTabContent(String tag) {
							return layout;
						}});
		tabHost.getTabWidget().setStripEnabled(true);
	}
	
	public void rehost(TabHost tabHost) {
		Bundle bundle = new Bundle();
		webView.saveState(bundle);
		CharSequence text = tabDataView.getText();
		String src = getSource();
		uiInit(tabHost, (src != null && ! src.isEmpty()) ? src : "<--->");
		tabDataView.setText(text);
		webView.restoreState(bundle);
	}
	
	public String getTag() { return tag; }	
	
	public TabHost.TabSpec getTabSpec() { return tabSpec; }
	
	public Set<String> getIndexIDs() {
		if (currentView != null) {
			HashSet<String> s = new HashSet<String>(currentView.getIDs());
			s.addAll(defaultIndexIDs);
			return s;
		} else 
			return defaultIndexIDs;
	}
	
	public String getIndexName() {
		if (currentView != null) 
			return currentView.getIndexName();
		else
			return null;
	}
	
	public String getTitle() {
		if (currentView != null)
			return currentView.getTitle();
		else
			return null;
	}
	
	public int getNth() { return (currentView == null) ? 0 : currentView.getIndex(); }
	
	public int getN() { return (currentView == null) ? 0 : currentView.getN(); }
	
	public String getSource() {
		if (currentView != null)
			return currentView.getSource();
		else
			return null;
	}
	
	public LookupResult getResult() {
		if (currentView != null)
			return currentView.getResult();
		else
			return null;
	}
	
	public int getMark() {
		WebBackForwardList bfl = webView.copyBackForwardList();
		return bfl.getCurrentIndex();
	}
	
	public boolean hasBack() {
		if (currentView == null)
			return false;
		else
			return currentView.getPreviousView() != null;
	}
	
	public boolean hasForward() {
		if (currentView == null)
			return false;
		else
			return currentView.getNextView() != null;
	}
	
	public boolean hasVariants() {
		if (currentView == null)
			return false;
		else
			return currentView.getResultFrame().hasVariants();
	}
	
	public boolean hasJSEnabled() {
		return false; // TODO
	}
	
//	public WebSettings.TextSize getTextSize() {
//		return webView.getSettings().getTextSize();
//	}
//	
//	public void setTextSize(WebSettings.TextSize textSize) {
//		webView.getSettings().setTextSize(textSize);
//	}
	
	public void setJSEnabled(boolean enabled) {
		// TODO
	}
	
	private void setCurrentView(ViewFrame newView) {
		currentView = newView;
		tabDataView.setText(currentView.statusInfo());
//		tabName.setText(getIndexName());
		app.updateTabState();
	}
	 
	public void select() {
//		tabName.setTypeface(Typeface.DEFAULT_BOLD, Typeface.BOLD);
	}
	public void unselect() {
//		tabName.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
	}
	
	public void updateResult(LookupResult newResult) {
		ViewFrame f = currentView;
		while (f != null && ! f.getResultFrame().resultTag.equals(newResult.tag))
			f = f.nextView;
		if (f == null) {
			f = currentView;
			while (f != null && ! f.getResultFrame().resultTag.equals(newResult.tag)) 
				f = f.previousView;
		}
		if (f == null)
			Log.d(Tag, "No ResultFrame found for " + newResult.tag);
		else {
			Log.d(Tag, "ResultFrame found for " + newResult.tag);
			f.getResultFrame().updateResult(newResult);
			tabDataView.setText(f.statusInfo());
			app.updateTabState();
		}
	}

	/**
	 * Creates a new view for an indexable in the current response that's at 
	 * an index some delta from the current indexable.
	 * <p>A typical use of this is for sequentially moving forward (delta = +1)
	 * or backward (delta = -1) through the sequence of alternative indexables.
	 * Indexing is modulo the number of indexables in the response, so a delta
	 * that would put the index past the end of the sequence wraps.
	 * @param delta The signed displacement to move relative to the current
	 * indexable within the sequence of indexables in the current response. 
	 */
	public void showVariant(int delta) {
		if (delta != 0) {
			ViewFrame newView = currentView.relativeView(delta);
			if (newView != null) {
				newView.engage(currentView);
				newView.setMark(getMark());
				setCurrentView(newView);
				load();
			}
		}
	}
	
	public void goBack() {
		int currentMark = getMark();
		ViewFrame prev = currentView.getPreviousView();
		Log.d(Tag, "TabContext back, current " + currentMark
					+ " cv " + currentView.getMark());
		webView.goBack();
		if (currentMark - 1 <= currentView.getMark() && prev != null)
			setCurrentView(prev);	
	}	// goBack
	
	public void goForward() {
		int currentMark = getMark();
		ViewFrame next = currentView.getNextView();
		Log.d(Tag, "TabContext forward, current " + currentMark
					+ " next " + ((next != null) ? next.getMark() : -1));
		webView.goForward();
		if (next != null && currentMark + 1 >= next.getMark())
			setCurrentView(next);
	}	// goForward
	
	/**
	 * Accepts a {@link LookupResult} that the server has coughed up,
	 * and creates new {@link ResultFrame} and {@link ViewFrame} objects
	 * for it.
	 * @param result The result of a successful lookup from the server.
	 * @return The new ResultFrame.
	 */
	public ResultFrame newResult(LookupResult result) {
		ResultFrame nr = new ResultFrame(result);
		ViewFrame newView = nr.initialView(); 
		if (currentView != null)
			newView.engage(currentView);
		newView.setMark(getMark());
		setCurrentView(newView);
		load();
		return nr;
	}
	
	public void toTop() { webView.pageUp(true); }
	public void toBottom() { webView.pageDown(true); }
	public void pageUp(int n) {
		for (int i = 0; i < 1 * n; i++)  // 2 * n 'cause pageUp moves in half-pages ???
			webView.pageUp(false);
	}
	public void pageDown(int n) {
		for (int i = 0; i < 1 * n; i++)  // 2 * n 'cause pageDown moves in half-pages 
			webView.pageDown(false);
	}
	public void scrollUp() { webView.pageUp(false); }
	public void scrollDown() { webView.pageDown(false); }
	
	/**
	 * Causes whatever's targeted by the current view to be loaded by
	 * the Tab's WebView. 
	 * <p>If the current view doesn't have a target,
	 * or if the target is "special" (i.e., the URN for a page number),
	 * generate an appropriate placeholder (about:blank, eh?).
	 * Note that this should be called if and only if currentView has
	 * been set to a 
	 */
	void load() {
		if (currentView != null) {
			String target = currentView.getURI();
			Log.v(Tag, "Loading " + target);
			if (target != null) {
//				if (target.startsWith("/"))
//					webView.loadUrl(app.getServiceURI() + target);
//				else
				if (target.startsWith("http"))
					webView.loadUrl(target);
				else
					; // XXX 
			} else
				; // XXX -- need to do a default display or something
		}	
	} 	// load
	

}
