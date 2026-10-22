package com.fixedsource.mobile.ui

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.fixedsource.mobile.R
import com.fixedsource.mobile.data.Api
import com.fixedsource.mobile.data.ApiClient
import com.fixedsource.mobile.data.ApiException
import com.fixedsource.mobile.data.CacheStore
import com.fixedsource.mobile.data.SessionStore
import com.fixedsource.mobile.data.SessionExpiredException
import com.fixedsource.mobile.databinding.ActivityLoginBinding
import com.fixedsource.mobile.util.UiText
import kotlinx.coroutines.launch
import java.io.IOException

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.etServer.setText(SessionStore.baseUrl)
        val savedUser = SessionStore.username
        binding.etUsername.setText(savedUser.ifBlank { "inspector" })
        binding.etPassword.setText("123456")

        binding.tvTestConnection.setOnClickListener { testConnection() }
        binding.btnLogin.setOnClickListener { doLogin() }
        binding.tvDemo.setOnClickListener {
            binding.etUsername.setText("inspector")
            binding.etPassword.setText("123456")
            binding.etServer.setText(ApiClient.normalizeBaseUrl(binding.etServer.text?.toString().orEmpty())
                ?: SessionStore.baseUrl)
            binding.tvLoginError.visibility = android.view.View.GONE
        }

        if (SessionStore.isLoggedIn) {
            trySilentLogin()
        }
    }

    private fun trySilentLogin() {
        setBusy(true)
        lifecycleScope.launch {
            try {
                val user = Api.client.currentUser()
                SessionStore.name = user.name
                SessionStore.role = user.role
                SessionStore.roleLabel = user.roleLabel
                openTasks()
            } catch (e: SessionExpiredException) {
                SessionStore.clearSession()
                setBusy(false)
            } catch (e: IOException) {
                // Weak-network: cached tasks remain usable; token is validated once online.
                openTasks()
            } catch (e: Exception) {
                SessionStore.clearSession()
                setBusy(false)
            }
        }
    }

    private fun testConnection() {
        val base = ApiClient.normalizeBaseUrl(binding.etServer.text?.toString().orEmpty())
        if (base == null) {
            showError(getString(R.string.invalid_server))
            return
        }
        binding.tvTestConnection.isEnabled = false
        binding.tvConnection.text = getString(R.string.checking)
        setDot(R.color.text_muted)
        lifecycleScope.launch {
            try {
                val health = Api.client.health(base)
                binding.tvConnection.text =
                    if (health.status.equals("UP", true)) getString(R.string.connected)
                    else "服务状态 ${health.status} · ${health.service}"
                setDot(R.color.success)
            } catch (e: Exception) {
                binding.tvConnection.text = getString(R.string.connect_failed)
                setDot(R.color.danger)
            } finally {
                binding.tvTestConnection.isEnabled = true
            }
        }
    }

    private fun doLogin() {
        if (busy) return
        hideKeyboard()
        val base = ApiClient.normalizeBaseUrl(binding.etServer.text?.toString().orEmpty())
        val username = binding.etUsername.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()
        if (base == null) {
            showError(getString(R.string.invalid_server))
            return
        }
        if (username.isBlank() || password.isBlank()) {
            showError(getString(R.string.field_empty))
            return
        }
        binding.tvLoginError.visibility = android.view.View.GONE
        setBusy(true)
        lifecycleScope.launch {
            try {
                val result = Api.client.login(base, username, password)
                CacheStore.clearAll()
                SessionStore.saveLogin(base, result)
                openTasks()
            } catch (e: SessionExpiredException) {
                showError(e.message ?: getString(R.string.login_failed))
                setBusy(false)
            } catch (e: ApiException) {
                showError(if (e.statusCode == 401) "用户名或密码错误" else e.message)
                setBusy(false)
            } catch (e: IOException) {
                showError("服务不可达：${e.message ?: "请检查网络与地址"}")
                setDot(R.color.danger)
                setBusy(false)
            } catch (e: Exception) {
                showError(getString(R.string.login_failed))
                setBusy(false)
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        binding.btnLogin.isEnabled = !value
        binding.btnLogin.text =
            if (value) "正在连接…" else getString(R.string.login_button)
        binding.etServer.isEnabled = !value
        binding.etUsername.isEnabled = !value
        binding.etPassword.isEnabled = !value
    }

    private fun showError(message: String) {
        binding.tvLoginError.text = message
        binding.tvLoginError.visibility = android.view.View.VISIBLE
    }

    private fun setDot(colorRes: Int) {
        binding.dotConnection.backgroundTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(this, colorRes)
        )
    }

    private fun openTasks() {
        startActivity(
            Intent(this, TaskListActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    private fun hideKeyboard() {
        val manager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        manager.hideSoftInputFromWindow(binding.etPassword.windowToken, 0)
    }
}
