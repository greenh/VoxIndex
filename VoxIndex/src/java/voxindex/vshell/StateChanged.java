package voxindex.vshell;

import system.EventArgs;
import system.EventHandler;

	public class StateChanged extends EventHandler {
		@Override public void Invoke(system.Object arg0, EventArgs arg1) {
			try {
				System.out.println("State changed");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
