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

fun initSentry() {
    Sentry.init { options ->
        options.dsn = "https://182bf04220e37772a37194d801c0624a@o4510727626883072.ingest.de.sentry.io/4510727742554192"
//            requireNotNull(System.getenv("SENTRY_DNS_CRYPTO_TRACKER_BFF")) {
//                "SENTRY_DNS_CRYPTO_TRACKER_BFF env is missing"
//            }

        options.tracesSampleRate = 1.0 // Adjust these for production
        // options.isEnableUncaughtExceptionHandler = true
        options.isDebug = true
        // This allows Sentry to capture headers like sentry-trace, baggage, and others
        options.isSendDefaultPii = true
    }
}

/**
 * Ktor Server doesn't have an "automatic" server-side Sentry plugin like some other frameworks,
 * so we use an interceptor to extract the headers and start the transaction.
 */

@Suppress("TooGenericExceptionCaught", "LongMethod", "MaxLineLength", "MagicNumber")
fun Application.configureSentryTracing() {
    intercept(ApplicationCallPipeline.Monitoring) {
        application.log.info("TRACE: Interceptor triggered for ${call.request.uri}")

        val sentryTraceHeader = call.request.header("sentry-trace")
        application.log.warn("DEBUG [Sentry]: sentryTraceHeader: $sentryTraceHeader")

        // Ktor's getHeaders provides an enumeration; we need a List for Sentry
        // baggageHeaders: [sentry-environment=production,sentry-public_key=0e3c6ffd2caeeb9b3894e9f7a23eb08c,sentry-release=cz.kotox.sdk.crypto.app%400.0.1%2B1,sentry-replay_id=72c8ac2197624cec95916af98c09ca2f,sentry-sample_rand=0.941817019361639,sentry-trace_id=5cd9a67eef5e497a87c39aab7fb693b9]
        // val baggageHeaders = call.request.headers.getAll("baggage")
        val baggageHeader = call.request.header("baggage")
        application.log.warn("DEBUG [Sentry]: baggageHeader: $baggageHeader")

        // 1. Continue the trace or start a new one
        // Sentry.continueTrace handles the parsing of IDs for you.
        // If it returns null, it's often due to a malformed header or sampling.
        val contextContinue: TransactionContext? = Sentry.continueTrace(sentryTraceHeader, listOfNotNull(baggageHeader))

        val context =
            if (contextContinue == null) {
                application.log.warn("DEBUG [Sentry]: contextContinue is null!")
                val parts = sentryTraceHeader?.split("-")
                application.log.info("DEBUG [Sentry]: parts:$parts")
                if (parts != null && parts.size >= 2) {
                    val traceId = io.sentry.protocol.SentryId(parts[0])
                    application.log.info("DEBUG [Sentry]: traceId:$traceId")
                    val parentSpanId = io.sentry.SpanId(parts[1])
                    application.log.info("DEBUG [Sentry]: parentSpanId:$parentSpanId")
                    val sampled = if (parts.size > 2) parts[2] == "1" else null
                    application.log.info("DEBUG [Sentry]: sampled:$sampled")

                    // NEW: Manually parse baggage to ensure stitching  and use it as the 'glue' for stitching
                    val baggage = io.sentry.Baggage.fromHeader(baggageHeader)
                    application.log.info("DEBUG [Sentry]: baggage:$baggage")
                    TransactionContext(
                        traceId,
                        io.sentry.SpanId(),
                        parentSpanId,
                        io.sentry.TracesSamplingDecision(sampled ?: true),
                        baggage,
                    ).apply {
                        name = "${call.request.httpMethod.value} ${call.request.uri}"
                        operation = "http.server"
                    }
                } else {
                    application.log.error("DEBUG [Sentry]: parts not available!")
                    TransactionContext(
                        "${call.request.httpMethod.value} ${call.request.uri}",
                        "http.server",
                    )
                }
            } else {
                contextContinue
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

        try {
            // This makes the transaction "Active" for Sentry.getSpan() and children
            withContext(SentryContext(requestScopes)) {
                // Now, Sentry.getSpan() (static) should return the transaction
                proceed()
            }
            transaction.status = SpanStatus.OK
        } catch (e: Throwable) {
            application.log.error("TRACE: Error in interceptor: ${e.message}")
            transaction.throwable = e
            transaction.status = SpanStatus.INTERNAL_ERROR
            throw e
        } finally {
            application.log.info("DEBUG [Sentry]: Finishing trace for ${call.request.uri}")
            transaction.finish()

            try {
                // FORCE SEND: Wait up to 2 seconds for the background worker to flush the transaction
                // This is vital for serverless environments.
                Sentry.flush(2000)
                application.log.info("DEBUG [Sentry]: Flushed for ${call.request.uri}")
                // Optional: Clear the transaction from the scope after finishing
                Sentry.configureScope { it.transaction = null }
            } catch (e: Exception) {
                application.log.warn("Sentry: Flush was interrupted: ${e.message}")
            }
            application.log.info("DEBUG [Sentry]: Trace fully synchronized for ${call.request.uri}")
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
// fun Application.configureSentryTracing() {
//    install(StatusPages) {
//        exception<Throwable> { call, cause ->
//            // 1. Capture the exception in Sentry
//            Sentry.captureException(cause)
//
//            // 2. Respond to the client (optional: don't leak details in prod)
//            call.respondText(
//                text = "500: Internal Server Error",
//                status = HttpStatusCode.InternalServerError
//            )
//        }
//    }
// }
