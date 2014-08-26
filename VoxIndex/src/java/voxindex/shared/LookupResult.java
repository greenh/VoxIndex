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

/**
 * Result from an RPC operation to do a voice-recognition-based lookup.
 * <p>In general, a successful lookup returns a collection of Indexables,
 * each of which describes an entity that corresponds to what the voice recognition
 * process discovered. A praticular point here is that a single request
 * can give rise to multiple outcomes (e.g., multiple classes of the same name
 * in different packages, overloaded methods, etc.).
 */
public class LookupResult implements IsSerializable {
	
	/**
	 * Describes the target of a lookup request.
	 */
	public static class Indexable implements IsSerializable {
		public String source;
		public String title;
		public String uri; 
		public Set<String> indexIDs;
		
		public Indexable(String title, String source, String uri, Set<String> indexIDs) {
			this.title = title;
			this.source = source;
			this.uri = uri;
			this.indexIDs = indexIDs;
		}
		
		public Indexable() { }
	}
	
	public String tag;
	public boolean recognized;
	public boolean belowThreshold;
	public float confidence;
	public String indexName;
	public String commandURI;
	public String recognizedText;
	public Indexable[] indexables;
	
	public LookupResult() { }
	
	public LookupResult(String tag, boolean recognized, boolean belowThreshold, float confidence,
			String indexName, String recognizedText, String commandURI, Indexable[] indexables) {
		this.tag = tag;
		this.recognized = recognized;
		this.belowThreshold = belowThreshold;
		this.confidence = confidence;
		this.indexName = indexName;
		this.recognizedText = recognizedText;
		this.commandURI = commandURI;
		this.indexables = indexables;
	}
}
 
