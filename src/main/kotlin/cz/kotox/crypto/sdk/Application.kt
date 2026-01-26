package cz.kotox.crypto.sdk

import cz.kotox.crypto.sdk.service.IntegrityService
import cz.kotox.crypto.sdk.service.NewsService
import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain

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
//    initSentry()

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
