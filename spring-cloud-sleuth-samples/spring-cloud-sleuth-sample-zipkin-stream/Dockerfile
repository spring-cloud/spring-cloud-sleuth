FROM springio/java8
RUN chmod 777 -R /tmp
VOLUME /tmp
VOLUME /app
EXPOSE 9411 8000
RUN groupadd -g 1000 spring
RUN useradd -u 1000 -g 1000 -ms /bin/bash spring
VOLUME /home/spring
ENV HOME /home/spring
USER spring
WORKDIR /app/spring-cloud-sleuth-samples/spring-cloud-sleuth-sample-zipkin-stream
CMD ["../../mvnw", "spring-boot:run"]
