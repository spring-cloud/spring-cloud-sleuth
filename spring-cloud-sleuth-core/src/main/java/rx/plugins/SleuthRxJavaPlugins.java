package rx.plugins;

/**
 *
 * @author Shivang Shah
 */
public class SleuthRxJavaPlugins extends RxJavaPlugins {

	SleuthRxJavaPlugins() {
		super();
	}

	public static void resetPlugins() {
		getInstance().reset();
	}
}
