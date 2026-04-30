package com.billsummarizer

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.billsummarizer.data.repository.BillRepository
import com.billsummarizer.theme.MyApplicationTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "BillSummarizer"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

  @Inject lateinit var billRepository: BillRepository

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    setContent {
      MyApplicationTheme { Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainNavigation() } }
    }

    lifecycleScope.launch {
      billRepository.getBills()
        .onSuccess { bills ->
          Log.d(TAG, "Fetched ${bills.size} bills")
          bills.take(5).forEach { Log.d(TAG, " - ${it.type.uppercase()} ${it.number}: ${it.title.take(80)} (${it.outcome})") }
        }
        .onFailure { Log.e(TAG, "Failed to fetch bills", it) }
    }
  }
}
