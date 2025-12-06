package com.app.busiscoming.presentation.screens.routeresult

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.app.busiscoming.domain.model.RouteInfo
import com.app.busiscoming.domain.model.TransitMode
import com.app.busiscoming.presentation.navigation.Screen
import com.app.busiscoming.ui.theme.*
import kotlinx.coroutines.launch
import android.widget.Toast

private fun buildCardText(route: RouteInfo): String {
    val time = "소요시간 ${formatTime(route.totalTime)}"
    val transfer = if (route.transferCount == 0) "환승없음" else "환승 ${route.transferCount}회"
    val lines = route.legs.filter { it.mode != TransitMode.WALK }.mapNotNull { it.routeName }
    val linesText = if (lines.isEmpty()) "" else "이용노선 ${lines.joinToString(" ")}"
    val firstStop = "최초정류장 ${route.firstStopName}"
    return listOf(time, transfer, linesText, firstStop).filter { it.isNotEmpty() }.joinToString("\n")
}

/**
 * 경로 결과 화면
 */
@Composable
fun RouteResultScreen(
    navController: NavController,
    viewModel: RouteResultViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // HomeViewModel에서 검색한 결과 가져오기
    val parentEntry = remember(navController.currentBackStackEntry) {
        navController.getBackStackEntry(Screen.Home.route)
    }
    val homeViewModel: com.app.busiscoming.presentation.screens.home.HomeViewModel = hiltViewModel(parentEntry)
    val homeUiState by homeViewModel.uiState.collectAsState()

    // 검색된 경로를 RouteResultViewModel에 설정
    LaunchedEffect(homeUiState.routes) {
        if (homeUiState.routes.isNotEmpty()) {
            viewModel.setRoutes(homeUiState.routes)
        }
    }

    // 버스 근접/도착 알림 처리
    LaunchedEffect(uiState.busNearby) {
        if (uiState.busNearby) {
            // 진동 및 알림음 처리 (추후 구현)
            // TODO: 진동 및 알림음
        }
    }
    
    LaunchedEffect(uiState.busArrived) {
        if (uiState.busArrived) {
            // 버스 도착 알림
            // TODO: 진동 및 알림음
        }
    }

    RouteResultScreenContent(
        routes = uiState.routes,
        selectedRoute = uiState.selectedRoute,
        telemetryError = uiState.telemetryError,
        busNearby = uiState.busNearby,
        busArrived = uiState.busArrived,
        busDistance = uiState.busDistance,
        onRouteSelect = { route ->
            // 경로 선택 시 텔레메트리 전송 시작 (승차 요청은 보내지 않음)
            viewModel.selectRoute(route)
            viewModel.startTelemetryForRoute(route)
            
            // WalkingGuideScreen으로 바로 이동
            val busNumber = route.legs.firstOrNull { it.mode == TransitMode.BUS }?.routeName ?: ""
            val stopName = route.firstStopName
            val lat = route.firstStopLat
            val lng = route.firstStopLon
            
            navController.navigate(
                Screen.WalkingGuide.createRoute(busNumber, stopName, lat, lng)
            )
        },
        onBack = { 
            viewModel.stopTelemetry()
            navController.navigateUp() 
        },
        onStartNavigation = { route ->
            navController.navigate(
                Screen.Navigation.createRoute(
                    route.firstStopLat,
                    route.firstStopLon,
                    route.firstStopName,
                    route
                )
            )
        },
        onClearBusNearby = viewModel::clearBusNearby,
        onClearBusArrived = viewModel::clearBusArrived
    )
}

/**
 * 경로 결과 화면 컨텐츠
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteResultScreenContent(
    routes: List<RouteInfo>,
    selectedRoute: RouteInfo?,
    telemetryError: String?,
    busNearby: Boolean,
    busArrived: Boolean,
    busDistance: Double?,
    onRouteSelect: (RouteInfo) -> Unit,
    onBack: () -> Unit,
    onStartNavigation: (RouteInfo) -> Unit,
    onClearBusNearby: () -> Unit,
    onClearBusArrived: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "경로 선택",
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
                .padding(paddingValues)
        ) {
            val introFocusRequester = remember { FocusRequester() }
            val introText = remember(routes.size) {
                "총 ${routes.size}개의 경로를 찾았습니다. 현재 제공된 경로는 최소환승순입니다. 화면을 오른쪽으로 쓸어서 원하는 경로를 선택하신 뒤 화면을 더블탭 해주세요."
            }
            LaunchedEffect(routes) {
                if (routes.isNotEmpty()) {
                    introFocusRequester.requestFocus()
                }
            }
            Text(
                text = introText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics { contentDescription = introText }
                    .focusRequester(introFocusRequester)
                    .focusable()
            )
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(routes) { index, route ->
                    val cardModifier = Modifier
                    RouteCard(
                        route = route,
                        isSelected = selectedRoute == route,
                        onClick = { onRouteSelect(route) },
                        modifier = cardModifier
                    )
                }
            }
            
            // 선택된 경로의 상세 정보 및 시작 버튼
            selectedRoute?.let { route ->
                RouteDetailSection(
                    route = route,
                    onStartNavigation = { onStartNavigation(route) }
                )
            }
        }
        
        // 텔레메트리 오류 표시
        telemetryError?.let { error ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        
        // 버스 근접 알림
        if (busNearby) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "버스가 곧 도착합니다",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    busDistance?.let {
                        Text(
                            text = "거리: ${it.toInt()}m",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
        
        // 버스 도착 알림
        if (busArrived) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "버스 도착",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    busDistance?.let {
                        Text(
                            text = "거리: ${it.toInt()}m",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteCard(
    route: RouteInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 2.dp
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, borderColor)
        } else null
    ) {
        val composedText = buildCardText(route)
        Column(
            modifier = Modifier
                .padding(20.dp)
                .semantics { contentDescription = composedText }
        ) {
            Text(
                text = "소요시간 ${formatTime(route.totalTime)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (route.transferCount == 0) "환승없음" else "환승 ${route.transferCount}회",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "이용노선 ${route.legs.filter { it.mode != TransitMode.WALK }.mapNotNull { it.routeName }.joinToString(" ")}",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "최초정류장 ${route.firstStopName}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun RouteDetailSection(
    route: RouteInfo,
    onStartNavigation: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        // Text(
        //     text = "총 거리 ${route.totalDistance}m, 도보 시간 ${route.totalWalkTime / 60}분, 도보 거리 ${route.totalWalkDistance}m",
        //     style = MaterialTheme.typography.bodyMedium
        // )
        // Spacer(modifier = Modifier.height(12.dp))
        // PrimaryButton(
        //     text = "안내 시작",
        //     onClick = onStartNavigation
        // )
    }
}

private fun formatTime(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    
    return when {
        hours > 0 -> "${hours}시간 ${minutes}분"
        else -> "${minutes}분"
    }
}

private fun parseColor(colorString: String?): Long {
    return try {
        if (colorString != null && colorString.length == 6) {
            ("FF$colorString").toLong(16)
        } else {
            0xFF53B332 // 기본 색상
        }
    } catch (e: Exception) {
        0xFF53B332
    }
}

