package com.fixedsource.mobile.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object CacheStore {
    private const val PREFS = "fixed_source_cache"
    private const val KEY_TASKS = "tasks_json"
    private const val KEY_TASKS_TIME = "tasks_time"
    private const val KEY_DETAIL_PREFIX = "detail_"
    private const val KEY_DRAFT_PREFIX = "draft_"
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun saveTasks(tasks: List<TaskSummary>) {
        prefs.edit()
            .putString(KEY_TASKS, gson.toJson(tasks))
            .putLong(KEY_TASKS_TIME, System.currentTimeMillis())
            .apply()
    }

    fun loadTasks(): List<TaskSummary>? {
        val json = prefs.getString(KEY_TASKS, null) ?: return null
        return runCatching {
            val type = object : TypeToken<List<TaskSummary>>() {}.type
            gson.fromJson<List<TaskSummary>>(json, type)
        }.getOrNull()
    }

    fun tasksCachedAt(): Long = prefs.getLong(KEY_TASKS_TIME, 0L)

    fun saveTaskDetail(detail: TaskDetail) {
        prefs.edit().putString(KEY_DETAIL_PREFIX + detail.taskNo, gson.toJson(detail)).apply()
    }

    fun loadTaskDetail(taskNo: String): TaskDetail? {
        val json = prefs.getString(KEY_DETAIL_PREFIX + taskNo, null) ?: return null
        return runCatching { gson.fromJson(json, TaskDetail::class.java) }.getOrNull()
    }

    fun saveDraft(taskNo: String, draft: FeedbackDraft) {
        prefs.edit().putString(KEY_DRAFT_PREFIX + taskNo, gson.toJson(draft)).apply()
    }

    fun loadDraft(taskNo: String): FeedbackDraft? {
        val json = prefs.getString(KEY_DRAFT_PREFIX + taskNo, null) ?: return null
        return runCatching { gson.fromJson(json, FeedbackDraft::class.java) }.getOrNull()
    }

    fun clearDraft(taskNo: String) {
        prefs.edit().remove(KEY_DRAFT_PREFIX + taskNo).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
