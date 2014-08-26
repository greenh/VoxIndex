package voxfront.util;

import com.google.gwt.core.client.JavaScriptObject;


/**
 * A class that fronts for a JavaScript object-cum-map, a collection of
 * JavaScript name/value tuples.
 */
public class Map extends JavaScriptObject {

	protected Map() { }
	
	public final static native Map create() /*-{ return { }; }-*/; 
	
	public final native <T extends JavaScriptObject> T get(String key) /*-{
		return this[key];
	}-*/;
	
	public final native int getInt(String key) /*-{
		return this[key];
	}-*/;
	
	public final native float getFloat(String key) /*-{
		return this[key];
	}-*/;
	
	public final native String getString(String key) /*-{
		return this[key];
	}-*/;
	
	/**
	 * Puts a (key, value) entry into the map.
	 * @param what The entry to set.
	 * @param obj The value. NB: Note that this is a Java object, not restricted 
	 * to a JavaScriptObject. Use carefully!
	 * @return The Map object.
	 */
	public final native Map put(String what, Object obj) /*-{
		this[what] = obj; 
		return this;
	}-*/;

	public final native Map put(String what, float obj) /*-{
		this[what] = obj; 
		return this;
	}-*/;

	public final native Map put(String what, int obj) /*-{
		this[what] = obj; 
		return this;
	}-*/;

	public final native Map put(String what, String obj) /*-{
		this[what] = obj; 
		return this;
	}-*/;


//	public void poo() {
//		Map m = Map.create().put("cmd", "poo").put("data", 123).put("piffle","poo");
//		int doo = m.get("data");
//		String arf = m.get("piffle");
//		
//		
//	}
	
}
