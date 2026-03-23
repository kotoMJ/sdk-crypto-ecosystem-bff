# Sentry Integration

## Why custom tracing (`Monitoring.kt`) instead of `sentry-ktor-server`

Sentry provides an official `sentry-ktor-server` plugin that auto-instruments Ktor routes. We intentionally chose a custom interceptor approach instead for the following reasons:

1. **Session stitching with the Android app** — The mobile client sends `sentry-trace` and `baggage` headers to the BFF. We need to call `Sentry.continueTrace()` manually to link the BFF transaction to the Android trace. The official plugin doesn't expose enough control over how incoming trace context is parsed and continued.

2. **Sampling decision fallback** — In some cases the Sentry SDK doesn't automatically honor the parent's sampling decision from the baggage header. Our interceptor includes a manual fallback that parses the baggage and forces `context.sampled` accordingly. This was critical for ensuring transactions are actually sent when the Android client marks them as sampled.

3. **Forked scopes per request** — We use `Sentry.getCurrentScopes().forkedScopes("Ktor-Request")` to isolate each request's Sentry data, and bind the transaction to the scope so the outgoing `HttpClient` (with `SentryKtorClientPlugin`) can automatically attach trace headers to downstream calls.

4. **Shutdown flush instead of per-request flush** — We flush Sentry events once at shutdown via `ApplicationStopped` hook rather than blocking on every request. This is important for Cloud Run latency while still ensuring no data is lost when the instance is terminated.

## Components

| File | Role |
|---|---|
| `Monitoring.kt` | Sentry init + custom server-side tracing interceptor + StatusPages error capture |
| `Http.kt` | Outgoing `HttpClient` with `SentryKtorClientPlugin` for downstream trace propagation |
| `Configuration.kt` | `CallLogging` format that logs `sentry-trace`/`baggage` headers for diagnostics |
| `Application.kt` | Shutdown hook that flushes and closes Sentry |
| `logback.xml` | `SentryTracing` logger toggle (set to `TRACE` for verbose diagnostics) |