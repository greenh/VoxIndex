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
package voxindex.shared;

import java.util.Set;

import com.google.gwt.user.client.rpc.AsyncCallback;

/**
 * Async version of the VoxIndexService RPC interface.
 */
public interface VoxIndexServiceAsync {
	
	public void login(String userID, String credentials, String sessionID, AsyncCallback<String> callback);
	
	public void logout(String sessionID, AsyncCallback<Void> callback);
	
	public void voxLookup(String sessionID, String tag, Set<String> indexIDs, byte[] waveData, AsyncCallback<LookupResult> callback);
	
	public void indexLookup(String sessionID, String tag, Set<String> indexIDs, AsyncCallback<LookupResult> callback);
	
	public void linkLookup(String sessionID, String tag, Set<String> indexIDs, String link, AsyncCallback<LookupResult> callback);
	
	public void square(int victim, AsyncCallback<Integer> callback);
	
	public void availableIndexes(String sessionID, AsyncCallback<VocalIndexItem[]> callback);
	
	public void closeIndexes(String sessionID, Set<String> indexIDs, AsyncCallback<Void> callback);

	public void usingIndexes(String sessionID, Set<String> indexIDs, AsyncCallback<Void> callback);
	
	public void rootEntries(String sessionID, AsyncCallback<RootEntry[]> callback);
	
	public void indexContent(String sessionID, String[] indexIDs, AsyncCallback<IndexContent[]> callback);
	
	public void grammarFor(String sessionID, Set<String> indexIDs, AsyncCallback<String> callback);
	
	public void refresh(String sessionID, AsyncCallback<Void> callback); 
}
  