# Running a Zipkin Query Server

There are 4 parts to Zipkin: the instrumented client apps, the web UI, the backend database and the query server. The database for this implementation is MySQL.

## Zipkin Services

Run this app using the docker-compose provided.

```
$ docker-compose build zipkin
$ docker-compose up
...
```

and test it

```
$ curl localhost:9411/api/v1/services
["zipkin-query"]
```

The app might fail to start if mysql is not available when it needs it. If that happens you can just start it separately: just keep running `docker-compose up` until it works.


## Instrumenting Apps

Depend on [Spring Cloud Sleuth Stream](https://github.com/spring-cloud-spring-cloud-sleuth) and the rabbit binder (`spring-cloud-starter-stream-rabbit`).

Once the apps start publishing spans they will appear in the span store as well.

## Running in an IDE

You can run this app in an IDE and still use docker-compose to create the middleware:

```
$ docker-compose up rabbitmq mysql
```

The web UI cannot access the docker host though, so you have to run that separately, or just test against the query server directly on port 9411.
