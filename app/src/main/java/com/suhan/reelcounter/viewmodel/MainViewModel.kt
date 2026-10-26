package com.suhan.reelcounter.viewmodel

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suhan.reelcounter.BuildConfig
import com.suhan.reelcounter.data.CountStore
import com.suhan.reelcounter.data.ReelPreferences
import com.suhan.reelcounter.logic.StatsMath
import com.suhan.reelcounter.service.ReelAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class Screen {
    Home,
    Settings,
    FirstLaunch
}

data class DayUiState(
    val todayCount: Int = 0,
    val hourlyBars: List<Int> = List(24) { 0 },
    val currentHour: Int = 0,
    val yesterdayCount: Int = 0,
    val sevenDayAverage: Int = 0,
    val isEmpty: Boolean = true
)

data class WeekUiState(
    val offset: Int = 0,
    val label: String = "This week",
    val weekTotal: Int = 0,
    val dayBars: List<Int> = List(7) { 0 },
    val currentDayIndex: Int = 0, // 0 = Mon, 6 = Sun
    val isCurrentWeek: Boolean = true,
    val dailyAverage: Int = 0,
    val highestDayCount: Int = 0,
    val canGoLeft: Boolean = false,
    val canGoRight: Boolean = false,
    val isEmpty: Boolean = true
)

