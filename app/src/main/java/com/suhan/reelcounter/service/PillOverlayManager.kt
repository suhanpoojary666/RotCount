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
import android.util.TypedValue
import android.view.Gravity
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

            val params = WindowManager.LayoutParams().apply {
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = windowHeight
                type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                format = PixelFormat.TRANSLUCENT
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = getTopInset()
            }

            try {
                windowManager.addView(pillView, params)
                isAttached = true
                pillView?.animateShow()
            } catch (e: Exception) {
                e.printStackTrace()
                isAttached = false
                pillView = null
            }
        } else {
            pillView?.cancelHideAnimation()
            pillView?.setCount(count, animate = true)
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
                val params = pillView?.layoutParams as? WindowManager.LayoutParams
                if (params != null) {
                    params.y = getTopInset()
                    windowManager.updateViewLayout(pillView, params)
                }
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
