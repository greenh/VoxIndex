package voxindex.audiology;

public class RPCProxyPolicySpec {

	public RPCProxyPolicySpec() {
	}

	/** Timeout for policy retrieval, in msec. */
	int policyFetchTimeoutMsec = 0;
	
	/** true -> fail if serialization policy fetch fails */
	boolean policyFetchRequired = false;  
	
	/** Request timeout, in msec. */
	int requestConnectTimeout = 0; 
	
}
