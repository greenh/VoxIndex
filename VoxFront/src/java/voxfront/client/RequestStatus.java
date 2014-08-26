package voxfront.client;

import com.google.gwt.resources.client.ImageResource;

public enum RequestStatus {

	None {
		@Override public ImageResource image() {
			return Resources.R.blank();
		}
	},
	InProgress {
		@Override public ImageResource image() {
			return Resources.R.down_blue();
		}
	},
	Accepted {
		@Override public ImageResource image() {
			return Resources.R.check_green();
		}
	},
	LowConfidence {
		@Override public ImageResource image() {
			return Resources.R.yellow();    // need something else here
		}
	},
	Rejected {
		@Override public ImageResource image() {
			return Resources.R.x_red();
		}
	};

	public abstract ImageResource image();
}