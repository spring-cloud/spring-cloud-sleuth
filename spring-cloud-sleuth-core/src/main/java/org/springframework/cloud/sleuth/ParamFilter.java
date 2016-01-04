package org.springframework.cloud.sleuth;

import java.util.HashSet;
import java.util.Set;

/**
 * Class filtering the Param to be added in Span
 * @author Gaurav Rai Mazra
 *
 */
public class ParamFilter implements Filter {
	private final Set<String> params = new HashSet<>();
	public ParamFilter() {
	}
	
	public ParamFilter(String... params) {
		this();
		addParams(params);
	}
	
	private void addParams(String... params) {
		if (params != null) {
			for (String param : params) {
				// TODO use some library here
				if (param != null && param.trim().length() > 0) {
					this.params.add(param.toLowerCase());
				}
			}
		}
	}
	
	public void addParam(String param) {
		if (param != null && param.trim().length() > 0)
			this.params.add(param.toLowerCase());
	}

	@Override
	public boolean accept(String field) {
		return field != null && params.contains(field.toLowerCase());
	}

}
