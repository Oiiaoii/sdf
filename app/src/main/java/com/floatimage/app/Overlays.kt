package com.floatimage.app

import android.animation.*
import android.content.Context
import android.graphics.*
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.animation.*
import android.widget.FrameLayout
import android.widget.ImageView
import java.io.File
import kotlin.math.*

object Overlays {
    class Item(val id: Int, val file: File, val dens: Float) {
        lateinit var root: FrameLayout
        lateinit var holder: FrameLayout
        lateinit var img: ImageView
        lateinit var lp: WindowManager.LayoutParams
        var anim = "float"; var inFx = "pop"; var outFx = "fade"
        var alpha = 1f; var locked = false; var flip = 1f; var speed = 1f
        var loop: ValueAnimator? = null
        var fx: ValueAnimator? = null
        var removing = false
        var hx = 0; var hy = 0
        val clear = Runnable { root.background = null }
    }

    val items = mutableListOf<Item>()
    var sel: Item? = null
    var hidden = false
    var onChange: (() -> Unit)? = null
    private var next = 1
    private val h = Handler(Looper.getMainLooper())

    private fun wm(c: Context) = c.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    fun add(c: Context, src: File, from: Item? = null): Item {
        val ctx = c.applicationContext
        val dm = ctx.resources.displayMetrics
        val dir = File(ctx.filesDir, "stickers").apply { mkdirs() }
        val id = next++
        val f = File(dir, "s$id")
        src.copyTo(f, true)
        val item = Item(id, f, dm.density)
        val size = from?.lp?.width ?: (dm.widthPixels * 0.45f).toInt()
        item.root = FrameLayout(ctx)
        item.holder = FrameLayout(ctx)
        item.img = ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        item.holder.addView(item.img, FrameLayout.LayoutParams(-1, -1))
        item.root.addView(item.holder, FrameLayout.LayoutParams(-1, -1))
        item.lp = WindowManager.LayoutParams(
            size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (from?.lp?.x ?: (20 + 40 * ((id - 1) % 6))) + (if (from != null) 50 else 0)
            y = (from?.lp?.y ?: (260 + 60 * ((id - 1) % 6))) + (if (from != null) 50 else 0)
        }
        if (from != null) {
            item.anim = from.anim; item.inFx = from.inFx; item.outFx = from.outFx
            item.alpha = from.alpha; item.flip = from.flip; item.speed = from.speed
            item.holder.rotation = from.holder.rotation
        }
        item.holder.scaleX = item.flip
        loadImage(item)
        pad(item)
        attachTouch(ctx, item)
        item.root.alpha = 0f
        wm(ctx).addView(item.root, item.lp)
        items.add(item)
        setAnim(item, item.anim)
        select(item)
        runFx(ctx, item, item.inFx, true) {}
        return item
    }

    private fun loadImage(item: Item) {
        try {
            val d = ImageDecoder.decodeDrawable(ImageDecoder.createSource(item.file)) { dec, info, _ ->
                val m = max(info.size.width, info.size.height)
                if (m > 1200) dec.setTargetSampleSize(m / 1200 + 1)
            }
            item.img.setImageDrawable(d)
            if (d is AnimatedImageDrawable) { d.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE; d.start() }
        } catch (e: Exception) { }
    }

    private fun pad(item: Item) {
        val q = (item.lp.width * 0.16f).toInt()
        item.root.setPadding(q, q, q, q)
    }

    fun select(item: Item?) {
        sel = item
        if (item != null) {
            item.root.background = GradientDrawable().apply { setStroke(5, Color.parseColor("#5FD4FF"), 18f, 10f); cornerRadius = 16f }
            h.removeCallbacks(item.clear)
            h.postDelayed(item.clear, 2500)
        }
        onChange?.invoke()
    }

    fun resize(ctx: Context, item: Item, ns: Int) {
        val l = item.lp
        val s = ns.coerceIn(240, 2400)
        l.x -= (s - l.width) / 2
        l.y -= (s - l.height) / 2
        l.width = s; l.height = s
        pad(item)
        try { wm(ctx).updateViewLayout(item.root, l) } catch (e: Exception) { }
    }

    private fun angle(e: MotionEvent) =
        Math.toDegrees(atan2((e.getY(1) - e.getY(0)).toDouble(), (e.getX(1) - e.getX(0)).toDouble())).toFloat()

