package voxfront.client;

import java.util.HashSet;

public class IndexIDs {

	public static final String ControlIndexID = "000000000000000000000001";
	public static final String AudiologyIndexID = "000000000000000000000002";
	public static final HashSet<String> defaultIndexIDs = new HashSet<String>();
	static {
		defaultIndexIDs.add(ControlIndexID);
		defaultIndexIDs.add(AudiologyIndexID);
		};
	
}
