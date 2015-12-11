FROM frolvlad/alpine-oraclejdk8
VOLUME /tmp
ADD spring-cloud-sleuth-zipkin-stream-1.0.0.BUILD-SNAPSHOT.jar app.jar
RUN sh -c 'touch /app.jar'
CMD ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]
