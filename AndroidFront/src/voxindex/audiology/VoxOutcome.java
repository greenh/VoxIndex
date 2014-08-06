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
 * Enum describing voice recognition outcomes.
 */
public enum VoxOutcome {
	None {
		@Override public int imageID() {
			return R.drawable.blank;
		}
	},
	Accepted {
		@Override public int imageID() {
			return R.drawable.check_green;
		}
	},
	LowConfidence {
		@Override public int imageID() {
			return R.drawable.down_blue;
		}
	},
	Rejected {
		@Override public int imageID() {
			return R.drawable.x_red;
		}
	};

	public abstract int imageID();
}
