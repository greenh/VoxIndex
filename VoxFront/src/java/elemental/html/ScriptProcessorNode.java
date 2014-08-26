package elemental.html;

import elemental.events.EventListener;

public interface ScriptProcessorNode extends AudioNode {
  public int getBufferSize();

  public EventListener getOnaudioprocess();

  public void setOnaudioprocess(AudioProcessingHandler h);
//  public void setOnaudioprocess(EventListener h);

}
