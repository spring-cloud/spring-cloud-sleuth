package rx.plugins;

/**
<<<<<<< HEAD
 *
 * @author Shivang Shah
=======
 * {@link RxJavaPlugins} helper class to access the package scope method
 * of {@link RxJavaPlugins#reset()}. Will disappear once this gets closed
 * https://github.com/ReactiveX/RxJava/issues/2297
 *
 * @author Shivang Shah
 * @since 1.0.0
>>>>>>> e64ea26da45e5f9383d86a9689fe916c53eb7ad2
 */
public class SleuthRxJavaPlugins extends RxJavaPlugins {

	SleuthRxJavaPlugins() {
		super();
	}

	public static void resetPlugins() {
		getInstance().reset();
	}
}
