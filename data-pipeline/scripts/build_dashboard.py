"""Render a static HTML dashboard summarizing data-pipeline health.

Reads the published JSON artifacts in ``docs/data/`` plus the backfill state
file and emits ``docs/pipeline.html``. The page is served from the project's
GitHub Pages site at
``https://nukeforum.github.io/bill-summarizer/pipeline.html``.

Status chips share thresholds with ``check_freshness`` so the dashboard and
the scheduled alarm always agree on what "stale" means.

The page is self-contained: one HTML file, embedded CSS, no JS, no external
resources. Rendering is pure-Python; rebuild is a sub-second step pinned to
the end of every data-pipeline workflow.
"""

from __future__ import annotations

import html
import sys
from dataclasses import dataclass
from datetime import date, datetime, timezone
from typing import Any

from _common import (
    OLDEST_API_CONGRESS,
    OUTPUT_DIR,
    STATE_DIR,
    current_congress,
    manifest_path_for,
    members_index_path,
)
from check_freshness import (
    BACKFILL_MAX_AGE_DAYS,
    BILLS_MAX_AGE_DAYS,
    CALENDAR_MIN_LOOKAHEAD_DAYS,
    MEMBERS_MAX_AGE_DAYS,
    _load_json,
    _parse_iso_utc,
)

# Quarterly cadence (HUD publishes 4× per year); flag if older than this.
ZIP_MAX_AGE_DAYS = 120


@dataclass
class Row:
    name: str
    status: str  # "ok" | "fail"
    summary: str
    detail: str = ""


def _age_days(ts: datetime, now: datetime) -> float:
    return (now - ts).total_seconds() / 86400.0


def _fmt_age(ts: datetime | None, now: datetime) -> str:
    if ts is None:
        return "unknown"
    age = _age_days(ts, now)
    if age < 0:
        return "in the future"
    if age < 1:
        return f"{int(age * 24)}h ago"
    if age < 14:
        return f"{int(age)}d ago"
    if age < 90:
        return f"{int(age / 7)}w ago"
    return f"{int(age / 30)}mo ago"


def _bills_row(now: datetime) -> Row:
    congress = current_congress(now)
    path = manifest_path_for(congress)
    body = _load_json(path)
    label = f"Bills · Congress {congress}"
    if body is None:
        return Row(label, "fail", "missing", f"{path.name} not present")
    ts = _parse_iso_utc(body.get("generated_at"))
    count = len(body.get("bills") or [])
    if ts is None:
        return Row(label, "fail", "no timestamp", f"{count:,} bills on disk")
    age = _age_days(ts, now)
    status = "ok" if age < BILLS_MAX_AGE_DAYS else "fail"
    return Row(label, status, f"{count:,} bills", f"updated {_fmt_age(ts, now)}")


def _members_row(now: datetime) -> Row:
    congress = current_congress(now)
    path = members_index_path(congress)
    body = _load_json(path)
    label = f"Members · {congress}"
    if body is None:
        return Row(label, "fail", "missing", f"{path.name} not present")
    ts = _parse_iso_utc(body.get("generated_at"))
    count = len(body.get("members") or [])
    if ts is None:
        return Row(label, "fail", "no timestamp", f"{count} members")
    age = _age_days(ts, now)
    status = "ok" if age < MEMBERS_MAX_AGE_DAYS else "fail"
    return Row(label, status, f"{count} members", f"updated {_fmt_age(ts, now)}")


def _calendar_rows(now: datetime) -> list[Row]:
    body = _load_json(OUTPUT_DIR / "session_calendar.json")
    if body is None:
        return [Row("Session calendar", "fail", "missing", "session_calendar.json not present")]
    today = now.date()
    chambers = body.get("chambers") or {}
    rows: list[Row] = []
    for chamber in ("house", "senate"):
        label = f"Calendar · {chamber}"
        days = (chambers.get(chamber) or {}).get("session_days") or []
        future = [d for d in days if isinstance(d, str) and d >= today.isoformat()]
        if not future:
            rows.append(Row(label, "fail", "no future days", ""))
            continue
        try:
            last = date.fromisoformat(max(future))
        except ValueError:
            rows.append(Row(label, "fail", "malformed date", ""))
            continue
        lookahead = (last - today).days
        status = "ok" if lookahead >= CALENDAR_MIN_LOOKAHEAD_DAYS else "fail"
        rows.append(Row(label, status, f"{lookahead}d lookahead", f"last known {last.isoformat()}"))
    return rows


