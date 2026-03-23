package cz.kotox.crypto.sdk

import cz.kotox.crypto.sdk.service.IntegrityService
import cz.kotox.crypto.sdk.service.NewsService
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.netty.EngineMain
import io.sentry.Sentry

/**
 * Used for local start of the engine.
 */
fun main(args: Array<String>) {
    EngineMain.main(args)
}

/**
 * On the GCloud the module() function is called directly from application.yaml without main() function entrypoint.
 *
 */
fun Application.module() {
    initSentry()

    // Flush pending Sentry events on shutdown. Cloud Run sends SIGTERM before killing
    // idle instances, which triggers Ktor's graceful shutdown and this listener.
    // We intentionally do NOT flush per-request — the background worker handles delivery
    // while the instance is alive, and this single flush ensures nothing is lost at shutdown.
    monitor.subscribe(ApplicationStopped) {
        Sentry.flush(MAXIMUM_SENTRY_FLUSH_TIMEOUT_MS)
        Sentry.close()
    }

    val client = this.createHttpClient()
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
