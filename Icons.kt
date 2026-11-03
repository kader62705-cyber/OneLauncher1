package com.example.onelauncher

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

const val M = -1
const val W = -2

object Prefs {
    private lateinit var p: SharedPreferences
    fun init(c: Context) {
        if (!::p.isInitialized) p = c.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }
    fun int(k: String, d: Int) = p.getInt(k, d)
    fun bool(k: String, d: Boolean) = p.getBoolean(k, d)
    fun str(k: String, d: String) = p.getString(k, d) ?: d
    val rev: Int get() = p.getInt("rev", 0)
    fun bump() { p.edit().putInt("rev", rev + 1).apply() }
    fun set(k: String, v: Int) { p.edit().putInt(k, v).putInt("rev", rev + 1).apply() }
    fun set(k: String, v: Boolean) { p.edit().putBoolean(k, v).putInt("rev", rev + 1).apply() }
    fun set(k: String, v: String) { p.edit().putString(k, v).putInt("rev", rev + 1).apply() }
}

/** Supports ADW / Nova style icon packs (install a One UI icon pack and pick it in settings) */
class IconPack(private val pkg: String, private val res: Resources, private val map: Map<String, String>) {
    fun icon(component: String): Drawable? {
        val name = map[component] ?: return null
        val id = res.getIdentifier(name, "drawable", pkg)
        if (id == 0) return null
        return try { res.getDrawable(id, null) } catch (e: Exception) { null }
    }

    companion object {
        private val ACTIONS = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME")

        fun available(pm: PackageManager): List<Pair<String, String>> {
            val out = LinkedHashMap<String, String>()
            for (a in ACTIONS) {
                pm.queryIntentActivities(Intent(a), 0).forEach {
                    out[it.activityInfo.packageName] = it.loadLabel(pm).toString()
                }
            }
            return out.map { it.key to it.value }
        }

        fun load(ctx: Context, pkg: String): IconPack? {
            return try {
                val pc = ctx.createPackageContext(pkg, 0)
                val res = pc.resources
                val map = HashMap<String, String>()
                val xid = res.getIdentifier("appfilter", "xml", pkg)
                val parser: XmlPullParser = if (xid != 0) res.getXml(xid)
                else XmlPullParserFactory.newInstance().newPullParser().apply {
                    setInput(pc.assets.open("appfilter.xml"), null)
                }
                var ev = parser.eventType
                while (ev != XmlPullParser.END_DOCUMENT) {
                    if (ev == XmlPullParser.START_TAG && parser.name == "item") {
                        val comp = parser.getAttributeValue(null, "component")
                        val dr = parser.getAttributeValue(null, "drawable")
                        if (comp != null && dr != null && comp.startsWith("ComponentInfo{") && comp.endsWith("}")) {
                            map[comp.substring(14, comp.length - 1)] = dr
                        }
                    }
                    ev = parser.next()
                }
                IconPack(pkg, res, map)
            } catch (e: Exception) { null }
        }
    }
}

fun squircle(s: Float): Path {
    val p = Path()
    val r = s / 2f
    val n = 5.0
    for (i in 0..120) {
        val t = 2.0 * PI * i / 120
        val c = cos(t)
        val sn = sin(t)
        val x = r + r * sign(c) * abs(c).pow(2.0 / n)
        val y = r + r * sign(sn) * abs(sn).pow(2.0 / n)
        if (i == 0) p.moveTo(x.toFloat(), y.toFloat()) else p.lineTo(x.toFloat(), y.toFloat())
    }
    p.close()
    return p
}

fun shapePath(shape: Int, s: Float): Path = when (shape) {
    1 -> Path().apply { addCircle(s / 2, s / 2, s / 2, Path.Direction.CW) }
    2 -> Path().apply { addRoundRect(RectF(0f, 0f, s, s), s * 0.28f, s * 0.28f, Path.Direction.CW) }
    3 -> Path().apply {
        val big = s / 2
        val small = s * 0.14f
        addRoundRect(RectF(0f, 0f, s, s), floatArrayOf(big, big, big, big, big, big, small, small), Path.Direction.CW)
    }
    else -> squircle(s)
}

/** Icon from an icon pack: used as is (packs already include their own shape) */
fun renderPackIcon(d: Drawable, s: Int): Bitmap {
    val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    d.setBounds(0, 0, s, s)
    d.draw(c)
    return bmp
}

/** The app's OWN icon, cut into the chosen shape. style: 0 original, 1 themed (mono), 2 grayscale */
fun renderIcon(d: Drawable, s: Int, shape: Int, style: Int): Bitmap {
    val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val o = (s * 0.25f).toInt()
    val gray = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
    if (d is AdaptiveIconDrawable) {
        val mono: Drawable? = if (style == 1 && Build.VERSION.SDK_INT >= 33) d.monochrome else null
        if (mono != null) {
            c.drawColor(0xFFDCE3F2.toInt())
            mono.setTint(0xFF1F2A44.toInt())
            mono.setBounds(-o, -o, s + o, s + o)
            mono.draw(c)
        } else {
            d.background?.apply {
                if (style != 0) colorFilter = gray
                setBounds(-o, -o, s + o, s + o); draw(c)
            }
            d.foreground?.apply {
                if (style != 0) colorFilter = gray
                setBounds(-o, -o, s + o, s + o); draw(c)
            }
        }
    } else {
        c.drawColor(if (style == 0) Color.WHITE else 0xFFDCE3F2.toInt())
        val i = (s * 0.17f).toInt()
        if (style != 0) d.colorFilter = gray
        d.setBounds(i, i, s - i, s - i)
        d.draw(c)
    }
    val mask = shapePath(shape, s.toFloat()).apply { fillType = Path.FillType.INVERSE_WINDING }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    c.drawPath(mask, paint)
    return bmp
}
