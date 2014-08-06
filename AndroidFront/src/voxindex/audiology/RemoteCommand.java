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

import java.util.HashMap;
import voxindex.shared.VoxIndexIDs;

/**
 * Describes commands processed by the app on the basis of recognized voice
 */
public class RemoteCommand {

	public static final String baseURI = VoxIndexIDs.voxCmdURNPrefix; // "urn:indexterous.audiology-command:";
	
	public enum Command { 
		Back, Forward,
		Top, Bottom, Move, ScrollUp, ScrollDown,
		Log, CloseLog, Tab, Use, 
		Index, IndexIndex,
		NewTab, CloseTab,
		PreviousVariant, NextVariant,
		Stop,
		Accept, Reject,
		Exit
	}
	
	public static HashMap<String, Command> commandMap = new HashMap<String, Command>();

	static {
		for (Command cmd : Command.values()) {
			commandMap.put(cmd.name().toLowerCase(), cmd);
		}
	}
	
	public static String URIFor(Command cmd) {
		return baseURI + ":" + cmd.name().toLowerCase();
	}
	
	public static boolean isCommand(String uri) {
		return uri.startsWith(baseURI);
	}
	
	public static Command getCommand(String uri) {
		if (uri.startsWith(baseURI)) {
			String s = uri.substring(baseURI.length());  // baseURI includes final ':'!!!
			int n = s.indexOf(':');
			if (n >= 0)
				s = s.substring(0, n);
			return commandMap.get(s);
		} else
			return null;
	}
	
	public static String getPostCommand(String uri) {
		if (uri.startsWith(baseURI)) {
			String s = uri.substring(baseURI.length());
			int n = s.indexOf(':');
			if (n >= 0)
				return s.substring(n + 1);
			return null;
		} else
			return null;
	}
}
