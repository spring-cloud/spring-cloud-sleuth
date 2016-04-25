package rx.plugins;

/**
 * {@link RxJavaPlugins} helper class to access the package scope method of
 * {@link RxJavaPlugins#reset()}.
 *
 * @deprecated Will disappear once this gets closed
 * https://github.com/ReactiveX/RxJava/issues/2297
 *
 * @author Shivang Shah
 */
@Deprecated
public class SleuthRxJavaPlugins {

	public static void resetPlugins() {
		RxJavaPlugins.getInstance().reset();
	}

}
