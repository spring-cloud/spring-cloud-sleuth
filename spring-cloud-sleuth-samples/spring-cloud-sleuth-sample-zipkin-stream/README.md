# Running a Zipkin Server

There are 3 parts to Zipkin: 

- the instrumented client apps
- the backend database 
- the Zipkin server

## Instrumenting Apps

Depend on [Spring Cloud Sleuth Stream](https://github.com/spring-cloud-spring-cloud-sleuth) and the 
rabbit binder (`spring-cloud-starter-stream-rabbit`).

Once the apps start publishing spans they will appear in the span store as well.

## Running in an IDE

You can run this app in an IDE and still use docker-compose to create the middleware:

```
$ docker-compose up rabbitmq
```
