package com.app.busiscoming.data.remote

import com.app.busiscoming.data.remote.dto.HelloMessage
import com.app.busiscoming.data.remote.dto.WebSocketMessage
import com.google.gson.Gson
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    private var webSocket: WebSocket? = null
    private var listener: ((WebSocketMessage) -> Unit)? = null

    fun connect(url: String, onMessage: (WebSocketMessage) -> Unit) {
        listener = onMessage
        val request = Request.Builder()
            .url(url)
            .build()
        
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // 연결 성공
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val message = gson.fromJson(text, WebSocketMessage::class.java)
                    listener?.invoke(message)
                } catch (e: Exception) {
                    // JSON 파싱 실패
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // 연결 실패
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                // 연결 종료
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        listener = null
    }

    /**
     * WebSocket 메시지를 관찰하는 Flow를 반환.
     * 연결 성공 후 자동으로 hello 메시지를 전송 << 251203.
     * 
     * @param url WebSocket 서버 URL
     * @param deviceId 연결 후 전송할 deviceId
     */
    fun observeMessages(url: String, deviceId: String): Flow<WebSocketMessage> = callbackFlow {
        val request = Request.Builder()
            .url(url)
            .build()
        
        val ws = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // 연결 성공 후 hello 메시지 전송
                try {
                    val helloMessage = HelloMessage(deviceId = deviceId)
                    val jsonMessage = gson.toJson(helloMessage)
                    webSocket.send(jsonMessage)
                    android.util.Log.i("WebSocketManager", "Hello 메시지 전송: $jsonMessage")
                } catch (e: Exception) {
                    android.util.Log.e("WebSocketManager", "Hello 메시지 전송 실패: ${e.message}", e)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val message = gson.fromJson(text, WebSocketMessage::class.java)
                    trySend(message)
                } catch (e: Exception) {
                    // JSON 파싱 실패
                    android.util.Log.e("WebSocketManager", "메시지 파싱 실패: $text", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                android.util.Log.e("WebSocketManager", "WebSocket 연결 실패: ${t.message}", t)
                close(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                android.util.Log.i("WebSocketManager", "WebSocket 연결 종료: code=$code, reason=$reason")
                close()
            }
        })

        awaitClose {
            ws.close(1000, "Normal closure")
        }
    }
}







