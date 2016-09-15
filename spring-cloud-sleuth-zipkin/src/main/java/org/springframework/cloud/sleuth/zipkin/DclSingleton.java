package org.springframework.cloud.sleuth.zipkin;

/**
 * Implementation of Dcl singleton creation/
 *
 * @see <a href=
 * "https://en.wikipedia.org/wiki/Double-checked_locking">https://en.wikipedia.org/wiki/Double-checked_locking/a>
 * @author Marcin Wielgus
 */
public class DclSingleton<K> {
	interface InstanceFactory<K> {
		K newInstance();
	}

	private volatile K value;

	public K get(InstanceFactory<K> factory) {
		K result = this.value;
		if (result == null) {
			synchronized (this) {
				result = this.value;
				if (result == null) {
					this.value = result = factory.newInstance();
				}
			}
		}
		return result;
	}

	public void invalidate() {
		this.value = null;
	}
}
