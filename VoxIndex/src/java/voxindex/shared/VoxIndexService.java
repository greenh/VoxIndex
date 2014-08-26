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

import com.google.gwt.user.client.rpc.IsSerializable;
import com.google.gwt.user.client.rpc.RemoteService;
import com.google.gwt.user.client.rpc.RemoteServiceRelativePath;

/**
 * VoxIndex RPC interface in its synchronous form.
 */
@RemoteServiceRelativePath("VoxLookup")
public interface VoxIndexService extends RemoteService {
	
	public static class IndexTermUpdate implements IsSerializable {
		public String indexID;
		public String category;
		public String indexableID;
		public String oldTerm;		// null to add newTerm, non-null to replace 
		public String newTerm;		// null to delete oldTerm, non-null to replace
		
		public IndexTermUpdate(String indexID, String category, String indexableID, 
								String oldTerm, String newTerm) {
			this.indexID = indexID;
			this.category = category;
			this.indexableID = indexableID;
			this.oldTerm = oldTerm;
			this.newTerm = newTerm;
		}
		
		IndexTermUpdate() { }
	}

	/**
	 * Attempts a login to the server, and establishes a session if successful.
	 * @param userID A string containing the username.
	 * @param credentials Currently, a password string.
	 * @param sessionID An existing session ID, which the server will reuse if it 
	 * exists. If null or not a valid session, the server creates a new session.
	 * @return A string containing the session ID to use.
	 * @throws AuthenticationException The server was unable to authenticate the 
	 * requestor.
	 */
	public String login(String userID, String credentials, String sessionID)
			throws AuthenticationException;

	/**
	 * Terminates the specified session, if the session is still active, and no-op
	 * if not.
	 * @param sessionID The ID of the session to terminate.
	 */
	public void logout(String sessionID);

	/**
	 * Performs a voice-recognition operation on a chunk of audio data over a specified set
	 * of indexes, and if successful, returns a result describing the entities ("indexables")
	 * that the voice data selected.
	 * @param sessionID The ID of the RPC session.
	 * @param tag An arbitrary string set by the client to identify the request. 
	 * Its value is returned unaltered in the result object.
	 * @param indexIDs A set of identifiers denoting indexes to be used in the lookup
	 * process. This establishes the context for the lookup.
	 * @param waveData The recorded audio to be analyzed by the voice recognition engine.
	 * This takes the form of a byte array containing WAVE-formatted audio data, i.e.
	 * just the same content that one would expect to see in a .wav file. By preference,
	 * this is encoded as single-channel 16-bit PCM with a 16K sample rate, though other 
	 * encodings may work too. 
	 * @return A LookupResult object describing the outcome of the vocal lookup exercise.
	 * @throws LoginRequiredException Thrown if the session is not valid, indicating
	 * the need for the client to reauthenticate itself.
	 */
	public LookupResult voxLookup(String sessionID, String tag, 
																Set<String> indexIDs, byte[] waveData) 
			throws LoginRequiredException;
	
	public LookupResult indexLookup(String sessionID, String tag, Set<String> indexIDs)
			throws LoginRequiredException;
	
	/**
	 * Given a link in a document, attempts to identify an indexable that corresponds to
	 * that link based on current context. 
	 * <p>This is used by capable front ends to maintain context if a user should shoose
	 * to navigate by clicking on a link in a displayed document rather than doing a
	 * voice-recognition lookup.
	 * @param sessionID The ID of the RPC session.
	 * @param tag An arbitrary string set by the client to identify the request. 
	 * Its value is returned unaltered in the result object.
	 * @param indexIDs A set of identifiers denoting indexes to be used in the lookup
	 * process. This establishes the context for the lookup.
	 * @param link The URI to look up.
	 * @return A LookupResult identifying the indexable denoted by the link.
	 * @throws LoginRequiredException Thrown if the session is not valid, indicating
	 * the need for the client to reauthenticate itself.
	 */
	public LookupResult linkLookup(String sessionID, String tag, 
											Set<String> indexIDs, String link)
			throws LoginRequiredException;

	public int square(int victim);
	
	public VocalIndexItem[] availableIndexes(String sessionID)
			throws LoginRequiredException;
	
	public void closeIndexes(String sessionID, Set<String> indexIDs)
			throws LoginRequiredException;

	public void usingIndexes(String sessionID, Set<String> indexIDs)
			throws LoginRequiredException;
	
	public RootEntry[] rootEntries(String sessionID)
			throws LoginRequiredException;
	
	public IndexContent[] indexContent(String sessionID, String[] indexIDs)
			throws LoginRequiredException;
	
	public String grammarFor(String sessionID, Set<String> indexIDs)
			throws LoginRequiredException;
	
	public void refresh(String sessionID)
			throws LoginRequiredException; 
	
//	public boolean loginCheck(String userID, String sessionID)
//			throws LoginRequiredException;
}
  