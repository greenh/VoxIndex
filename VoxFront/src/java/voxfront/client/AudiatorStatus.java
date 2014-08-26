package voxfront.client;

import java.util.HashMap;

import com.google.gwt.resources.client.ImageResource;

public enum AudiatorStatus {
	Idle { 
		@Override public ImageResource image() { return Resources.R.white_dot(); }
	}, 
	Quiet {
		@Override public ImageResource image() { return Resources.R.green_dot(); }
	}, 
	SignalDetected {
		@Override public ImageResource image() { return Resources.R.yellow_dot(); }
	}, 
	Overflow {
		@Override public ImageResource image() { return Resources.R.red_dot(); }
	},
	Unknown { 
		@Override public ImageResource image() { return Resources.R.white_dot(); }
	};
	
	public abstract ImageResource image();
	
	public static HashMap<String,AudiatorStatus> statusMap = new HashMap<String,AudiatorStatus>();
	static {
		for (AudiatorStatus stat: AudiatorStatus.values()) {
			statusMap.put(stat.toString(), stat);
		}
	}
}
