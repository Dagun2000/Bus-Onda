package com.app.busiscoming.presentation.screens.disembarkationnotification

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

/**
 * 하차 알림 화면
 */
@Composable
fun DisembarkationNotificationScreen(
    navController: NavController,
    busNumber: String? = null,
    viewModel: DisembarkationNotificationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(busNumber) {
        viewModel.initialize(busNumber)
    }
    
    DisembarkationNotificationScreenContent(
        onDoubleTap = {
            // 하차 알림 전송 (나중에 구현)
        }
    )
}

/**
 * 하차 알림 화면 컨텐츠
 */
@Composable
fun DisembarkationNotificationScreenContent(
    onDoubleTap: () -> Unit
) {
    val buttonText = "하차하실 정류장에 가까워지시면 화면을 더블탭해서\n기사님께 하차 알림을 보내세요."
    
    val buttonFocusRequester = remember { FocusRequester() }
    
    // 화면 진입 시 버튼에 포커스 요청 (TalkBack 지원)
    LaunchedEffect(Unit) {
        buttonFocusRequester.requestFocus()
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 상단 공간
        Spacer(modifier = Modifier.weight(1f))
        
        // 빨간색 테두리 버튼
        Button(
            onClick = onDoubleTap,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(buttonFocusRequester)
                .focusable(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp
            )
        ) {
            Text(
                text = buttonText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
        
        // 하단 공간
        Spacer(modifier = Modifier.weight(1f))
    }
}

