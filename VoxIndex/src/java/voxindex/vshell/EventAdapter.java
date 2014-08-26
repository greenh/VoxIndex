package voxindex.vshell;

import system.EventArgs;
import system.EventHandler;
import system.Object;

public class EventAdapter extends EventHandler {

	final EventHandler_ handler;
	
	public EventAdapter(EventHandler_ handler) {
		this.handler = handler;
	}
	@Override public void Invoke(Object arg0, EventArgs arg1) {
		handler.onEvent(arg1);
	}

}
