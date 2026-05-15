package com.informedcitizen.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.informedcitizen.pipeline.model.Outcome
import com.informedcitizen.ui.preview.PreviewWrap
import com.informedcitizen.ui.preview.sampleBill
import com.informedcitizen.ui.preview.sampleSponsor

@PreviewLightDark
@Composable
private fun PreviewBillCardStandard() = PreviewWrap(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
    BillCard(bill = sampleBill(), onClick = {})
}

@PreviewLightDark
@Composable
private fun PreviewBillCardLongTitle() = PreviewWrap(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
    BillCard(
        bill = sampleBill(
            shortTitle = null,
            title = "A bill to amend title 38, United States Code, to expand eligibility for " +
                "veterans' housing assistance, modernize Department of Veterans Affairs claim " +
                "intake systems, and authorize community-care reimbursement at parity with " +
                "in-network rates.",
        ),
        onClick = {},
    )
}

@PreviewLightDark
@Composable
private fun PreviewBillCardOutcomes() = PreviewWrap(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BillCard(
            bill = sampleBill(outcome = Outcome.PASSED_HOUSE),
            onClick = {},
        )
        BillCard(
            bill = sampleBill(
                outcome = Outcome.ENACTED,
                sponsor = sampleSponsor(party = "R", state = "TX"),
            ),
            onClick = {},
        )
        BillCard(
            bill = sampleBill(outcome = Outcome.VETOED),
            onClick = {},
        )
        BillCard(
            bill = sampleBill(
                outcome = Outcome.FAILED,
                sponsor = sampleSponsor(party = "I", state = "VT"),
            ),
            onClick = {},
        )
    }
}
