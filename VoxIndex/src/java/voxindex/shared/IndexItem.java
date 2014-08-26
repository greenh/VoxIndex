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

import com.google.gwt.user.client.rpc.IsSerializable;

public class IndexItem implements IsSerializable {
	
	public static class IndexTerm implements IsSerializable {
		public String originalTerm;
		public String vocalTerms[];
		
		public IndexTerm() { }
		
		public IndexTerm(String originalTerm, String[] vocalTerms) {
			this.originalTerm = originalTerm;
			this.vocalTerms = vocalTerms;
		}
		
	}
	
	public String indexID;
	public IndexTerm[] indexTerms;
	
	public IndexItem(String indexID, IndexTerm[] indexTerms) {
		this.indexID = indexID;
		this.indexTerms = indexTerms;
	}
	
	public IndexItem() { }
}
