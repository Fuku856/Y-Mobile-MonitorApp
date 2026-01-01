package com.hachi.ymobilemonitor.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.hachi.ymobilemonitor.data.YMobileData
import com.hachi.ymobilemonitor.ui.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: UiState,
    onRefresh: () -> Unit,
    onLogout: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("データ残量") },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "更新")
                    }
                    TextButton(onClick = onLogout) {
                        Text("ログアウト")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.data != null) {
                DashboardContent(uiState.data)
            } else if (uiState.error != null) {
                Text(
                    text = uiState.error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
fun DashboardContent(data: YMobileData) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 円グラフカード
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(200.dp)
                ) {
                    DataUsageChart(
                        usagePercent = (data.percentage / 100).toFloat(),
                        modifier = Modifier.fillMaxSize()
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "残り",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = "${data.remainingGb} GB",
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "/ ${data.totalGb} GB",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        // 詳細情報
        Text(
            text = "内訳",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(bottom = 8.dp)
        )
        
        DetailItem(label = "基本データ量", value = "${data.kihonGb} GB")
        DetailItem(label = "繰越データ量", value = "${data.kurikoshiGb} GB")
        DetailItem(label = "追加購入分", value = "${data.yuryouGb} GB")
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        DetailItem(label = "使用済みデータ量", value = "${data.usedGb} GB", isHighlight = true)

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "最終更新: ${data.updatedAt}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
fun DetailItem(label: String, value: String, isHighlight: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isHighlight) MaterialTheme.colorScheme.primary else Color.Unspecified
        )
    }
}

@Composable
fun DataUsageChart(usagePercent: Float, modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Canvas(modifier = modifier) {
        val strokeWidth = 20.dp.toPx()
        val radius = size.minDimension / 2 - strokeWidth / 2
        
        // 背景の円 (グレー)
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            size = Size(radius * 2, radius * 2),
            topLeft = center.minus(androidx.compose.ui.geometry.Offset(radius, radius))
        )

        // 使用率の円 (プライマリカラー)
        // startAngle -90f (12時)
        // usagePercent は「使用率」なので、残量を示すなら reverse だが、
        // 「円グラフ」としては使用分を塗るのが一般的。
        // ここでは「使用分」を描画する。
        drawArc(
            color = primaryColor,
            startAngle = -90f,
            sweepAngle = 360f * usagePercent,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            size = Size(radius * 2, radius * 2),
            topLeft = center.minus(androidx.compose.ui.geometry.Offset(radius, radius))
        )
    }
}
