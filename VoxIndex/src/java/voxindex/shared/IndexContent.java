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

public class IndexContent implements IsSerializable {
	
	public static class IndexEntry implements IsSerializable, Comparable<IndexEntry> {
		public String sourceTerm;
		public String[] terms;
		
		public IndexEntry(String sourceTerm, String... terms) {
			this.sourceTerm = sourceTerm;
			this.terms = terms;
		}
		
		@SuppressWarnings("unused") private IndexEntry() { }

		@Override public int compareTo(IndexEntry ent) {
			return sourceTerm.compareTo(ent.sourceTerm);
		}

	}
	
	public static class IndexSpec implements IsSerializable {
		public String categoryName;
		public String[] prefixes;
		public IndexEntry[] entries;
		
		public IndexSpec(String categoryName, String[] prefixes, IndexEntry... entries) {
			this.prefixes = prefixes;
			this.entries = entries;
			this.categoryName = categoryName;
		}
		
		@SuppressWarnings("unused") private IndexSpec() {}
	}
	
	public String name;
	public String description;
	public IndexSpec[] specs;

	public IndexContent(String name, String description, IndexSpec[] specs) {
		this.name = name;
		this.description = description;
		this.specs = specs;
	}
	
	@SuppressWarnings("unused") private IndexContent() { }
}
