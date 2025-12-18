package com.app.busiscoming.presentation.screens.disembarkcomplete

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.app.busiscoming.domain.model.TransitMode
import com.app.busiscoming.presentation.navigation.Screen
import com.app.busiscoming.presentation.screens.home.HomeViewModel
import com.app.busiscoming.util.SelectedRouteHolder

@Composable
fun DisembarkCompleteScreen(
    navController: NavController,
    busNumber: String? = null
) {
    val parentEntry = remember(navController.currentBackStackEntry) {
        navController.getBackStackEntry(Screen.Home.route)
    }
    val homeViewModel: HomeViewModel = hiltViewModel(parentEntry)
    val homeUiState by homeViewModel.uiState.collectAsState()

    // 🌟 [핵심] API의 "도착지"를 무시하고 홈 화면의 검색어를 최종 목적지명으로 확정
    val realDestName = homeUiState.destinationText.ifEmpty {
        homeUiState.endPlace?.name ?: "최종 목적지"
    }

    DisembarkCompleteScreenContent(
        onDoubleTap = {
            val currentLeg = SelectedRouteHolder.getCurrentLeg()

            if (currentLeg == null) {
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Home.route) { inclusive = true }
                }
                return@DisembarkCompleteScreenContent
            }

            when (currentLeg.mode) {
                TransitMode.BUS, TransitMode.SUBWAY -> {
                    navController.navigate(Screen.BusRecognition.createRoute(currentLeg.routeName ?: "")) {
                        popUpTo(Screen.DisembarkComplete.route) { inclusive = true }
                    }
                }
                else -> {
                    // 최종 도보 안내: 보정된 realDestName과 Double 좌표 전달
                    navController.navigate(
                        Screen.WalkingGuide.createRoute(
                            busNumber = "도보 이동",
                            stopName = realDestName, // 🌟 검색어로 보정된 이름
                            lat = currentLeg.endLat.toDouble(),
                            lng = currentLeg.endLon.toDouble()
                        )
                    ) { popUpTo(Screen.DisembarkComplete.route) { inclusive = true } }
                }
            }
        }
    )
}

@Composable
fun DisembarkCompleteScreenContent(onDoubleTap: () -> Unit) {
    val buttonText = "하차 완료 시 더블탭 하세요"
    val buttonFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { buttonFocusRequester.requestFocus() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = onDoubleTap,
            modifier = Modifier.fillMaxWidth().focusRequester(buttonFocusRequester).focusable().semantics { contentDescription = buttonText },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp)
        ) {
            Text(text = buttonText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}