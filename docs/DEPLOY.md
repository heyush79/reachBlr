# Deploying

The app is two parts:

- **Static files:** the React build, about 2 MB.
- **One container:** the Spring Boot API, using about 200–300 MB of memory, with no database.

They can be hosted separately: the frontend calls the API across sites, using `VITE_API_BASE` on the frontend and
`ALLOWED_ORIGINS` on the API.

## Recommended free setup

| Part | Host | Why |
|---|---|---|
| Frontend | **Cloudflare Pages** | Free plan, builds straight from GitHub, fast in India. |
| Backend | **Google Cloud Run**, region `asia-south1` (Mumbai) | Its free monthly allowance covers a small site. You need a Google Cloud billing account for the Places API anyway, so it's one account for both. Scales to zero when idle. |

The cost of scaling to zero: the first visitor after a quiet spell waits a few seconds while the API starts. Spring Boot
starts in about 1 s here; allow 2–5 s on Cloud Run.

**Without a card:** Render's free web service also fits (512 MB). It sleeps after 15 idle minutes, and the first visit
after that waits about a minute.

## Free tiers, as checked on 26 November 2026

These change, so check the linked pages before you rely on them.

- **Google Cloud Run** ([free tier page](https://cloud.google.com/free/docs/free-cloud-features)): with request-based
  billing, per month,
  - 2 million requests,
  - 180,000 vCPU-seconds,
  - 360,000 GB-seconds of memory,
  - 1 GB of data out from North America.

  It needs a billing account. The page lists no region limit for Cloud Run. (The [Cloud Run pricing
  page](https://cloud.google.com/run/pricing) could not be read automatically; check it once in a browser.)
- **Cloudflare Pages** ([limits](https://developers.cloudflare.com/pages/platform/limits/)): free plan, 500 builds a
  month, one build at a time, up to 20,000 files, 100 custom domains per project. The limits page gives no bandwidth
  cap for static files.
- **Render** ([free plan](https://render.com/docs/free)): 0.1 CPU and 512 MB, 750 free instance hours a month. It sleeps
  after 15 minutes without traffic and takes about a minute to wake.
- **Fly.io** ([pricing](https://fly.io/docs/about/pricing/)): $5 of usage a month once a card is added (US$1 credit
  without one). A 512 MB machine is about US$3.69 a month.
- **Koyeb**: its [pricing page](https://www.koyeb.com/pricing) no longer lists a free compute instance.
- **Oracle Cloud Always Free**: its page refused automated reading, so this could not be checked.

## Backend on Cloud Run

You need the [gcloud CLI](https://cloud.google.com/sdk/docs/install) and Docker. Replace `PROJECT_ID`, and do this
once:

```bash
gcloud auth login
gcloud config set project PROJECT_ID
gcloud services enable run.googleapis.com artifactregistry.googleapis.com secretmanager.googleapis.com places.googleapis.com
gcloud artifacts repositories create commute-map --repository-format=docker --location=asia-south1
gcloud auth configure-docker asia-south1-docker.pkg.dev
```

Build and push the image from the repository root. On an Apple Silicon Mac, `--platform linux/amd64` is needed, because
Cloud Run runs x86 machines.

```bash
docker build --platform linux/amd64 -f backend/Dockerfile \
  -t asia-south1-docker.pkg.dev/PROJECT_ID/commute-map/api:v1 .
docker push asia-south1-docker.pkg.dev/PROJECT_ID/commute-map/api:v1
```

Keep the Google key in Secret Manager, not in a plain environment variable. Let the service read it, then deploy:

```bash
printf '%s' "YOUR_PLACES_KEY" | gcloud secrets create places-api-key --data-file=-
gcloud secrets add-iam-policy-binding places-api-key \
  --member="serviceAccount:$(gcloud projects describe PROJECT_ID --format='value(projectNumber)')-compute@developer.gserviceaccount.com" \
  --role=roles/secretmanager.secretAccessor

gcloud run deploy commute-map-api \
  --image asia-south1-docker.pkg.dev/PROJECT_ID/commute-map/api:v1 \
  --region asia-south1 --allow-unauthenticated \
  --memory 512Mi --cpu 1 --max-instances 2 \
  --set-env-vars ALLOWED_ORIGINS=https://YOUR-SITE.pages.dev \
  --set-secrets GOOGLE_PLACES_API_KEY=places-api-key:latest
```

`--max-instances 2` caps how far it can scale, and with it the cost. Cloud Run sets `PORT`, which the app reads.

Check it:

```bash
curl https://commute-map-api-....run.app/actuator/health
```

## Frontend on Cloudflare Pages

1. Cloudflare dashboard → Workers & Pages → Create → Pages → connect the GitHub repository.
2. Build settings:
   - root directory `frontend`
   - build command `npm run build`
   - output directory `dist`
3. Environment variables:
   - `NODE_VERSION=22`
   - `VITE_API_BASE=https://commute-map-api-....run.app`, the Cloud Run address, with no trailing slash
4. Deploy, then put the Pages address (for example `https://commute-map.pages.dev`) into the API's `ALLOWED_ORIGINS` and
   redeploy the API.

After deploying, open the site and check the browser console:

- There should be no "Worker failed to load" error. MapLibre's worker is a `.mjs` file and must be served as
  JavaScript; the nginx setup here needed a fix for exactly that.
- The ward count for ITPL with default settings should be 22.

## Keeping the Google bill safe

The API already limits lookups to 10 per visitor per minute and 500 per day for the whole site (see `.env.example`).
Add Google's own guards too:

1. **Restrict the key** to "Places API (New)" (APIs & Services → Credentials → the key → API restrictions).
2. **Set a daily quota** on Places API (New) (APIs & Services → Places API (New) → Quotas), for example 500 requests a
   day.
3. **Add a budget alert** (Billing → Budgets & alerts), for example at US$5.

At the time of writing, each lookup is billed as *Nearby Search Pro*: 5,000 free a month, then US$32 per 1,000.
