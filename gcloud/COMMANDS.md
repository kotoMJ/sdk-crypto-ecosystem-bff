# Setup commands

## Create docker repository
One-Time Setup (Do this once per project)
Create the Docker Repository: This creates the "folder" in the cloud where your images live.
```
gcloud artifacts repositories create bff-repo \
--project=bff-sdk-crypto-ecosystem \
--repository-format=docker \
--location=us-central1 \
--description="Docker repository for Ktor BFF"
```

## Secret containers
Create Secret "Containers": Creates the empty safe boxes for your sensitive data.
```
gcloud secrets create news-api-key --replication-policy="automatic"
gcloud secrets create bff-crypto-admin-bypass-secret --replication-policy="automatic"
gcloud secrets create sentry-dns-crypto-tracker-bff --replication-policy="automatic"
gcloud secrets create sentry-dns-crypto-tracker-android --replication-policy="automatic"
```

## Grant permissions

### Allow Cloud Build to save images to Artifact Registry
```
gcloud projects add-iam-policy-binding bff-sdk-crypto-ecosystem \
--member="serviceAccount:XYZ-compute@developer.gserviceaccount.com" \
--role="roles/artifactregistry.writer"
```

### Allow Cloud Run to read Secrets
```
gcloud projects add-iam-policy-binding bff-sdk-crypto-ecosystem \
--member="serviceAccount:XYZ-compute@developer.gserviceaccount.com" \
--role="roles/secretmanager.secretAccessor"
```

# Service commands

## Build the docker image
(Remote Build) Zips your code, sends it to Google, builds the Docker image, and saves it.
```
gcloud builds submit --tag us-central1-docker.pkg.dev/bff-sdk-crypto-ecosystem/bff-repo/bff-service:v1
```
## Update Secrets
(Only if keys change) Updates the content inside the secret "safe".

```
printf "YOUR_REAL_API_KEY_VALUE" | gcloud secrets versions add news-api-key --data-file=- 
printf "YOUR_REAL_ADMIN_SECRET_VALUE" | gcloud secrets versions add bff-crypto-admin-bypass-secret --data-file=- 
printf "YOUR_REAL_SENTRY_DNS_CRYPTO_TRACKER_BFF_VALUE" | gcloud secrets versions add sentry-dns-crypto-tracker-bff --data-file=-
printf "YOUR_REAL_SENTRY_DNS_CRYPTO_TRACKER_ANDROID_VALUE" | gcloud secrets versions add sentry-dns-crypto-tracker-android --data-file=-

```

## Deploy to Cloud Run
Starts the server using the docker image and the secrets.

```
gcloud run deploy bff-service \
--image=us-central1-docker.pkg.dev/bff-sdk-crypto-ecosystem/bff-repo/bff-service:v1 \
--region=us-central1 \
--platform=managed \
--allow-unauthenticated \
--port=8080 \
--set-secrets="CRYPTO_SDK_NEWS_API_KEY=news-api-key:latest,BFF_CRYPTO_ADMIN_BYPASS_SECRET=bff-crypto-admin-bypass-secret:latest,SENTRY_DNS_CRYPTO_TRACKER_BFF_VALUE=sentry-dns-crypto-tracker-bff:latest,SENTRY_DNS_CRYPTO_TRACKER_ANDROID_VALUE=sentry-dns-crypto-tracker-android:latest"
```

## Pause(Stop) the service
```
gcloud run services remove-iam-policy-binding bff-service \
--region=us-central1 \
--member="allUsers" \
--role="roles/run.invoker"
```

## Resume(Start) the service
```
gcloud run services add-iam-policy-binding bff-service \
--region=us-central1 \
--member="allUsers" \
--role="roles/run.invoker"
```