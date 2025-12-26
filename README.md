# SDK Crypto Ecosystem backend for frontend (BFF)

Case study of BFF for the [SDK ecosystem](https://github.com/kotoMJ/android-sdk-crypto-ecosystem).

This is a classic "Backend for Frontend" (BFF) pattern.   
A lightweight server that acts as a gatekeeper, verifying the Android device's integrity 
before allowing access to a protected upstream service (API under real API key).

## Features

The main purpose of this backend is to handle requests with public API keys together with the Google Play Integrity token
and use them to call services with real API key not exposed via frontend.

Here's a list of features included in this project:

| Name                                               | Description                                                 |
| ----------------------------------------------------|------------------------------------------------------------- |
| [Routing](https://start.ktor.io/p/routing-default) | Allows to define structured routes and associated handlers. |

## Building & Running

To build or run the project, use one of the following tasks:

| Task                                    | Description                                                          |
| -----------------------------------------|---------------------------------------------------------------------- |
| `./gradlew test`                        | Run the tests                                                        |
| `./gradlew build`                       | Build everything                                                     |
| `./gradlew buildFatJar`                 | Build an executable JAR of the server with all dependencies included |
| `./gradlew buildImage`                  | Build the docker image to use with the fat JAR                       |
| `./gradlew publishImageToLocalRegistry` | Publish the docker image locally                                     |
| `./gradlew run`                         | Run the server                                                       |
| `./gradlew runDocker`                   | Run using the local docker image                                     |

If the server starts successfully, you'll see the following output:

```
2024-12-04 14:32:45.584 [main] INFO  Application - Application started in 0.303 seconds.
2024-12-04 14:32:45.682 [main] INFO  Application - Responding at http://0.0.0.0:8080
```

## Contribution

In order to contribute to this codebase read [Conventions] part.

[Conventions]: docs/CONVENTIONS.md
