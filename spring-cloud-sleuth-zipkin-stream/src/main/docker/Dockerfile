FROM frolvlad/alpine-oraclejdk8
VOLUME /tmp
ADD @project.artifactId@-@project.version@-exec.jar app.jar
RUN sh -c 'touch /app.jar'
EXPOSE 9411
CMD ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]
