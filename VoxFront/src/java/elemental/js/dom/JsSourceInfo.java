package elemental.js.dom;

import com.google.gwt.core.client.JavaScriptObject;

import elemental.dom.SourceInfo;

public class JsSourceInfo extends JavaScriptObject implements SourceInfo {

	protected JsSourceInfo() { }

	@Override public final native String getKind() /*-{
    return this.kind;
  }-*/;

	@Override public final native String getLabel() /*-{
    return this.label;
  }-*/;

	@Override public final native String getID() /*-{
    return this.id;
  }-*/;

}
