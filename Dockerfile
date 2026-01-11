# Stage 1: Build the Fat JAR
FROM gradle:8-jdk17 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
# This task creates the "Fat JAR" (includes all dependencies)
# If this fails, try 'gradle build' or 'gradle shadowJar' instead
RUN gradle buildFatJar --no-daemon

# Stage 2: Run the server
FROM eclipse-temurin:17-jre
EXPOSE 8080
RUN mkdir /app
# We copy the 'all.jar' which contains your code + all libraries
COPY --from=build /home/gradle/src/build/libs/*-all.jar /app/ktor-server.jar
ENTRYPOINT ["java","-jar","/app/ktor-server.jar"]