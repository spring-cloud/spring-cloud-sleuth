# Running Zipkin a Query Server

There are 4 parts to Zipkin: the instrumented client apps, the web UI, the backend database and the query server. The database for this implementation is MySQL.

> There is a running instance on PWS: http://zipkin-web.cfapps.io. It is backed by a `zipkin-server` with a MySQL backend and RabbitMQ (Spring Cloud Stream) for span transport.

## Instrumenting Apps

Depend on [Spring Cloud Sleuth Stream](https://github.com/spring-cloud-spring-cloud-sleuth). Bind to a rabbit service (or redis if you prefer - normal Spring Cloud Stream process).

## Query Server

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

Bind to MySQL and the same Stream service that you did in the apps (rabbit, redis, kafka). Set `spring.datasource.initialize=true` the first time you start to initialize the database.

Uses the `zipkin-server` library from the [OSS](https://github.com/openzipkin/zipkin-java) as well as `spring-cloud-sleuth-stream`.

> NOTE: running in the "test" profile you don't need MySQL (the span store is in memory). You could even run in PWS without MySQL.

## Web UI in Cloud Foundry

Get the jar from the [OSS](https://github.com/openzipkin/zipkin) and push it:

```
$ cf push zipkin-web -p zipkin-web/build/libs/zipkin-web*all.jar
```

It needs an environment variable to set the command line args:

```
$ cf set-env zipkin-web JBP_CONFIG_JAVA_MAIN '{arguments: "-zipkin.web.port=:\$PORT -zipkin.web.rootUrl=/ -zipkin.web.query.dest=zipkin-server.cfapps.io:80 -zipkin.web.resourcesRoot=."}'
```

NOTE: `JBP_CONFIG_JAVA_MAIN` only works with Java buildpack v3.2 and above (so not in PEZ Heritage right now).
