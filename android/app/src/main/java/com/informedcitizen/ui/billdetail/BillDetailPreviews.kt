package com.informedcitizen.ui.billdetail

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.informedcitizen.ui.preview.MaterialPreviewTheme
import com.informedcitizen.ui.preview.sampleBill

@Preview(showBackground = true)
@Composable
private fun PreviewBillDetailLoading() = PreviewWrap {
    BillDetailContent(
        state = BillDetailUiState.Loading,
        innerPadding = PaddingValues(0.dp),
    )
}

@PreviewLightDark
@Composable
private fun PreviewBillDetailSuccess() = PreviewWrap {
    BillDetailContent(
        state = BillDetailUiState.Success(bill = sampleBill()),
        innerPadding = PaddingValues(0.dp),
    )
}

@PreviewLightDark
@Composable
private fun PreviewBillDetailSuccessNoSummary() = PreviewWrap {
    BillDetailContent(
        state = BillDetailUiState.Success(
            bill = sampleBill(summaryCrs = null),
        ),
        innerPadding = PaddingValues(0.dp),
    )
}

@PreviewLightDark
@Composable
private fun PreviewBillDetailError() = PreviewWrap {
    BillDetailContent(
        state = BillDetailUiState.Error(message = "Bill not found in cache."),
        innerPadding = PaddingValues(0.dp),
    )
}

@Composable
private fun PreviewWrap(content: @Composable () -> Unit) {
    MaterialPreviewTheme {
        Surface(modifier = Modifier.fillMaxSize()) { content() }
    }
}
