package com.fixedsource.mobile.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fixedsource.mobile.R
import com.fixedsource.mobile.data.TaskSummary
import com.fixedsource.mobile.databinding.ItemTaskBinding
import com.fixedsource.mobile.util.UiText

class TaskAdapter(
    private val onClick: (TaskSummary) -> Unit
) : ListAdapter<TaskSummary, TaskAdapter.TaskViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TaskViewHolder(private val binding: ItemTaskBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(task: TaskSummary) = with(binding) {
            val context = root.context
            val status = UiText.statusColors(context, task.status)
            UiText.setPill(tvStatus, UiText.statusLabel(task.status), status)
            tvTaskNo.text = task.taskNo
            tvTitle.text = task.title ?: "现场核查任务"
            tvDue.text = "截止 ${UiText.formatDue(task.dueAt)}"
            tvEnterprise.text = task.enterpriseName ?: "未知企业"
            tvPoint.text = listOfNotNull(task.pointName, task.region)
                .joinToString(" · ")
                .ifBlank { task.region ?: "固定污染源" }

            val severityColor = UiText.severityColor(context, task.severity)
            severityStrip.backgroundTintList =
                android.content.res.ColorStateList.valueOf(severityColor)
            tvRule.text = task.ruleName ?: "规则线索"
            tvSeverity.text = UiText.severityLabel(task.severity)
            tvSeverity.setTextColor(severityColor)
            tvUrge.text = "督办 ${task.urgeCount} 次"
            UiText.show(tvUrge, task.urgeCount > 0)
            UiText.show(tvOverdue, task.slaStatus == "OVERDUE")

            val credibility = task.credibility
            UiText.show(progressCredibility, credibility != null)
            UiText.show(tvCredibility, credibility != null)
            if (credibility != null) {
                progressCredibility.setProgressCompat(credibility.toInt().coerceIn(0, 100), false)
                tvCredibility.text = credibility.toInt().toString()
                tvCredibility.setTextColor(
                    ContextCompat.getColor(
                        context,
                        if (credibility >= 85) R.color.brand_teal_dark else R.color.warning
                    )
                )
            }

            root.setOnClickListener { onClick(task) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<TaskSummary>() {
            override fun areItemsTheSame(oldItem: TaskSummary, newItem: TaskSummary): Boolean =
                oldItem.taskNo == newItem.taskNo

            override fun areContentsTheSame(oldItem: TaskSummary, newItem: TaskSummary): Boolean =
                oldItem == newItem
        }
    }
}
