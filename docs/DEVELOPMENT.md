# Development

## Building & Running

| Task | Description |
|---|---|
| `./gradlew test` | Run the tests |
| `./gradlew build` | Build everything |
| `./gradlew buildFatJar` | Build an executable JAR of the server with all dependencies included |
| `./gradlew buildImage` | Build the docker image to use with the fat JAR |
| `./gradlew publishImageToLocalRegistry` | Publish the docker image locally |
| `./gradlew run` | Run the server |
| `./gradlew runDocker` | Run using the local docker image |

If the server starts successfully, you'll see the following output:

```
2024-12-04 14:32:45.584 [main] INFO  Application - Application started in 0.303 seconds.
2024-12-04 14:32:45.682 [main] INFO  Application - Responding at http://0.0.0.0:8080
```

## Required environment variables

| Variable | Description |
|---|---|
| `CRYPTO_SDK_NEWS_API_KEY` | API key for newsapi.org |
| `BFF_CRYPTO_ADMIN_BYPASS_SECRET` | Admin bypass secret for testing without Play Integrity |
| `SENTRY_DNS_CRYPTO_TRACKER_BFF_VALUE` | Sentry DSN for the BFF |
| `SENTRY_DNS_CRYPTO_TRACKER_ANDROID_VALUE` | Sentry DSN for the Android app (served via `/sentry/android`) |