/*
 *  Copyright (c) 2011 - 2014 Howard Green. All rights reserved.
 *  The use and distribution terms for this software are covered by the
 *  Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 *  which can be found in the file epl-v10.html at the root of this distribution.
 *  By using this software in any fashion, you are agreeing to be bound by
 *  the terms of this license.
 *  
 *  You must not remove this notice, or any other, from this software.
 */
package voxindex.audiology;

/**
 * Status value enumeration for the Audiology app.
 */
public enum StatusType {
	Idle {
		@Override public boolean startEnabled() {
			return true;
		}

		@Override public boolean stopEnabled() {
			return false;
		}

		@Override public int imageID() {
			return R.drawable.white_dot;
		}
	},
	Recording {
		@Override public boolean startEnabled() {
			return false;
		}

		@Override public boolean stopEnabled() {
			return true;
		}

		@Override public int imageID() {
			return R.drawable.green_dot;
		} // red
	},
	VoiceAcquired {
		@Override public boolean startEnabled() {
			return false;
		}

		@Override public boolean stopEnabled() {
			return true;
		}

		@Override public int imageID() {
			return R.drawable.green_dot;
		}
	},
	VoiceDetected {
		@Override public boolean startEnabled() {
			return false;
		}

		@Override public boolean stopEnabled() {
			return true;
		}

		@Override public int imageID() {
			return R.drawable.yellow_dot;
		}
	},
	VoiceOverflow {
		@Override public boolean startEnabled() {
			return false;
		}

		@Override public boolean stopEnabled() {
			return true;
		}

		@Override public int imageID() {
			return R.drawable.red;
		}
	},
	Failed {
		@Override public boolean startEnabled() {
			return false;
		}

		@Override public boolean stopEnabled() {
			return true;
		}

		@Override public int imageID() {
			return R.drawable.x_red;
		}
	};

	public abstract boolean startEnabled();

	public abstract boolean stopEnabled();

	public abstract int imageID();
};
