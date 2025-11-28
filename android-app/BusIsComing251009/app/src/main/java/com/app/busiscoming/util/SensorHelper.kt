package com.app.busiscoming.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * 자이로센서를 이용한 나침판 기능
 */
@Singleton
class SensorHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sensorManager: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    fun getDeviceOrientation(): Flow<Float> = callbackFlow {
        val sensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
                    }
                }
                
                // 두 센서 데이터가 모두 준비되면 방향 계산
                if (accelerometerReading.isNotEmpty() && magnetometerReading.isNotEmpty()) {
                    val orientation = calculateOrientation()
                    trySend(orientation)
                }
            }
            
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                // 정확도 변경 시 처리 (필요시)
            }
        }
        
        // 센서 등록
        accelerometer?.let { 
            sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_UI)
        }
        magnetometer?.let { 
            sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_UI)
        }
        
        awaitClose {
            sensorManager.unregisterListener(sensorEventListener)
        }
    }.distinctUntilChanged()

    private fun calculateOrientation(): Float {
        // 회전 행렬 계산
        SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerReading,
            magnetometerReading
        )
        
        // 방향 각도 계산
        SensorManager.getOrientation(rotationMatrix, orientationAngles)
        
        // 방향각을 0-360도 범위로 변환
        val azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
        return if (azimuth < 0) azimuth + 360f else azimuth
    }

    fun calculateRelativeDirection(deviceOrientation: Float, targetBearing: Double): Double {
        var relativeDirection = targetBearing - deviceOrientation
        
        // -180 ~ 180도 범위로 정규화
        while (relativeDirection > 180) relativeDirection -= 360
        while (relativeDirection < -180) relativeDirection += 360
        
        return relativeDirection
    }
}


