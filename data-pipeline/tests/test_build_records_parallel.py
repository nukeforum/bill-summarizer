"""Tests for ``build_bill_records_parallel`` — the per-bill enrichment
thread-pool wrapper used by ``fetch_bills`` and ``backfill_bills``."""
from __future__ import annotations

import threading
import time
from concurrent.futures import ThreadPoolExecutor

from _common import ErrorCollector, build_bill_records_parallel


class _RecordingClient:
    """Stand-in for ``CongressClient`` that returns canned bill detail
    responses and tracks how many threads ever entered ``get`` at once."""

    def __init__(
        self,
        simulate_failures_for: set[str] | None = None,
        per_call_delay: float = 0.0,
    ) -> None:
        self._fail = simulate_failures_for or set()
        self._delay = per_call_delay
        self._in_flight = 0
        self._max_in_flight = 0
        self._lock = threading.Lock()
        self.calls: int = 0

    def get(self, path, **params):
        with self._lock:
            self.calls += 1
            self._in_flight += 1
            self._max_in_flight = max(self._max_in_flight, self._in_flight)
        if self._delay:
            time.sleep(self._delay)
        try:
            # Identify a failing bill by checking if its number appears in
            # the path. The summary endpoint is hit first so this matches.
            for ref in self._fail:
                if f"/{ref}" in path:
                    raise RuntimeError(f"forced failure on {ref}")
            # Branch on endpoint shape.
            if path.endswith("/summaries"):
                return {"summaries": []}
            if path.endswith("/text"):
                return {"textVersions": []}
            # Detail endpoint.
            return {"bill": {
                "title": "Stub title",
                "introducedDate": "2025-01-01",
                "sponsors": [{"fullName": "Sen. Stub, S. [D-XX]", "party": "D", "state": "XX"}],
                "titles": [],
            }}
        finally:
            with self._lock:
                self._in_flight -= 1

    @property
    def max_in_flight(self) -> int:
        return self._max_in_flight


def _summary(bill_type: str, number: int) -> dict:
    return {
        "type": bill_type,
        "number": str(number),
        "title": "",
        "latestAction": {"text": "Became Public Law", "actionDate": "2025-06-01"},
    }


def test_records_built_in_parallel_when_multiple_bills():
    # Small per-call delay ensures workers overlap; without it, each
    # request returns so fast the pool serializes by accident.
    client = _RecordingClient(per_call_delay=0.02)
    errors = ErrorCollector()
    items = [(_summary("hr", n), "enacted") for n in range(1, 11)]

    records, failures = build_bill_records_parallel(
        client, 119, items, errors, max_workers=4
    )

    assert failures == 0
    assert len(records) == 10
    # 10 bills × 3 endpoints = 30 calls regardless of parallelism.
    assert client.calls == 30
    # With 4 workers and 10 bills we should observe > 1 in flight at some
    # point. Smoke-check parallelism actually engaged.
    assert client.max_in_flight > 1


def test_empty_input_returns_empty_without_pool_use():
    client = _RecordingClient()
    errors = ErrorCollector()
    records, failures = build_bill_records_parallel(client, 119, [], errors)
    assert records == []
    assert failures == 0
    assert client.calls == 0


def test_per_bill_failures_recorded_into_collector_not_raised():
    client = _RecordingClient(simulate_failures_for={"7"})
    errors = ErrorCollector()
    items = [(_summary("hr", n), "enacted") for n in range(1, 11)]

    records, failures = build_bill_records_parallel(
        client, 119, items, errors, max_workers=4
    )

    assert failures == 1
    assert len(records) == 9
    assert len(errors) == 1
    rec = errors.records()[0]
    assert rec.kind == "build_bill_record"
    assert rec.identifier == "hr7"
    assert rec.error_class == "RuntimeError"


def test_error_collector_record_is_thread_safe():
    """Stress-test concurrent ``record`` calls — would intermittently lose
    entries on CPython if the underlying list mutation weren't locked."""
    ec = ErrorCollector()
    total = 500
    workers = 16

    def push(i: int) -> None:
        ec.record("kind", f"id-{i}", RuntimeError(f"boom-{i}"))

    with ThreadPoolExecutor(max_workers=workers) as pool:
        list(pool.map(push, range(total)))

    assert len(ec) == total
    assert {r.identifier for r in ec.records()} == {f"id-{i}" for i in range(total)}
