package cz.kotox.crypto.sdk

// Aliasing the plugin specifically for the Ktor 'install' block
import cz.kotox.crypto.sdk.exception.MissingConfigException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.sentry.Sentry
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.kotlin.SentryContext
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

private const val MAXIMUM_SENTRY_FLUSH_TIMEOUT_MS = 5000L

fun initSentry() {
    Sentry.init { options ->
        val dsnBff =
            requireNotNull(System.getenv("SENTRY_DNS_CRYPTO_TRACKER_BFF")) {
                "SENTRY_DNS_CRYPTO_TRACKER_BFF env is missing"
            }
        options.dsn = dsnBff
        LoggerFactory.getLogger("SentryBeforeSend")
            .info("SENTRY_DNS_CRYPTO_TRACKER_BFF:[$dsnBff]")

        options.tracesSampleRate = 1.0 // Adjust these for production
        // options.isEnableUncaughtExceptionHandler = true
        options.isDebug = false
        // This allows Sentry to capture headers like sentry-trace, baggage, and others
        options.isSendDefaultPii = true

        options.setBeforeSend { event, hint ->
            // This will log for ANY event (error or transaction) before it's sent.
            LoggerFactory.getLogger("SentryBeforeSend")
                .info("--- Sentry BeforeSend triggered for event: ${event.eventId} ---")
            event // Return the event unmodified
        }
    }
}

/**
 * Ktor Server doesn't have an "automatic" server-side Sentry plugin like some other frameworks,
 * so we use an interceptor to extract the headers and start the transaction.
 */

@Suppress("TooGenericExceptionCaught", "LongMethod", "MaxLineLength", "MagicNumber")
fun Application.configureSentryTracing() {
    intercept(ApplicationCallPipeline.Monitoring) {
        initSentry()
        application.log.info("TRACE: Interceptor triggered for ${call.request.uri}")

        val sentryTraceHeader = call.request.header("sentry-trace")
        application.log.warn("DEBUG [Sentry]: sentryTraceHeader: $sentryTraceHeader")

        // Ktor's getHeaders provides an enumeration; we need a List for Sentry
        // baggageHeaders: [sentry-environment=production,sentry-public_key=0e3c6ffd2caeeb9b3894e9f7a23eb08c,sentry-release=cz.kotox.sdk.crypto.app%400.0.1%2B1,sentry-replay_id=72c8ac2197624cec95916af98c09ca2f,sentry-sample_rand=0.941817019361639,sentry-trace_id=5cd9a67eef5e497a87c39aab7fb693b9]
        // val baggageHeaders = call.request.headers.getAll("baggage")
        val baggageHeader = call.request.header("baggage")
        application.log.warn("DEBUG [Sentry]: baggageHeader: $baggageHeader")

        // 1. Let Sentry do the heavy lifting. This automatically links IDs from Android.
        val context =
            Sentry.continueTrace(sentryTraceHeader, listOfNotNull(baggageHeader))
                ?: TransactionContext("${call.request.httpMethod.value} ${call.request.uri}", "http.server")

        val isContinued = context.parentSpanId != null
        application.log.info("DEBUG [Sentry]: Trace State for ${call.request.uri}")
        application.log.info("  - Continued from Parent: $isContinued")
        application.log.info("  - Is Sampled: ${context.samplingDecision?.sampled}")
        application.log.info("  - Trace ID: ${context.traceId}")
        application.log.info("  - Parent Span ID: ${context.parentSpanId ?: "NONE (Root)"}")

        if (isContinued) {
            // If you want to see the specific 'baggage' metadata Sentry extracted
            application.log.info("  - Baggage: ${context.baggage?.toHeaderString(null)}")
        }

        // Manually honor the sampling decision from the parent if the SDK didn't pick it up.
        if (context.sampled == null) {
            application.log.warn("[Sentry] SDK did not set sampling decision. Manually checking baggage header.")
            // The baggage header from the client contains 'sentry-sampled=true'
            if (baggageHeader?.contains("sentry-sampled=true") == true) {
                application.log.info("[Sentry] Baggage indicates parent was sampled. Forcing sampling decision to 'true'.")
                context.sampled = true // This ensures the transaction is sent
            }
        }

        // Ensure the transaction name is human-readable in the UI
        context.name = "${call.request.httpMethod.value} ${call.request.uri}"
        context.operation = "http.server"

        // 2. Use forked scopes to keep this request's data isolated
        val requestScopes = Sentry.getCurrentScopes().forkedScopes("Ktor-Request")

        // Bind the transaction to the scope so the HttpClient can find it
        val options =
            TransactionOptions().apply {
                isBindToScope = true
            }

        // startTransaction now receives a non-null TransactionContext
        val transaction = requestScopes.startTransaction(context, options)

        // application.testSentryErrorEvent()

        withContext(SentryContext(requestScopes)) {
            try {
                proceed()
                transaction.status =
                    call.response.status()?.let { SpanStatus.fromHttpStatusCode(it.value) } ?: SpanStatus.OK
            } catch (e: Throwable) {
                application.log.error("TRACE: Error in interceptor: ${e.message}")
                Sentry.captureException(e)
                transaction.throwable = e
                transaction.status = SpanStatus.INTERNAL_ERROR
                throw e
            } finally {
                application.log.info("DEBUG [Sentry]: Finishing trace for ${call.request.uri}")
                Sentry.addBreadcrumb("Finishing transaction for ${call.request.uri}")
                transaction.finish()

                LoggerFactory.getLogger("io.sentry.transport").debug("--- MANUAL TRANSPORT LOG TEST ---")

                try {
                    // FORCE SEND: Wait up to MAXIMUM_SENTRY_FLUSH_TIMEOUT_MS for the background worker to flush the transaction
                    // This is vital for serverless environments.
                    Sentry.flush(MAXIMUM_SENTRY_FLUSH_TIMEOUT_MS)
                    application.log.info("DEBUG [Sentry]: Flushed for ${call.request.uri}")
                } catch (e: Exception) {
                    application.log.warn("Sentry: Flush was interrupted: ${e.message}")
                }
                application.log.info("DEBUG [Sentry]: Trace fully synchronized for ${call.request.uri}")
            }
        }
    }

    install(StatusPages) {
        // 1. Specific handler (Matches first if this exact exception occurs)
        exception<MissingConfigException> { call, cause ->
            Sentry.captureException(cause) // Report specific config errors to Sentry
            call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Configuration Error")
        }

        exception<Throwable> { call, cause ->
            Sentry.captureException(cause) // Report specific config errors to Sentry

            // 2. Respond to the client (optional: don't leak details in prod)
            call.respondText(
                text = "500: Internal Server Error",
                status = HttpStatusCode.InternalServerError,
            )
        }
    }
}

@Suppress("TooGenericExceptionCaught", "TooGenericExceptionThrown", "UnusedPrivateMember")
private fun Application.testSentryErrorEvent() {
    try {
        log.info("DEBUG [Sentry]: forcing test event")
        throw RuntimeException("--- SENTRY FORCED TEST EXCEPTION ---")
    } catch (e: Exception) {
        Sentry.captureException(e)
    }
}
