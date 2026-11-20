package com.floatimage.app

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.*
import java.io.File

class MainActivity : Activity() {
    private val BG = Color.parseColor("#0F0D18")
    private val CARD = Color.parseColor("#1A1727")
    private val CARD2 = Color.parseColor("#26223A")
    private val PINK = Color.parseColor("#FF5FA2")
    private val VIOLET = Color.parseColor("#8B5CF6")
    private val MUTED = Color.parseColor("#A59FC4")
    private val INS = listOf("pop", "elastic", "zoom", "flip", "spin", "slide", "drop", "fade")
    private val OUTS = listOf("fade", "pop", "zoom", "flip", "spin", "slide", "fall")
    private val ANIMS = listOf("none", "float", "pulse", "spin", "shake", "bounce", "wobble", "heartbeat", "flash",
        "orbit", "rainbow", "jelly", "swing", "tada", "breathe", "glitch", "sparkle", "wave")

    private lateinit var status: TextView
    private lateinit var thumbs: LinearLayout
    private lateinit var sizeBar: SeekBar
    private lateinit var opBar: SeekBar
    private lateinit var speedBar: SeekBar
    private lateinit var rotBar: SeekBar
    private lateinit var lockTile: Tile
    private lateinit var hideTile: Tile
    private lateinit var animChips: Chips
    private lateinit var inChips: Chips
    private lateinit var outChips: Chips
    private val cache = HashMap<Int, Bitmap>()

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()
    private fun rr(color: Int, r: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(r).toFloat() }
    private fun grad(r: Int) = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(PINK, VIOLET))
        .apply { cornerRadius = dp(r).toFloat() }

    private fun pressFx(v: View) {
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.animate().scaleX(.93f).scaleY(.93f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).setDuration(240).setInterpolator(OvershootInterpolator(3f)).start()
            }
            false
        }
    }

    private inner class Tile(icon: Int, label: String, f: () -> Unit) {
        val iv = ImageView(this@MainActivity).apply { setImageResource(icon); setColorFilter(Color.WHITE) }
        val tv = TextView(this@MainActivity).apply { text = label; textSize = 11f; setTextColor(Color.WHITE); gravity = Gravity.CENTER }
        val view = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = rr(CARD2, 16); setPadding(0, dp(11), 0, dp(11))
            addView(iv, LinearLayout.LayoutParams(dp(24), dp(24)))
            addView(tv, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(4) })
            setOnClickListener { f() }
        }
        init { pressFx(view) }
        fun set(icon: Int, label: String) { iv.setImageResource(icon); tv.text = label }
    }

    private inner class Chips(labels: List<String>, onPick: (Int) -> Unit) {
        val views = mutableListOf<TextView>()
        val scroll = HorizontalScrollView(this@MainActivity).apply { isHorizontalScrollBarEnabled = false }
        init {
            val row = LinearLayout(this@MainActivity)
            labels.forEachIndexed { i, l ->
                val tv = TextView(this@MainActivity).apply {
                    text = l.replaceFirstChar { it.uppercase() }; textSize = 13f
                    setPadding(dp(14), dp(8), dp(14), dp(8))
                    setOnClickListener { onPick(i) }
                }
                pressFx(tv); views.add(tv)
                row.addView(tv, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) })
            }
            scroll.addView(row); mark(-1)
        }
        fun mark(sel: Int) {
            views.forEachIndexed { i, tv ->
                if (i == sel) { tv.background = grad(20); tv.setTextColor(Color.WHITE) }
                else { tv.background = rr(CARD2, 20); tv.setTextColor(MUTED) }
            }
        }
    }

    private fun label(t: String, sz: Float = 13f, c: Int = MUTED, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = sz; setTextColor(c); setPadding(0, dp(8), 0, dp(6))
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun card(title: String, vararg v: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rr(CARD, 22); setPadding(dp(16), dp(12), dp(16), dp(16))
        addView(label(title, 14f, Color.WHITE, true))
        v.forEach { addView(it) }
    }

    private fun tileRow(vararg t: Tile) = LinearLayout(this).apply {
        t.forEach { addView(it.view, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) }) }
    }

    private fun seek(max: Int, onUser: (Int) -> Unit, onStop: () -> Unit = {}) = SeekBar(this).apply {
        this.max = max
        progressTintList = ColorStateList.valueOf(PINK)
        thumbTintList = ColorStateList.valueOf(PINK)
        progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#3A3552"))
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { if (u) onUser(p) }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) { onStop() }
        })
    }

    private fun cur(): Overlays.Item? {
        val s = Overlays.sel
        if (s == null) Toast.makeText(this, "Add or tap a floating image first", Toast.LENGTH_SHORT).show()
        return s
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        val ctx = applicationContext
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(28)); setBackgroundColor(BG)
        }
        val blocks = mutableListOf<View>()
        fun put(v: View) { blocks.add(v); col.addView(v, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }) }

        // header
        status = label("", 14f, Color.parseColor("#F2EEFF"))
        put(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = grad(26); setPadding(dp(16), dp(14), dp(18), dp(14))
            addView(ImageView(this@MainActivity).apply { setImageResource(R.drawable.ic_launcher_fg) }, LinearLayout.LayoutParams(dp(68), dp(68)))
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(8), 0, 0, 0)
                addView(TextView(this@MainActivity).apply { text = "Float Image"; textSize = 28f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD })
                addView(status)
            })
        })

        // add button + app actions
        val addBtn = LinearLayout(this).apply {
            gravity = Gravity.CENTER; background = grad(18); setPadding(0, dp(16), 0, dp(16))
            addView(ImageView(this@MainActivity).apply { setImageResource(R.drawable.ic_image); setColorFilter(Color.WHITE) }, LinearLayout.LayoutParams(dp(24), dp(24)))
            addView(TextView(this@MainActivity).apply { text = "Add image or GIF"; textSize = 17f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; setPadding(dp(10), 0, 0, 0) })
            setOnClickListener {
                if (ready()) startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true), 7)
            }
        }
        pressFx(addBtn)
        hideTile = Tile(R.drawable.ic_eye, "Hide all") { Overlays.setHidden(!Overlays.hidden) }
        put(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(addBtn, LinearLayout.LayoutParams(-1, -2))
            addView(tileRow(
                Tile(R.drawable.ic_shield, "Allow overlay") {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                },
                hideTile,
                Tile(R.drawable.ic_close, "Stop all") {
                    Overlays.removeAll(ctx); stopService(Intent(this@MainActivity, OverlayService::class.java))
                },
                Tile(R.drawable.ic_home, "Go live") { moveTaskToBack(true) }
            ), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        })

        // stickers
        val em = LinearLayout(this)
        listOf("❤️", "🔥", "⭐", "😂", "👑", "💎", "🎉", "👀", "✨", "🚀").forEach { e ->
            val tv = TextView(this).apply {
                text = e; textSize = 26f; gravity = Gravity.CENTER; background = rr(CARD2, 26)
                setOnClickListener { if (ready()) addEmoji(e) }
            }
            pressFx(tv)
            em.addView(tv, LinearLayout.LayoutParams(dp(54), dp(54)).apply { marginEnd = dp(8) })
        }
        put(card("Quick stickers", HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(em) }))

        // floating now
        thumbs = LinearLayout(this)
        put(card("Floating now", label("Tap one to edit it", 12f), HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(thumbs) }))

        // edit
        lockTile = Tile(R.drawable.ic_lock, "Lock") { cur()?.let { Overlays.setLock(ctx, it, !it.locked); refresh() } }
        put(card("Edit selected",
            tileRow(
                Tile(R.drawable.ic_flip, "Flip") { cur()?.let { it.flip *= -1f; it.holder.animate().scaleX(it.flip).setDuration(220).start() } },
                Tile(R.drawable.ic_copy, "Copy") { cur()?.let { Overlays.add(ctx, it.file, it) } },
                Tile(R.drawable.ic_front, "Front") { cur()?.let { Overlays.front(ctx, it) } },
                lockTile
            ),
            tileRow(
                Tile(R.drawable.ic_play, "Replay") { cur()?.let { Overlays.replay(ctx, it) } },
                Tile(R.drawable.ic_reset, "Reset") {
                    cur()?.let {
                        it.holder.rotation = 0f; it.flip = 1f; it.holder.scaleX = 1f
                        it.alpha = 1f; it.root.alpha = 1f; it.speed = 1f
                        Overlays.resize(ctx, it, (resources.displayMetrics.widthPixels * 0.45f).toInt())
                        Overlays.setAnim(it, it.anim); refresh()
                    }
                },
                Tile(R.drawable.ic_trash, "Delete") { cur()?.let { Overlays.remove(ctx, it) } }
            ).apply { setPadding(0, dp(6), 0, 0) }
        ))

        // animation
        animChips = Chips(ANIMS) { i -> cur()?.let { Overlays.setAnim(it, ANIMS[i]); refresh() } }
        speedBar = seek(30, { p -> Overlays.sel?.speed = .25f + p * .075f }, { Overlays.sel?.let { Overlays.setAnim(it, it.anim) } })
        put(card("Looping animation", animChips.scroll, label("Speed"), speedBar))

        // look
        sizeBar = seek(100, { p -> Overlays.sel?.let { Overlays.resize(ctx, it, 240 + (p * 17.6f).toInt()) } })
        opBar = seek(90, { p -> Overlays.sel?.let { it.alpha = (p + 10) / 100f; it.root.alpha = it.alpha } })
        rotBar = seek(360, { p -> Overlays.sel?.let { it.holder.rotation = (p - 180).toFloat() } })
        put(card("Look", label("Size"), sizeBar, label("Opacity"), opBar, label("Rotation"), rotBar))

        // effects
        inChips = Chips(INS) { i -> cur()?.let { it.inFx = INS[i]; Overlays.replay(ctx, it); refresh() } }
        outChips = Chips(OUTS) { i -> cur()?.let { it.outFx = OUTS[i]; refresh(); Toast.makeText(this, "Plays when you delete it", Toast.LENGTH_SHORT).show() } }
        put(card("Enter and exit effects", label("Enter"), inChips.scroll, label("Exit"), outChips.scroll))

        put(label("On screen: drag with one finger, pinch to resize, twist with two fingers to rotate. Double-tap replays the enter effect, long-press deletes. Lock makes an image click-through.", 12f))

        setContentView(ScrollView(this).apply { setBackgroundColor(BG); addView(col) })
        blocks.forEachIndexed { i, v ->
            v.alpha = 0f; v.translationY = dp(36).toFloat()
            v.animate().alpha(1f).translationY(0f).setStartDelay(i * 70L).setDuration(420).setInterpolator(DecelerateInterpolator(2f)).start()
        }
    }

    private fun ready(): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow display over other apps first", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return false
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        startForegroundService(Intent(this, OverlayService::class.java))
        return true
    }

    private fun addEmoji(e: String) {
        val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 380f; textAlign = Paint.Align.CENTER }
        Canvas(bmp).drawText(e, 256f, 256f - (paint.descent() + paint.ascent()) / 2f, paint)
        val f = File(cacheDir, "emoji.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Overlays.add(applicationContext, f)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req != 7 || res != RESULT_OK || data == null) return
        val uris = data.clipData?.let { c -> (0 until c.itemCount).map { c.getItemAt(it).uri } } ?: listOfNotNull(data.data)
        val tmp = File(cacheDir, "pick.tmp")
        for (u in uris) {
            try {
                contentResolver.openInputStream(u)?.use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
                Overlays.add(applicationContext, tmp)
            } catch (e: Exception) {
                Toast.makeText(this, "Could not read one of the files", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun thumb(f: File): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = maxOf(1, maxOf(o.outWidth, o.outHeight) / 220) })
    } catch (e: Exception) { null }

    private fun refresh() {
        val n = Overlays.items.size
        status.text = if (n == 0) "Allow overlay, then add an image or GIF." else "$n floating. Open any app and it stays on top."
        thumbs.removeAllViews()
        if (n == 0) thumbs.addView(label("Nothing yet", 13f))
        Overlays.items.forEach { it0 ->
            val bmp = cache.getOrPut(it0.id) { thumb(it0.file) ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888) }
            val iv = ImageView(this).apply {
                setImageBitmap(bmp); scaleType = ImageView.ScaleType.CENTER_CROP
                val on = Overlays.sel === it0
                background = rr(if (on) PINK else CARD2, 16)
                setPadding(dp(3), dp(3), dp(3), dp(3))
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, dp(16).toFloat()) }
                }
                clipToOutline = true
                setOnClickListener { Overlays.select(it0) }
            }
            pressFx(iv)
            thumbs.addView(iv, LinearLayout.LayoutParams(dp(72), dp(72)).apply { marginEnd = dp(10) })
        }
        val s = Overlays.sel
        animChips.mark(if (s == null) -1 else ANIMS.indexOf(s.anim))
        inChips.mark(if (s == null) -1 else INS.indexOf(s.inFx))
        outChips.mark(if (s == null) -1 else OUTS.indexOf(s.outFx))
        if (s != null) {
            sizeBar.progress = ((s.lp.width - 240) / 17.6f).toInt().coerceIn(0, 100)
            opBar.progress = (s.alpha * 100f - 10f).toInt().coerceIn(0, 90)
            var r = ((s.holder.rotation % 360f) + 360f) % 360f
            if (r > 180f) r -= 360f
            rotBar.progress = (r + 180f).toInt().coerceIn(0, 360)
            speedBar.progress = ((s.speed - .25f) / .075f).toInt().coerceIn(0, 30)
            if (s.locked) lockTile.set(R.drawable.ic_unlock, "Unlock") else lockTile.set(R.drawable.ic_lock, "Lock")
        }
        hideTile.tv.text = if (Overlays.hidden) "Show all" else "Hide all"
    }

    override fun onResume() { super.onResume(); Overlays.onChange = { refresh() }; refresh() }
    override fun onPause() { super.onPause(); Overlays.onChange = null }
}
