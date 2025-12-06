package com.app.busiscoming.data.remote.dto

/**
 * WebSocket 연결 후 전송하는 hello 메시지
 */
data class HelloMessage(
    val type: String = "hello",
    val deviceId: String
)



