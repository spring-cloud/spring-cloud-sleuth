package org.springframework.cloud.sleuth;

/**
 * 
 * @author Gaurav Rai Mazra
 *
 */
public interface Filter {
	public boolean accept (final String field);
}
