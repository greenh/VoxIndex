package elemental.js.html;

import elemental.html.AudioProcessingHandler;
import elemental.html.ScriptProcessorNode;
import elemental.events.EventListener;

public class JsScriptProcessorNode extends JsAudioNode  implements ScriptProcessorNode {
  protected JsScriptProcessorNode() {}

  @Override public final native int getBufferSize() /*-{
    return this.bufferSize;
  }-*/;

  @Override public final native EventListener getOnaudioprocess() /*-{
    return @elemental.js.dom.JsElementalMixinBase::getListenerFor(Lcom/google/gwt/core/client/JavaScriptObject;)(this.onaudioprocess);
  }-*/;

  @Override public final native void setOnaudioprocess(AudioProcessingHandler handler) /*-{
    this.onaudioprocess = function(event) {
      handler.@elemental.html.AudioProcessingHandler::onAudioProcessing(Lelemental/html/AudioProcessingEvent;)(event);    }   
  }-*/;

//  @Override public final native void setOnaudioprocess(EventListener listener) /*-{
//    this.onaudioprocess = @elemental.js.dom.JsElementalMixinBase::getHandlerFor(Lelemental/events/EventListener;)(listener);
//  }-*/;

}