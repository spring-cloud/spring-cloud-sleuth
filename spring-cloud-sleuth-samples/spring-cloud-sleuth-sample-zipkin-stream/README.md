# Running a Zipkin Server

There are 3 parts to Zipkin: 

- the instrumented client apps
- the backend database (defaults to in-memory db)
- the Zipkin server

## Zipkin Services

Run the Zipkin (remember to have RabbitMQ running) by 

either running Spring Boot Maven plugin:

```
./mvnw spring-boot:run --projects spring-cloud-sleuth-samples/spring-cloud-sleuth-sample-zipkin-stream
```

or running the packaged app from the root:

```
./mvnw package --projects spring-cloud-sleuth-samples/spring-cloud-sleuth-sample-zipkin-stream
java -jar spring-cloud-sleuth-samples/spring-cloud-sleuth-sample-zipkin-stream/target/spring-cloud-sleuth-sample-zipkin-stream-1.0.0.BUILD-SNAPSHOT-exec.jar
```

and test it

```
$ curl localhost:9411/api/v1/services
["zipkin-server"]
```

## Instrumenting Apps

Depend on [Spring Cloud Sleuth Stream](https://github.com/spring-cloud-spring-cloud-sleuth) and the 
rabbit binder (`spring-cloud-starter-stream-rabbit`).

Once the apps start publishing spans they will appear in the span store as well.

## Running in an IDE

You can run this app in an IDE and still use docker-compose to create the middleware:

```
$ docker-compose up rabbitmq
```
