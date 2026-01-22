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

/**
 * Ktor Server doesn't have an "automatic" server-side Sentry plugin like some other frameworks,
 * so we use an interceptor to extract the headers and start the transaction.
 */

@Suppress("TooGenericExceptionCaught", "LongMethod", "MaxLineLength")
fun Application.configureSentryTracing() {
    intercept(ApplicationCallPipeline.Monitoring) {
        application.log.info("TRACE: Interceptor triggered for ${call.request.uri}")
        val sentryTraceHeader = call.request.header("sentry-trace")
        // Ktor's getHeaders provides an enumeration; we need a List for Sentry
        val baggageHeaders = call.request.headers.getAll("baggage")

        // 1. Attempt to continue the trace
        var context = Sentry.continueTrace(sentryTraceHeader, baggageHeaders)

        if (context == null) {
            // 2. Add a breadcrumb to see why the trace failed in the next Sentry event
            Sentry.addBreadcrumb(
                "Tracing context was null. " +
                    "sentry-trace header present: ${sentryTraceHeader != null}, " +
                    "baggage headers count: ${baggageHeaders?.size}",
            )

            // 3. Optional: Capture a message if you want to track this as a searchable event
            // Sentry.captureMessage("BFF Tracing context missing for: ${call.request.uri}", SentryLevel.DEBUG)

            // 4. Fallback to a new root transaction
            context =
                TransactionContext(
                    "${call.request.httpMethod.value} ${call.request.uri}",
                    "http.server",
                )
        } else {
            // Update the name to be more descriptive than just the trace ID
            context.name = "${call.request.httpMethod.value} ${call.request.uri}"
            context.operation = "http.server"
        }

        // Bind the transaction to the scope so the HttpClient can find it
        val options =
            TransactionOptions().apply {
                isBindToScope = true
            }

        // startTransaction now receives a non-null TransactionContext
        val transaction = Sentry.startTransaction(context, options)

        // This ensures Sentry.getSpan() knows which transaction is active.
        Sentry.configureScope { it.transaction = transaction }

        try {
            // This makes the transaction "Active" for Sentry.getSpan() and children
            withContext(SentryContext()) {
                // DEBUG SECTION: Verify if Sentry actually "sees" the transaction in this scope
                val activeSpan = Sentry.getSpan()
                if (activeSpan != null) {
                    val traceId = activeSpan.spanContext.traceId
                    val spanId = activeSpan.spanContext.spanId

                    application.log.info("DEBUG [Sentry]: Active Trace ID: $traceId | Span ID: $spanId for ${call.request.uri}")
                } else {
                    application.log.warn("DEBUG [Sentry]: NO ACTIVE SPAN FOUND for ${call.request.uri}. Stitching will fail!")
                }

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
            // Optional: Clear the transaction from the scope after finishing
            Sentry.configureScope { it.transaction = null }
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
