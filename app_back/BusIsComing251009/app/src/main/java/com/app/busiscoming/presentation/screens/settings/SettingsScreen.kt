package com.app.busiscoming.presentation.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.app.busiscoming.presentation.components.InputField
import com.app.busiscoming.presentation.components.PrimaryButton
import com.app.busiscoming.ui.theme.BusIsComingTheme
import com.app.busiscoming.ui.theme.Gray200

/**
 * 설정 화면
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "설정",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
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
        SettingsScreenContent(
            modifier = Modifier.padding(paddingValues),
            uiState = uiState,
            onDeviceIdChange = { /* 읽기 전용 */ },
            onServerIpChange = viewModel::updateServerIp,
            onPortChange = viewModel::updateServerPort,
            onPingTestClick = viewModel::pingTest,
            onClearPingResult = viewModel::clearPingResult
        )
    }
}

@Composable
private fun SettingsScreenContent(
    modifier: Modifier = Modifier,
    uiState: SettingsUiState,
    onDeviceIdChange: (String) -> Unit,
    onServerIpChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onPingTestClick: () -> Unit,
    onClearPingResult: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        // 설정 카드
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp)
        ) {
            // 단말기 ID
            Text(
                text = "단말기 ID",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            InputField(
                value = uiState.deviceId,
                onValueChange = onDeviceIdChange,
                placeholder = "단말기 ID를 입력하세요",
                readOnly = true
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            Divider(color = Gray200)
            Spacer(modifier = Modifier.height(20.dp))
            
            // 서버 IP
            Text(
                text = "서버 IP",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            InputField(
                value = uiState.serverIp,
                onValueChange = onServerIpChange,
                placeholder = "서버 IP를 입력하세요"
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            Divider(color = Gray200)
            Spacer(modifier = Modifier.height(20.dp))
            
            // 포트
            Text(
                text = "포트",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            InputField(
                value = uiState.port,
                onValueChange = onPortChange,
                placeholder = "포트를 입력하세요"
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 핑테스트 버튼
        PrimaryButton(
            text = if (uiState.isPinging) "테스트 중..." else "핑테스트",
            onClick = onPingTestClick,
            enabled = !uiState.isPinging
        )
        
        // 핑 테스트 결과
        uiState.pingResult?.let { result ->
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Ping Success",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    result.serverTime?.let {
                        Text(
                            text = "Server Time: $it",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    result.rtt?.let {
                        Text(
                            text = "RTT: ${it}ms",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
        
        uiState.pingError?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "연결 실패",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SettingsScreenPreview() {
    BusIsComingTheme {
        SettingsScreenContent(
            uiState = SettingsUiState(
                deviceId = "android-12345678",
                serverIp = "192.168.0.1",
                port = "3000"
            ),
            onDeviceIdChange = {},
            onServerIpChange = {},
            onPortChange = {},
            onPingTestClick = {},
            onClearPingResult = {}
        )
    }
}

