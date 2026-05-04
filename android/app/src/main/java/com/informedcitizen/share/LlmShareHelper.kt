package com.informedcitizen.share

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import com.informedcitizen.data.model.Bill
import com.informedcitizen.ui.util.displayName
import com.informedcitizen.ui.util.formatBillRef
import com.informedcitizen.ui.util.openInCustomTab

object LlmShareHelper {

    private const val BODY_CAP_CHARS = 50_000

    fun buildPrompt(bill: Bill, body: String?): String {
        val cleanBody = body?.takeIf { it.isNotBlank() }
            ?.let { capBody(it, bill.congressGovUrl) }
            ?: "(No bill text included; see Congress.gov: ${bill.congressGovUrl})"

        return buildString {
            appendLine(
                "You are explaining a U.S. Congressional bill to a curious American who reads news " +
                    "but isn't a lawyer. Write in plain English, neutral tone, no editorializing. " +
                    "Use markdown headings and bullet points. If the text below doesn't contain " +
                    "information a section asks for, write \"not specified in this excerpt\" rather " +
                    "than inferring."
            )
            appendLine()
            appendLine("## What the bill would do")
            appendLine(
                "Two or three sentences. Name the specific mechanism — what it creates, repeals, " +
                    "amends, appropriates, or requires. Include dollar amounts and dates when " +
                    "central. Don't restate the title."
            )
            appendLine()
            appendLine("## Who is affected")
            appendLine(
                "Name specific stakeholder groups: industries, federal/state agencies, demographic " +
                    "groups, geographic regions. If the bill sets eligibility thresholds (income " +
                    "cutoffs, employer size, age bands), state the numbers. Avoid generalities " +
                    "like \"all Americans.\""
            )
            appendLine()
            appendLine("## Key provisions")
            appendLine(
                "Three to five bullets. Each bullet must contain, where present in the text: an " +
                    "action verb (creates / repeals / increases / requires / authorizes), the " +
                    "specific dollar amount or quantitative threshold, any deadline, and the " +
                    "implementing agency."
            )
            appendLine()
            appendLine("## Notable or contested elements")
            appendLine(
                "Provisions that drew floor debate, were amended late, or are likely to be " +
                    "litigated. Stick to factual contention; don't characterize partisan framing. " +
                    "If none are evident from the text, say so."
            )
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("Bill: ${formatBillRef(bill.type, bill.number)} — ${bill.title}")
            bill.shortTitle?.takeIf { it.isNotBlank() }?.let { appendLine("Also known as: $it") }
            appendLine("Status: ${bill.outcome.displayName()} on ${bill.latestAction.date}")
            appendLine("Latest action: ${bill.latestAction.text}")
            appendLine("Introduced: ${bill.introducedDate}")
            appendLine("Sponsor: ${bill.sponsor.name} (${bill.sponsor.party}-${bill.sponsor.state})")
            appendLine()
            append(cleanBody)
        }
    }

    fun shareTo(context: Context, target: LlmTarget, payload: String) {
        if (isPackageInstalled(context, target.packageName) && tryDirectShare(context, target.packageName, payload)) {
            return
        }
        copyToClipboard(context, payload)
        Toast.makeText(
            context,
            "Prompt copied — opening ${target.displayName} in browser",
            Toast.LENGTH_SHORT,
        ).show()
        openInCustomTab(context, target.webUrl)
    }

    fun shareToOther(context: Context, payload: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, payload)
        }
        val chooser = Intent.createChooser(send, "Share to…").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun tryDirectShare(context: Context, packageName: String, payload: String): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage(packageName)
            putExtra(Intent.EXTRA_TEXT, payload)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Bill summary prompt", text))
    }

    private fun capBody(body: String, congressGovUrl: String): String =
        if (body.length <= BODY_CAP_CHARS) {
            body
        } else {
            body.substring(0, BODY_CAP_CHARS) +
                "\n\n[Bill text truncated. Full text: $congressGovUrl]"
        }
}
