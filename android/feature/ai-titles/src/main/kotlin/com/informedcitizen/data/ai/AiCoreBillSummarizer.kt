package com.informedcitizen.data.ai

import android.content.Context
import android.os.Build
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.generationConfig
import com.informedcitizen.crash.CrashReporter
import com.informedcitizen.pipeline.model.Bill
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiCoreBillSummarizer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val crashReporter: CrashReporter,
) : BillSummarizer {

    private val model: GenerativeModel by lazy {
        GenerativeModel(
            generationConfig = generationConfig {
                this.context = this@AiCoreBillSummarizer.context
                temperature = 0.2f
                topK = 40
                maxOutputTokens = 96
            },
        )
    }

    override suspend fun summarize(bill: Bill): BillSummarizer.Result {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return BillSummarizer.Result.Failure(ERR_UNSUPPORTED)
        }
        val prompt = buildPrompt(bill)
        val raw: String? = withTimeoutOrNull(TIMEOUT_MS) {
            runCatching { model.generateContent(prompt).text }.getOrNull()
        }

        if (raw == null) {
            return logged(BillSummarizer.Result.Failure(ERR_TIMEOUT), bill)
        }
        if (raw.isBlank()) {
            return logged(BillSummarizer.Result.Failure(ERR_EMPTY), bill)
        }

        return when (val parsed = BillSummaryParser.parse(raw)) {
            is BillSummaryParser.Result.Success -> BillSummarizer.Result.Success(parsed.summary)
            BillSummaryParser.Result.ParseFailed -> logged(BillSummarizer.Result.Failure(ERR_PARSE), bill)
        }
    }

    private fun logged(failure: BillSummarizer.Result.Failure, bill: Bill): BillSummarizer.Result {
        crashReporter.recordNonFatal(
            AiCoreBillSummarizationException("bill=${bill.id} kind=${failure.errorKind}"),
        )
        return failure
    }

    private fun buildPrompt(bill: Bill): String {
        val seedTitle = bill.shortTitle ?: bill.title
        return PROMPT_TEMPLATE
            .replace("{title}", seedTitle)
            .replace("{latest_action}", bill.latestAction.text)
    }

    companion object {
        const val MODEL_VERSION = "gemini-nano-1.0"
        const val PROMPT_VERSION = 1
        private const val TIMEOUT_MS = 20_000L
        private const val ERR_TIMEOUT = "TIMEOUT"
        private const val ERR_EMPTY = "EMPTY_OUTPUT"
        private const val ERR_PARSE = "PARSE_FAILED"
        private const val ERR_UNSUPPORTED = "UNSUPPORTED_API_LEVEL"

        private val PROMPT_TEMPLATE = """
            You are summarizing a US Congressional bill for a curious American who reads news but isn't a lawyer. Output ONLY a JSON object with two fields and nothing else:
              concise_title: a plain-English title, at most 8 words, no acronyms, neutral tone, no hype.
              topic: exactly one of: Tech, Healthcare, TaxAndBudget, Defense, ForeignAffairs, EnergyAndEnvironment, Education, JusticeAndCrime, Immigration, LaborAndWorkforce, Agriculture, Transportation, Housing, Veterans, Trade, BankingAndFinance, CivilRights, Elections, ScienceAndSpace, GovernmentOperations, Other.

            Bill: {title}
            Latest action: {latest_action}
        """.trimIndent()
    }
}

class AiCoreBillSummarizationException(message: String) : RuntimeException(message)