class MainViewModel(
    private val countStore: CountStore,
    private val preferences: ReelPreferences
) : ViewModel() {

    private val _currentScreen = MutableStateFlow(Screen.Home)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _isAccessibilityGranted = MutableStateFlow(false)
    val isAccessibilityGranted: StateFlow<Boolean> = _isAccessibilityGranted.asStateFlow()

    private val _hasReturnedFromSettings = MutableStateFlow(false)
    val hasReturnedFromSettings: StateFlow<Boolean> = _hasReturnedFromSettings.asStateFlow()

    // 0 = Day, 1 = Week
    private val _selectedSegment = MutableStateFlow(0)
    val selectedSegment: StateFlow<Int> = _selectedSegment.asStateFlow()

    // Week offset: 0 = current week, -1 = previous, etc.
    private val _weekOffset = MutableStateFlow(0)
    val weekOffset: StateFlow<Int> = _weekOffset.asStateFlow()

    private val _dayUiState = MutableStateFlow(DayUiState())
    val dayUiState: StateFlow<DayUiState> = _dayUiState.asStateFlow()

    private val _weekUiState = MutableStateFlow(WeekUiState())
    val weekUiState: StateFlow<WeekUiState> = _weekUiState.asStateFlow()

    // Heatmap State
    private val _selectedHeatmapYear = MutableStateFlow(LocalDate.now().year)
    val selectedHeatmapYear: StateFlow<Int> = _selectedHeatmapYear.asStateFlow()

    private val _availableHeatmapYears = MutableStateFlow<List<Int>>(listOf(LocalDate.now().year))
    val availableHeatmapYears: StateFlow<List<Int>> = _availableHeatmapYears.asStateFlow()

    private val _heatmapData = MutableStateFlow<Map<LocalDate, Int>>(emptyMap())
    val heatmapData: StateFlow<Map<LocalDate, Int>> = _heatmapData.asStateFlow()

    // Preferences
    val showCounter: StateFlow<Boolean> = preferences.showCounter
    val countEnabled: StateFlow<Boolean> = preferences.countEnabled
    val countInstagram: StateFlow<Boolean> = preferences.countInstagram
    val countYouTube: StateFlow<Boolean> = preferences.countYouTube

    init {
        // Save pending counts and refresh when Home opens
        viewModelScope.launch {
            countStore.savePendingCounts()
            countStore.refreshTodayFromDb()
            refreshData()
        }

        // Live update whenever today's count changes
        viewModelScope.launch {
            countStore.todayCount.collectLatest {
                refreshData()
            }
        }
    }

    fun onHomeOpened() {
        viewModelScope.launch {
            countStore.savePendingCounts()
            countStore.refreshTodayFromDb()
            refreshData()
        }
    }

    fun checkAccessibilityPermission(context: Context) {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val enabledServices = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC) ?: emptyList()
        val isGranted = enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == context.packageName &&
                    it.resolveInfo.serviceInfo.name == ReelAccessibilityService::class.java.name
        }
        val wasGranted = _isAccessibilityGranted.value
        _isAccessibilityGranted.value = isGranted

        if (!isGranted && _currentScreen.value == Screen.Home) {
            _currentScreen.value = Screen.FirstLaunch
        } else if (isGranted && _currentScreen.value == Screen.FirstLaunch) {
            _currentScreen.value = Screen.Home
        }

        if (!isGranted && wasGranted == false && _hasReturnedFromSettings.value) {
            // Already flagged as returned and still off
        }
    }

    fun openAccessibilitySettings(context: Context) {
        _hasReturnedFromSettings.value = true
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w("rotcount", "Failed to open accessibility settings", e)
        }
    }

    fun selectSegment(index: Int) {
        _selectedSegment.value = index
        when (index) {
            0 -> refreshDayData()
            1 -> {
                _weekOffset.value = 0
                refreshWeekData(0)
            }
            2 -> refreshHeatmapData(LocalDate.now().year)
        }
    }

    fun changeWeek(delta: Int) {
        val current = _weekOffset.value
        val newOffset = current + delta
        if (delta > 0 && current >= 0) {
            // Right chevron does nothing on current week
            return
        }
        if (delta < 0 && !_weekUiState.value.canGoLeft) {
            // Left chevron does nothing when no earlier data
            return
        }
        _weekOffset.value = newOffset
        refreshWeekData(newOffset)
    }

    fun refreshData() {
        refreshDayData()
        refreshWeekData(_weekOffset.value)
        refreshHeatmapData(_selectedHeatmapYear.value)
    }

    fun selectHeatmapYear(year: Int) {
        if (_selectedHeatmapYear.value != year) {
            _selectedHeatmapYear.value = year
            refreshHeatmapData(year)
        }
    }

    private fun refreshHeatmapData(year: Int = LocalDate.now().year) {
        viewModelScope.launch {
            val currentYear = LocalDate.now().year
            _selectedHeatmapYear.value = currentYear
            _availableHeatmapYears.value = listOf(currentYear)

            val startOfYear = LocalDate.of(currentYear, 1, 1)
            val endOfYear = LocalDate.of(currentYear, 12, 31)
            val dailyMap = countStore.getDailyTotals(startOfYear, endOfYear).toMutableMap()
            val today = LocalDate.now()
            if (today.year == currentYear) {
                dailyMap[today] = maxOf(dailyMap[today] ?: 0, countStore.todayCount.value)
            }
            _heatmapData.value = dailyMap
        }
    }

    private fun refreshDayData() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val hourly = countStore.getDayHourly(today)
            val todayTotal = maxOf(hourly.sum(), countStore.todayCount.value)
            val currentHour = java.time.LocalTime.now().hour

            val pastWeekDaily = countStore.getDailyTotals(today.minusDays(7), today.minusDays(1))
            val yesterdayTotal = StatsMath.calculateYesterdayTotal(today, pastWeekDaily)
            val sevenDayAvg = StatsMath.calculateSevenDayAverage(today, pastWeekDaily)

            val isEmpty = todayTotal == 0 && pastWeekDaily.values.all { it == 0 }

            _dayUiState.value = DayUiState(
                todayCount = todayTotal,
                hourlyBars = hourly,
                currentHour = currentHour,
                yesterdayCount = yesterdayTotal,
                sevenDayAverage = sevenDayAvg,
                isEmpty = isEmpty
            )
        }
    }

    private fun refreshWeekData(offset: Int) {
        viewModelScope.launch {
            val today = LocalDate.now()
            val currentMonday = StatsMath.getWeekRange(today).first
            val targetMonday = currentMonday.plusWeeks(offset.toLong())
            val targetSunday = targetMonday.plusDays(6)

            val weekDailyMap = countStore.getDailyTotals(targetMonday, targetSunday).toMutableMap()
            if (offset == 0) {
                weekDailyMap[today] = maxOf(weekDailyMap[today] ?: 0, countStore.todayCount.value)
            }
            val dayBars = (0..6).map { i ->
                weekDailyMap[targetMonday.plusDays(i.toLong())] ?: 0
            }
            val weekTotal = dayBars.sum()

            val isCurrentWeek = (offset == 0)
            val currentDayIndex = today.dayOfWeek.value - 1 // 0 = Mon, 6 = Sun

            val dailyAvg = StatsMath.calculateWeekDailyAverage(
                weekMonday = targetMonday,
                today = today,
                isCurrentWeek = isCurrentWeek,
                dailyTotals = weekDailyMap
            )
            val highest = StatsMath.calculateHighestDay(targetMonday, weekDailyMap)

            val earliestDate = countStore.getEarliestDate()
            val canLeft = StatsMath.canNavigateLeft(targetMonday, earliestDate)
            val canRight = offset < 0

            val label = StatsMath.formatWeekLabel(offset, targetMonday)
            val isEmpty = weekTotal == 0

            _weekUiState.value = WeekUiState(
                offset = offset,
                label = label,
                weekTotal = weekTotal,
                dayBars = dayBars,
                currentDayIndex = currentDayIndex,
                isCurrentWeek = isCurrentWeek,
                dailyAverage = dailyAvg,
                highestDayCount = highest.count,
                canGoLeft = canLeft,
                canGoRight = canRight,
                isEmpty = isEmpty
            )
        }
    }

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    fun setShowCounter(enabled: Boolean) {
        preferences.setShowCounter(enabled)
    }

    fun setCountEnabled(enabled: Boolean) {
        preferences.setCountEnabled(enabled)
    }

    fun setCountInstagram(enabled: Boolean) {
        preferences.setCountInstagram(enabled)
    }

    fun setCountYouTube(enabled: Boolean) {
        preferences.setCountYouTube(enabled)
    }
}
