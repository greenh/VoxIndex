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
package voxindex.shared;

/**
 * Defines a number of static values used in the VoxIndex code.
 */
public class VoxIndexIDs {

	public static final String ControlIndexID = "000000000000000000000001";
	public static final String AudiologyIndexID = "000000000000000000000002";
	
	public static final String MaxPreassignedID = "000000000000000000000010";
	
	
	
	public static final String voxCmdURNPrefix = "urn:voxindex:";
	
	public static String voxCmdURN(String cmd) { return voxCmdURNPrefix + cmd; }
	
	public static boolean isCmd(String targetURN, String cmdURN) {
		if (targetURN == null)
			return false;
		else
			return targetURN.equals(cmdURN);
	}
	public static boolean isCmdURN(String targetURN, String cmd) {
		if (targetURN == null)
			return false;
		else
			return voxCmdURN(cmd).equals(targetURN);
	}

}