def _backfill_row(now: datetime) -> Row:
    state = _load_json(STATE_DIR / "backfill_state.json")
    label = "Backfill cursor"
    if state is None:
        return Row(label, "fail", "missing", "backfill_state.json not present")
    active = state.get("active_congress")
    completed = state.get("completed") or []
    if active is None:
        return Row(label, "ok", "complete", f"all {len(completed)} Congresses done")
    ts = _parse_iso_utc(state.get("last_run_at"))
    if ts is None:
        return Row(label, "fail", "no timestamp", f"active {active}")
    age = _age_days(ts, now)
    status = "ok" if age < BACKFILL_MAX_AGE_DAYS else "fail"
    offset = state.get("active_offset", 0)
    return Row(
        label,
        status,
        f"on Congress {active}",
        f"offset {offset:,} · last ran {_fmt_age(ts, now)}",
    )


def _zip_row(now: datetime) -> Row:
    zip_path = OUTPUT_DIR.parent.parent / "android" / "app" / "src" / "main" / "assets" / "zip_to_cd.json"
    label = "ZIP → district crosswalk"
    if not zip_path.is_file():
        return Row(label, "fail", "missing", "quarterly workflow has not published yet")
    mtime = datetime.fromtimestamp(zip_path.stat().st_mtime, tz=timezone.utc)
    age = _age_days(mtime, now)
    status = "ok" if age < ZIP_MAX_AGE_DAYS else "fail"
    size_kb = zip_path.stat().st_size // 1024
    return Row(label, status, _fmt_age(mtime, now), f"{size_kb} KB on disk")


def _backfill_progress(now: datetime) -> tuple[int, int, str]:
    """Return ``(completed_count, total_count, human_label)`` for the progress bar."""
    state = _load_json(STATE_DIR / "backfill_state.json")
    cur = current_congress(now)
    total = cur - OLDEST_API_CONGRESS + 1
    if state is None:
        return 0, total, "no state file"
    completed = state.get("completed") or []
    if state.get("active_congress") is None:
        return len(completed), total, f"all {len(completed)} Congresses complete"
    active = state.get("active_congress")
    offset = state.get("active_offset", 0)
    return (
        len(completed),
        total,
        f"{len(completed)} of {total} Congresses complete · currently filling {active} at offset {offset:,}",
    )


def _per_congress_table() -> list[dict[str, Any]]:
    index = _load_json(OUTPUT_DIR / "congresses.json")
    if index is None:
        return []
    return list(index.get("congresses") or [])


def render(now: datetime | None = None) -> str:
    now = now or datetime.now(timezone.utc)
    rows: list[Row] = [
        _bills_row(now),
        _members_row(now),
        *_calendar_rows(now),
        _backfill_row(now),
        _zip_row(now),
    ]
    fail_count = sum(1 for r in rows if r.status == "fail")
    overall_status = "ok" if fail_count == 0 else "fail"
    overall_summary = (
        "All artifacts fresh"
        if fail_count == 0
        else f"{fail_count} issue{'s' if fail_count != 1 else ''} — see rows flagged ✗"
    )

    completed_n, total_n, backfill_label = _backfill_progress(now)
    per_congress = _per_congress_table()

    h = html.escape

    artifact_rows = []
    for r in rows:
        chip = "✓" if r.status == "ok" else "✗"
        artifact_rows.append(
            f'<tr class="row-{h(r.status)}">'
            f'<td class="chip">{chip}</td>'
            f'<td class="name">{h(r.name)}</td>'
            f'<td class="summary">{h(r.summary)}</td>'
            f'<td class="detail">{h(r.detail)}</td>'
            f"</tr>"
        )

    congress_rows = []
    for c in per_congress:
        congress = c.get("congress", "?")
        bill_count = c.get("bill_count", 0)
        first = c.get("first_action_date") or "—"
        last = c.get("last_action_date") or "—"
        marks = []
        if c.get("is_current"):
            marks.append("current")
        if c.get("backfill_complete"):
            marks.append("backfilled")
        marks_label = f" · {' · '.join(marks)}" if marks else ""
        congress_rows.append(
            f"<tr>"
            f"<td>{h(str(congress))}{h(marks_label)}</td>"
            f"<td>{bill_count:,}</td>"
            f"<td>{h(str(first))} → {h(str(last))}</td>"
            f"</tr>"
        )

    return _PAGE_TEMPLATE.format(
        generated_at=h(now.replace(microsecond=0).isoformat().replace("+00:00", "Z")),
        overall_status=h(overall_status),
        overall_summary=h(overall_summary),
        artifact_rows="\n".join(artifact_rows),
        backfill_label=h(backfill_label),
        backfill_value=completed_n,
        backfill_max=max(total_n, 1),
        per_congress_rows="\n".join(congress_rows) or '<tr><td colspan="3">no manifests yet</td></tr>',
    )


