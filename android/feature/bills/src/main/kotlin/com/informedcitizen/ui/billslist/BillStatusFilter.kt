package com.informedcitizen.ui.billslist

import com.informedcitizen.pipeline.lifecycleStatusToWireString
import com.informedcitizen.pipeline.model.LifecycleStatus

/**
 * The bills-list lifecycle-status filter (backlog #42, epic #38).
 *
 * The #39 pipeline classifies every bill that never reached a floor vote with a
 * pre-floor lifecycle status ([LifecycleStatus]) — introduced, in committee,
 * reported — so committee burials and stalled bills stop being silently dropped
 * from the published record. This filter lets a citizen slice the broadened
 * record by that status.
 *
 * It is a **SQL-bound** filter: [sqlStatus] is passed to
 * [com.informedcitizen.data.repository.BillRepository.pagedBills] where it
 * becomes the `status = ?` predicate on the DB-paged read (the
 * `cached_bill.status` column, iter 40). It therefore composes with the
 * policy-area SQL filter and the in-memory outcome / free-text / AI-topic
 * filters under AND semantics, consistent with the existing filters.
 *
 * **Axis note:** the terminal *outcomes* (Passed / Failed / Enacted / Vetoed)
 * are a separate axis carried by `Bill.outcome` and filtered by the existing
 * [BillsListFilter] chips. A floor-outcome bill has a **null** lifecycle status
 * (the pipeline's `classify_bill_status` lets a real outcome win over a
 * lifecycle status), so the two filters address disjoint bills and never
 * overlap.
 *
 * **Default view ([ALL]):** applies no status predicate. Until #39's
 * publish-filter flip ships the broadened set, live data carries no pre-floor
 * bills (every published bill has a null lifecycle status), so [ALL] shows
 * exactly today's floor-action list — no behavior change for a user who never
 * touches the filter. A dedicated *floor-action-only* default (which needs a
 * `status IS NULL` query variant the current `(:status IS NULL OR status = :status)`
 * predicate can't express) only becomes meaningful once pre-floor bills are
 * actually published, and is deferred to that slice.
 */
enum class BillStatusFilter(val displayName: String, val sqlStatus: String?) {
    ALL("All statuses", null),
    INTRODUCED("Introduced", lifecycleStatusToWireString(LifecycleStatus.INTRODUCED)),
    IN_COMMITTEE("In committee", lifecycleStatusToWireString(LifecycleStatus.IN_COMMITTEE)),
    REPORTED("Reported", lifecycleStatusToWireString(LifecycleStatus.REPORTED)),
}
