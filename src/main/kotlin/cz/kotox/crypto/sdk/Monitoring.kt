package cz.kotox.crypto.sdk

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
import io.sentry.TransactionOptions
import io.sentry.kotlin.SentryContext
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

internal const val MAXIMUM_SENTRY_FLUSH_TIMEOUT_MS = 5000L

// Toggle tracing diagnostics via logback: set "SentryTracing" logger to TRACE level.
private val sentryLog = LoggerFactory.getLogger("SentryTracing")

fun initSentry() {
    Sentry.init { options ->
        val dsnBff =
            requireNotNull(System.getenv("SENTRY_DNS_CRYPTO_TRACKER_BFF_VALUE")) {
                "SENTRY_DNS_CRYPTO_TRACKER_BFF env is missing"
            }
        options.dsn = dsnBff
        options.tracesSampleRate = 1.0 // Adjust these for production
        options.isDebug = false
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
        val sentryTraceHeader = call.request.header("sentry-trace")
        val baggageHeader = call.request.header("baggage")

        sentryLog.trace("Interceptor triggered for {}", call.request.uri)
        sentryLog.trace("sentryTraceHeader: {}", sentryTraceHeader)
        sentryLog.trace("baggageHeader: {}", baggageHeader)

        // Let Sentry do the heavy lifting. This automatically links IDs from Android.
        val context =
            Sentry.continueTrace(sentryTraceHeader, listOfNotNull(baggageHeader))
                ?: TransactionContext("${call.request.httpMethod.value} ${call.request.uri}", "http.server")

        val isContinued = context.parentSpanId != null
        sentryLog.trace(
            "Trace State for {} — continued={}, sampled={}, traceId={}, parentSpanId={}",
            call.request.uri,
            isContinued,
            context.samplingDecision?.sampled,
            context.traceId,
            context.parentSpanId ?: "NONE (Root)",
        )
        if (isContinued) {
            sentryLog.trace("Baggage: {}", context.baggage?.toHeaderString(null))
        }

        // Manually honor the sampling decision from the parent if the SDK didn't pick it up.
        if (context.sampled == null) {
            sentryLog.trace("SDK did not set sampling decision. Checking baggage header.")
            val baggage = io.sentry.Baggage.fromHeader(baggageHeader)

            when (baggage?.sampled) {
                "true" -> {
                    sentryLog.trace("Baggage indicates parent was sampled. Forcing sampled=true.")
                    context.sampled = true
                }
                "false" -> {
                    sentryLog.trace("Baggage indicates parent was NOT sampled. Forcing sampled=false.")
                    context.sampled = false
                }
                else -> {
                    sentryLog.trace("Baggage sampled status is unknown/null. Deferring to SDK tracesSampleRate.")
                }
            }
        }

        context.name = "${call.request.httpMethod.value} ${call.request.uri}"
        context.operation = "http.server"

        val requestScopes = Sentry.getCurrentScopes().forkedScopes("Ktor-Request")

        val options =
            TransactionOptions().apply {
                isBindToScope = true
            }

        val transaction = requestScopes.startTransaction(context, options)

        withContext(SentryContext(requestScopes)) {
            try {
                proceed()
                transaction.status =
                    call.response.status()?.let { SpanStatus.fromHttpStatusCode(it.value) } ?: SpanStatus.OK
            } catch (e: Throwable) {
                Sentry.captureException(e)
                transaction.throwable = e
                transaction.status = SpanStatus.INTERNAL_ERROR
                throw e
            } finally {
                transaction.finish()
            }
        }
    }

    install(StatusPages) {
        exception<MissingConfigException> { call, cause ->
            Sentry.captureException(cause)
            call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Configuration Error")
        }

        exception<Throwable> { call, cause ->
            Sentry.captureException(cause)
            call.respondText(
                text = "500: Internal Server Error",
                status = HttpStatusCode.InternalServerError,
            )
        }
    }
}