_PAGE_TEMPLATE = """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <title>Bill Summarizer · pipeline health</title>
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <style>
    :root {{
      color-scheme: light dark;
      --fg: #1a1a1a;
      --fg-muted: #5a5a5a;
      --bg: #f8f8f8;
      --bg-card: #ffffff;
      --border: #e3e3e3;
      --ok: #1b7a3e;
      --fail: #b3261e;
      --ok-bg: rgba(27, 122, 62, 0.10);
      --fail-bg: rgba(179, 38, 30, 0.10);
    }}
    @media (prefers-color-scheme: dark) {{
      :root {{
        --fg: #ececec;
        --fg-muted: #9a9a9a;
        --bg: #141414;
        --bg-card: #1d1d1d;
        --border: #2e2e2e;
        --ok: #6dd292;
        --fail: #ff7a70;
        --ok-bg: rgba(109, 210, 146, 0.10);
        --fail-bg: rgba(255, 122, 112, 0.12);
      }}
    }}
    * {{ box-sizing: border-box; }}
    body {{
      margin: 0;
      padding: 2rem 1rem 3rem;
      background: var(--bg);
      color: var(--fg);
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      line-height: 1.5;
    }}
    main {{ max-width: 780px; margin: 0 auto; }}
    h1 {{ margin: 0 0 0.25rem 0; font-size: 1.4rem; }}
    h2 {{
      margin: 2rem 0 0.6rem 0;
      font-size: 0.78rem;
      text-transform: uppercase;
      letter-spacing: 0.06em;
      color: var(--fg-muted);
    }}
    .meta {{ color: var(--fg-muted); font-size: 0.85rem; margin: 0 0 1rem 0; }}
    .overall {{
      display: inline-block;
      padding: 0.45rem 0.85rem;
      border-radius: 0.5rem;
      font-weight: 600;
      font-size: 0.95rem;
    }}
    .overall-ok {{ background: var(--ok-bg); color: var(--ok); }}
    .overall-fail {{ background: var(--fail-bg); color: var(--fail); }}
    table {{
      width: 100%;
      border-collapse: collapse;
      background: var(--bg-card);
      border-radius: 0.5rem;
      overflow: hidden;
      border: 1px solid var(--border);
    }}
    th, td {{
      text-align: left;
      padding: 0.6rem 0.8rem;
      border-top: 1px solid var(--border);
      font-size: 0.9rem;
      vertical-align: top;
    }}
    th {{
      background: var(--bg);
      border-top: none;
      font-weight: 600;
      color: var(--fg-muted);
      font-size: 0.74rem;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }}
    tbody tr:first-child td {{ border-top: 1px solid var(--border); }}
    .chip {{
      width: 1.6rem;
      text-align: center;
      font-weight: 700;
      font-size: 1rem;
    }}
    .row-ok .chip {{ color: var(--ok); }}
    .row-fail .chip {{ color: var(--fail); }}
    .row-fail .name {{ color: var(--fail); }}
    .name {{ font-weight: 500; }}
    .detail {{ color: var(--fg-muted); font-size: 0.85rem; }}
    progress {{
      width: 100%;
      height: 0.7rem;
      accent-color: var(--ok);
    }}
    footer {{
      margin-top: 2.5rem;
      color: var(--fg-muted);
      font-size: 0.8rem;
      border-top: 1px solid var(--border);
      padding-top: 1rem;
    }}
    a {{ color: inherit; }}
    a:hover {{ color: var(--fg); }}
  </style>
</head>
<body>
  <main>
    <h1>Bill Summarizer · pipeline health</h1>
    <p class="meta">
      Generated <time datetime="{generated_at}">{generated_at}</time>
      · data feed at <a href="data/congresses.json">docs/data/</a>
    </p>
    <p class="overall overall-{overall_status}">{overall_summary}</p>

    <h2>Artifacts</h2>
    <table>
      <thead>
        <tr><th></th><th>Artifact</th><th>Status</th><th>Detail</th></tr>
      </thead>
      <tbody>
{artifact_rows}
      </tbody>
    </table>

    <h2>Historical backfill</h2>
    <p style="margin:0 0 0.4rem 0; color: var(--fg-muted); font-size: 0.9rem;">{backfill_label}</p>
    <progress value="{backfill_value}" max="{backfill_max}"></progress>

    <h2>Per-Congress bill counts</h2>
    <table>
      <thead>
        <tr><th>Congress</th><th>Bills</th><th>Action-date range</th></tr>
      </thead>
      <tbody>
{per_congress_rows}
      </tbody>
    </table>

    <footer>
      <p>Page rebuilds at the end of every data-pipeline workflow run. Status
      chips share thresholds with the weekly freshness check, which emails on
      alarm. Source: <a href="https://github.com/nukeforum/bill-summarizer">nukeforum/bill-summarizer</a>.</p>
    </footer>
  </main>
</body>
</html>
"""


def main() -> int:
    output = OUTPUT_DIR.parent / "pipeline.html"
    output.write_text(render(), encoding="utf-8")
    print(f"Wrote {output}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
