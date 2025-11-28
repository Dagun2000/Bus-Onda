package com.app.busiscoming.presentation.screens.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.app.busiscoming.domain.model.RouteInfo
import com.app.busiscoming.presentation.components.PrimaryButton
import com.app.busiscoming.ui.theme.BusIsComingTheme

/**
 * 정류장 안내 화면
 */
@Composable
fun NavigationScreen(
    navController: NavController,
    stationLat: Double,
    stationLon: Double,
    stationName: String,
    routeInfo: RouteInfo? = null,
    viewModel: NavigationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.startNavigation(stationLat, stationLon, stationName, routeInfo)
    }
    
    LaunchedEffect(uiState.arrived) {
        if (uiState.arrived) {
            kotlinx.coroutines.delay(2000)
            navController.navigateUp()
        }
    }
    
    NavigationScreenContent(
        uiState = uiState,
        stationName = stationName,
        onBack = { navController.navigateUp() },
        onConfirmArrival = { viewModel.confirmArrival() }
    )
}

/**
 * 정류장 안내 화면 컨텐츠 (ViewModel과 분리)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationScreenContent(
    uiState: NavigationUiState,
    stationName: String,
    onBack: () -> Unit,
    onConfirmArrival: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = stationName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로 가기"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            
            // 거리 정보
            Text(
                text = "${uiState.distance}m",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "목적지까지",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(60.dp))
            
            // 방향 화살표 (나침판)
            DirectionArrow(
                deviceOrientation = uiState.deviceOrientation,
                targetBearing = uiState.targetBearing,
                modifier = Modifier
                    .size(300.dp)
                    .weight(1f)
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // 방향 텍스트
            Text(
                text = uiState.direction,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // 정류장 도착 확인 버튼
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                if (uiState.isNearDestination) {
                    Text(
                        text = "정류장 근처에 도착했습니다",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    )
                }
                
                PrimaryButton(
                    text = "정류장 도착 확인",
                    onClick = onConfirmArrival,
                    enabled = uiState.isNearDestination
                )
            }
        }
    }
}

/**
 * 나침판 화살표 컴포넌트
 * @param deviceOrientation 폰의 현재 방향 (0-360도)
 * @param targetBearing 목적지까지의 절대 방향 (0-360도)
 */
@Composable
private fun DirectionArrow(
    deviceOrientation: Float,
    targetBearing: Double,
    modifier: Modifier = Modifier
) {
    val arrowColor = Color.Black
    
    val targetAngle = remember(deviceOrientation, targetBearing) {
        (targetBearing - deviceOrientation).toFloat()
    }
    
    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val centerX = canvasWidth / 2
        val centerY = canvasHeight / 2
        
        rotate(targetAngle, Offset(centerX, centerY)) {
            val arrowPath = Path().apply {
                moveTo(centerX, centerY - canvasHeight * 0.35f)
                lineTo(centerX - canvasWidth * 0.25f, centerY + canvasHeight * 0.05f)
                lineTo(centerX - canvasWidth * 0.1f, centerY + canvasHeight * 0.05f)
                
                // 화살표 몸통
                lineTo(centerX - canvasWidth * 0.1f, centerY + canvasHeight * 0.35f)
                lineTo(centerX + canvasWidth * 0.1f, centerY + canvasHeight * 0.35f)
                lineTo(centerX + canvasWidth * 0.1f, centerY + canvasHeight * 0.05f)
                
                // 화살표 머리 오른쪽
                lineTo(centerX + canvasWidth * 0.25f, centerY + canvasHeight * 0.05f)
                close()
            }
            
            drawPath(
                path = arrowPath,
                color = arrowColor,
                style = Fill
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun NavigationScreenContentPreview() {
    BusIsComingTheme {
        NavigationScreenContent(
            uiState = NavigationUiState(
                distance = 125,
                bearing = 45.0,
                direction = "우측",
                isNearDestination = false
            ),
            stationName = "홍대입구역 버스정류장",
            onBack = {},
            onConfirmArrival = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun NavigationScreenContentNearPreview() {
    BusIsComingTheme {
        NavigationScreenContent(
            uiState = NavigationUiState(
                distance = 35,
                bearing = 90.0,
                direction = "우회전",
                isNearDestination = true
            ),
            stationName = "신촌역 버스정류장",
            onBack = {},
            onConfirmArrival = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DirectionArrowPreview() {
    BusIsComingTheme {
        Box(
            modifier = Modifier
                .size(300.dp)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            DirectionArrow(
                deviceOrientation = 0f,
                targetBearing = 45.0,
                modifier = Modifier.size(250.dp)
            )
        }
    }
}

