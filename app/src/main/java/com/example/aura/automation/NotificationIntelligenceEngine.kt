package com.example.aura.automation

import android.content.Context
import android.content.Intent
import com.example.aura.calls.PhoneCallAndMessagingManager
import com.example.aura.security.PrivacyDataRouter
import com.example.aura.security.PromptInjectionDefender

enum class NotificationCategory(val label: String) {
    OTP_FINANCE("Finance & OTP"),
    MESSAGING("Messaging"),
    WORK("Work & DevOps"),
    SYSTEM("System & Battery"),
    PROMOTIONS("Promotions")
}

enum class NotificationPriority(val rank: Int, val label: String) {
    URGENT(4, "URGENT"),
    HIGH(3, "HIGH"),
    NORMAL(2, "NORMAL"),
    LOW(1, "LOW")
}

data class IntelligentNotificationItem(
    val source: CapturedNotification,
    val category: NotificationCategory,
    val priority: NotificationPriority,
    val sanitizedTitle: String,
    val sanitizedText: String,
    val summaryLine: String,
    val suggestedReplies: List<String>,
    val suggestedActionLabel: String,
    val injectionFlagged: Boolean
)

data class NotificationDigestSummary(
    val totalCount: Int,
    val urgentCount: Int,
    val messagingCount: Int,
    val otpFinanceCount: Int,
    val workCount: Int,
    val promoCount: Int,
    val digestParagraph: String,
    val prioritizedItems: List<IntelligentNotificationItem>
)

/**
 * Categorizes, scores urgency, sanitizes against prompt injection/secret leaks,
 * summarizes captured notifications, and executes user-approved quick replies.
 */
