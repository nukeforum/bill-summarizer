# HUD ZIP-CD crosswalk source

The `assets/zip_to_cd.json` asset is regenerated **automatically** by
`.github/workflows/update-zip-crosswalk.yml`, which runs quarterly and
fetches data from HUD's USPS Crosswalk API:

  https://www.huduser.gov/portal/dataset/uspszip-api.html

The workflow uses the `HUDUSER_API_KEY` repository secret. To trigger an
on-demand build, run the "Update ZIP-to-CD crosswalk" workflow from the
GitHub Actions UI; you can override the year and quarter via inputs.

## Manual fallback (CSV mode)

If the API is unavailable, you can still rebuild from a downloaded CSV:

1. Download the latest "ZIP-CD" crosswalk CSV from
   https://www.huduser.gov/portal/datasets/usps_crosswalk.html
2. Save as `data-pipeline/data/hud/zip_cd_crosswalk.csv`.
3. From repo root:

   ```
   python data-pipeline/scripts/build_zip_crosswalk.py csv \
     data-pipeline/data/hud/zip_cd_crosswalk.csv \
     android/app/src/main/assets/zip_to_cd.json
   ```

4. Commit the rebuilt `zip_to_cd.json`.
