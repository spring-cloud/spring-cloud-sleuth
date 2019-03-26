# This project is deprecated

Please switch to the normal Zipkin server which supports RabbitMQ and Kafka.
See [our documentation](https://cloud.spring.io/spring-cloud-sleuth/single/spring-cloud-sleuth.html#_sleuth_with_zipkin_over_rabbitmq_or_kafka) for more.

# Running Zipkin Server

There are 3 parts to Zipkin: the instrumented client apps, the backend database and the Zipkin server. The database for this implementation is MySQL.

> There is a running instance on PWS: https://zipkin-web.cfapps.io. It is backed by a `zipkin-server` with a MySQL backend and RabbitMQ (Spring Cloud Stream) for span transport.

## Instrumenting Apps

Depend on [Spring Cloud Sleuth Stream](https://github.com/spring-cloud-spring-cloud-sleuth). Bind to a rabbit service (or redis if you prefer - normal Spring Cloud Stream process).

## Zipkin Server

Depend on `spring-cloud-sleuth-zipkin-stream` and enable the server:

```java
@SpringBootApplication
@EnableZipkinStreamServer
public class ZipkinStreamServerApplication {

	public static void main(String[] args) throws Exception {
		SpringApplication.run(ZipkinStreamServerApplication.class, args);
	}

}
```

Zipkin has a web UI, which is enabled by default when you depend on `io.zipkin.java:zipkin-autoconfigure-ui`.

Bind to MySQL and the same Stream service that you did in the apps (rabbit, redis, kafka). Set `spring.datasource.initialize=true` the first time you start to initialize the database.

Uses the `zipkin-server` library from the [OSS](https://github.com/openzipkin/zipkin-java) as well as `spring-cloud-sleuth-stream`.

> NOTE: running in the "test" profile you don't need MySQL (the span store is in memory). You could even run in PWS without MySQL.

