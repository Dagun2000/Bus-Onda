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
import com.app.busiscoming.util.SelectedRouteHolder // [추가] 글로벌 홀더 임포트
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
    val context = LocalContext.current

    // HomeViewModel에서 검색한 결과 가져오기
    val parentEntry = remember(navController.currentBackStackEntry) {
        navController.getBackStackEntry(Screen.Home.route)
    }
    val homeViewModel: com.app.busiscoming.presentation.screens.home.HomeViewModel = hiltViewModel(parentEntry)
    val homeUiState by homeViewModel.uiState.collectAsState()

    // 검색된 경로를 ViewModel에 설정 (최초 1회)
    LaunchedEffect(homeUiState.routes) {
        if (homeUiState.routes.isNotEmpty() && uiState.routes.isEmpty()) {
            viewModel.setRoutes(homeUiState.routes)
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
            // 1. [핵심] 글로벌 변수에 전체 경로 저장 및 인덱스 0으로 초기화
            SelectedRouteHolder.setRoute(route)

            // 2. ViewModel 상태 업데이트 및 텔레메트리 준비
            viewModel.selectRoute(route)
            viewModel.startTelemetryForRoute(route)

            // 3. 첫 번째 버스 정보 추출 (가이드 화면 표시용)
            val firstBusLeg = route.legs.firstOrNull { it.mode == TransitMode.BUS }
            val busNumber = firstBusLeg?.routeName ?: ""

            // 4. 다음 화면(첫 번째 정류장까지 도보 가이드)으로 이동
            navController.navigate(
                Screen.WalkingGuide.createRoute(
                    busNumber = busNumber,
                    stopName = route.firstStopName, // 타야 할 정류장 이름
                    lat = route.firstStopLat,      // 타야 할 정류장 위도
                    lng = route.firstStopLon       // 타야 할 정류장 경도
                )
            )
        },
        onBack = {
            viewModel.stopTelemetry()
            SelectedRouteHolder.clear() // [추가] 뒤로 갈 때 데이터 초기화
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
                }
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
            val introText = "총 ${routes.size}개의 경로를 찾았습니다. 원하는 경로를 선택하신 뒤 더블탭 해주세요."

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
                    RouteCard(
                        route = route,
                        isSelected = selectedRoute == route,
                        onClick = { onRouteSelect(route) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteCard(
    route: RouteInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
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
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (route.transferCount == 0) "환승없음" else "환승 ${route.transferCount}회",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(6.dp))
            val busLines = route.legs.filter { it.mode != TransitMode.WALK }.mapNotNull { it.routeName }.joinToString(" ")
            Text(
                text = "이용노선 $busLines",
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

private fun formatTime(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "${hours}시간 ${minutes}분" else "${minutes}분"
}