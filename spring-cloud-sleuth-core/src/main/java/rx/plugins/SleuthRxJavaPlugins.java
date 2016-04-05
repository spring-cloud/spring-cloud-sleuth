package rx.plugins;

/**
 * {@link RxJavaPlugins} helper class to access the package scope method
 * of {@link RxJavaPlugins#reset()}. Will disappear once this gets closed
 * https://github.com/ReactiveX/RxJava/issues/2297
 *
 * @author Shivang Shah
 * @since 1.0.0
 */
@Deprecated
public class SleuthRxJavaPlugins extends RxJavaPlugins {

	SleuthRxJavaPlugins() {
		super();
	}

	public static void resetPlugins() {
		getInstance().reset();
	}
}
