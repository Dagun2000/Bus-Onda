package com.app.busiscoming.presentation.screens.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.app.busiscoming.domain.model.PlaceInfo
import com.app.busiscoming.presentation.components.InputField
import com.app.busiscoming.presentation.components.PrimaryButton
import com.app.busiscoming.presentation.navigation.Screen
import com.app.busiscoming.ui.theme.BusIsComingTheme
import com.app.busiscoming.util.VoiceInputHelper

/**
 * 홈 화면 (초기 화면)
 */
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // 위치 권한 요청
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            viewModel.setCurrentLocationAsStart()
        }
    }

    // 음성 입력 런처
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val text = VoiceInputHelper.parseResult(result)
        if (text != null) {
            viewModel.onDestinationRecognized(text)
        } else {
            viewModel.onVoiceRecognitionFailed()
        }
    }
    
    // 앱 시작 시 위치 권한 요청
    LaunchedEffect(Unit) {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.RECORD_AUDIO
            )
        )
    }

    // 에러 메시지 표시
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            // 실제로는 Snackbar 등으로 표시
            kotlinx.coroutines.delay(2000)
            viewModel.clearError()
        }
    }

    // 검색 완료 시 결과 화면으로 이동
    LaunchedEffect(uiState.searchCompleted) {
        if (uiState.searchCompleted) {
            viewModel.resetSearchCompleted()
            navController.navigate(Screen.RouteResult.route)
        }
    }

    HomeScreenContent(
        uiState = uiState,
        onDestinationTextChanged = viewModel::onDestinationTextChanged,
        onSearchClick = { viewModel.onStartClicked() },
        onDestinationFieldClick = {
            voiceLauncher.launch(
                VoiceInputHelper.buildKoreanFreeFormIntent("목적지를 말씀해주세요")
            )
        }
    )
}

@Composable
private fun HomeScreenContent(
    uiState: HomeUiState,
    onDestinationTextChanged: (String) -> Unit,
    onSearchClick: () -> Unit,
    onDestinationFieldClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)

    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 42.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            
            // 입력 필드 카드
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(20.dp)
            ) {
                InputField(
                    value = uiState.destinationText,
                    onValueChange = onDestinationTextChanged,
                    placeholder = "화면을 두 번 빠르게 터치하고 목적지를 말해주세요",
                    readOnly = true,
                    onClick = onDestinationFieldClick,
                    modifier = Modifier.semantics {
                        contentDescription = ""
                    }
                )
            }
            Spacer(modifier = Modifier.height(46.dp))

            // 경로 안내 시작 버튼
            PrimaryButton(
                text = if (uiState.isLoading) "검색 중..." else "경로 안내 시작",
                onClick = onSearchClick,
                enabled = !uiState.isLoading && uiState.isLocationReady
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun HomeScreenPreview() {
    BusIsComingTheme {
        HomeScreenContent(
            uiState = HomeUiState(
                startPlace = PlaceInfo("현재 위치", 37.5665, 126.9780),
                endPlace = null,
                destinationText = "홍대"
            ),
            onDestinationTextChanged = {},
            onSearchClick = {},
            onDestinationFieldClick = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun HomeScreenWithDestinationPreview() {
    BusIsComingTheme {
        HomeScreenContent(
            uiState = HomeUiState(
                startPlace = PlaceInfo("현재 위치", 37.5665, 126.9780),
                endPlace = PlaceInfo("홍대입구역", 37.5563, 126.9226),
                destinationText = "홍대입구역"
            ),
            onDestinationTextChanged = {},
            onSearchClick = {},
            onDestinationFieldClick = {}
        )
    }
}

