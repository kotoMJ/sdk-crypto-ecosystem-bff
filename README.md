# SDK Crypto Ecosystem BFF

Backend for Frontend (BFF) for the [SDK Crypto Ecosystem](https://github.com/kotoMJ/android-sdk-crypto-ecosystem) Android app.

A lightweight Ktor server running on Google Cloud Run that acts as a secure gatekeeper — it verifies the Android device's integrity via Google Play Integrity API before proxying requests to upstream services protected by secret API keys. This keeps all secrets on the server side, never exposed to the client.

## Endpoints

| Endpoint | Method | Description |
|---|---|---|
| `/` | GET | Health check — confirms the BFF is alive |
| `/api/news` | POST | Proxies [newsapi.org](https://newsapi.org/v2/) requests, guarded by Play Integrity verification (rate-limited) |
| `/sentry/android` | POST | Returns the Android Sentry DSN after Play Integrity verification (rate-limited) |

### Diagnostic endpoints (dev only, require `X-Kotox-Bypass-Key` header)

| Endpoint | Method | Description |
|---|---|---|
| `/test` | GET | Sends a test message to Sentry |
| `/network` | GET | Verifies DNS resolution to the Sentry ingest endpoint |
| `/sentry` | GET | Reports Sentry SDK status and DSN |

## Architecture

All protected endpoints require a valid Play Integrity token in the request body. An admin bypass header (`X-Kotox-Bypass-Key`) is available for testing.

The BFF integrates with Sentry for monitoring, including session stitching with the Android app — traces started on the mobile client are linked to BFF transactions via `sentry-trace` and `baggage` header propagation.

## Documentation

| Document | Description |
|---|---|
| [Development](docs/DEVELOPMENT.md) | Building, running, and Gradle tasks |
| [Sentry](docs/SENTRY.md) | Sentry integration architecture and tracing diagnostics |
| [Deployment](docs/DEPLOYMENT.md) | Google Cloud Run setup, secrets, and deploy commands |
| [Conventions](docs/CONVENTIONS.md) | Code style, linting, and git hooks |