package com.clestial.keyboard

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.provider.Settings
import android.transition.TransitionManager
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.clestial.keyboard.floating.FloatingMenuManager

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private var currentTab = TAB_HOME

    private lateinit var tvTitle: TextView
    private lateinit var screenHome: View
    private lateinit var screenTheme: View
    private lateinit var screenShortcut: View

    private lateinit var navPill: View
    private lateinit var navHome: View
    private lateinit var navHomeIcon: ImageView
    private lateinit var navHomeLabel: TextView
    private lateinit var navTheme: View
    private lateinit var navThemeIcon: ImageView
    private lateinit var navThemeLabel: TextView
    private lateinit var navShortcut: View
    private lateinit var navShortcutIcon: ImageView
    private lateinit var navShortcutLabel: TextView

    private lateinit var statusDesc: TextView
    private lateinit var statusPill: TextView
    private lateinit var btnStep1: TextView
    private lateinit var btnStep2: TextView
    private lateinit var cardPrefs: View
    private lateinit var cardTools: View
    private lateinit var cardDownloads: View
    private lateinit var cardMath: View
    private lateinit var cardFloating: View
    private lateinit var switchFloating: SwitchCompat

    private lateinit var optDark: View
    private lateinit var optLight: View
    private lateinit var optSystem: View
    private lateinit var themeStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(R.layout.activity_main)
        applySystemBars()

        currentTab = savedInstanceState?.getInt(STATE_TAB, TAB_HOME) ?: TAB_HOME

        bindViews()
        setupListeners()
        selectTab(currentTab, false)
        renderTheme()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_TAB, currentTab)
    }

    private fun bindViews() {
        tvTitle = findViewById(R.id.tvTitle)
        screenHome = findViewById(R.id.screenHome)
        screenTheme = findViewById(R.id.screenTheme)
        screenShortcut = findViewById(R.id.screenShortcut)

        findViewById<View>(R.id.heroCard)?.clipToOutline = true

        navPill = findViewById(R.id.navPill)
        navHome = findViewById(R.id.navHome)
        navHomeIcon = findViewById(R.id.navHomeIcon)
        navHomeLabel = findViewById(R.id.navHomeLabel)
        navTheme = findViewById(R.id.navTheme)
        navThemeIcon = findViewById(R.id.navThemeIcon)
        navThemeLabel = findViewById(R.id.navThemeLabel)
        navShortcut = findViewById(R.id.navShortcut)
        navShortcutIcon = findViewById(R.id.navShortcutIcon)
        navShortcutLabel = findViewById(R.id.navShortcutLabel)

        statusDesc = findViewById(R.id.statusDesc)
        statusPill = findViewById(R.id.statusPill)
        btnStep1 = findViewById(R.id.btnStep1)
        btnStep2 = findViewById(R.id.btnStep2)
        cardPrefs = findViewById(R.id.cardPrefs)
        cardTools = findViewById(R.id.cardTools)
        cardDownloads = findViewById(R.id.cardDownloads)
        cardMath = findViewById(R.id.cardMath)
        cardFloating = findViewById(R.id.cardFloating)
        switchFloating = findViewById(R.id.switchFloating)

        optDark = findViewById(R.id.optDark)
        optLight = findViewById(R.id.optLight)
        optSystem = findViewById(R.id.optSystem)
        themeStatus = findViewById(R.id.themeStatus)
    }

    private fun setupListeners() {
        findViewById<View>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        navHome.setOnClickListener { selectTab(TAB_HOME, true) }
        navTheme.setOnClickListener { selectTab(TAB_THEME, true) }
        navShortcut.setOnClickListener { selectTab(TAB_SHORTCUT, true) }

        btnStep1.setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        btnStep2.setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }
        cardPrefs.setOnClickListener {
            startActivity(Intent(this, PreferencesActivity::class.java))
        }
        cardDownloads.setOnClickListener {
            startActivity(
                Intent(this, PreferencesActivity::class.java)
                    .putExtra(PreferencesActivity.EXTRA_PAGE, PreferencesActivity.PAGE_DOWNLOADS)
            )
        }
        cardTools.setOnClickListener {
            startActivity(
                Intent(this, PreferencesActivity::class.java)
                    .putExtra(PreferencesActivity.EXTRA_PAGE, PreferencesActivity.PAGE_TOOLS)
            )
        }
        cardMath.setOnClickListener {
            startActivity(Intent(this, MathModulesActivity::class.java))
        }
        cardFloating.setOnClickListener {
            if (!FloatingMenuManager.canDrawOverlays(this)) {
                Toast.makeText(this, R.string.floating_overlay_permission_desc, Toast.LENGTH_LONG).show()
                FloatingMenuManager.requestOverlayPermission(this)
                return@setOnClickListener
            }
            val next = !prefs.floatingEnabled
            prefs.floatingEnabled = next
            switchFloating.isChecked = next
            if (next) {
                FloatingMenuManager.start(this)
                Toast.makeText(this, "Menu Melayang aktif! Ketuk 2x tombol mengambang untuk membuka.", Toast.LENGTH_SHORT).show()
            } else {
                FloatingMenuManager.stop(this)
                Toast.makeText(this, "Menu Melayang dinonaktifkan.", Toast.LENGTH_SHORT).show()
            }
            refreshFloatingCard()
        }

        optDark.setOnClickListener { chooseTheme(Prefs.THEME_DARK) }
        optLight.setOnClickListener { chooseTheme(Prefs.THEME_LIGHT) }
        optSystem.setOnClickListener { chooseTheme(Prefs.THEME_SYSTEM) }
    }

    // ---------------------------------------------------------------- Navigasi

    private fun selectTab(tab: Int, animate: Boolean) {
        currentTab = tab
        if (animate) {
            TransitionManager.beginDelayedTransition(navPill as android.view.ViewGroup)
        }

        val home = tab == TAB_HOME
        val theme = tab == TAB_THEME
        val shortcut = tab == TAB_SHORTCUT
        screenHome.visibility = if (home) View.VISIBLE else View.GONE
        screenTheme.visibility = if (theme) View.VISIBLE else View.GONE
        screenShortcut.visibility = if (shortcut) View.VISIBLE else View.GONE
        tvTitle.setText(
            when (tab) {
                TAB_THEME -> R.string.title_theme
                TAB_SHORTCUT -> R.string.title_shortcut
                else -> R.string.app_name
            }
        )

        styleNavItem(navHome, navHomeIcon, navHomeLabel, home)
        styleNavItem(navTheme, navThemeIcon, navThemeLabel, theme)
        styleNavItem(navShortcut, navShortcutIcon, navShortcutLabel, shortcut)
    }

    private fun styleNavItem(item: View, icon: ImageView, label: TextView, active: Boolean) {
        val pl = item.paddingLeft
        val pt = item.paddingTop
        val pr = item.paddingRight
        val pb = item.paddingBottom
        item.background = if (active) {
            ContextCompat.getDrawable(this, R.drawable.bg_nav_active)
        } else {
            null
        }
        item.setPadding(pl, pt, pr, pb)
        label.visibility = if (active) View.VISIBLE else View.GONE
        val tint = ContextCompat.getColor(this, if (active) R.color.on_inv else R.color.muted)
        label.setTextColor(tint)
        icon.imageTintList = ColorStateList.valueOf(tint)
    }

    // ------------------------------------------------------------------- Tema

    private fun chooseTheme(value: String) {
        prefs.theme = value
        AppCompatDelegate.setDefaultNightMode(prefs.nightMode())
        renderTheme()
    }

    private fun renderTheme() {
        val selected = prefs.theme
        setBgKeepingPadding(optDark, optionBackground(selected == Prefs.THEME_DARK))
        setBgKeepingPadding(optLight, optionBackground(selected == Prefs.THEME_LIGHT))
        setBgKeepingPadding(optSystem, optionBackground(selected == Prefs.THEME_SYSTEM))

        val nameRes = when (selected) {
            Prefs.THEME_DARK -> R.string.theme_dark
            Prefs.THEME_LIGHT -> R.string.theme_light
            else -> R.string.theme_system
        }
        themeStatus.text = getString(R.string.theme_active, getString(nameRes))
    }

    private fun optionBackground(selected: Boolean): Int =
        if (selected) R.drawable.bg_opt_selected else R.drawable.bg_opt

    // ----------------------------------------------------------------- Status

    private fun refreshStatus() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val defaultIme = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        )
        val selected = defaultIme?.startsWith("$packageName/") == true

        when {
            enabled && selected -> {
                statusPill.setText(R.string.pill_on)
                setBgKeepingPadding(statusPill, R.drawable.bg_pill_on)
                statusPill.setTextColor(ContextCompat.getColor(this, R.color.on_inv))
                statusDesc.setText(R.string.status_on_desc)
            }
            enabled -> {
                statusPill.setText(R.string.pill_not_selected)
                setBgKeepingPadding(statusPill, R.drawable.bg_pill)
                statusPill.setTextColor(ContextCompat.getColor(this, R.color.muted))
                statusDesc.setText(R.string.status_not_selected_desc)
            }
            else -> {
                statusPill.setText(R.string.pill_off)
                setBgKeepingPadding(statusPill, R.drawable.bg_pill)
                statusPill.setTextColor(ContextCompat.getColor(this, R.color.muted))
                statusDesc.setText(R.string.status_off_desc)
            }
        }

        styleStepButton(btnStep1, enabled, R.string.btn_open)
        styleStepButton(btnStep2, selected, R.string.btn_choose)
        refreshFloatingCard()
    }

    private fun refreshFloatingCard() {
        val canDraw = FloatingMenuManager.canDrawOverlays(this)
        val active = canDraw && prefs.floatingEnabled
        switchFloating.isChecked = active
        if (active) {
            FloatingMenuManager.start(this)
        }
        val tvStatus = findViewById<TextView>(R.id.tvFloatingCardStatus) ?: return
        if (!canDraw) {
            tvStatus.text = "Izin 'Tampilkan di atas aplikasi lain' belum diberikan. Ketuk untuk memberi izin."
            tvStatus.setTextColor(0xFFF59E0B.toInt())
        } else if (active) {
            tvStatus.text = "Aktif (Gelembung tersamar siap di layar). Ketuk untuk buka menu."
            tvStatus.setTextColor(0xFF10B981.toInt())
        } else {
            tvStatus.text = getString(R.string.floating_card_desc)
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.muted))
        }
    }

    private fun styleStepButton(button: TextView, done: Boolean, idleText: Int) {
        if (done) {
            button.setText(R.string.btn_done)
            setBgKeepingPadding(button, R.drawable.bg_btn_done)
            button.setTextColor(ContextCompat.getColor(this, R.color.fg))
        } else {
            button.setText(idleText)
            setBgKeepingPadding(button, R.drawable.bg_btn)
            button.setTextColor(ContextCompat.getColor(this, R.color.on_inv))
        }
    }

    /** Ganti background drawable tanpa menghapus padding XML akibat inset layer-list. */
    private fun setBgKeepingPadding(view: View, resId: Int) {
        val pl = view.paddingLeft
        val pt = view.paddingTop
        val pr = view.paddingRight
        val pb = view.paddingBottom
        view.setBackgroundResource(resId)
        view.setPadding(pl, pt, pr, pb)
    }

    companion object {
        private const val TAB_HOME = 0
        private const val TAB_THEME = 1
        private const val TAB_SHORTCUT = 2
        private const val STATE_TAB = "state_tab"
    }
}
