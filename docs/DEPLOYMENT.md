# Deployment (Google Cloud Run)

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

## Cleanup policy for old images

### Why you are still paying for old images
- **Tags vs. Digests:** When you push an image with the same tag (e.g., `:v1`), Google Cloud doesn't delete the old file. Instead, it "moves" the `:v1` label to the new file.
- **The "Orphan" Problem:** The old image still exists in your storage, but it no longer has a tag. It becomes a "tag-less" or "untagged" image (identified only by its SHA digest).
- **Accumulation:** Every time you run that `gcloud builds submit` command, you are adding roughly 100MB-500MB (depending on your Ktor app size) to your storage. Even if you use the same tag name, the old bytes remain on Google's disks, and they charge you for them.

### Set automated cleanup (one-time setup)
Keeps only the 3 most recent images and deletes everything older (tagged or not). This works with the timestamp tagging strategy — each build gets a unique tag, and the policy ensures only the 3 newest survive for rollback.

The `cleanup-policy.json` in the project root should contain:
```json
[
  {
    "name": "keep-recent-versions",
    "action": {
      "type": "Keep"
    },
    "mostRecentVersions": {
      "keepCount": 3
    }
  }
]
```

Apply (or re-apply after changes) with:
```
gcloud artifacts repositories set-cleanup-policies bff-repo --project=bff-sdk-crypto-ecosystem --location=us-central1 --policy=cleanup-policy.json
```

### Verify the cleanup
Google doesn't always delete the files instantly (it usually runs the cleanup task within 24 hours). To see what is currently eating your budget, list all images and check how many "hidden" versions you have:
```
gcloud artifacts docker images list us-central1-docker.pkg.dev/bff-sdk-crypto-ecosystem/bff-repo/bff-service --include-tags
```
If you see a lot of entries with no tags (just a long SHA string), those are the "orphans" costing you money.

### Manual cleanup (if needed)
Note: After running delete commands, the listing API has **eventual consistency** — deleted images may still appear for a few minutes up to an hour. The storage charges stop regardless.

List untagged images:
```
gcloud artifacts docker images list us-central1-docker.pkg.dev/bff-sdk-crypto-ecosystem/bff-repo/bff-service --include-tags --filter="tags=''"
```
Delete all untagged images:
```
gcloud artifacts docker images list us-central1-docker.pkg.dev/bff-sdk-crypto-ecosystem/bff-repo/bff-service --include-tags --filter="tags=''" --format="value(version)" | xargs -I {} gcloud artifacts docker images delete us-central1-docker.pkg.dev/bff-sdk-crypto-ecosystem/bff-repo/bff-service@{} --quiet
```

# Service commands

## Build the docker image
(Remote Build) Zips your code, sends it to Google, builds the Docker image, and saves it.
Uses a timestamp tag (e.g., `v20260414-1530`) so each build is uniquely identifiable and you can roll back to a previous version. The cleanup policy automatically keeps only the 3 most recent images.
```
gcloud builds submit --tag us-central1-docker.pkg.dev/bff-sdk-crypto-ecosystem/bff-repo/bff-service:v$(date +%Y%m%d-%H%M)
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
Replace `TAG` with the tag from your latest build (e.g., `v20260414-1530`):
```
gcloud run deploy bff-service --image=us-central1-docker.pkg.dev/bff-sdk-crypto-ecosystem/bff-repo/bff-service:TAG --region=us-central1 --platform=managed --allow-unauthenticated --port=8080 --set-secrets="CRYPTO_SDK_NEWS_API_KEY=news-api-key:latest,BFF_CRYPTO_ADMIN_BYPASS_SECRET=bff-crypto-admin-bypass-secret:latest,SENTRY_DNS_CRYPTO_TRACKER_BFF_VALUE=sentry-dns-crypto-tracker-bff:latest,SENTRY_DNS_CRYPTO_TRACKER_ANDROID_VALUE=sentry-dns-crypto-tracker-android:latest"
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