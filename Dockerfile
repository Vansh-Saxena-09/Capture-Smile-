# Capture Smile has no build tool (no Maven/Gradle) — javac compiles capturesmile/*.java directly.
# Two stages: compile with a full JDK, run with a slim JRE so the shipped image doesn't carry javac.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY capturesmile/ capturesmile/
RUN javac -encoding UTF-8 -d out capturesmile/*.java

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/out ./out
COPY web/ web/

# Render/Fly.io/Railway inject PORT; Main.java reads it and binds 0.0.0.0 automatically.
# TMDB_API_KEY is read from the environment — see Tmdb.apiKey(). No secrets/ dir ships in the image.
EXPOSE 8080
CMD ["java", "-Dwebroot=/app/web", "-cp", "out", "capturesmile.Main"]
