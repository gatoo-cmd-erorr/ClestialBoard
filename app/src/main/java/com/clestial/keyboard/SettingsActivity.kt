package com.clestial.keyboard

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.clestial.keyboard.math.AiRouterClient

/**
 * Halaman Pengaturan: informasi aplikasi dan pengaturan dinamis Base URL, API Key,
 * serta Model AI (9router / OpenAI / Groq / OpenRouter).
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var etBaseUrl: EditText
    private lateinit var etApiKey: EditText
    private lateinit var etModel: EditText
    private lateinit var tvAiStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(R.layout.activity_settings)
        applySystemBars()

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        val version = (try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            null
        }) ?: "1.0.0"
        findViewById<TextView>(R.id.aboutVersion).text = getString(R.string.version_fmt, version)

        etBaseUrl = findViewById(R.id.etSettingsBaseUrl)
        etApiKey = findViewById(R.id.etSettingsApiKey)
        etModel = findViewById(R.id.etSettingsModel)
        tvAiStatus = findViewById(R.id.tvSettingsAiStatus)

        syncAiFields()

        findViewById<View>(R.id.btnSettingsSaveAi).setOnClickListener {
            saveCurrentInputs(showToast = true)
        }

        findViewById<View>(R.id.btnSettingsResetAi).setOnClickListener {
            prefs.chatGptEndpoint = Prefs.DEFAULT_CHATGPT_ENDPOINT
            prefs.chatGptApiKey = ""
            prefs.chatGptModel = Prefs.DEFAULT_CHATGPT_MODEL
            syncAiFields()
            Toast.makeText(this, "Konfigurasi AI direset ke default.", Toast.LENGTH_SHORT).show()
        }

        val btnTest = findViewById<View>(R.id.btnSettingsTestAi)
        btnTest.setOnClickListener {
            if (!saveCurrentInputs(showToast = false)) return@setOnClickListener
            val modelsUrl = AiRouterClient.resolveModelsEndpoint(prefs.chatGptEndpoint)
            tvAiStatus.text = "Menguji koneksi ke $modelsUrl..."
            btnTest.isEnabled = false
            AiRouterClient.testConnection(prefs) { res ->
                btnTest.isEnabled = true
                tvAiStatus.text = res.message
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::prefs.isInitialized) {
            syncAiFields()
        }
    }

    private fun saveCurrentInputs(showToast: Boolean): Boolean {
        val rawUrl = etBaseUrl.text.toString().trim()
        val rawKey = etApiKey.text.toString().trim()
        val rawModel = etModel.text.toString().trim()
        if (rawUrl.isNotEmpty() && AiRouterClient.normalizeBaseUrl(rawUrl) == null) {
            Toast.makeText(this, "Base URL harus diawali http:// atau https://", Toast.LENGTH_SHORT).show()
            return false
        }
        val saved = AiRouterClient.saveDynamicConfig(prefs, rawUrl, rawKey, rawModel)
        syncAiFields()
        if (showToast) {
            Toast.makeText(this, saved.message, Toast.LENGTH_SHORT).show()
        }
        return true
    }

    private fun syncAiFields() {
        etBaseUrl.setText(prefs.chatGptEndpoint)
        etApiKey.setText(prefs.chatGptApiKey)
        etModel.setText(prefs.chatGptModel)
        val resolvedChat = AiRouterClient.resolveChatCompletionsEndpoint(prefs.chatGptEndpoint)
        tvAiStatus.text = "Endpoint aktif: $resolvedChat\nKey: ${AiRouterClient.maskKey(prefs.chatGptApiKey)} • Model: ${prefs.chatGptModel}"
    }
}
