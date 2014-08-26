package voxfront.util;

import com.google.gwt.core.client.JavaScriptObject;

/** 
 * Sligbt specialization of the Map-thingy to describe operations traveling
 * e.g. between the main thread and a worker.
 */
public class Op extends Map {
	
	protected Op() { }
	
	public final native String getOp() /*-{ return this.op; }-*/;

	public final static native Op makeOp(String op) /*-{
		return { op: op };
	}-*/;

}
