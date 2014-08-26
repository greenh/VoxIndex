package voxindex.server;

import java.util.Enumeration;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;

public class ConfigWrapper implements ServletConfig {
	final ServletConfig config;
	
	public ConfigWrapper(ServletConfig config) {
		this.config = config;
	}

	@Override public String getInitParameter(String arg0) {
		return config.getInitParameter(arg0);
	}

	@SuppressWarnings("unchecked") 
	@Override public Enumeration<String> getInitParameterNames() {
		return config.getInitParameterNames();
	}

	@Override public ServletContext getServletContext() {
		return config.getServletContext();
	}

	@Override public String getServletName() {
		return config.getServletName();
	}

}
