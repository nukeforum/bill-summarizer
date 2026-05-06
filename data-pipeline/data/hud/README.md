# HUD ZIP-CD crosswalk source

Drop the latest HUD ZIP-CD crosswalk CSV here as `zip_cd_crosswalk.csv`.

Source: <https://www.huduser.gov/portal/datasets/usps_crosswalk.html>

## Refresh procedure

1. Download the latest "ZIP-CD" crosswalk CSV from HUD.
2. Save it as `data-pipeline/data/hud/zip_cd_crosswalk.csv`.
3. From the repo root, run:

   ```
   python data-pipeline/scripts/build_zip_crosswalk.py \
     data-pipeline/data/hud/zip_cd_crosswalk.csv \
     android/app/src/main/assets/zip_to_cd.json
   ```

4. Commit both the source CSV (or its replacement) and the rebuilt
   `zip_to_cd.json`.

The crosswalk is regenerated **manually** on HUD release (annual-ish;
redistricting cycles drive most churn).
