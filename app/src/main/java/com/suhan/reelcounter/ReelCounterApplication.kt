package com.suhan.reelcounter

import android.app.Application
import com.suhan.reelcounter.data.CountStore
import com.suhan.reelcounter.data.ReelDatabase
import com.suhan.reelcounter.data.ReelPreferences

class ReelCounterApplication : Application() {

    lateinit var database: ReelDatabase
        private set

    lateinit var preferences: ReelPreferences
        private set

    lateinit var countStore: CountStore
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = ReelDatabase.getInstance(this)
        preferences = ReelPreferences(this)
        countStore = CountStore(database.hourlyCountDao())
    }

    companion object {
        lateinit var instance: ReelCounterApplication
            private set
    }
}
