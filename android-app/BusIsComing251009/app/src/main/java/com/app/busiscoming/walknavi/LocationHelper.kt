package com.app.busiscoming.walknavi

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*

class LocationHelper(private val context: Context) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private var locationCallback: LocationCallback? = null

    // ★ 핵심 수정: Try-Catch 로 감싸서 앱이 꺼지는 것 방지
    @SuppressLint("MissingPermission")
    fun startListening(onLocationReceived: (Double, Double) -> Unit) {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
            .setMinUpdateDistanceMeters(3f)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let {
                    Log.d("GPS", "내 위치 갱신: ${it.latitude}, ${it.longitude}")
                    onLocationReceived(it.latitude, it.longitude)
                }
            }
        }

        try {
            // 위치 요청 시도
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            // ★ 여기서 앱이 꺼지는 대신 로그를 남깁니다.
            Log.e("GPS_ERROR", "!!! 에러 발생: 위치 권한이 아직 허용되지 않았습니다 !!!")
        } catch (e: Exception) {
            Log.e("GPS_ERROR", "알 수 없는 오류: ${e.message}")
        }
    }

    fun stopListening() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
    }
}