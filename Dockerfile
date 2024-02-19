FROM maven:3.9.6-amazoncorretto-21

WORKDIR /app

COPY . /app
RUN mvn install -f /app/pom.xml && rm -rf /root/.m2/repository/*

RUN chmod +x /app/entrypoint.sh

ENTRYPOINT ["/app/entrypoint.sh"]