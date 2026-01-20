package cz.kotox.crypto.sdk

// Aliasing the plugin specifically for the Ktor 'install' block
import cz.kotox.crypto.sdk.exception.MissingConfigException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.sentry.Sentry
import io.sentry.SpanStatus
import io.sentry.TransactionContext

/**
 * Ktor Server doesn't have an "automatic" server-side Sentry plugin like some other frameworks,
 * so we use an interceptor to extract the headers and start the transaction.
 */

@Suppress("TooGenericExceptionCaught")
fun Application.configureSentryTracing() {
    intercept(ApplicationCallPipeline.Monitoring) {
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
        }

        // Ensure the context has the correct metadata for this specific request
        context.name = "${call.request.httpMethod.value} ${call.request.uri}"
        context.operation = "http.server"

        // startTransaction now receives a non-null TransactionContext
        val transaction = Sentry.startTransaction(context)

        try {
            proceed()
            transaction.status = SpanStatus.OK
        } catch (e: Throwable) {
            transaction.throwable = e
            transaction.status = SpanStatus.INTERNAL_ERROR
            throw e
        } finally {
            transaction.finish()
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
