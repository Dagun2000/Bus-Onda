package com.app.busiscoming

import android.app.Application
import com.app.busiscoming.data.datastore.AppPreferences
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application 클래스
 */
@HiltAndroidApp
class BusIsComingApplication : Application() {
    
    @Inject
    lateinit var appPreferences: AppPreferences
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    override fun onCreate() {
        super.onCreate()
        
        // deviceId 초기화
        applicationScope.launch {
            appPreferences.initializeDeviceIdIfNeeded()
        }
    }
}




