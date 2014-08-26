package voxfront.client;

import elemental.html.Blob;
import com.google.gwt.typedarrays.client.ArrayBufferNative;


public class Native {
	public static native void log(String message) /*-{
      console.log(message);
  }-*/;
	
	public static native Blob makeBlob(ArrayBufferNative ab, int bytes, String type) /*-{
		return new Blob([new DataView(ab, 0, bytes + 8)], { type: type });
	}-*/;
	
	public static native String createObjectURL(Blob blob) /*-{
		return URL.createObjectURL(blob);
	}-*/;
	

}
