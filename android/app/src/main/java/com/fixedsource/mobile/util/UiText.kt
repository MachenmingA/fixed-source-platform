package com.fixedsource.mobile.util

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.fixedsource.mobile.R
import com.fixedsource.mobile.data.Citation
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object UiText {

    data class PillColors(val fg: Int, val bg: Int)

    fun statusColors(context: Context, status: String?): PillColors = when (status) {
        "DISPATCHED" -> PillColors(
            ContextCompat.getColor(context, R.color.warning),
            ContextCompat.getColor(context, R.color.warning_soft)
        )
        "IN_PROGRESS" -> PillColors(
            ContextCompat.getColor(context, R.color.brand_teal_dark),
            ContextCompat.getColor(context, R.color.teal_soft)
        )
        "FEEDBACK" -> PillColors(
            ContextCompat.getColor(context, R.color.info),
            ContextCompat.getColor(context, R.color.info_soft)
        )
        else -> PillColors(
            ContextCompat.getColor(context, R.color.text_muted),
            ContextCompat.getColor(context, R.color.surface)
        )
    }

    fun statusLabel(status: String?): String = when (status) {
        "DISPATCHED" -> "待接收"
        "IN_PROGRESS" -> "核查中"
        "FEEDBACK" -> "待确认"
        "CLOSED" -> "已关闭"
        else -> status ?: "未知"
    }

    fun severityColor(context: Context, severity: String?): Int = when (severity) {
        "HIGH" -> ContextCompat.getColor(context, R.color.danger)
        "MEDIUM" -> ContextCompat.getColor(context, R.color.warning)
        "LOW" -> ContextCompat.getColor(context, R.color.brand_teal_dark)
        else -> ContextCompat.getColor(context, R.color.text_muted)
    }

    fun severityLabel(severity: String?): String = when (severity) {
        "HIGH" -> "高风险"
        "MEDIUM" -> "中风险"
        "LOW" -> "低风险"
        else -> "风险待定"
    }

    fun resultLabel(result: String?): String = when (result) {
        "ISSUE" -> "发现问题"
        "NORMAL" -> "未发现问题"
        else -> result ?: "—"
    }

    fun formatNumber(value: Double?): String {
        if (value == null) return "—"
        return if (value == value.toLong().toDouble()) value.toLong().toString()
        else String.format(Locale.CHINA, "%.2f", value)
    }

    fun formatDue(dueAt: String?): String {
        if (dueAt.isNullOrBlank()) return "未设置截止时间"
        val parts = dueAt.split(" ")
        return if (parts.size == 2) "${parts[0].takeLast(5)} ${parts[1]}" else dueAt
    }

    fun formatCacheTime(timestamp: Long): String {
        if (timestamp <= 0) return "未知"
        return SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(timestamp))
    }

    fun greeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour < 6 -> "夜深了"
            hour < 12 -> "上午好"
            hour < 14 -> "中午好"
            hour < 18 -> "下午好"
            else -> "晚上好"
        }
    }

    fun todayLabel(): String {
        val now = Calendar.getInstance()
        val week = when (now.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "星期一"
            Calendar.TUESDAY -> "星期二"
            Calendar.WEDNESDAY -> "星期三"
            Calendar.THURSDAY -> "星期四"
            Calendar.FRIDAY -> "星期五"
            Calendar.SATURDAY -> "星期六"
            else -> "星期日"
        }
        return "${now.get(Calendar.MONTH) + 1}月${now.get(Calendar.DAY_OF_MONTH)}日 $week · 任务实时同步"
    }

    fun addEvidenceLine(parent: LinearLayout, text: String) {
        val context = parent.context
        val view = TextView(context).apply {
            this.text = "·  $text"
            textSize = 10f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setLineSpacing(0f, 1.25f)
            setPadding(0, dp(context, 5), 0, 0)
        }
        parent.addView(view)
    }

    fun addSuggestion(parent: LinearLayout, text: String) {
        val context = parent.context
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(0, dp(context, 6), 0, 0)
        }
        val marker = TextView(context).apply {
            this.text = "›"
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, R.color.brand_teal_dark))
            setPadding(0, 0, dp(context, 5), 0)
        }
        val label = TextView(context).apply {
            this.text = text
            textSize = 10f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setLineSpacing(0f, 1.3f)
        }
        row.addView(marker)
        row.addView(
            label,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        parent.addView(row)
    }

    fun addCitation(parent: LinearLayout, citation: Citation) {
        val context = parent.context
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card_inner)
            setPadding(dp(context, 10), dp(context, 9), dp(context, 10), dp(context, 9))
        }
        val head = TextView(context).apply {
            text = listOfNotNull(citation.law, citation.clause).joinToString(" ")
            textSize = 10f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setLineSpacing(0f, 1.2f)
        }
        val meta = TextView(context).apply {
            text = listOfNotNull(citation.title, citation.effectiveDate?.let { "生效 $it" })
                .joinToString(" · ")
            textSize = 8f
            setTextColor(ContextCompat.getColor(context, R.color.text_muted))
            setPadding(0, dp(context, 3), 0, 0)
        }
        val quote = TextView(context).apply {
            text = citation.quote.orEmpty()
            textSize = 9f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setLineSpacing(0f, 1.35f)
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(context, 5), 0, 0)
        }
        card.addView(head)
        card.addView(meta)
        if (!citation.quote.isNullOrBlank()) card.addView(quote)
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.topMargin = dp(context, 6)
        parent.addView(card, params)
    }

    fun setPill(view: TextView, text: String, colors: PillColors) {
        view.text = text
        view.setTextColor(colors.fg)
        view.backgroundTintList = android.content.res.ColorStateList.valueOf(colors.bg)
    }

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    fun show(view: View, visible: Boolean) {
        view.visibility = if (visible) View.VISIBLE else View.GONE
    }
}