class NotificationIntelligenceEngine(
    private val context: Context,
    private val callMessaging: PhoneCallAndMessagingManager,
    private val privacyRouter: PrivacyDataRouter = PrivacyDataRouter(),
    private val injectionDefender: PromptInjectionDefender = PromptInjectionDefender()
) {

    fun analyzeAndPrioritize(notifications: List<CapturedNotification>): NotificationDigestSummary {
        val analyzed = notifications.map { analyzeSingle(it) }
            .sortedWith(compareByDescending<IntelligentNotificationItem> { it.priority.rank }.thenByDescending { it.source.postTime })

        val urgent = analyzed.count { it.priority == NotificationPriority.URGENT }
        val msgs = analyzed.count { it.category == NotificationCategory.MESSAGING }
        val fin = analyzed.count { it.category == NotificationCategory.OTP_FINANCE }
        val work = analyzed.count { it.category == NotificationCategory.WORK }
        val promo = analyzed.count { it.category == NotificationCategory.PROMOTIONS }

        val paragraph = if (analyzed.isEmpty()) {
            "Inbox clear. Enable Notification Listener access to let AURA categorize and summarize incoming alerts."
        } else {
            buildString {
                append("${analyzed.size} notifications analyzed: ")
                if (urgent > 0) append("$urgent urgent, ")
                if (msgs > 0) append("$msgs messages, ")
                if (fin > 0) append("$fin finance/OTP alerts, ")
                if (work > 0) append("$work work/dev alerts, ")
                append("$promo low-priority promos. ")
                analyzed.firstOrNull()?.let { top ->
                    append("Top priority: [${top.source.appLabel}] ${top.summaryLine}")
                }
            }
        }

        return NotificationDigestSummary(
            totalCount = analyzed.size,
            urgentCount = urgent,
            messagingCount = msgs,
            otpFinanceCount = fin,
            workCount = work,
            promoCount = promo,
            digestParagraph = paragraph,
            prioritizedItems = analyzed
        )
    }

    fun analyzeSingle(n: CapturedNotification): IntelligentNotificationItem {
        val pkg = n.packageName.lowercase()
        val combined = "${n.title} ${n.text}".lowercase()
        val injectionScan = injectionDefender.scanUntrustedContent("Notification:${n.appLabel}", n.text)
        val cleanTitle = privacyRouter.sanitizeForExternalProvider(n.title, redactEnabled = true).redactedText
        val cleanText = privacyRouter.sanitizeForExternalProvider(n.text, redactEnabled = true).redactedText

        val category = when {
            combined.contains("otp") || combined.contains("verification code") || combined.contains("bank") ||
                combined.contains("payment") || combined.contains("transaction") -> NotificationCategory.OTP_FINANCE
            pkg.contains("whatsapp") || pkg.contains("telegram") || pkg.contains("messaging") ||
                pkg.contains("sms") || pkg.contains("messenger") || pkg.contains("instagram") -> NotificationCategory.MESSAGING
            pkg.contains("github") || pkg.contains("slack") || pkg.contains("teams") || pkg.contains("gmail") ||
                pkg.contains("outlook") || combined.contains("meeting") || combined.contains("calendar") ||
                combined.contains("build failed") || combined.contains("pull request") -> NotificationCategory.WORK
            combined.contains("% off") || combined.contains("sale") || combined.contains("promo") ||
                combined.contains("discount") || combined.contains("offer") -> NotificationCategory.PROMOTIONS
            else -> NotificationCategory.SYSTEM
        }

        val priority = when {
            combined.contains("urgent") || combined.contains("emergency") || combined.contains("security alert") ||
                combined.contains("failed") || combined.contains("call me") || category == NotificationCategory.OTP_FINANCE ->
                NotificationPriority.URGENT
            category == NotificationCategory.MESSAGING || category == NotificationCategory.WORK ->
                NotificationPriority.HIGH
            category == NotificationCategory.PROMOTIONS ->
                NotificationPriority.LOW
            else -> NotificationPriority.NORMAL
        }

        val replies = when (category) {
            NotificationCategory.MESSAGING -> when {
                combined.contains("?") || combined.contains("kahan") || combined.contains("where") ->
                    listOf("I'll check and reply shortly.", "Busy right now — talk in 15 mins.", "Ji, main abhi reply karta hoon.")
                combined.contains("meet") || combined.contains("call") ->
                    listOf("Works for me!", "Can we connect in 30 minutes?", "Confirmed, thanks!")
                else ->
                    listOf("Got it, thanks!", "Will review soon.", "Okay, noted.")
            }
            NotificationCategory.WORK -> listOf("Acknowledged, reviewing now.", "On it — will update shortly.")
            else -> emptyList()
        }

        val actionLabel = when (category) {
            NotificationCategory.OTP_FINANCE -> "Keep Private (Redacted)"
            NotificationCategory.MESSAGING -> if (n.supportsDirectReply) "Send Approved Reply" else "Open Reply Draft"
            NotificationCategory.WORK -> "Create Follow-Up Task"
            NotificationCategory.PROMOTIONS -> "Dismiss Low Priority"
            NotificationCategory.SYSTEM -> "Inspect System Status"
        }

        val summary = "${cleanTitle.take(40)}: ${cleanText.take(90)}".trim(':', ' ')
        return IntelligentNotificationItem(
            source = n,
            category = category,
            priority = priority,
            sanitizedTitle = cleanTitle,
            sanitizedText = cleanText,
            summaryLine = summary,
            suggestedReplies = replies,
            suggestedActionLabel = actionLabel,
            injectionFlagged = injectionScan.isSuspicious
        )
    }

    fun executeApprovedQuickReply(item: IntelligentNotificationItem, replyText: String): Pair<Boolean, String> {
        val cleanReply = replyText.trim()
        if (cleanReply.isBlank()) return false to "Reply text cannot be blank."

        if (item.source.supportsDirectReply) {
            val direct = AuraNotificationListenerService.sendDirectReply(context, item.source.key, cleanReply)
            if (direct.first) {
                NotificationBridge.removeNotification(item.source.key)
                return direct
            }
        }
        // Fallback: open WhatsApp / Share draft with explicit confirmation
        return if (item.source.packageName.contains("whatsapp")) {
            callMessaging.openWhatsAppDraft("", cleanReply)
        } else {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, cleanReply)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching {
                context.startActivity(Intent.createChooser(shareIntent, "Send Approved Reply").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true to "Opened system share chooser for approved reply."
            }.getOrElse {
                false to "Could not launch reply chooser: ${it.localizedMessage}"
            }
        }
    }
}
