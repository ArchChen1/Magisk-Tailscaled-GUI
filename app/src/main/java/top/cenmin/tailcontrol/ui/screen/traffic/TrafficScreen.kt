package top.cenmin.tailcontrol.ui.screen.traffic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.cenmin.tailcontrol.R
import top.cenmin.tailcontrol.ui.component.SpeedChart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrafficScreen(viewModel: TrafficViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_traffic)) }) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            if (!ui.hasData) {
                Text(stringResource(R.string.traffic_no_data), style = MaterialTheme.typography.bodyMedium)
                return@Column
            }

            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SpeedChart(
                        rxValues = ui.rxBps,
                        txValues = ui.txBps,
                        rxLabel = stringResource(R.string.traffic_rx),
                        txLabel = stringResource(R.string.traffic_tx),
                    )
                }
            }

            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(stringResource(R.string.traffic_rx), style = MaterialTheme.typography.titleSmall)
                        Text("now: ${formatBps(ui.rxBps.lastOrNull() ?: 0.0)}")
                        Text(stringResource(R.string.traffic_rx_total, formatBytes(ui.totalRxBytes)))
                    }
                    Column {
                        Text(stringResource(R.string.traffic_tx), style = MaterialTheme.typography.titleSmall)
                        Text("now: ${formatBps(ui.txBps.lastOrNull() ?: 0.0)}")
                        Text(stringResource(R.string.traffic_tx_total, formatBytes(ui.totalTxBytes)))
                    }
                }
            }
        }
    }
}
