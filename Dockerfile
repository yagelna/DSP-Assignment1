FROM public.ecr.aws/amazoncorretto/amazoncorretto:21

WORKDIR /app

COPY manager/target/manager*.jar /app/Manager.jar
COPY worker/target/worker*.jar /app/Worker.jar

COPY entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

ENTRYPOINT ["/app/entrypoint.sh"]