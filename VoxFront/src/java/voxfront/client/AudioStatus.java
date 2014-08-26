package voxfront.client;

import com.google.gwt.resources.client.ImageResource;

public enum AudioStatus {
	Idle {
		@Override public boolean startEnabled() {
			return true;
		}

		@Override public boolean stopEnabled() {
			return false;
		}

		@Override public ImageResource image() {
			return Resources.R.white_dot();
		}
	},
	Recording {
		@Override public boolean startEnabled() {
			return false;
		}

		@Override public boolean stopEnabled() {
			return true;
		}

		@Override public ImageResource image() {
			return Resources.R.green_dot();
		} // red
	},
	VoiceAcquired {
		@Override public boolean startEnabled() {
			return false;
		}

		@Override public boolean stopEnabled() {
			return true;
		}

		@Override public ImageResource image() {
			return Resources.R.green_dot();
		}
	},
	VoiceDetected {
		@Override public boolean startEnabled() {
			return false;
		}

		@Override public boolean stopEnabled() {
			return true;
		}

		@Override public ImageResource image() {
			return Resources.R.yellow_dot();
		}
	},
	VoiceOverflow {
		@Override public boolean startEnabled() {
			return false;
		}

		@Override public boolean stopEnabled() {
			return true;
		}

		@Override public ImageResource image() {
			return Resources.R.red();
		}
	},
	Failed {
		@Override public boolean startEnabled() {
			return false;
		}

		@Override public boolean stopEnabled() {
			return true;
		}

		@Override public ImageResource image() {
			return Resources.R.x_red();
		}
	};

	public abstract boolean startEnabled();

	public abstract boolean stopEnabled();

	public abstract ImageResource image();
};
