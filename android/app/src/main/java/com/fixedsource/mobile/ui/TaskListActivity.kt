package com.fixedsource.mobile.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.fixedsource.mobile.R
import com.fixedsource.mobile.data.Api
import com.fixedsource.mobile.data.ApiException
import com.fixedsource.mobile.data.CacheStore
import com.fixedsource.mobile.data.SessionExpiredException
import com.fixedsource.mobile.data.SessionStore
import com.fixedsource.mobile.data.TaskSummary
import com.fixedsource.mobile.databinding.ActivityTaskListBinding
import com.fixedsource.mobile.util.UiText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TaskListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTaskListBinding
    private lateinit var adapter: TaskAdapter
    private var allTasks: List<TaskSummary> = emptyList()
    private var selectedStatus: String? = null
    private var requesting = false
    private var lastError: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTaskListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyUser()
        setupList()
        CacheStore.loadTasks()?.let {
            allTasks = it
            render()
        }
        if (CacheStore.tasksCachedAt() > 0) {
            binding.tvSyncState.text = "缓存 ${UiText.formatCacheTime(CacheStore.tasksCachedAt())}"
        }

        binding.btnRefresh.setOnClickListener {
            it.animate().rotationBy(360f).setDuration(450).start()
            loadTasks()
        }
        binding.btnLogout.setOnClickListener { confirmLogout() }
    }

    override fun onResume() {
        super.onResume()
        if (!SessionStore.isLoggedIn) {
            goLogin()
            return
        }
        applyUser()
        loadTasks()
    }

    private fun setupList() {
        adapter = TaskAdapter { task ->
            startActivity(
                Intent(this, TaskDetailActivity::class.java)
                    .putExtra(TaskDetailActivity.EXTRA_TASK_NO, task.taskNo)
            )
        }
        binding.recyclerTasks.layoutManager = LinearLayoutManager(this)
        binding.recyclerTasks.adapter = adapter
        binding.swipeRefresh.setColorSchemeColors(
            androidx.core.content.ContextCompat.getColor(this, R.color.brand_teal_dark)
        )
        binding.swipeRefresh.setProgressBackgroundColorSchemeColor(
            androidx.core.content.ContextCompat.getColor(this, R.color.card)
        )
        binding.swipeRefresh.setOnRefreshListener { loadTasks() }

        binding.chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedStatus = when (checkedIds.firstOrNull()) {
                R.id.chipPending -> "DISPATCHED"
                R.id.chipActive -> "IN_PROGRESS"
                R.id.chipFeedback -> "FEEDBACK"
                else -> null
            }
            renderList()
        }
    }

    private fun applyUser() {
        binding.tvGreeting.text = "${UiText.greeting()}，${SessionStore.name}"
        binding.tvAvatar.text = SessionStore.name.take(1).ifBlank { "现" }
        binding.tvDate.text = UiText.todayLabel()
    }

    private fun loadTasks() {
        if (requesting) return
        requesting = true
        val hasCache = CacheStore.tasksCachedAt() > 0
        if (allTasks.isEmpty() && !hasCache) {
            binding.progress.visibility = android.view.View.VISIBLE
            binding.emptyState.visibility = android.view.View.GONE
        }
        lifecycleScope.launch {
            try {
                val tasks = Api.client.tasks()
                allTasks = tasks
                lastError = null
                CacheStore.saveTasks(tasks)
                binding.offlineBanner.visibility = android.view.View.GONE
                binding.tvSyncState.text = "已同步 ${
                    SimpleDateFormat("HH:mm", Locale.CHINA).format(Date())
                }"
                render()
            } catch (e: SessionExpiredException) {
                Toast.makeText(this@TaskListActivity, R.string.session_expired, Toast.LENGTH_SHORT)
                    .show()
                logout()
            } catch (e: Exception) {
                lastError = when (e) {
                    is ApiException -> e.message
                    is IOException -> "网络不可用：${e.message ?: "连接超时"}"
                    else -> e.message ?: "加载失败"
                }
                if (allTasks.isEmpty()) {
                    CacheStore.loadTasks()?.let {
                        allTasks = it
                        render()
                    }
                }
                showOffline()
                render()
            } finally {
                requesting = false
                binding.progress.visibility = android.view.View.GONE
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun showOffline() {
        val cachedAt = CacheStore.tasksCachedAt()
        binding.offlineBanner.visibility = android.view.View.VISIBLE
        binding.tvOffline.text = if (cachedAt > 0) {
            "网络不可用，展示 ${UiText.formatCacheTime(cachedAt)} 的本地缓存"
        } else {
            "网络不可用，请检查服务地址或稍后重试"
        }
        if (cachedAt > 0) {
            binding.tvSyncState.text = "缓存 ${UiText.formatCacheTime(cachedAt)}"
        }
    }

    private fun render() {
        binding.tvStatPending.text =
            allTasks.count { it.status == "DISPATCHED" }.toString()
        binding.tvStatActive.text =
            allTasks.count { it.status == "IN_PROGRESS" }.toString()
        binding.tvStatFeedback.text =
            allTasks.count { it.status == "FEEDBACK" }.toString()
        renderList()
    }

    private fun renderList() {
        val filtered = if (selectedStatus == null) allTasks
        else allTasks.filter { it.status == selectedStatus }
        adapter.submitList(filtered)
        val empty = filtered.isEmpty()
        binding.emptyState.visibility = if (empty) android.view.View.VISIBLE else android.view.View.GONE
        binding.recyclerTasks.visibility = if (empty) android.view.View.GONE else android.view.View.VISIBLE
        if (empty) {
            if (allTasks.isEmpty() && lastError != null) {
                binding.tvEmptyTitle.text = "任务加载失败"
                binding.tvEmptyDesc.text = lastError
            } else if (allTasks.isNotEmpty()) {
                binding.tvEmptyTitle.text = "当前筛选下暂无任务"
                binding.tvEmptyDesc.text = "切换筛选条件或下拉刷新试试。"
            } else {
                binding.tvEmptyTitle.text = getString(R.string.empty_title)
                binding.tvEmptyDesc.text = getString(R.string.empty_desc)
            }
        }
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(this)
            .setTitle("退出登录")
            .setMessage(R.string.logout_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ -> logout() }
            .show()
    }

    private fun logout() {
        SessionStore.clearSession()
        CacheStore.clearAll()
        goLogin()
    }

    private fun goLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
