FROM maven:3 AS build

COPY . /build
WORKDIR /build

RUN mvn -Dmaven.test.skip=true clean package


FROM openjdk:11-slim

COPY --from=build /build/target/assembly/chameleon-1.0-SNAPSHOT-full-tar.tar /tmp

RUN tar -xf /tmp/chameleon-1.0-SNAPSHOT-full-tar.tar -C /opt

WORKDIR /opt/chameleon-1.0-SNAPSHOT

CMD java -Dlogback.configurationFile=logback-packaged.xml -Dconfig.resource=application-packaged.conf $JAVA_OPTS -cp "/opt/chameleon-1.0-SNAPSHOT/*" de.devk.chameleon.MainImpl