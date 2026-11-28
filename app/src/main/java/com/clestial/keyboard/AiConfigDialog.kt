package com.clestial.keyboard

import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.clestial.keyboard.math.AiRouterClient

/**
 * Dialog terpusat untuk mengatur Base URL, API Key, dan Model AI secara dinamis.
 */
object AiConfigDialog {

    fun show(context: Context, prefs: Prefs, onUpdated: () -> Unit) {
        val pad = (18 * context.resources.displayMetrics.density).toInt()
        val gap = (8 * context.resources.displayMetrics.density).toInt()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }

        fun addLabeledInput(label: String, initialValue: String, hint: String, isUri: Boolean): EditText {
            val tv = TextView(context).apply {
                text = label
                textSize = 12f
                setPadding(0, gap, 0, gap / 2)
            }
            val et = EditText(context).apply {
                setText(initialValue)
                this.hint = hint
                isSingleLine = true
                inputType = if (isUri) {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                } else {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                }
            }
            root.addView(tv)
            root.addView(et)
            return et
        }

        val etUrl = addLabeledInput(
            "Base URL (9router / Tunnel / OpenAI / Groq)",
            prefs.chatGptEndpoint,
            context.getString(R.string.ai_cfg_base_url_hint),
            isUri = true
        )
        val etKey = addLabeledInput(
            "API Key",
            prefs.chatGptApiKey,
            context.getString(R.string.ai_cfg_api_key_hint),
            isUri = false
        )
        val etModel = addLabeledInput(
            "Model / Combo",
            prefs.chatGptModel,
            context.getString(R.string.ai_cfg_model_hint),
            isUri = false
        )

        AlertDialog.Builder(context)
            .setTitle(R.string.ai_cfg_card_title)
            .setView(root)
            .setPositiveButton(R.string.ai_cfg_save) { _, _ ->
                val rawUrl = etUrl.text.toString().trim()
                val rawKey = etKey.text.toString().trim()
                val rawModel = etModel.text.toString().trim()
                if (rawUrl.isNotEmpty() && AiRouterClient.normalizeBaseUrl(rawUrl) == null) {
                    Toast.makeText(context, "Base URL harus diawali http:// atau https://", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val saved = AiRouterClient.saveDynamicConfig(prefs, rawUrl, rawKey, rawModel)
                Toast.makeText(context, saved.message, Toast.LENGTH_LONG).show()
                onUpdated()
            }
            .setNeutralButton(R.string.ai_cfg_reset) { _, _ ->
                prefs.chatGptEndpoint = Prefs.DEFAULT_CHATGPT_ENDPOINT
                prefs.chatGptApiKey = ""
                prefs.chatGptModel = Prefs.DEFAULT_CHATGPT_MODEL
                Toast.makeText(context, "Konfigurasi AI dikembalikan ke default.", Toast.LENGTH_SHORT).show()
                onUpdated()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }
}
