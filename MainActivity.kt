package com.example.onelauncher

import android.animation.ValueAnimator
import android.app.Activity
import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.*
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.text.TextUtils
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

data class App(val label: String, val pkg: String, val intent: Intent, val icon: Bitmap)
enum class Kind { HOME, DRAWER, DOCK }
class PageVH(v: View) : RecyclerView.ViewHolder(v)

class MainActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private var apps = listOf<App>()
    private var home = mutableListOf<String>()
    private var dock = mutableListOf<String>()
    private lateinit var root: FrameLayout
    private lateinit var homeLayer: LinearLayout
    private lateinit var pager: ViewPager2
    private lateinit var dots: LinearLayout
    private lateinit var dockBar: LinearLayout
    private lateinit var drawer: FrameLayout
    private lateinit var drawerPanel: LinearLayout
    private lateinit var search: EditText
    private lateinit var rv: RecyclerView
    private lateinit var drawerAdapter: DrawerAdapter
    private lateinit var detector: GestureDetector
    private var drawerOpen = false
    private var pageCount = 1
    private var topInset = 0
    private var bottomInset = 0

    private var appliedRev = 0
    private val cols: Int get() = 4 + Prefs.int("cols", 0)
    private val rows: Int get() = 4 + Prefs.int("rows", 1)
    private val dCols: Int get() = 4 + Prefs.int("dcols", 0)
    private val iconDp: Int get() = intArrayOf(52, 58, 66)[Prefs.int("iconSize", 1)]
    private val showLabels: Boolean get() = Prefs.bool("labels", true)
    private val clockStyle: Int get() = Prefs.int("clock", 0)
    private val drawerBg: Int get() = Prefs.int("drawerBg", 0)
    private val animMul: Double get() = doubleArrayOf(0.6, 1.0, 1.5)[Prefs.int("anim", 1)]
    private val blurMax: Int get() = intArrayOf(0, 30, 60, 100)[Prefs.int("blur", 2)]
    private fun ad(ms: Long) = (ms * animMul).toLong()

    private fun labelColor(kind: Kind): Int {
        val dark = if (kind == Kind.DRAWER) drawerBg == 1 else Prefs.int("labelColor", 0) == 1
        return if (dark) 0xFF1B1B1F.toInt() else Color.WHITE
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { loadApps() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, r: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = r.toFloat()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        appliedRev = Prefs.rev
        WindowCompat.setDecorFitsSystemWindows(window, false)
        prefs = getSharedPreferences("launcher", MODE_PRIVATE)
        buildUi()
        detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                val dy = e2.y - (e1?.y ?: e2.y)
                val dx = e2.x - (e1?.x ?: e2.x)
                if (abs(dy) < abs(dx)) return false
                if (!drawerOpen && vy < -900 && dy < -dp(80)) { setDrawer(true); return true }
                if (drawerOpen && vy > 900 && !rv.canScrollVertically(-1)) { setDrawer(false); return true }
                return false
            }
        })
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        registerReceiver(pkgReceiver, f)
        loadApps()
    }

    override fun onResume() {
        super.onResume()
        if (Prefs.rev != appliedRev) recreate()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(pkgReceiver) } catch (_: Exception) {}
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        detector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) {
            setDrawer(false)
            pager.setCurrentItem(0, true)
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (drawerOpen) setDrawer(false) else pager.setCurrentItem(0, true)
    }

    // ---------------- UI ----------------
    private fun buildUi() {
        root = FrameLayout(this)
        setContentView(root)

        homeLayer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        pager = ViewPager2(this)
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) { buildDots(pageCount, position) }
        })
        homeLayer.addView(pager, LinearLayout.LayoutParams(M, 0, 1f))
        dots = LinearLayout(this).apply { gravity = Gravity.CENTER }
        homeLayer.addView(dots, LinearLayout.LayoutParams(M, dp(20)))
        dockBar = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = rounded(0x33FFFFFF, dp(34))
            setPadding(dp(8), dp(10), dp(8), dp(10))
        }
        homeLayer.addView(dockBar, LinearLayout.LayoutParams(M, W).apply {
            setMargins(dp(14), dp(4), dp(14), 0)
        })
        root.addView(homeLayer, FrameLayout.LayoutParams(M, M))

        val light = drawerBg == 1
        drawer = FrameLayout(this).apply {
            visibility = View.GONE
            alpha = 0f
            isClickable = true
            setBackgroundColor(when (drawerBg) { 1 -> 0xE6F4F4F8.toInt(); 2 -> 0x33000000; else -> 0x99101014.toInt() })
        }
        drawerPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        search = EditText(this).apply {
            hint = "ፍለጋ · Search"
            setHintTextColor(if (light) 0x88000000.toInt() else 0xAAFFFFFF.toInt())
            setTextColor(if (light) 0xFF1B1B1F.toInt() else Color.WHITE)
            textSize = 16f
            isSingleLine = true
            background = rounded(if (light) 0x22000000 else 0x33FFFFFF, dp(26))
            setPadding(dp(20), 0, dp(20), 0)
        }
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topRow.addView(search, LinearLayout.LayoutParams(0, dp(52), 1f))
        val gear = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_preferences)
            setColorFilter(if (light) 0xFF1B1B1F.toInt() else Color.WHITE)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(if (light) 0x22000000 else 0x33FFFFFF, dp(26))
            setOnClickListener { openSettings() }
        }
        topRow.addView(gear, LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginStart = dp(8) })
        drawerPanel.addView(topRow, LinearLayout.LayoutParams(M, W).apply {
            setMargins(dp(14), 0, dp(14), dp(12))
        })
        drawerAdapter = DrawerAdapter()
        rv = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, dCols)
            adapter = drawerAdapter
            clipToPadding = false
            setPadding(dp(8), 0, dp(8), dp(24))
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        drawerPanel.addView(rv, LinearLayout.LayoutParams(M, 0, 1f))
        drawer.addView(drawerPanel, FrameLayout.LayoutParams(M, M))
        root.addView(drawer, FrameLayout.LayoutParams(M, M))
        search.doAfterTextChanged { drawerAdapter.filter(it?.toString() ?: "") }

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, ins ->
            val b = ins.getInsets(WindowInsetsCompat.Type.systemBars())
            topInset = b.top
            bottomInset = b.bottom
            homeLayer.setPadding(0, topInset, 0, bottomInset + dp(12))
            drawerPanel.setPadding(0, topInset + dp(12), 0, bottomInset)
            ins
        }
    }

    private fun buildDots(n: Int, sel: Int) {
        dots.removeAllViews()
        if (n < 2) return
        for (i in 0 until n) {
            dots.addView(View(this).apply {
                background = rounded(if (i == sel) 0xFFFFFFFF.toInt() else 0x66FFFFFF, dp(4))
                layoutParams = LinearLayout.LayoutParams(dp(if (i == sel) 16 else 7), dp(7)).apply {
                    setMargins(dp(3), 0, dp(3), 0)
                }
            })
        }
    }

    private fun clockWidget(): View {
        val ctx = this
        val center = clockStyle == 1
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            if (center) gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(24), dp(20), dp(28))
            addView(TextClock(ctx).apply {
                format12Hour = "h:mm"; format24Hour = "H:mm"
                textSize = 64f
                setTextColor(Color.WHITE)
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                setShadowLayer(8f, 0f, 2f, 0x55000000)
            })
            addView(TextClock(ctx).apply {
                format12Hour = "EEEE, MMMM d"; format24Hour = "EEEE, MMMM d"
                textSize = 16f
                setTextColor(Color.WHITE)
                setShadowLayer(6f, 0f, 1f, 0x55000000)
            })
        }
    }

    // ---------------- Drawer open/close ----------------
    private fun setDrawer(open: Boolean) {
        if (open == drawerOpen) return
        drawerOpen = open
        val ip = DecelerateInterpolator(2f)
        if (open) {
            drawer.visibility = View.VISIBLE
            drawer.alpha = 0f
            drawerPanel.scaleX = 1.08f; drawerPanel.scaleY = 1.08f
            drawerPanel.translationY = dp(40).toFloat()
            drawer.animate().alpha(1f).setDuration(ad(320)).setInterpolator(ip).start()
            drawerPanel.animate().scaleX(1f).scaleY(1f).translationY(0f).setDuration(ad(320)).setInterpolator(ip).start()
            homeLayer.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(ad(300)).setInterpolator(ip).start()
            animateBlur(0, blurMax)
        } else {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(search.windowToken, 0)
            search.setText("")
            drawer.animate().alpha(0f).setDuration(ad(220)).withEndAction {
                if (!drawerOpen) drawer.visibility = View.GONE
            }.start()
            drawerPanel.animate().scaleX(0.96f).scaleY(0.96f).setDuration(ad(220)).start()
            homeLayer.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(ad(300))
                .setInterpolator(OvershootInterpolator(1.2f)).start()
            animateBlur(blurMax, 0)
        }
    }

    private fun animateBlur(from: Int, to: Int) {
        if (Build.VERSION.SDK_INT < 31 || blurMax == 0) return
        ValueAnimator.ofInt(from, to).apply {
            duration = 300
            addUpdateListener {
                try { window.setBackgroundBlurRadius(it.animatedValue as Int) } catch (_: Throwable) {}
            }
            start()
        }
    }

    // ---------------- Data ----------------
    private fun loadApps() {
        Thread {
            val pm = packageManager
            val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val size = dp(64)
            val shape = Prefs.int("iconShape", 0)
            val style = Prefs.int("iconStyle", 0)
            val packPkg = Prefs.str("iconPack", "")
            val pack = if (packPkg.isNotEmpty()) IconPack.load(this, packPkg) else null
            val list = pm.queryIntentActivities(q, 0)
                .filter { it.activityInfo.packageName != packageName }
                .map { ri ->
                    val ai = ri.activityInfo
                    val intent = Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .setClassName(ai.packageName, ai.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    val packIcon = pack?.icon(ai.packageName + "/" + ai.name)
                    val bmp = if (packIcon != null) renderPackIcon(packIcon, size)
                    else renderIcon(ri.loadIcon(pm), size, shape, style)
                    App(ri.loadLabel(pm).toString(), ai.packageName, intent, bmp)
                }
                .distinctBy { it.pkg }
                .sortedBy { it.label.lowercase() }
            runOnUiThread { apps = list; initDefaults(); refreshAll() }
        }.start()
    }

    private fun loadList(k: String) =
        (prefs.getString(k, "") ?: "").split(",").filter { it.isNotBlank() }.toMutableList()

    private fun saveLists() {
        prefs.edit().putString("home", home.joinToString(",")).putString("dock", dock.joinToString(",")).apply()
    }

    private fun initDefaults() {
        val pm = packageManager
        if (!prefs.contains("dock")) {
            val intents = listOf(
                Intent(Intent.ACTION_DIAL),
                Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")),
                Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            )
            dock = intents.mapNotNull {
                pm.resolveActivity(it, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            }.filter { p -> apps.any { it.pkg == p } }.distinct().toMutableList()
            prefs.edit().putString("dock", dock.joinToString(",")).apply()
        }
        if (!prefs.contains("home")) {
            val d = loadList("dock")
            home = apps.filter { it.pkg !in d }.take(12).map { it.pkg }.toMutableList()
            prefs.edit().putString("home", home.joinToString(",")).apply()
        }
    }

    private fun refreshAll() {
        val byPkg = apps.associateBy { it.pkg }
        home = loadList("home").filter { it in byPkg }.toMutableList()
        dock = loadList("dock").filter { it in byPkg }.toMutableList()
        val homeApps = home.mapNotNull { byPkg[it] }
        val pages = mutableListOf<List<App>>()
        var i = 0
        var first = true
        while (i < homeApps.size || first) {
            val cap = maxOf(1, if (first && clockStyle != 2) cols * (rows - 1) else cols * rows)
            pages.add(homeApps.drop(i).take(cap))
            i += cap
            first = false
        }
        val cur = pager.currentItem
        pageCount = pages.size
        pager.adapter = PageAdapter(pages)
        pager.setCurrentItem(min(cur, pages.size - 1), false)
        buildDots(pageCount, pager.currentItem)

        dockBar.removeAllViews()
        dock.mapNotNull { byPkg[it] }.forEach { app ->
            val cell = Cell(this, true)
            cell.bind(app, Kind.DOCK)
            dockBar.addView(cell, LinearLayout.LayoutParams(0, W, 1f))
        }
        dockBar.visibility = if (dock.isEmpty() || !Prefs.bool("dock", true)) View.GONE else View.VISIBLE
        drawerAdapter.submit(apps)
    }

    // ---------------- Actions ----------------
    private fun launch(a: App, v: View) {
        try {
            val o = ActivityOptions.makeScaleUpAnimation(v, 0, 0, v.width, v.height)
            startActivity(Intent(a.intent), o.toBundle())
        } catch (e: Exception) {
            Toast.makeText(this, "Can't open", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showMenu(anchor: View, a: App, kind: Kind) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(0xF5FFFFFF.toInt(), dp(24))
            setPadding(0, dp(8), 0, dp(8))
        }
        val pw = PopupWindow(card, W, W, true)
        pw.elevation = dp(16).toFloat()
        pw.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        fun item(t: String, act: () -> Unit) {
            card.addView(TextView(this).apply {
                text = t
                textSize = 15f
                setTextColor(0xFF1B1B1F.toInt())
                setPadding(dp(22), dp(13), dp(34), dp(13))
                setOnClickListener { pw.dismiss(); act() }
            })
        }
        when (kind) {
            Kind.HOME -> item("Remove from Home") { home.remove(a.pkg); saveLists(); refreshAll() }
            Kind.DRAWER -> {
                if (a.pkg in home) item("Remove from Home") { home.remove(a.pkg); saveLists(); refreshAll() }
                else item("Add to Home") { home.add(a.pkg); saveLists(); refreshAll() }
                if (a.pkg !in dock && dock.size < 5) item("Add to Dock") { dock.add(a.pkg); saveLists(); refreshAll() }
            }
            Kind.DOCK -> item("Remove from Dock") { dock.remove(a.pkg); saveLists(); refreshAll() }
        }
        item("App info") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}")))
        }
        item("Uninstall") {
            startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${a.pkg}")))
        }
        card.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        val dm = resources.displayMetrics
        val x = (loc[0]).coerceIn(dp(12), dm.widthPixels - card.measuredWidth - dp(12))
        val y = if (kind == Kind.DOCK) loc[1] - card.measuredHeight - dp(8)
        else min(loc[1] + anchor.height, dm.heightPixels - card.measuredHeight - dp(24))
        pw.showAtLocation(root, Gravity.TOP or Gravity.START, x, y)
    }

    // ---------------- Views ----------------
    inner class Cell(ctx: Context, private val dockMode: Boolean) : LinearLayout(ctx) {
        private val img = ImageView(ctx)
        private val tv = TextView(ctx)

        init {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(4), 0, dp(4))
            addView(img, LinearLayout.LayoutParams(dp(iconDp), dp(iconDp)))
            tv.apply {
                setTextColor(Color.WHITE)
                textSize = 11.5f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                setShadowLayer(4f, 0f, 1f, 0x88000000.toInt())
            }
            addView(tv, LinearLayout.LayoutParams(M, W).apply { topMargin = dp(6) })
            if (dockMode || !showLabels) tv.visibility = View.GONE
            setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN ->
                        v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(120).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        v.animate().scaleX(1f).scaleY(1f).setDuration(260)
                            .setInterpolator(OvershootInterpolator(3f)).start()
                }
                false
            }
        }

        fun bind(a: App, kind: Kind) {
            img.setImageBitmap(a.icon)
            tv.text = a.label
            val lc = labelColor(kind)
            tv.setTextColor(lc)
            tv.setShadowLayer(if (lc == Color.WHITE) 4f else 0f, 0f, 1f, 0x88000000.toInt())
            setOnClickListener { launch(a, img) }
            setOnLongClickListener {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                showMenu(this, a, kind)
                true
            }
        }
    }

    inner class PageAdapter(private val pages: List<List<App>>) : RecyclerView.Adapter<PageVH>() {
        override fun onCreateViewHolder(p: ViewGroup, t: Int): PageVH {
            val c = LinearLayout(p.context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.LayoutParams(M, M)
                setPadding(dp(8), dp(8), dp(8), 0)
            }
            return PageVH(c)
        }

        override fun getItemCount() = pages.size

        override fun onBindViewHolder(h: PageVH, pos: Int) {
            val c = h.itemView as LinearLayout
            c.removeAllViews()
            c.setOnLongClickListener {
                c.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                openSettings(); true
            }
            if (pos == 0 && clockStyle != 2) c.addView(clockWidget())
            val grid = GridLayout(c.context).apply { columnCount = cols }
            pages[pos].forEach { app ->
                val cell = Cell(c.context, false)
                cell.bind(app, Kind.HOME)
                val lp = GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f)
                ).apply { width = 0; height = W; bottomMargin = dp(14) }
                grid.addView(cell, lp)
            }
            c.addView(grid, LinearLayout.LayoutParams(M, W))
        }
    }

    inner class DrawerAdapter : RecyclerView.Adapter<PageVH>() {
        private var all = listOf<App>()
        private var shown = listOf<App>()

        fun submit(l: List<App>) { all = l; filter(search.text.toString()) }

        fun filter(q: String) {
            shown = if (q.isBlank()) all else all.filter { it.label.contains(q.trim(), true) }
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(p: ViewGroup, t: Int): PageVH {
            val cell = Cell(p.context, false)
            cell.layoutParams = RecyclerView.LayoutParams(M, W)
            return PageVH(cell)
        }

        override fun getItemCount() = shown.size

        override fun onBindViewHolder(h: PageVH, pos: Int) {
            (h.itemView as Cell).bind(shown[pos], Kind.DRAWER)
        }
    }
}
