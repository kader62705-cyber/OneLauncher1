package com.example.onelauncher

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {
    private lateinit var col: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, r: Int) = GradientDrawable().apply { setColor(color); cornerRadius = r.toFloat() }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Prefs.init(this)
        render()
    }

    private fun add(v: View) {
        col.addView(v, LinearLayout.LayoutParams(M, W).apply { bottomMargin = dp(8) })
    }

    private fun section(t: String) {
        col.addView(TextView(this).apply {
            text = t
            textSize = 14f
            setTextColor(0xFF7FB0FF.toInt())
            setPadding(dp(8), dp(18), 0, dp(8))
        })
    }

    private fun row(title: String, value: String, onClick: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(0xFF1E1E24.toInt(), dp(20))
            setPadding(dp(20), dp(14), dp(20), dp(14))
            addView(TextView(context).apply { text = title; textSize = 16f; setTextColor(Color.WHITE) })
            if (value.isNotEmpty()) addView(TextView(context).apply {
                text = value; textSize = 14f; setTextColor(0xFF9AA0B5.toInt())
            })
            setOnClickListener { onClick() }
        }

    private fun choice(title: String, key: String, opts: List<String>, def: Int) {
        val cur = Prefs.int(key, def).coerceIn(0, opts.size - 1)
        add(row(title, opts[cur]) {
            AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(title)
                .setSingleChoiceItems(opts.toTypedArray(), cur) { d, w ->
                    Prefs.set(key, w); d.dismiss(); render()
                }.show()
        })
    }

    private fun toggle(title: String, key: String, def: Boolean) {
        add(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(0xFF1E1E24.toInt(), dp(20))
            setPadding(dp(20), dp(10), dp(20), dp(10))
            addView(TextView(context).apply { text = title; textSize = 16f; setTextColor(Color.WHITE) },
                LinearLayout.LayoutParams(0, W, 1f))
            addView(Switch(context).apply {
                isChecked = Prefs.bool(key, def)
                setOnCheckedChangeListener { _, v -> Prefs.set(key, v) }
            })
        })
    }

    private fun iconPackRow() {
        val cur = Prefs.str("iconPack", "")
        val packs = IconPack.available(packageManager)
        val curName = packs.firstOrNull { it.first == cur }?.second ?: "None"
        add(row("Icon pack", curName) {
            val names = listOf("None (use app icons)") + packs.map { it.second }
            val b = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert).setTitle("Icon pack")
            if (packs.isEmpty()) {
                b.setMessage("No icon packs found. Install a One UI style icon pack from the Play Store, then come back here.")
                    .setPositiveButton("OK", null)
            } else {
                b.setSingleChoiceItems(names.toTypedArray(), packs.indexOfFirst { it.first == cur } + 1) { d, w ->
                    Prefs.set("iconPack", if (w == 0) "" else packs[w - 1].first)
                    d.dismiss(); render()
                }
            }
            b.show()
        })
    }

    private fun render() {
        val sv = ScrollView(this)
        col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(48), dp(16), dp(40))
        }
        sv.addView(col)
        setContentView(sv)

        col.addView(TextView(this).apply {
            text = "Launcher settings"
            textSize = 30f
            setTextColor(Color.WHITE)
            setPadding(dp(8), 0, 0, dp(8))
        })

        section("Icons")
        choice("Icon shape", "iconShape", listOf("One UI squircle", "Circle", "Rounded square", "Teardrop"), 0)
        choice("Icon style", "iconStyle", listOf("Original", "Themed (mono)", "Grayscale"), 0)
        choice("Icon size", "iconSize", listOf("Small", "Medium", "Large"), 1)
        iconPackRow()

        section("Home screen")
        choice("Columns", "cols", listOf("4", "5", "6"), 0)
        choice("Rows", "rows", listOf("4", "5", "6", "7"), 1)
        choice("Clock widget", "clock", listOf("Left", "Center", "Hidden"), 0)
        toggle("Show app names", "labels", true)
        choice("App name color", "labelColor", listOf("White", "Black"), 0)
        toggle("Show dock", "dock", true)

        section("App drawer")
        choice("Columns", "dcols", listOf("4", "5", "6"), 0)
        choice("Background", "drawerBg", listOf("Dark", "Light", "Transparent"), 0)
        choice("Blur (Android 12+)", "blur", listOf("Off", "Low", "Medium", "High"), 2)

        section("Animation")
        choice("Speed", "anim", listOf("Fast", "Normal", "Slow"), 1)

        section("Other")
        add(row("Reset home screen", "Restore default apps on Home and Dock") {
            getSharedPreferences("launcher", MODE_PRIVATE).edit().remove("home").remove("dock").apply()
            Prefs.bump()
            Toast.makeText(this, "Done", Toast.LENGTH_SHORT).show()
        })
    }
}
