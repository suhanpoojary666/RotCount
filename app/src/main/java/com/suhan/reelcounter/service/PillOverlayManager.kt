package com.suhan.reelcounter.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import com.suhan.reelcounter.BuildConfig
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import com.suhan.reelcounter.R

class PillOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var pillView: PillView? = null
    private var isAttached = false
    private var isHiding = false
    private var removeRunnable: Runnable? = null

    // Saved position across show/hide cycles
    private var savedX: Int? = null
    private var savedY: Int? = null
    private var magneticAnimator: ValueAnimator? = null

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        )
    }

    private fun getTopInset(): Int {
        var statusBarHeight = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val windowMetrics = wm.currentWindowMetrics
                val insets = windowMetrics.windowInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()
                )
                statusBarHeight = insets.top
            } catch (ignored: Exception) {}
        }
        if (statusBarHeight <= 0) {
            val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resourceId > 0) {
                statusBarHeight = context.resources.getDimensionPixelSize(resourceId)
            }
        }
        if (statusBarHeight <= 0) {
            statusBarHeight = dpToPx(24f).toInt()
        }
        return statusBarHeight + dpToPx(6f).toInt()
    }

    private fun getBottomInset(): Int {
        var navBarHeight = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val windowMetrics = wm.currentWindowMetrics
                val insets = windowMetrics.windowInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.navigationBars()
                )
                navBarHeight = insets.bottom
            } catch (ignored: Exception) {}
        }
        if (navBarHeight <= 0) {
            val resourceId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
            if (resourceId > 0) {
                navBarHeight = context.resources.getDimensionPixelSize(resourceId)
            }
        }
        if (navBarHeight <= 0) {
            navBarHeight = dpToPx(24f).toInt()
        }
        return navBarHeight + dpToPx(12f).toInt()
    }

    fun showPill(initialCount: Int) {
        val count = initialCount.coerceIn(0, 99999)

        // Cancel any pending removal immediately
        removeRunnable?.let { mainHandler.removeCallbacks(it) }
        removeRunnable = null
        val wasHiding = isHiding
        isHiding = false

        if (wasHiding) {
            pillView?.cancelHideAnimation()
        }

        if (!isAttached || pillView == null) {
            pillView = PillView(context).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setCount(count, animate = false)
            }

            val windowHeight = dpToPx(36f).toInt()
            val metrics = context.resources.displayMetrics
            val defaultWidth = dpToPx(88f).toInt()
            val initialX = savedX ?: ((metrics.widthPixels - defaultWidth) / 2)
            val initialY = savedY ?: getTopInset()

            val params = WindowManager.LayoutParams().apply {
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = windowHeight
                type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                format = PixelFormat.TRANSLUCENT
                gravity = Gravity.TOP or Gravity.START
                x = initialX
                y = initialY
            }

            setupDragAndSnap(pillView!!, params)

            // Keep right-snapped pill aligned when number of digits widens pill
            pillView?.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                val newW = right - left
                val oldW = oldRight - oldLeft
                if (oldW > 0 && newW != oldW && isAttached && pillView != null) {
                    val p = pillView?.layoutParams as? WindowManager.LayoutParams ?: return@addOnLayoutChangeListener
                    val m = context.resources.displayMetrics
                    val minX = dpToPx(12f).toInt()
                    val oldMaxX = (m.widthPixels - oldW - dpToPx(12f).toInt()).coerceAtLeast(minX)
                    val newMaxX = (m.widthPixels - newW - dpToPx(12f).toInt()).coerceAtLeast(minX)
                    if (Math.abs(p.x - oldMaxX) < dpToPx(24f).toInt()) {
                        p.x = newMaxX
                        savedX = newMaxX
                        try {
                            windowManager.updateViewLayout(pillView, p)
                        } catch (ignored: Exception) {}
                    }
                }
            }

            try {
                windowManager.addView(pillView, params)
                isAttached = true
                pillView?.animateShow()
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w("rotcount", "addView failed", e)
                isAttached = false
                pillView = null
            }
        } else {
            pillView?.cancelHideAnimation()
            pillView?.setCount(count, animate = true)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragAndSnap(view: PillView, params: WindowManager.LayoutParams) {
        var startParamsX = 0
        var startParamsY = 0
        var touchStartX = 0f
        var touchStartY = 0f
        var isDragging = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    magneticAnimator?.cancel()
                    startParamsX = params.x
                    startParamsY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchStartX
                    val dy = event.rawY - touchStartY
                    if (!isDragging && (Math.abs(dx) > dpToPx(4f) || Math.abs(dy) > dpToPx(4f))) {
                        isDragging = true
                    }
                    if (isDragging) {
                        val metrics = context.resources.displayMetrics
                        val pillW = view.width.takeIf { it > 0 } ?: dpToPx(88f).toInt()
                        val pillH = view.height.takeIf { it > 0 } ?: dpToPx(36f).toInt()

                        val minX = dpToPx(12f).toInt()
                        val maxX = (metrics.widthPixels - pillW - dpToPx(12f).toInt()).coerceAtLeast(minX)
                        val minY = getTopInset()
                        val maxY = (metrics.heightPixels - pillH - getBottomInset()).coerceAtLeast(minY)

                        params.x = (startParamsX + dx).toInt().coerceIn(minX, maxX)
                        params.y = (startParamsY + dy).toInt().coerceIn(minY, maxY)

                        try {
                            windowManager.updateViewLayout(view, params)
                        } catch (ignored: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        val metrics = context.resources.displayMetrics
                        val pillW = view.width.takeIf { it > 0 } ?: dpToPx(88f).toInt()
                        val pillH = view.height.takeIf { it > 0 } ?: dpToPx(36f).toInt()

                        val minX = dpToPx(12f).toInt()
                        val maxX = (metrics.widthPixels - pillW - dpToPx(12f).toInt()).coerceAtLeast(minX)
                        val minY = getTopInset()
                        val maxY = (metrics.heightPixels - pillH - getBottomInset()).coerceAtLeast(minY)

                        val curX = params.x.coerceIn(minX, maxX)
                        val curY = params.y.coerceIn(minY, maxY)

                        // Magnetic snap to the nearest of all 4 borders
                        val distLeft = Math.abs(curX - minX)
                        val distRight = Math.abs(maxX - curX)
                        val distTop = Math.abs(curY - minY)
                        val distBottom = Math.abs(maxY - curY)

                        val minDist = minOf(distLeft, distRight, distTop, distBottom)

                        val targetX: Int
                        val targetY: Int

                        when (minDist) {
                            distLeft -> {
                                targetX = minX
                                targetY = curY.coerceIn(minY, maxY)
                            }
                            distRight -> {
                                targetX = maxX
                                targetY = curY.coerceIn(minY, maxY)
                            }
                            distTop -> {
                                targetX = curX.coerceIn(minX, maxX)
                                targetY = minY
                            }
                            else -> {
                                targetX = curX.coerceIn(minX, maxX)
                                targetY = maxY
                            }
                        }

                        magneticAnimator?.cancel()
                        val animStartX = curX
                        val animStartY = curY
                        magneticAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                            duration = 260
                            interpolator = OvershootInterpolator(1.1f)
                            addUpdateListener { anim ->
                                val frac = anim.animatedValue as Float
                                params.x = (animStartX + (targetX - animStartX) * frac).toInt()
                                params.y = (animStartY + (targetY - animStartY) * frac).toInt()
                                try {
                                    windowManager.updateViewLayout(view, params)
                                } catch (ignored: Exception) {}
                            }
                            addListener(object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator) {
                                    savedX = targetX
                                    savedY = targetY
                                }
                            })
                            start()
                        }
                    }
                    isDragging = false
                    true
                }
                else -> false
            }
        }
    }

    fun updateCount(newCount: Int) {
        if (isAttached) {
            val count = newCount.coerceIn(0, 99999)
            pillView?.setCount(count, animate = true)
        }
    }

    fun hidePill() {
        if (!isAttached || isHiding) return
        isHiding = true

        val safetyRemove = Runnable {
            if (isHiding) {
                forceRemove()
            }
        }
        removeRunnable = safetyRemove
        mainHandler.postDelayed(safetyRemove, 300)

        pillView?.animateHide {
            if (isHiding) {
                forceRemove()
            }
        }
    }

    private fun forceRemove() {
        removeRunnable?.let { mainHandler.removeCallbacks(it) }
        removeRunnable = null
        if (isAttached) {
            try {
                pillView?.let { windowManager.removeView(it) }
            } catch (ignored: Exception) {}
            isAttached = false
            isHiding = false
            pillView = null
        }
    }

    fun onConfigurationChanged(newConfig: Configuration) {
        if (isAttached && pillView != null) {
            try {
                val params = pillView?.layoutParams as? WindowManager.LayoutParams ?: return
                val metrics = context.resources.displayMetrics
                val pillW = pillView?.width.takeIf { it != null && it > 0 } ?: dpToPx(88f).toInt()
                val pillH = pillView?.height.takeIf { it != null && it > 0 } ?: dpToPx(36f).toInt()
                val minX = dpToPx(12f).toInt()
                val maxX = (metrics.widthPixels - pillW - dpToPx(12f).toInt()).coerceAtLeast(minX)
                val minY = getTopInset()
                val maxY = (metrics.heightPixels - pillH - getBottomInset()).coerceAtLeast(minY)

                val curX = params.x.coerceIn(minX, maxX)
                val curY = params.y.coerceIn(minY, maxY)

                // Re-snap to closest border in new orientation
                val distLeft = Math.abs(curX - minX)
                val distRight = Math.abs(maxX - curX)
                val distTop = Math.abs(curY - minY)
                val distBottom = Math.abs(maxY - curY)

                val minDist = minOf(distLeft, distRight, distTop, distBottom)

                when (minDist) {
                    distLeft -> {
                        params.x = minX
                        params.y = curY
                    }
                    distRight -> {
                        params.x = maxX
                        params.y = curY
                    }
                    distTop -> {
                        params.x = curX
                        params.y = minY
                    }
                    else -> {
                        params.x = curX
                        params.y = maxY
                    }
                }
                savedX = params.x
                savedY = params.y
                windowManager.updateViewLayout(pillView, params)
            } catch (ignored: Exception) {}
        }
    }

    fun destroy() {
        forceRemove()
    }

    /**
     * PillView renders the sleek floating pill:
     * - Solid #000000 capsule, 36dp high, corner radius 18dp.
     * - Left pad: 14dp, Reel symbol: 16dp x 16dp, 8dp gap, animated roll number, 14dp right pad.
     * - Swipe / vertical roll ticker transition on count changes (slides up & out / slides in from below).
     * - Zero horizontal line across the pill.
     */
    @SuppressLint("ViewConstructor")
    private class PillView(context: Context) : View(context) {

        private val density = context.resources.displayMetrics.density
        private fun dp(v: Float) = v * density
        private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, context.resources.displayMetrics)

        private val pillHeight = dp(36f)
        private val cornerRadius = dp(18f)
        private val minWidth = dp(88f)
        private val horizontalPadding = dp(14f)
        private val iconSize = dp(16f)
        private val iconTextGap = dp(8f)

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }

        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = sp(15f)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                fontFeatureSettings = "tnum"
            }
        }

        private val reelIconDrawable = ContextCompat.getDrawable(context, R.drawable.ic_reel_symbol)

        private var currentCount = 0
        private var currentCountStr = "0"
        private var previousCountStr = "0"

        private var rollProgress = 1f
        private var rollAnimator: ValueAnimator? = null

        private fun isAnimationEnabled(): Boolean {
            return try {
                val duration = Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f
                )
                duration > 0f
            } catch (e: Exception) {
                true
            }
        }

        fun setCount(count: Int, animate: Boolean) {
            val safeCount = count.coerceIn(0, 99999)
            if (safeCount == currentCount && rollProgress >= 1f) return

            previousCountStr = currentCount.toString()
            currentCount = safeCount
            currentCountStr = currentCount.toString()

            requestLayout()

            if (!animate || !isAnimationEnabled()) {
                rollProgress = 1f
                invalidate()
                return
            }

            rollAnimator?.cancel()
            rollProgress = 0f
            rollAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 220
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    rollProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        fun animateShow() {
            if (!isAnimationEnabled()) {
                alpha = 1f
                scaleX = 1f
                scaleY = 1f
                visibility = VISIBLE
                return
            }
            alpha = 0f
            scaleX = 0.8f
            scaleY = 0.8f
            visibility = VISIBLE
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(350)
                .setInterpolator(OvershootInterpolator(1.2f))
                .setListener(null)
                .start()
        }

        fun animateHide(onEnd: () -> Unit) {
            animate().setListener(null)
            animate().cancel()
            if (!isAnimationEnabled()) {
                visibility = GONE
                onEnd()
                return
            }
            animate()
                .alpha(0f)
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(200)
                .setInterpolator(DecelerateInterpolator())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        animate().setListener(null)
                        onEnd()
                    }
                })
                .start()
        }

        fun cancelHideAnimation() {
            animate().setListener(null)
            animate().cancel()
            alpha = 1f
            scaleX = 1f
            scaleY = 1f
            visibility = VISIBLE
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val textWidth = Math.max(
                textPaint.measureText(currentCountStr),
                textPaint.measureText(previousCountStr)
            )
            // Ensure number space is at least 26dp wide so single digits have balanced breathing room
            val numberSpace = Math.max(dp(26f), textWidth)
            val contentWidth = horizontalPadding * 2 + iconSize + iconTextGap + numberSpace
            val measuredWidth = Math.max(minWidth, contentWidth).toInt()
            val measuredHeight = pillHeight.toInt()
            setMeasuredDimension(measuredWidth, measuredHeight)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val w = width.toFloat()
            val h = height.toFloat()

            // 1. Solid black capsule (no border, no line)
            val capsuleRect = RectF(0f, 0f, w, h)
            canvas.drawRoundRect(capsuleRect, cornerRadius, cornerRadius, bgPaint)

            // 2. Reel clapperboard icon
            val iconX = horizontalPadding
            val iconY = (h - iconSize) / 2f
            reelIconDrawable?.let { drawable ->
                drawable.setBounds(
                    iconX.toInt(),
                    iconY.toInt(),
                    (iconX + iconSize).toInt(),
                    (iconY + iconSize).toInt()
                )
                drawable.draw(canvas)
            }

            // 3. Number space bounds: centered horizontally between icon gap and right padding
            val numberSpaceLeft = iconX + iconSize + iconTextGap
            val numberSpaceRight = w - horizontalPadding
            val numberCenterX = (numberSpaceLeft + numberSpaceRight) / 2f

            val fontMetrics = textPaint.fontMetrics
            val baseline = (h - (fontMetrics.descent + fontMetrics.ascent)) / 2f
            val lineHeight = fontMetrics.bottom - fontMetrics.top

            canvas.save()
            // Clip to number space window inside the capsule
            canvas.clipRect(numberSpaceLeft - dp(2f), 0f, w - dp(2f), h)

            if (rollProgress < 1f) {
                // Previous number slides up and out
                val oldY = baseline - (rollProgress * lineHeight)
                val oldAlpha = ((1f - rollProgress) * 255).toInt().coerceIn(0, 255)
                textPaint.alpha = oldAlpha
                canvas.drawText(previousCountStr, numberCenterX, oldY, textPaint)

                // New number slides in from below
                val newY = baseline + ((1f - rollProgress) * lineHeight)
                val newAlpha = (rollProgress * 255).toInt().coerceIn(0, 255)
                textPaint.alpha = newAlpha
                canvas.drawText(currentCountStr, numberCenterX, newY, textPaint)

                textPaint.alpha = 255
            } else {
                // Settled state: centered in number space
                textPaint.alpha = 255
                canvas.drawText(currentCountStr, numberCenterX, baseline, textPaint)
            }

            canvas.restore()
        }
    }
}
