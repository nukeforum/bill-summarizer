# Getting your own API keys

The app works out of the box with data the project publishes for
everyone — you never *need* a key. With your own (free) keys, the app
or the CLI can fetch data directly from the source instead, on your
schedule.

| Key | Used for | Where to get it | Rate limit |
|-----|----------|-----------------|------------|
| Congress.gov API key | Bills, representatives | [api.congress.gov/sign-up](https://api.congress.gov/sign-up/) | 5,000 requests/hour |
| HUD USER token (CLI only) | ZIP→congressional-district crosswalk | [huduser.gov USPS crosswalk API](https://www.huduser.gov/portal/dataset/uspszip-api.html) | Much lower — fetch quarterly |

The session calendar comes from public house.gov / senate.gov feeds
and needs no key at all.

## Congress.gov key, step by step

1. Open <https://api.congress.gov/sign-up/>.
2. Fill in your name and email address (no account or payment needed)
   and submit.
3. Watch for the activation email from `api.data.gov` — the key is in
   the email body, a long string like `aBcDeF...`. Some providers file
   it under spam.
4. The key is active immediately. You can sanity-check it in a
   browser:

   ```
   https://api.congress.gov/v3/bill?limit=1&api_key=YOUR_KEY
   ```

   A JSON document means the key works; an HTTP 403 means it was
   mistyped or not yet activated.

### Entering it in the app

Settings → **Data sources** → paste the key into the *Congress.gov API
key* field → **Verify and save**. The app makes one test request and
stores the key encrypted on the device. From then on:

- bills refresh daily, representatives and the session calendar weekly
  (background, network-connected only), and
- **Fetch now** refreshes everything immediately.

Note the cost of a full refresh: roughly three requests per bill
(detail, summaries, and text) plus one per member (~550). At 5,000
requests/hour you can comfortably refresh several times a day; the
historical bill archive is deliberately not fetched on-device.

### BYOK bill-coverage bound

The published record is broadening ~100× — beyond the bills that reached
a floor outcome to every bill introduced, in committee, or reported
(~10,000+ per Congress). Enriching that whole set on-device would need
~30,000 requests, far past one key's 5,000/hour budget. So direct fetch
with your own key is **bounded**:

- Your key fetches bills **active in the last 60 days** directly — the
  freshness-critical slice, capped per run (≈1,660 bills, three requests
  each, leaving headroom under 5,000/hour) and resumed across daily runs
  if a window is unusually busy.
- Breadth beyond that window comes from the **project-published data**
  (sharded per-Congress manifests) over the app's normal read path — no
  key needed, so your record stays complete.

The Data sources screen states this bound so you always know what your
key is fetching directly versus what comes from the published data.

## HUD USER token (CLI users only)

The app ships the ZIP→district table as a bundled asset rebuilt
quarterly, so the app never asks for this token. It only matters if
you run the pipeline CLI yourself.

1. Open <https://www.huduser.gov/portal/dataset/uspszip-api.html>.
2. Create a HUD USER account (email + password), then under *Create
   New Token* generate an access token.
3. The token is shown once in the account portal — copy it somewhere
   safe.

HUD's rate limits are tight; the pipeline fetches one request per
state (56 total) with a polite delay, and only quarterly.

## CLI environment variables

For running the pipeline CLI locally (or in your own CI fork):

```bash
export CONGRESS_API_KEY="your congress.gov key"
export HUDUSER_API_KEY="your HUD USER token"

cd pipeline
./gradlew :cli:run --args="fetch-bills"
./gradlew :cli:run --args="fetch-members --phase1-only"
./gradlew :cli:run --args="build-session-calendar"      # no key needed
./gradlew :cli:run --args="build-zip-crosswalk --sleep 0.1"
./gradlew :cli:run --args="check-freshness"             # no key needed
```