    private fun attachTouch(ctx: Context, item: Item) {
        val sd = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                resize(ctx, item, (item.lp.width * d.scaleFactor).toInt()); return true
            }
        })
        val gd = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) { remove(ctx, item) }
            override fun onDoubleTap(e: MotionEvent): Boolean { replay(ctx, item); return true }
        })
        var lx = 0f; var ly = 0f; var skip = false; var prev = Float.NaN
        item.root.setOnTouchListener { _, e ->
            if (item.removing) return@setOnTouchListener true
            sd.onTouchEvent(e); gd.onTouchEvent(e)
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    select(item); lx = e.rawX; ly = e.rawY; skip = false; prev = Float.NaN
                    if (item.fx?.isRunning != true) item.root.animate().scaleX(1.06f).scaleY(1.06f).setDuration(120).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!item.removing && item.fx?.isRunning != true)
                        item.root.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(OvershootInterpolator(3f)).start()
                }
                MotionEvent.ACTION_POINTER_DOWN -> { skip = true; prev = if (e.pointerCount == 2) angle(e) else Float.NaN }
                MotionEvent.ACTION_POINTER_UP -> { skip = true; prev = Float.NaN }
                MotionEvent.ACTION_MOVE -> {
                    if (e.pointerCount == 2) {
                        val a = angle(e)
                        if (!prev.isNaN()) {
                            var d = a - prev
                            if (d > 180f) d -= 360f
                            if (d < -180f) d += 360f
                            item.holder.rotation += d
                        }
                        prev = a
                    } else if (e.pointerCount == 1 && !sd.isInProgress) {
                        if (skip) skip = false else {
                            item.lp.x += (e.rawX - lx).toInt()
                            item.lp.y += (e.rawY - ly).toInt()
                            try { wm(ctx).updateViewLayout(item.root, item.lp) } catch (x: Exception) { }
                        }
                        lx = e.rawX; ly = e.rawY
                    }
                }
            }
            true
        }
    }

    /* ---------- enter / exit effects ---------- */
    private fun runFx(ctx: Context, item: Item, kind: String, enter: Boolean, end: () -> Unit) {
        val dm = ctx.resources.displayMetrics
        val l = item.lp
        val w = wm(ctx)
        val old = item.fx
        if (old != null && old.isRunning) { old.removeAllListeners(); old.cancel(); l.x = item.hx; l.y = item.hy }
        else { item.hx = l.x; item.hy = l.y }
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = if (kind == "drop" || kind == "fall") 800L else if (kind == "elastic") 1000L else 600L
        a.interpolator = when (kind) {
            "pop" -> if (enter) OvershootInterpolator(2f) else AccelerateInterpolator()
            "drop" -> BounceInterpolator()
            "fall" -> AccelerateInterpolator()
            "elastic" -> TimeInterpolator { t ->
                if (t <= 0f) 0f else if (t >= 1f) 1f
                else (2.0.pow(-10.0 * t) * sin((t - 0.075) * (2.0 * PI) / 0.3) + 1.0).toFloat()
            }
            else -> DecelerateInterpolator()
        }
        fun step(p: Float) {
            val v = if (enter) p else 1f - p
            val r = item.root
            r.scaleX = 1f; r.scaleY = 1f; r.rotation = 0f; r.rotationY = 0f; r.alpha = item.alpha; r.cameraDistance = 9000f * item.dens
            l.x = item.hx; l.y = item.hy
            when (kind) {
                "pop", "elastic" -> { r.scaleX = v; r.scaleY = v }
                "zoom" -> { val z = 1f + .8f * (1f - v); r.scaleX = z; r.scaleY = z; r.alpha = item.alpha * v.coerceIn(0f, 1f) }
                "flip" -> { r.rotationY = 90f * (1f - v); r.alpha = item.alpha * v.coerceIn(0f, 1f) }
                "fade" -> r.alpha = item.alpha * v.coerceIn(0f, 1f)
                "spin" -> { r.rotation = -540f * (1f - v); r.scaleX = v; r.scaleY = v }
                "slide" -> l.x = if (enter) (dm.widthPixels + (item.hx - dm.widthPixels) * v).toInt()
                           else (item.hx + (-l.width - item.hx) * (1f - v)).toInt()
                "drop", "fall" -> l.y = if (enter) (-l.height + (item.hy + l.height) * v).toInt()
                           else (item.hy + (dm.heightPixels - item.hy) * (1f - v)).toInt()
            }
            try { w.updateViewLayout(r, l) } catch (x: Exception) { }
        }
        a.addUpdateListener { step(it.animatedValue as Float) }
        a.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(an: Animator) { if (enter) step(1f); end() }
        })
        item.fx = a
        step(0f)
        a.start()
    }

    fun replay(ctx: Context, item: Item) { if (!item.removing) runFx(ctx, item, item.inFx, true) {} }

    fun remove(ctx: Context, item: Item) {
        if (item.removing) return
        item.removing = true
        item.loop?.cancel()
        runFx(ctx, item, item.outFx, false) {
            try { wm(ctx).removeView(item.root) } catch (e: Exception) { }
            items.remove(item); item.file.delete()
            if (sel === item) sel = items.lastOrNull()
            onChange?.invoke()
        }
    }

    fun removeAll(c: Context) {
        val w = wm(c.applicationContext)
        items.toList().forEach {
            it.loop?.cancel(); it.fx?.removeAllListeners(); it.fx?.cancel()
            try { w.removeView(it.root) } catch (e: Exception) { }
            it.file.delete()
        }
        items.clear(); sel = null
        onChange?.invoke()
    }

    /* ---------- misc controls ---------- */
    fun setLock(ctx: Context, item: Item, on: Boolean) {
        item.locked = on
        val l = item.lp
        l.flags = if (on) l.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        else l.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        try { wm(ctx).updateViewLayout(item.root, l) } catch (e: Exception) { }
    }

    fun front(ctx: Context, item: Item) {
        try { wm(ctx).removeView(item.root); wm(ctx).addView(item.root, item.lp) } catch (e: Exception) { }
        items.remove(item); items.add(item)
    }

    fun setHidden(b: Boolean) {
        hidden = b
        items.forEach { it.root.visibility = if (b) View.INVISIBLE else View.VISIBLE }
        onChange?.invoke()
    }

    /* ---------- looping animations ---------- */
    private fun hue(deg: Float): ColorMatrixColorFilter {
        val r = Math.toRadians(deg.toDouble()); val c = cos(r).toFloat(); val s = sin(r).toFloat()
        return ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
            .213f + c * .787f - s * .213f, .715f - c * .715f - s * .715f, .072f - c * .072f + s * .928f, 0f, 0f,
            .213f - c * .213f + s * .143f, .715f + c * .285f + s * .140f, .072f - c * .072f - s * .283f, 0f, 0f,
            .213f - c * .213f - s * .787f, .715f - c * .715f + s * .715f, .072f + c * .928f + s * .072f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f)))
    }

    fun setAnim(item: Item, name: String) {
        item.anim = name
        item.loop?.cancel()
        val v = item.img
        v.translationX = 0f; v.translationY = 0f; v.rotation = 0f; v.scaleX = 1f; v.scaleY = 1f; v.alpha = 1f
        v.clearColorFilter()
        if (name == "none") return
        val dur = when (name) {
            "float" -> 2600L; "pulse" -> 1200L; "spin" -> 4000L; "shake" -> 600L; "bounce" -> 900L
            "wobble" -> 1600L; "heartbeat" -> 1200L; "flash" -> 1000L; "orbit" -> 3000L
            "rainbow" -> 3500L; "swing" -> 2200L; "tada" -> 1800L; "breathe" -> 3200L
            "glitch" -> 1500L; "sparkle" -> 1400L; "wave" -> 2400L; else -> 800L
        }
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = (dur / item.speed).toLong().coerceAtLeast(150L)
        a.repeatCount = ValueAnimator.INFINITE
        a.repeatMode = ValueAnimator.RESTART
        a.interpolator = LinearInterpolator()
        a.addUpdateListener {
            val t = it.animatedValue as Float
            val ph = (2.0 * PI * t).toFloat()
            val d = min(14f * item.dens, item.lp.width * 0.12f)
            when (name) {
                "float" -> { v.translationY = -d * sin(ph); v.rotation = 2.5f * sin(ph + 1f) }
                "pulse" -> { val s = 1f + .12f * (1f - cos(ph)) / 2f; v.scaleX = s; v.scaleY = s }
                "spin" -> v.rotation = 360f * t
                "shake" -> v.translationX = d * .5f * sin(ph * 6f)
                "bounce" -> {
                    val b = abs(sin(ph / 2f)); val q = (1f - b).pow(6)
                    v.translationY = -d * 1.2f * b; v.scaleY = 1f - .15f * q; v.scaleX = 1f + .15f * q
                }
                "wobble" -> v.rotation = 10f * sin(ph)
                "heartbeat" -> { val k = max(0f, sin(ph * 2f)); val s = 1f + .18f * k * k; v.scaleX = s; v.scaleY = s }
                "flash" -> v.alpha = .6f + .4f * cos(ph)
                "orbit" -> { v.translationX = d * cos(ph); v.translationY = d * sin(ph) }
                "rainbow" -> v.setColorFilter(hue(360f * t))
                "swing" -> { v.rotation = 12f * sin(ph); v.translationX = d * .7f * sin(ph) }
                "tada" -> if (t < .5f) {
                    val k = t / .5f; val s = 1f + .12f * sin(PI.toFloat() * k)
                    v.scaleX = s; v.scaleY = s; v.rotation = 6f * sin(PI.toFloat() * 6f * k) * (1f - k)
                } else { v.scaleX = 1f; v.scaleY = 1f; v.rotation = 0f }
                "breathe" -> { val s = 1f + .06f * (1f - cos(ph)); v.scaleX = s; v.scaleY = s; v.alpha = .8f + .1f * (1f - cos(ph)) }
                "glitch" -> if (t % .25f < .04f) {
                    v.translationX = (kotlin.random.Random.nextFloat() - .5f) * d * 1.2f
                    v.setColorFilter(hue(kotlin.random.Random.nextFloat() * 70f))
                } else { v.translationX = 0f; v.clearColorFilter() }
                "sparkle" -> { val s = 1f + .08f * sin(ph * 2f); v.scaleX = s; v.scaleY = s; v.alpha = .75f + .25f * abs(sin(ph * 3f)); v.rotation = 6f * sin(ph) }
                "wave" -> { v.rotation = 7f * sin(ph); v.translationY = -d * .5f * sin(ph * 2f); v.translationX = d * .4f * sin(ph) }
                "jelly" -> { v.scaleX = 1f + .1f * sin(ph); v.scaleY = 1f - .1f * sin(ph) }
            }
        }
        item.loop = a
        a.start()
    }
}
