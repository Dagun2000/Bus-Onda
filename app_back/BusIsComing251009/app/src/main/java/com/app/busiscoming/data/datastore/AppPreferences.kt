package com.app.busiscoming.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        private val DEVICE_ID_KEY = stringPreferencesKey("device_id")
        private val SERVER_IP_KEY = stringPreferencesKey("server_ip")
        private val SERVER_PORT_KEY = stringPreferencesKey("server_port")
        private val REQUEST_ID_KEY = stringPreferencesKey("request_id")
        private val BUS_PLATE_NUMBER_KEY = stringPreferencesKey("bus_plate_number")
        private val BUS_LINE_NAME_KEY = stringPreferencesKey("bus_line_name")
        private val BUS_STOP_NO_KEY = stringPreferencesKey("bus_stop_no")
    }

    val deviceId: Flow<String> = dataStore.data.map { preferences ->
        preferences[DEVICE_ID_KEY] ?: "android-xxxx"
    }

    val serverIp: Flow<String> = dataStore.data.map { preferences ->
        preferences[SERVER_IP_KEY] ?: ""
    }

    val serverPort: Flow<String> = dataStore.data.map { preferences ->
        preferences[SERVER_PORT_KEY] ?: "3000"
    }

    suspend fun getDeviceId(): String {
        val preferences = dataStore.data.first()
        return preferences[DEVICE_ID_KEY] ?: generateAndSaveDeviceId()
    }

    suspend fun setServerIp(ip: String) {
        dataStore.edit { preferences ->
            preferences[SERVER_IP_KEY] = ip
        }
    }

    suspend fun setServerPort(port: String) {
        dataStore.edit { preferences ->
            preferences[SERVER_PORT_KEY] = port
        }
    }

    private suspend fun generateAndSaveDeviceId(): String {
        val newDeviceId = "android-${UUID.randomUUID().toString().take(8)}"
        dataStore.edit { preferences ->
            preferences[DEVICE_ID_KEY] = newDeviceId
        }
        return newDeviceId
    }

    suspend fun initializeDeviceIdIfNeeded() {
        val preferences = dataStore.data.first()
        if (preferences[DEVICE_ID_KEY] == null) {
            generateAndSaveDeviceId()
        }
    }
    
    suspend fun getRequestId(): String? {
        val preferences = dataStore.data.first()
        return preferences[REQUEST_ID_KEY]
    }
    
    suspend fun setRequestId(requestId: String?) {
        dataStore.edit { preferences ->
            if (requestId != null) {
                preferences[REQUEST_ID_KEY] = requestId
            } else {
                preferences.remove(REQUEST_ID_KEY)
            }
        }
    }
    
    suspend fun setBusInfo(plateNumber: String, lineName: String, stopNo: String) {
        dataStore.edit { preferences ->
            preferences[BUS_PLATE_NUMBER_KEY] = plateNumber
            preferences[BUS_LINE_NAME_KEY] = lineName
            preferences[BUS_STOP_NO_KEY] = stopNo
        }
    }
    
    suspend fun getBusInfo(): Triple<String, String, String>? {
        val preferences = dataStore.data.first()
        val plateNumber = preferences[BUS_PLATE_NUMBER_KEY]
        val lineName = preferences[BUS_LINE_NAME_KEY]
        val stopNo = preferences[BUS_STOP_NO_KEY]
        
        return if (plateNumber != null && lineName != null && stopNo != null) {
            Triple(plateNumber, lineName, stopNo)
        } else {
            null
        }
    }
}

