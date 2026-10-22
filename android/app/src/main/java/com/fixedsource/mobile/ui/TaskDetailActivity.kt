package com.fixedsource.mobile.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.fixedsource.mobile.R
import com.fixedsource.mobile.data.Api
import com.fixedsource.mobile.data.ApiException
import com.fixedsource.mobile.data.CacheStore
import com.fixedsource.mobile.data.Clue
import com.fixedsource.mobile.data.FeedbackDraft
import com.fixedsource.mobile.data.FeedbackRequest
import com.fixedsource.mobile.data.SessionExpiredException
import com.fixedsource.mobile.data.SessionStore
import com.fixedsource.mobile.data.TaskDetail
import com.fixedsource.mobile.databinding.ActivityTaskDetailBinding
import com.fixedsource.mobile.util.LocationHelper
import com.fixedsource.mobile.util.UiText
import com.fixedsource.mobile.util.WatermarkRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TaskDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TASK_NO = "taskNo"
    }

    private lateinit var binding: ActivityTaskDetailBinding
    private lateinit var taskNo: String
    private var task: TaskDetail? = null
    private var loading = false
    private var accepting = false
    private var submitting = false
    private var uploading = false
    private var formInitialized = false

    private var photoPath: String? = null
    private var attachmentUrl: String? = null
    private var watermarkText: String = ""
    private var photoLatitude: Double? = null
    private var photoLongitude: Double? = null
    private var draftFeedbackNo: String = ""
    private var pendingCaptureFile: File? = null

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingCaptureFile
        pendingCaptureFile = null
        if (ok && file != null && file.exists()) {
            processCapturedPhoto(file)
        } else {
            file?.delete()
        }
    }

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show()
        }
        launchCamera()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTaskDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        taskNo = intent.getStringExtra(EXTRA_TASK_NO).orEmpty()
        if (taskNo.isBlank()) {
            finish()
            return
        }
        draftFeedbackNo = "FB-AND-$taskNo"

        binding.btnBack.setOnClickListener { finish() }
        binding.btnRetry.setOnClickListener { loadTask(showProgress = task == null) }
        binding.btnAccept.setOnClickListener { acceptTask() }
        binding.btnPhoto.setOnClickListener { requestPhoto() }
        binding.btnSubmit.setOnClickListener { submitFeedback() }
        binding.btnViewPhoto.setOnClickListener { openEvidence() }

        CacheStore.loadTaskDetail(taskNo)?.let {
            task = it
            render(it)
        }
        loadTask(showProgress = task == null)
    }

    override fun onPause() {
        cacheDraft()
        super.onPause()
    }

    private fun loadTask(showProgress: Boolean) {
        if (loading) return
        loading = true
        if (showProgress) {
            binding.progress.visibility = View.VISIBLE
            binding.contentGroup.visibility = View.INVISIBLE
            binding.errorState.visibility = View.GONE
        }
        lifecycleScope.launch {
            try {
                val fresh = Api.client.task(taskNo)
                task = fresh
                CacheStore.saveTaskDetail(fresh)
                render(fresh)
                binding.offlineBanner.visibility = View.GONE
                binding.errorState.visibility = View.GONE
            } catch (e: SessionExpiredException) {
                sessionExpired()
            } catch (e: Exception) {
                if (task == null) {
                    binding.errorState.visibility = View.VISIBLE
                    binding.contentGroup.visibility = View.GONE
                    binding.tvError.text = friendlyError(e)
                } else {
                    binding.offlineBanner.visibility = View.VISIBLE
                    binding.tvOffline.text = "网络不可用，当前展示本地缓存 · ${
                        UiText.formatCacheTime(System.currentTimeMillis())
                    }"
                }
            } finally {
                loading = false
                binding.progress.visibility = View.GONE
            }
        }
    }

    private fun render(current: TaskDetail) {
        binding.contentGroup.visibility = View.VISIBLE
        binding.errorState.visibility = View.GONE

        val status = UiText.statusColors(this, current.status)
        UiText.setPill(
            binding.tvHeaderStatus,
            UiText.statusLabel(current.status),
            status
        )
        binding.tvTaskNo.text = current.taskNo
        binding.tvTitle.text = current.title ?: "现场核查任务"
        binding.tvEnterprise.text = current.enterpriseName ?: "未知企业"
        val clue = current.clue
        val pointName = clue?.pointName ?: clue?.evidence?.pointName ?: "监测点"
        binding.tvPoint.text = listOfNotNull(
            pointName,
            clue?.pollutantName,
            clue?.ruleName
        ).joinToString(" · ")

        val meta = mutableListOf<String>()
        meta.add("截止 ${current.dueAt ?: "未设置"}")
        current.slaHours?.let { meta.add("SLA ${it}h") }
        meta.add("督办 ${current.urgeCount} 次")
        current.externalNo?.takeIf { it.isNotBlank() }?.let { meta.add("外部单号 $it") }
        binding.tvMeta.text = meta.joinToString("    ·    ")

        renderEvidence(clue)
        renderAi(clue)
        renderActions(current)
        renderFeedback(current)
    }

    private fun renderEvidence(clue: Clue?) {
        val evidence = clue?.evidence
        binding.tvRulePill.text = listOfNotNull(clue?.ruleCode, clue?.ruleName)
            .joinToString(" ")
            .ifBlank { "规则证据" }
        binding.tvEvidenceSummary.text = evidence?.summary
            ?: "规则命中后生成的结构化证据快照，支持人工复核。"
        binding.tvTrigger.text = UiText.formatNumber(clue?.triggerValue)
        binding.tvThreshold.text = UiText.formatNumber(clue?.thresholdValue)
        binding.tvCredibilityValue.text = clue?.credibility?.toString() ?: "—"
        binding.tvEvidenceTime.text =
            "触发时段  ${clue?.startTime ?: "—"}  —  ${clue?.endTime ?: "—"}"

        binding.evidenceContainer.removeAllViews()
        val values = evidence?.values.orEmpty()
        if (values.isEmpty()) {
            UiText.addEvidenceLine(binding.evidenceContainer, "暂无逐时证据快照")
        } else {
            values.take(10).forEach { UiText.addEvidenceLine(binding.evidenceContainer, it) }
        }
    }

    private fun renderAi(clue: Clue?) {
        val ai = clue?.aiContent
        UiText.show(binding.cardAi, ai != null)
        if (ai == null) return

        UiText.show(binding.tvRefusal, ai.refusal)
        binding.tvAiExplanation.text = ai.explanation ?: "暂无研判说明"

        binding.suggestionsContainer.removeAllViews()
        val suggestions = ai.suggestions.orEmpty()
        UiText.show(binding.tvSuggestionsTitle, suggestions.isNotEmpty())
        UiText.show(binding.suggestionsContainer, suggestions.isNotEmpty())
        suggestions.forEach { UiText.addSuggestion(binding.suggestionsContainer, it) }

        binding.citationsContainer.removeAllViews()
        val citations = clue.aiCitations?.takeIf { it.isNotEmpty() }
            ?: ai.citations.orEmpty()
        UiText.show(binding.tvCitationsTitle, citations.isNotEmpty())
        UiText.show(binding.citationsContainer, citations.isNotEmpty())
        citations.forEach { UiText.addCitation(binding.citationsContainer, it) }

        val uncertainty = ai.uncertainty
        UiText.show(binding.tvUncertainty, !uncertainty.isNullOrBlank())
        if (!uncertainty.isNullOrBlank()) {
            binding.tvUncertainty.text = "不确定性 · $uncertainty"
        }
    }

    private fun renderActions(current: TaskDetail) {
        val canAccept = current.status == "DISPATCHED"
        UiText.show(binding.cardAccept, canAccept)
        if (canAccept) {
            val role = SessionStore.role
            val allowed = role == "inspector" || role == "admin"
            binding.btnAccept.isEnabled = allowed
            binding.btnAccept.text = getString(R.string.accept_button)
            UiText.show(binding.tvRoleHint, !allowed)
        }

        val showForm = current.status == "IN_PROGRESS"
        UiText.show(binding.cardFeedbackForm, showForm)
        if (showForm && !formInitialized) {
            formInitialized = true
            restoreDraft()
        }
        if (!showForm) formInitialized = false

        binding.btnSubmit.text = getString(R.string.submit_feedback)
        binding.btnSubmit.isEnabled = showForm && !submitting && !uploading
        UiText.show(binding.tvFormError, false)
        updatePhotoButton()
    }

    private fun renderFeedback(current: TaskDetail) {
        val feedback = current.feedback
        UiText.show(binding.cardSubmitted, feedback != null)
        if (feedback == null) return

        val issue = feedback.result == "ISSUE"
        val fg = ContextCompat.getColor(
            this,
            if (issue) R.color.danger else R.color.success
        )
        val bg = ContextCompat.getColor(
            this,
            if (issue) R.color.danger_soft else R.color.success_soft
        )
        UiText.setPill(binding.tvFeedbackResult, UiText.resultLabel(feedback.result),
            UiText.PillColors(fg, bg))
        binding.tvFeedbackTime.text = "提交时间 ${feedback.submittedAt ?: "—"}"
        binding.tvFeedbackProblem.text = feedback.problem?.takeIf { it.isNotBlank() } ?: "—"
        binding.tvFeedbackDisposal.text = feedback.disposal?.takeIf { it.isNotBlank() } ?: "—"
        UiText.show(binding.tvFeedbackWatermark, !feedback.watermark.isNullOrBlank())
        binding.tvFeedbackWatermark.text = feedback.watermark.orEmpty()
        val hasAttachment = !feedback.attachmentUrl.isNullOrBlank()
        UiText.show(binding.btnViewPhoto, hasAttachment)
    }

    private fun restoreDraft() {
        val draft = CacheStore.loadDraft(taskNo) ?: return
        draftFeedbackNo = draft.feedbackNo
        if (draft.result == "ISSUE") {
            binding.btnResultIssue.isChecked = true
        } else {
            binding.btnResultNormal.isChecked = true
        }
        binding.etProblem.setText(draft.problem)
        binding.etDisposal.setText(draft.disposal)
        attachmentUrl = draft.attachmentUrl
        watermarkText = draft.watermark
        photoLatitude = draft.latitude
        photoLongitude = draft.longitude
        draft.photoPath?.takeIf { File(it).exists() }?.let {
            photoPath = it
            showPhoto(it)
        }
        if (!watermarkText.isBlank()) {
            binding.tvWatermark.text = watermarkText
            binding.tvWatermark.visibility = View.VISIBLE
        }
        when {
            !draft.attachmentUrl.isNullOrBlank() -> showPhotoStatus(getString(R.string.photo_uploaded))
            !draft.photoPath.isNullOrBlank() -> showPhotoStatus("本地草稿照片，提交时将自动上传")
        }
        updatePhotoButton()
    }

    private fun requestPhoto() {
        val fine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            launchCamera()
        }
    }

    private fun launchCamera() {
        val dir = File(cacheDir, "capture").apply { mkdirs() }
        val file = File(dir, "raw_${System.currentTimeMillis()}.jpg")
        pendingCaptureFile = file
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        runCatching { takePicture.launch(uri) }.onFailure {
            pendingCaptureFile = null
            file.delete()
            Toast.makeText(this, R.string.camera_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun processCapturedPhoto(raw: File) {
        setPhotoBusy(true)
        hideFormError()
        showPhotoStatus(getString(R.string.photo_uploading))
        lifecycleScope.launch {
            try {
                val rendered = withContext(Dispatchers.IO) { buildWatermarkedPhoto(raw) }
                photoPath = rendered.file.absolutePath
                watermarkText = rendered.watermark
                photoLatitude = rendered.latitude
                photoLongitude = rendered.longitude
                attachmentUrl = null
                showPhoto(rendered.file.absolutePath)
                binding.tvWatermark.text = watermarkText
                binding.tvWatermark.visibility = View.VISIBLE
                cacheDraft()

                showPhotoStatus("水印照片已生成，正在上传…")
                try {
                    val uploaded = Api.client.uploadEvidence(rendered.file)
                    attachmentUrl = uploaded.url
                    showPhotoStatus(getString(R.string.photo_uploaded))
                    cacheDraft()
                } catch (e: SessionExpiredException) {
                    sessionExpired()
                } catch (e: Exception) {
                    showPhotoStatus(getString(R.string.photo_upload_failed))
                    cacheDraft()
                }
            } catch (e: Exception) {
                showPhotoStatus("")
                Toast.makeText(
                    this@TaskDetailActivity,
                    "照片处理失败：${e.message ?: "未知错误"}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                raw.delete()
                setPhotoBusy(false)
            }
        }
    }

    private fun buildWatermarkedPhoto(raw: File): WatermarkedPhoto {
        val location = LocationHelper.bestLastKnown(this)
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())
        val coordinate = LocationHelper.coordinateLabel(location)
        val entity = "${task?.enterpriseName ?: "企业"} · ${currentPointName()}"
        val brand = "净源智研 · 现场取证 · $taskNo"
        val watermark = "$stamp · $coordinate\n$entity\n$brand"
        val outDir = File(filesDir, "evidence").apply { mkdirs() }
        val output = File(outDir, "${taskNo}_${System.currentTimeMillis()}.jpg")
        WatermarkRenderer.render(
            source = raw,
            target = output,
            stampLine = "$stamp · $coordinate",
            entityLine = entity,
            brandLine = brand
        )
        return WatermarkedPhoto(output, watermark, location?.latitude, location?.longitude)
    }

    private fun showPhoto(path: String) {
        val options = BitmapFactory.Options().apply { inSampleSize = 2 }
        val bitmap = BitmapFactory.decodeFile(path, options)
        if (bitmap != null) {
            binding.ivPhotoPreview.setImageBitmap(bitmap)
            binding.ivPhotoPreview.visibility = View.VISIBLE
            binding.photoPlaceholder.visibility = View.GONE
        }
    }

    private fun submitFeedback() {
        if (submitting || uploading) return
        hideKeyboard()
        binding.tvFormError.visibility = View.GONE
        val draft = collectDraft()
        if (draft.result == "ISSUE" && draft.problem.isBlank()) {
            showFormError("核查结果为“发现问题”时，请填写问题描述。")
            return
        }
        if (draft.photoPath.isNullOrBlank() && draft.problem.isBlank()) {
            showFormError(getString(R.string.photo_required))
            return
        }

        submitting = true
        setSubmitBusy(true)
        lifecycleScope.launch {
            try {
                if (!draft.photoPath.isNullOrBlank() && draft.attachmentUrl.isNullOrBlank()) {
                    val file = File(draft.photoPath)
                    if (!file.exists()) {
                        showFormError("本地取证照片已丢失，请重新拍摄。")
                        return@launch
                    }
                    setPhotoBusy(true)
                    showPhotoStatus("正在上传取证照片…")
                    try {
                        attachmentUrl = Api.client.uploadEvidence(file).url
                        showPhotoStatus(getString(R.string.photo_uploaded))
                    } catch (e: SessionExpiredException) {
                        sessionExpired()
                        return@launch
                    } catch (e: Exception) {
                        showFormError("取证照片上传失败：${e.message ?: "网络异常"}")
                        return@launch
                    } finally {
                        setPhotoBusy(false)
                    }
                }

                val current = collectDraft()
                CacheStore.saveDraft(taskNo, current)
                val updated = Api.client.feedback(
                    taskNo,
                    FeedbackRequest(
                        feedbackNo = current.feedbackNo,
                        inspector = current.inspector,
                        result = current.result,
                        problem = current.problem,
                        disposal = current.disposal,
                        attachmentUrl = current.attachmentUrl,
                        watermark = current.watermark,
                        latitude = current.latitude,
                        longitude = current.longitude
                    )
                )
                CacheStore.clearDraft(taskNo)
                draftFeedbackNo = ""
                task = updated
                CacheStore.saveTaskDetail(updated)
                formInitialized = false
                binding.offlineBanner.visibility = View.GONE
                render(updated)
                Toast.makeText(this@TaskDetailActivity, R.string.submit_success, Toast.LENGTH_LONG)
                    .show()
            } catch (e: SessionExpiredException) {
                sessionExpired()
            } catch (e: Exception) {
                CacheStore.saveDraft(taskNo, collectDraft())
                showFormError("${friendlyError(e)}；已保存本地草稿，可稍后重试。")
            } finally {
                submitting = false
                setSubmitBusy(false)
            }
        }
    }

    private fun acceptTask() {
        if (accepting) return
        accepting = true
        binding.btnAccept.isEnabled = false
        binding.btnAccept.text = "正在接收…"
        lifecycleScope.launch {
            try {
                val updated = Api.client.accept(taskNo)
                task = updated
                CacheStore.saveTaskDetail(updated)
                formInitialized = false
                render(updated)
                Toast.makeText(
                    this@TaskDetailActivity,
                    "任务已接收，请开展现场核查",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: SessionExpiredException) {
                sessionExpired()
            } catch (e: Exception) {
                Toast.makeText(this@TaskDetailActivity, friendlyError(e), Toast.LENGTH_LONG).show()
            } finally {
                accepting = false
                binding.btnAccept.isEnabled = true
                binding.btnAccept.text = getString(R.string.accept_button)
            }
        }
    }

    private fun collectDraft(): FeedbackDraft {
        val result = if (binding.btnResultIssue.isChecked) "ISSUE" else "NORMAL"
        return FeedbackDraft(
            feedbackNo = draftFeedbackNo.ifBlank { "FB-AND-$taskNo" },
            inspector = SessionStore.name,
            result = result,
            problem = binding.etProblem.text?.toString()?.trim().orEmpty(),
            disposal = binding.etDisposal.text?.toString()?.trim().orEmpty(),
            attachmentUrl = attachmentUrl,
            watermark = watermarkText,
            latitude = photoLatitude,
            longitude = photoLongitude,
            photoPath = photoPath
        )
    }

    private fun cacheDraft() {
        if (task?.status != "IN_PROGRESS" || !::binding.isInitialized) return
        if (binding.cardFeedbackForm.visibility != View.VISIBLE) return
        CacheStore.saveDraft(taskNo, collectDraft())
    }

    private fun setPhotoBusy(value: Boolean) {
        uploading = value
        binding.btnPhoto.isEnabled = !value
        binding.btnSubmit.isEnabled = !value && !submitting
        updatePhotoButton()
        binding.btnPhoto.text = when {
            value -> "处理中…"
            photoPath != null -> getString(R.string.photo_retake)
            else -> getString(R.string.photo_take)
        }
    }

    private fun updatePhotoButton() {
        if (uploading) return
        binding.btnPhoto.text = if (photoPath.isNullOrBlank()) {
            getString(R.string.photo_take)
        } else {
            getString(R.string.photo_retake)
        }
    }

    private fun setSubmitBusy(value: Boolean) {
        binding.btnSubmit.isEnabled = !value && !uploading
        binding.btnSubmit.text = if (value) getString(R.string.submitting)
        else getString(R.string.submit_feedback)
    }

    private fun showPhotoStatus(text: String) {
        binding.tvPhotoStatus.text = text
        binding.tvPhotoStatus.visibility =
            if (text.isBlank()) View.GONE else View.VISIBLE
    }

    private fun showFormError(text: String) {
        binding.tvFormError.text = text
        binding.tvFormError.visibility = View.VISIBLE
    }

    private fun hideFormError() {
        binding.tvFormError.visibility = View.GONE
    }

    private fun currentPointName(): String {
        val clue = task?.clue
        return clue?.pointName ?: clue?.evidence?.pointName ?: "监测点"
    }

    private fun openEvidence() {
        val url = task?.feedback?.attachmentUrl?.takeIf { it.isNotBlank() } ?: return
        val full = if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else {
            SessionStore.baseUrl.trimEnd('/') + url
        }
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(full)))
        }.onFailure {
            Toast.makeText(this, "无法打开取证附件", Toast.LENGTH_SHORT).show()
        }
    }

    private fun friendlyError(e: Exception): String = when (e) {
        is ApiException -> e.message
        is IOException -> "网络不可用：${e.message ?: "请求超时"}"
        else -> e.message ?: getString(R.string.load_failed)
    }

    private fun sessionExpired() {
        Toast.makeText(this, R.string.session_expired, Toast.LENGTH_SHORT).show()
        SessionStore.clearSession()
        CacheStore.clearAll()
        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    private fun hideKeyboard() {
        val manager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        manager.hideSoftInputFromWindow(binding.etProblem.windowToken, 0)
    }

    private data class WatermarkedPhoto(
        val file: File,
        val watermark: String,
        val latitude: Double?,
        val longitude: Double?
    )
}
