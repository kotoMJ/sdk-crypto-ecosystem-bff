package cz.kotox.crypto.sdk

import cz.kotox.crypto.sdk.service.IntegrityService
import cz.kotox.crypto.sdk.service.NewsService
import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain
import io.sentry.Sentry

fun main(args: Array<String>) {
    // Initialize Sentry before starting the engine.
    Sentry.init { options ->
        options.dsn =
            requireNotNull(System.getenv("SENTRY_DNS_CRYPTO_TRACKER_BFF")) {
                "SENTRY_DNS_CRYPTO_TRACKER_BFF env is missing"
            }

        options.tracesSampleRate = 1.0 // Adjust these for production
        options.isEnableUncaughtExceptionHandler = true
    }

    EngineMain.main(args)
}

fun Application.module() {
    val client = this.httpClient
    val newsService = NewsService(client)
    val integrityService = IntegrityService()
    val adminBypassSecret =
        requireNotNull(System.getenv("BFF_CRYPTO_ADMIN_BYPASS_SECRET")) {
            "BFF_CRYPTO_ADMIN_BYPASS_SECRET env is missing!"
        }

    configurePlugins()
    configureRouting(newsService, integrityService, adminBypassSecret)
    configureSentryTracing()
}
