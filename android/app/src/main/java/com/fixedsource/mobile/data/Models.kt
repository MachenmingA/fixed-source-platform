package com.fixedsource.mobile.data

import com.google.gson.annotations.SerializedName

data class LoginResult(
    val token: String = "",
    val username: String = "",
    val name: String = "",
    val role: String = "",
    val roleLabel: String = ""
)

data class UserInfo(
    val username: String = "",
    val name: String = "",
    val role: String = "",
    val roleLabel: String = ""
)

data class HealthResult(
    val status: String = "",
    val service: String = "",
    val time: String = ""
)

data class TaskSummary(
    val id: Long = 0L,
    val taskNo: String = "",
    val title: String? = null,
    val status: String = "",
    val assignee: String? = null,
    val dueAt: String? = null,
    val urgeCount: Int = 0,
    val slaStatus: String? = null,
    val enterpriseName: String? = null,
    val region: String? = null,
    val pointName: String? = null,
    val pollutantName: String? = null,
    val ruleName: String? = null,
    val severity: String? = null,
    val credibility: Double? = null
)

data class TaskDetail(
    val id: Long = 0L,
    val taskNo: String = "",
    val title: String? = null,
    val status: String = "",
    val assignee: String? = null,
    val externalNo: String? = null,
    val clueId: Long? = null,
    val eventId: Long? = null,
    val enterpriseId: String? = null,
    @SerializedName(value = "slaHours", alternate = ["slahours"])
    val slaHours: Int? = null,
    val urgeCount: Int = 0,
    val dueAt: String? = null,
    val dispatchedAt: String? = null,
    val feedbackAt: String? = null,
    val enterpriseName: String? = null,
    val feedback: Feedback? = null,
    val clue: Clue? = null
)

data class Clue(
    val id: Long = 0L,
    val clueNo: String? = null,
    val ruleCode: String? = null,
    val ruleName: String? = null,
    val severity: String? = null,
    val triggerValue: Double? = null,
    val thresholdValue: Double? = null,
    val aiStatus: String? = null,
    val credibility: Int? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val pointName: String? = null,
    val pollutantName: String? = null,
    val evidence: Evidence? = null,
    val aiContent: AiContent? = null,
    val aiCitations: List<Citation>? = null
)

data class Evidence(
    val ruleCode: String? = null,
    val ruleName: String? = null,
    val summary: String? = null,
    val pointId: String? = null,
    val pointName: String? = null,
    val outletName: String? = null,
    val pollutant: String? = null,
    val limit: Double? = null,
    val values: List<String>? = null
)

data class AiContent(
    val summary: String? = null,
    val ruleCode: String? = null,
    val ruleName: String? = null,
    val timeRange: String? = null,
    val explanation: String? = null,
    val suggestions: List<String>? = null,
    val uncertainty: String? = null,
    val refusal: Boolean = false,
    val citations: List<Citation>? = null
)

data class Citation(
    val id: String? = null,
    val law: String? = null,
    val clause: String? = null,
    val title: String? = null,
    val effectiveDate: String? = null,
    val quote: String? = null
)

data class Feedback(
    val feedbackNo: String? = null,
    val inspector: String? = null,
    val result: String? = null,
    val problem: String? = null,
    val disposal: String? = null,
    val attachmentUrl: String? = null,
    val watermark: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val submittedAt: String? = null
)

data class FeedbackRequest(
    val feedbackNo: String,
    val inspector: String,
    val result: String,
    val problem: String,
    val disposal: String,
    val attachmentUrl: String?,
    val watermark: String,
    val latitude: Double?,
    val longitude: Double?
)

data class UploadResult(
    val name: String? = null,
    val url: String = "",
    val size: Long = 0L
)

data class FeedbackDraft(
    val feedbackNo: String,
    val inspector: String,
    val result: String,
    val problem: String,
    val disposal: String,
    val attachmentUrl: String? = null,
    val watermark: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val photoPath: String? = null,
    val savedAt: Long = System.currentTimeMillis()
)
