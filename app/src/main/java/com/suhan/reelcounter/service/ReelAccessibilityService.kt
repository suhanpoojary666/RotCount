package com.suhan.reelcounter.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import com.suhan.reelcounter.BuildConfig
import com.suhan.reelcounter.ReelCounterApplication
import com.suhan.reelcounter.data.CountStore
import com.suhan.reelcounter.data.ReelPreferences
import com.suhan.reelcounter.logic.ReelProgress
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ReelAccessibilityService : AccessibilityService() {

    private val serviceExceptionHandler = CoroutineExceptionHandler { _, t ->
        if (BuildConfig.DEBUG) Log.w("rotcount", "Service scope error", t)
    }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + serviceExceptionHandler)
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var countStore: CountStore
    private lateinit var preferences: ReelPreferences
    private lateinit var pillManager: PillOverlayManager

    private var todayCountJob: Job? = null
    private var prefsCollectorJob: Job? = null

    // State machine: IDLE or REEL(packageName)
    private sealed class ServiceMode {
        object IDLE : ServiceMode()
        data class REEL(val packageName: String) : ServiceMode()
    }
    private var currentMode: ServiceMode = ServiceMode.IDLE

    // Ignore list: com.android.systemui, android, this app, installed keyboards
    private val keyboardPackages = mutableSetOf<String>()
    private val standardIgnoreList = setOf("com.android.systemui", "android")

    // Timers
    private var saveTimerRunnable: Runnable? = null
    private var watchdogRunnable: Runnable? = null
    private var resyncDebounceRunnable: Runnable? = null
    private var settleRunnable: Runnable? = null
    private var inactivityResetRunnable: Runnable? = null

    // Counting state
    private val reelProgress = ReelProgress()
    private var lastSettledPosition = 0
    private var furthestPosition = -1
    private var pendingCandidatePosition = 0
    private var lastItemCount = -1
    private val lastPackagePositions = mutableMapOf<String, Int>()
    private var isAwaitingInitialPosition = false

    // BroadcastReceiver for Screen Off and User Present
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // Hide trigger 2: Screen off
                    lastPackagePositions.clear()
                    reelProgress.reset()
                    lastSettledPosition = -1
                    furthestPosition = -1
                    isAwaitingInitialPosition = true
                    lastItemCount = -1
                    cancelSettleTimer()
                    transitionToIdle()
                }
                Intent.ACTION_USER_PRESENT -> {
                    // Resync 800ms after unlock
                    mainHandler.postDelayed({ resync() }, 800)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val app = applicationContext as ReelCounterApplication
        countStore = app.countStore
        preferences = app.preferences
        pillManager = PillOverlayManager(this)

        // Observe live count from CountStore
        todayCountJob = serviceScope.launch {
            countStore.todayCount.collectLatest { count ->
                if (currentMode is ServiceMode.REEL && preferences.showCounter.value) {
                    pillManager.updateCount(count)
                }
            }
        }

        // Observe preference changes: Show counter toggle
        prefsCollectorJob = serviceScope.launch {
            launch {
                preferences.showCounter.collectLatest { show ->
                    if (!show) {
                        pillManager.hidePill()
                    } else if (currentMode is ServiceMode.REEL) {
                        pillManager.showPill(countStore.todayCount.value)
                    }
                }
            }
        }

        // Register screen receiver securely
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        refreshKeyboardPackages()

        // Base config: only window state changed
        setEventFilter(inReelMode = false)

        // Refresh today's count from Room on service connect
        serviceScope.launch(Dispatchers.IO) {
            countStore.refreshTodayFromDb()
        }

        // Initial resync
        resync()
    }

    private fun refreshKeyboardPackages() {
        keyboardPackages.clear()
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.enabledInputMethodList?.forEach { method ->
                keyboardPackages.add(method.packageName)
            }
        } catch (ignored: Exception) {}
    }

    private fun isPackageIgnored(pkg: String): Boolean {
        return pkg == packageName ||
                standardIgnoreList.contains(pkg) ||
                keyboardPackages.contains(pkg)
    }

    private fun setEventFilter(inReelMode: Boolean) {
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = if (inReelMode) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_SCROLLED
        } else {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOWS_CHANGED
        }
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.notificationTimeout = 50
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        try {
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    handleWindowStateChanged(event)
                }
                AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                    scheduleDebouncedResync(delayMs = 150)
                }
                AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                    if (currentMode is ServiceMode.REEL) {
                        handleViewScrolled(event)
                    }
                }
            }
        } catch (t: Throwable) {
            if (BuildConfig.DEBUG) Log.w("rotcount", "onAccessibilityEvent error", t)
        }
    }

    private fun safeRecycle(node: AccessibilityNodeInfo?) {
        try {
            node?.recycle()
        } catch (ignored: Exception) {}
    }

    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return

        // Rule 1: If in ignore list, do nothing except schedule a resync (rule 4)
        if (isPackageIgnored(pkg)) {
            scheduleDebouncedResync()
            return
        }

        // Rule 2: If Instagram Direct/Chat thread, go to IDLE so chats are never counted
        if (pkg == ReelPreferences.PACKAGE_INSTAGRAM) {
            val className = event.className?.toString() ?: ""
            val isDirectChat = className.contains("DirectThread", ignoreCase = true) ||
                    className.contains("DirectInbox", ignoreCase = true) ||
                    className.contains("DirectMessages", ignoreCase = true) ||
                    className.contains("DirectChat", ignoreCase = true)

            if (isDirectChat) {
                transitionToIdle()
                return
            }
            transitionToReel(pkg)
            return
        }

        // Rule 3: If a background/system package dispatches a window event (e.g. Play services, autofill, popups),
        // verify whether the reel app is still active in the foreground before blindly going to IDLE!
        val currentReelPkg = (currentMode as? ServiceMode.REEL)?.packageName
        if (currentReelPkg != null) {
            val root = rootInActiveWindow
            val activeRootPkg = root?.packageName?.toString()
            safeRecycle(root)

            if (activeRootPkg == currentReelPkg) {
                // Reel app is still actively in front! Do not hide pill.
                return
            }

            try {
                val appWindow = windows?.firstOrNull {
                    it.isFocused && it.type == AccessibilityWindowInfo.TYPE_APPLICATION
                }
                val winPkg = appWindow?.root?.packageName?.toString()
                if (winPkg == currentReelPkg) {
                    // Reel app is still the focused application window! Do not hide pill.
                    return
                }
            } catch (ignored: Exception) {}
        }

        // Rule 4: Genuine app exit (e.g. Home, Recents, other app): go to IDLE
        transitionToIdle()
    }

    private fun transitionToReel(pkg: String) {
        if (currentMode == ServiceMode.REEL(pkg)) return

        currentMode = ServiceMode.REEL(pkg)

        // 1. Add scroll event type
        setEventFilter(inReelMode = true)

        // 2. Setup counter state: if we recently had a position for this package, preserve it so reopening the app doesn't jump
        val knownPos = lastPackagePositions[pkg]
        if (knownPos != null && knownPos >= 0) {
            lastSettledPosition = knownPos
            furthestPosition = knownPos
            pendingCandidatePosition = knownPos
            isAwaitingInitialPosition = false
        } else {
            reelProgress.reset()
            lastSettledPosition = -1
            furthestPosition = -1
            pendingCandidatePosition = -1
            isAwaitingInitialPosition = true
        }
        lastItemCount = -1
        cancelSettleTimer()

        // 3. Show pill if "Show counter" is on
        countStore.ensureCurrentDay()
        if (preferences.showCounter.value) {
            pillManager.showPill(countStore.todayCount.value)
        }

        // 4. Start save timer (30s) and watchdog (30s)
        startSaveTimer()
        rearmWatchdog()
    }

    private fun transitionToIdle() {
        if (currentMode == ServiceMode.IDLE) return

        val oldMode = currentMode
        if (oldMode is ServiceMode.REEL) {
            if (lastSettledPosition >= 0) {
                lastPackagePositions[oldMode.packageName] = lastSettledPosition
            }
        }

        currentMode = ServiceMode.IDLE

        // 1. Remove scroll event type
        setEventFilter(inReelMode = false)

        // 2. Hide pill immediately
        pillManager.hidePill()

        // 3. Cancel timers
        cancelSaveTimer()
        cancelWatchdog()
        cancelSettleTimer()
        cancelInactivityTimer()

        // 4. Save pending counts to Room
        serviceScope.launch(Dispatchers.IO) {
            countStore.savePendingCounts()
        }
    }

    private fun scheduleDebouncedResync(delayMs: Long = 300) {
        resyncDebounceRunnable?.let { mainHandler.removeCallbacks(it) }
        val r = Runnable { resync() }
        resyncDebounceRunnable = r
        mainHandler.postDelayed(r, delayMs)
    }

    private fun resync() {
        try {
            var activePkg: String? = null

            // 1. Check root in active window
            val root = rootInActiveWindow
            if (root != null) {
                activePkg = root.packageName?.toString()
                safeRecycle(root)
            }

            // 2. If root was null or an ignored window (like SystemUI during swipe gesture),
            // check top focused application window from the interactive windows list
            if (activePkg == null || isPackageIgnored(activePkg)) {
                try {
                    val appWindow = windows?.firstOrNull {
                        it.isFocused && it.type == AccessibilityWindowInfo.TYPE_APPLICATION
                    } ?: windows?.firstOrNull {
                        it.type == AccessibilityWindowInfo.TYPE_APPLICATION
                    }
                    val winRoot = appWindow?.root
                    if (winRoot != null) {
                        activePkg = winRoot.packageName?.toString()
                        safeRecycle(winRoot)
                    }
                } catch (ignored: Exception) {}
            }

            if (activePkg == null) {
                return
            }

            // If it's a transient system component (keyboard / notification shade), do not alter mode
            if (isPackageIgnored(activePkg)) {
                return
            }

            if (activePkg == ReelPreferences.PACKAGE_INSTAGRAM) {
                transitionToReel(activePkg)
            } else {
                transitionToIdle()
            }
        } catch (ignored: Exception) {}
    }

    private fun startSaveTimer() {
        cancelSaveTimer()
        val r = object : Runnable {
            override fun run() {
                if (currentMode is ServiceMode.REEL) {
                    serviceScope.launch(Dispatchers.IO) {
                        countStore.savePendingCounts()
                    }
                    mainHandler.postDelayed(this, 30_000)
                }
            }
        }
        saveTimerRunnable = r
        mainHandler.postDelayed(r, 30_000)
    }

    private fun cancelSaveTimer() {
        saveTimerRunnable?.let { mainHandler.removeCallbacks(it) }
        saveTimerRunnable = null
    }

    private fun rearmWatchdog() {
        cancelWatchdog()
        val r = Runnable {
            if (currentMode is ServiceMode.REEL) {
                resync()
                rearmWatchdog()
            }
        }
        watchdogRunnable = r
        mainHandler.postDelayed(r, 30_000)
    }

    private fun cancelWatchdog() {
        watchdogRunnable?.let { mainHandler.removeCallbacks(it) }
        watchdogRunnable = null
    }

    private fun startInactivityTimer() {
        cancelInactivityTimer()
        val r = Runnable {
            // After 10 minutes with no candidate event, reset state so next reel starts fresh
            reelProgress.reset()
            lastPackagePositions.clear()
            lastSettledPosition = -1
            furthestPosition = -1
            isAwaitingInitialPosition = true
            lastItemCount = -1
        }
        inactivityResetRunnable = r
        mainHandler.postDelayed(r, 10 * 60 * 1000L) // 10 minutes
    }

    private fun cancelInactivityTimer() {
        inactivityResetRunnable?.let { mainHandler.removeCallbacks(it) }
        inactivityResetRunnable = null
    }

    // -------------------------------------------------------------
    // Counting Logic & Gates
    // -------------------------------------------------------------

    private fun handleViewScrolled(event: AccessibilityEvent) {
        val mode = currentMode as? ServiceMode.REEL ?: return
        val signature = ReelApps.getSignature(mode.packageName) ?: return

        // Re-arm watchdog on activity from supported app
        rearmWatchdog()

        val className = event.className?.toString() ?: ""

        // Step 0 logging in debug builds
        if (BuildConfig.DEBUG) {
            val node = event.source
            val viewId = node?.viewIdResourceName ?: "null"
            val bounds = Rect()
            node?.getBoundsInScreen(bounds)
            safeRecycle(node)
            Log.d(
                "rotcount_step0",
                "pkg=${mode.packageName} class=$className viewId=$viewId bounds=$bounds " +
                        "scrollY=${event.scrollY} maxScrollY=${event.maxScrollY} " +
                        "fromIndex=${event.fromIndex} toIndex=${event.toIndex} itemCount=${event.itemCount}"
            )
        }

        val sourceNode = event.source ?: return

        val viewId = sourceNode.viewIdResourceName ?: ""
        val viewIdLower = viewId.lowercase()
        val classNameLower = className.lowercase()

        // Strict Exclusion: Ignore chats, comments, explore, profile, search, notifications
        val isExcluded = viewIdLower.contains("direct") ||
                viewIdLower.contains("thread") ||
                viewIdLower.contains("chat") ||
                viewIdLower.contains("message") ||
                viewIdLower.contains("inbox") ||
                viewIdLower.contains("comment") ||
                viewIdLower.contains("explore") ||
                viewIdLower.contains("profile") ||
                viewIdLower.contains("search") ||
                viewIdLower.contains("notification") ||
                viewIdLower.contains("newsfeed") ||
                classNameLower.contains("direct") ||
                classNameLower.contains("thread") ||
                classNameLower.contains("chat") ||
                classNameLower.contains("message") ||
                classNameLower.contains("comment")

        if (isExcluded) {
            safeRecycle(sourceNode)
            return
        }

        val matchesViewId = signature.viewIdKeywords.any { viewId.contains(it, ignoreCase = true) }
        val isPagerClass = signature.pagerClassNames.any { className.contains(it, ignoreCase = true) }

        if (!isPagerClass && !matchesViewId) {
            safeRecycle(sourceNode)
            return
        }

        // Gate B: Size - source bounds cover >= 90% screen width and >= 75% screen height
        val bounds = Rect()
        sourceNode.getBoundsInScreen(bounds)
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val widthRatio = bounds.width().toFloat() / screenWidth.toFloat()
        val heightRatio = bounds.height().toFloat() / screenHeight.toFloat()

        if (widthRatio < 0.90f || heightRatio < 0.75f) {
            safeRecycle(sourceNode)
            return
        }

        // Gate B1: Media Card Size Check (Reels / Feed Posts vs Comments / Chat Rows)
        // A Reel or Home feed post contains large media cards (>= 35% screen height).
        // A Comment sheet or Chat conversation consists entirely of small items (< 28% screen height).
        // In Home feed, child 0 may be the Stories tray (~10%), but child 1 or 2 is a feed post (>= 45%).
        // Inspect up to the first 4 visible children to find the maximum child height.
        if (sourceNode.childCount > 0) {
            var maxChildH = 0
            val checkCount = minOf(sourceNode.childCount, 4)
            for (i in 0 until checkCount) {
                val child = sourceNode.getChild(i) ?: continue
                val childBounds = Rect()
                child.getBoundsInScreen(childBounds)
                safeRecycle(child)
                val h = childBounds.height()
                if (h > maxChildH) {
                    maxChildH = h
                }
            }
            if (maxChildH > 0 && (maxChildH.toFloat() / screenHeight.toFloat()) < 0.28f) {
                // All visible items are small rows (comment items or chat bubbles) - NOT a reel or feed post!
                safeRecycle(sourceNode)
                return
            }
        }

        // Window-level Comment & Chat Check
        try {
            val root = rootInActiveWindow
            if (root != null) {
                val isCommentOrChatOpen = hasCommentOrChatIndicators(root)
                safeRecycle(root)
                if (isCommentOrChatOpen) {
                    safeRecycle(sourceNode)
                    return
                }
            }
        } catch (ignored: Exception) {}

        // Gate B2: Strictly Vertical - discard horizontal swipes (e.g. swiping left to profile or DMs)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val dx = Math.abs(event.scrollDeltaX)
            val dy = Math.abs(event.scrollDeltaY)
            if (dx > 0 && dx > dy) {
                safeRecycle(sourceNode)
                return
            }
        }
        if (event.scrollX > 0 && event.scrollY == 0 && event.maxScrollX > 0 && event.maxScrollY == 0) {
            safeRecycle(sourceNode)
            return
        }

        // Gate C: Keyboard not showing
        if (isKeyboardShowing()) {
            safeRecycle(sourceNode)
            return
        }

        // Candidate event accepted!
        safeRecycle(sourceNode)

        // Pager item count check: if list reloaded (itemCount decreased), reset progress
        val itemCount = event.itemCount
        if (itemCount in 1 until lastItemCount) {
            reelProgress.reset()
            lastSettledPosition = -1
            furthestPosition = -1
            isAwaitingInitialPosition = true
        }
        if (itemCount > 0) {
            lastItemCount = itemCount
        }

        // Determine position: prefer index (fromIndex when fromIndex == toIndex)
        val pos = if (event.fromIndex >= 0 && event.fromIndex == event.toIndex) {
            event.fromIndex
        } else if (event.scrollY > 0 && bounds.height() > 0) {
            Math.round(event.scrollY.toFloat() / bounds.height().toFloat())
        } else {
            event.fromIndex.coerceAtLeast(0)
        }

        // -------------------------------------------------------------
        // Position & Direction Evaluation
        // -------------------------------------------------------------

        // Baseline initialization when service starts or resets
        if (isAwaitingInitialPosition || lastSettledPosition < 0) {
            if (pos == 1) {
                // User opened reels (which start at index 0) and immediately swiped to index 1!
                reelProgress.reset()
                lastSettledPosition = 0
                furthestPosition = 0
                pendingCandidatePosition = 1
                isAwaitingInitialPosition = false
                lastPackagePositions[mode.packageName] = 1
                startInactivityTimer()
                restartSettleTimer()
                return
            } else {
                // Set initial baseline
                reelProgress.reset()
                lastSettledPosition = pos
                furthestPosition = pos
                pendingCandidatePosition = pos
                isAwaitingInitialPosition = false
                lastPackagePositions[mode.packageName] = pos
                return
            }
        }

        // At index 0 (idle on first reel or top of feed)
        // Keep furthestPosition intact so scrolling back to the top does NOT wipe history
        if (pos == 0) {
            lastSettledPosition = 0
            pendingCandidatePosition = 0
            lastPackagePositions[mode.packageName] = 0
            cancelSettleTimer()
            return
        }

        // Backward scroll (re-watching earlier reels/posts)
        // Must NEVER count backward moves!
        if (pos < lastSettledPosition) {
            lastSettledPosition = pos
            pendingCandidatePosition = pos
            lastPackagePositions[mode.packageName] = pos
            cancelSettleTimer()
            return
        }

        // Forward scroll (pos > lastSettledPosition)
        // Single swipe (pos = lastSettledPosition + 1) or rapid swipe & stop (pos in lastSettledPosition + 1..15)
        if (pos > lastSettledPosition) {
            pendingCandidatePosition = pos
            startInactivityTimer()
            restartSettleTimer()
            return
        }

        // Same position (pos == lastSettledPosition)
        // User is settling on the current post/reel. Do NOT cancel active settle timer.
    }

    private fun isKeyboardShowing(): Boolean {
        return try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.isAcceptingText ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun restartSettleTimer() {
        cancelSettleTimer()
        val r = Runnable {
            onSettled()
        }
        settleRunnable = r
        mainHandler.postDelayed(r, 350)
    }

    private fun cancelSettleTimer() {
        settleRunnable?.let { mainHandler.removeCallbacks(it) }
        settleRunnable = null
    }

    private fun hasCommentOrChatIndicators(root: AccessibilityNodeInfo): Boolean {
        try {
            val knownIds = listOf(
                "com.instagram.android:id/layout_comment_thread_edittext",
                "com.instagram.android:id/comment_thread_recyclerview",
                "com.instagram.android:id/row_comment_textarea_edittext",
                "com.instagram.android:id/comments_recycler_view",
                "com.instagram.android:id/layout_comment_thread_parent_container",
                "com.instagram.android:id/row_thread_composer",
                "com.instagram.android:id/message_composer_container"
            )
            for (id in knownIds) {
                val nodes = root.findAccessibilityNodeInfosByViewId(id)
                if (nodes.isNotEmpty()) {
                    nodes.forEach { safeRecycle(it) }
                    return true
                }
            }
        } catch (ignored: Exception) {}
        return false
    }

    private fun onSettled() {
        val mode = currentMode as? ServiceMode.REEL ?: return
        val positionNow = pendingCandidatePosition
        if (lastSettledPosition < 0) {
            lastSettledPosition = positionNow
            furthestPosition = maxOf(furthestPosition, positionNow)
            lastPackagePositions[mode.packageName] = positionNow
            return
        }

        val pages = positionNow - lastSettledPosition
        lastSettledPosition = positionNow
        lastPackagePositions[mode.packageName] = positionNow

        if (pages <= 0) return

        if (pages > 15) {
            // Abnormally large jump (e.g. background view pager restore), align baseline without counting
            furthestPosition = maxOf(furthestPosition, positionNow)
            return
        }

        if (!preferences.countEnabled.value) {
            return
        }

        // Strict anti-recounting:
        // Only count if this move reaches a NEW reel beyond the furthest position reached so far!
        // Re-watching earlier reels (reverting and coming back) does NOT recount!
        if (positionNow > furthestPosition) {
            furthestPosition = positionNow
            countStore.recordCount(1)
            pillManager.updateCount(countStore.todayCount.value)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        pillManager.onConfigurationChanged(newConfig)
    }

    override fun onInterrupt() {
        // Hide trigger 3
        transitionToIdle()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        // Hide trigger 3
        transitionToIdle()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        // Hide trigger 3
        transitionToIdle()
        try {
            unregisterReceiver(screenReceiver)
        } catch (ignored: Exception) {}
        todayCountJob?.cancel()
        prefsCollectorJob?.cancel()
        pillManager.destroy()
        super.onDestroy()
    }
}
