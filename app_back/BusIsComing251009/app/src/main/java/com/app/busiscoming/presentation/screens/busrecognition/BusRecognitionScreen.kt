package com.app.busiscoming.presentation.screens.busrecognition

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.app.busiscoming.presentation.navigation.Screen
import com.app.busiscoming.util.SelectedRouteHolder
import android.widget.Toast
import kotlinx.coroutines.launch

@Composable
fun BusRecognitionScreen(
    navController: NavController,
    busNumber: String? = null,
    viewModel: BusRecognitionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // [로직 삽입] 홀더에서 현재 버스 번호 참조
    val currentLeg = remember { SelectedRouteHolder.getCurrentLeg() }
    val finalBusNumber = currentLeg?.routeName ?: busNumber ?: ""

    LaunchedEffect(finalBusNumber) {
        viewModel.initialize(finalBusNumber)
    }

    BusRecognitionScreenContent(
        busNumber = finalBusNumber,
        onFindBusOnly = {
            viewModel.selectFindBusOnly()
            navController.navigate(Screen.BusRecognitionResult.createRoute(finalBusNumber, isFindSeats = false))
        },
        onFindBusAndSeats = {
            viewModel.selectFindBusAndSeats()
            navController.navigate(Screen.BusRecognitionResult.createRoute(finalBusNumber, isFindSeats = true))
        },
        onCancelNotification = {
            scope.launch {
                val result = viewModel.cancelBoardingNotification()
                result.onSuccess { navController.popBackStack() }
                result.onFailure { Toast.makeText(context, it.message ?: "취소 실패", Toast.LENGTH_LONG).show() }
            }
        }
    )
}

@Composable
fun BusRecognitionScreenContent(
    busNumber: String,
    onFindBusOnly: () -> Unit,
    onFindBusAndSeats: () -> Unit,
    onCancelNotification: () -> Unit
) {
    val instructionText = "탑승하셔야 하는 버스는 ${busNumber}버스입니다.\n화면을 오른쪽으로 스와이프해서 사용하실 기능을\n선택하세요"
    val cancelButtonText = "승차 알림 취소"
    val instructionFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { instructionFocusRequester.requestFocus() }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp).focusRequester(instructionFocusRequester).focusable()) {
            Text(text = instructionText, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onFindBusOnly, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface), elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp)) {
                Text(text = "버스만 찾기", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal, textAlign = TextAlign.Center)
            }
            Button(onClick = onFindBusAndSeats, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface), elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp)) {
                Text(text = "버스 및 빈 좌석 찾기", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal, textAlign = TextAlign.Center)
            }
            Button(onClick = onCancelNotification, modifier = Modifier.fillMaxWidth().focusable(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface), elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp)) {
                Text(text = cancelButtonText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal, textAlign = TextAlign.Center)
            }
        }
    }
}