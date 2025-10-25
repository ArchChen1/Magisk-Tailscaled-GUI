package top.cenmin.tailcontrol

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.material.icons.filled.Save
import android.Manifest
import android.content.Context.MODE_PRIVATE
import android.os.Build
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
// 全局日志
val commandLogs = mutableStateListOf<Pair<String, String>>()

class MainActivity : ComponentActivity() {

    private val currentSettings = mutableStateOf(TailscaleSettings())
    // 请求通知权限的 launcher
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                Log.d("Tailscale", "通知权限已授予")
            } else {
                Log.d("Tailscale", "通知权限未授予，但不影响运行")
            }
        }

    @SuppressLint("SdCardPath")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!checkRootAccess()) {
            showRootDialog()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33+
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        currentSettings.value = loadSettings()

        val dropPrefs = getSharedPreferences("drop_prefs", MODE_PRIVATE)
        val dropEnabled = dropPrefs.getBoolean("drop_enabled", false)
        val dropPath = dropPrefs.getString("drop_path", "/sdcard/Download/TailDrop/")
        val dropBehavior = dropPrefs.getString("conflict_behavior", "rename")
        val showDropScreen = intent?.getBooleanExtra("show_drop_screen", false) == true
        val initialTab = if (showDropScreen) 1 else 0

        val dropIntent = Intent(this, DropProtectService::class.java).apply {
            action = if (dropEnabled) DropProtectService.ACTION_START else DropProtectService.ACTION_STOP
            if (dropEnabled) {
                putExtra(DropProtectService.EXTRA_PATH, dropPath)
                putExtra(DropProtectService.EXTRA_BEHAVIOR, dropBehavior)
            }
        }

        if (dropEnabled) startForegroundService(dropIntent) else startService(dropIntent)

        setContent { MainScreen(initialTab) }
    }

    override fun onResume() {
        super.onResume()
        currentSettings.value = loadSettings()
    }

    private fun checkRootAccess(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            process.waitFor()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            output.contains("uid=0")
        } catch (e: Exception) {
            Log.d("Tailscale", "checkRootAccess: $e")
            false
        }
    }

    private fun showRootDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.get_root_failed_title))
            .setMessage(getString(R.string.get_root_failed_msg))
            .setCancelable(false)
            .setPositiveButton(getString(R.string.exit)) { _, _ -> finish() }
            .show()
    }

    private fun loadSettings(): TailscaleSettings {
        val sp = getSharedPreferences("tailscale_prefs", MODE_PRIVATE)
        return TailscaleSettings(
            acceptRoutes = sp.getBoolean("acceptRoutes", true),
            acceptDns = sp.getBoolean("acceptDns", false),
            exitNode = sp.getString("exitNode", "") ?: "",
            advertiseExitNode = sp.getBoolean("advertiseExitNode", false),
            advertiseRoutes = sp.getString("advertiseRoutes", "") ?: "",
            customName = sp.getString("customName", "") ?: "",
            customParams = sp.getString("customParams", "") ?: ""
        )
    }
}

// 数据类
data class TailscaleSettings(
    var acceptRoutes: Boolean = true,
    var acceptDns: Boolean = false,
    var exitNode: String = "",
    var advertiseExitNode: Boolean = false,
    var advertiseRoutes: String = "",
    var customName: String = "",
    var customParams: String = ""
) {
    fun toArgs(): String {
        val args = mutableListOf<String>()
        args.add("--accept-routes=$acceptRoutes")
        args.add("--accept-dns=$acceptDns")
        args.add("--advertise-exit-node=$advertiseExitNode")
        args.add("--exit-node '${exitNode}'")
        args.add("--advertise-routes '${advertiseRoutes}'")
        args.add("--hostname '${customName}'")
        args.add(customParams)
        return args.joinToString(" ")
    }
}

object DropOutput {
    val outputFlow = MutableStateFlow("")
}


@Composable
fun MainScreen(initialTab: Int) {
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    val context = LocalContext.current
    val settings = remember { mutableStateOf(loadSettings(context)) }
    val hasUnsavedChanges = remember { mutableStateOf(false) }
    val username = remember { mutableStateOf("NeedsLogin") }
    val isLoggedIn = remember { mutableStateOf(false) }
    val tailscaleStatus = remember { mutableStateOf("unknown") }
    val deviceList = remember { mutableStateOf(listOf<TailscaleDevice>()) }
    val deviceStatus = remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val backendState = remember { mutableStateOf("") }
    val ip = remember { mutableStateOf("") }
    val hostName = remember { mutableStateOf("") }
    val connection = remember { mutableStateOf("offline") }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) }, label = { Text(stringResource(R.string.home)) })
                NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) }, label = { Text(stringResource(R.string.drop)) })
                NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) }, label = { Text(stringResource(R.string.settings)) })
            }
        }
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> TailscaleControlScreen(tailscaleStatus, deviceList, deviceStatus, scope, backendState, hostName , connection, ip)
                1 -> ADropScreen(tailscaleStatus)
                2 -> ASettingsScreen(username, isLoggedIn, settings, hasUnsavedChanges)
            }
        }
    }
}

// ------------------------ 首页 ------------------------
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TailscaleControlScreen(
    tailscaleStatus: MutableState<String>,
    deviceList: MutableState<List<TailscaleDevice>>,
    deviceStatus: MutableState<String>,
    scope: CoroutineScope,
    backendState: MutableState<String>,
    hostName: MutableState<String>,
    connection: MutableState<String>,
    ip: MutableState<String>
) {
    var autoRefresh by remember { mutableStateOf(true) }
    var countDown by remember { mutableIntStateOf(0) }
    var netcheckReport by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val refreshState = rememberPullToRefreshState()


    fun refreshStatus() {
        scope.launch(Dispatchers.IO) {
            val output = executeRootCommand("tailscale status --json")
            deviceStatus.value = output
            withContext(Dispatchers.Main) {
                // 更新 tailscaleStatus、deviceList
                isLoading = false
            }
            val newStatus = try {
                if (deviceStatus.value.contains("failed to")) "守护进程离线"
                else JSONObject(deviceStatus.value).optString("BackendState", "未知").let {
                    when (it) {
                        "Stopped" -> "服务已停止"
                        "Running" -> "服务运行中"
                        "Starting" -> "服务启动中"
                        "NeedsLogin" -> "未登录"
                        else -> "未知"
                    }
                }
            } catch (e: Exception) {
                Log.d("TailControl", "主活动: [$e]")
                "未知"
            }

            val devices = try {
                parseDevicesFromJson(deviceStatus.value).sortedWith(
                    compareBy<TailscaleDevice> { !it.online }.thenByDescending { it.lastSeen ?: "9999-99-99 99:99:99" }
                )
            } catch (e: Exception) {
                Log.d("TailControl", "主活动: [$e]")
                emptyList()
            }

            withContext(Dispatchers.Main) {
                tailscaleStatus.value = newStatus
                deviceList.value = devices
            }
        }
    }

    LaunchedEffect(autoRefresh) {
        if (!isLoading) {
            isLoading = true
            refreshStatus()
            isLoading = false
        }
        while (autoRefresh) {
            for (i in 5 downTo 1) { countDown = i; delay(1000L) }
            Log.d("TailControl", "主活动: [$countDown]")
            refreshStatus()
        }
    }
    @Composable
    fun TailscaleStatusCard(
        deviceStatusJson: String,
        onClick: (String) -> Unit // 新增回调参数
    ) {
        // 解析 JSON 获取 BackendState 和 Self 信息
        LaunchedEffect(deviceStatusJson) {
            // 在后台线程解析 JSON
            val result = withContext(Dispatchers.Default) {
                try {
                    if (deviceStatusJson.contains("failed to connect")) {
                        mapOf(
                            "backendState" to "守护进程离线",
                            "rawHostName" to "Unknown",
                            "connection" to "离线",
                            "dnsName" to "Unknown",
                            "hostName" to "Unknown",
                            "ip" to "Unknown"
                        )
                    } else {
                        val root = JSONObject(deviceStatusJson)
                        val backendStateStr = when (root.optString("BackendState", "Unknown")) {
                            "Stopped" -> "服务已停止"
                            "Running" -> "服务运行中"
                            "Starting" -> "服务启动中"
                            "NeedsLogin" -> "未登录"
                            else -> "未知"
                        }
                        val self = root.optJSONObject("Self")
                        val rawHostNameStr = self?.optString("HostName", "Unknown") ?: "Unknown"
                        val connectionStr = if (self?.optBoolean("Online", false) == true) "在线" else "离线"
                        val dnsNameStr = self?.optString("DNSName", "Unknown") ?: "Unknown"
                        val hostNameStr = if (dnsNameStr.isNotBlank()) dnsNameStr.trimEnd('.').split(".").firstOrNull() ?: "Unknown" else rawHostNameStr
                        val ips = self?.optJSONArray("TailscaleIPs")
                        val ipStr = ips?.let { arr ->
                            var ipv4: String? = null
                            for (i in 0 until arr.length()) {
                                val candidate = arr.getString(i)
                                if (candidate.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) {
                                    ipv4 = candidate
                                    break
                                }
                            }
                            ipv4 ?: arr.getString(0)
                        } ?: "Unknown"

                        mapOf(
                            "backendState" to backendStateStr,
                            "rawHostName" to rawHostNameStr,
                            "connection" to connectionStr,
                            "dnsName" to dnsNameStr,
                            "hostName" to hostNameStr,
                            "ip" to ipStr
                        )
                    }
                } catch (e: Exception) {
                    Log.d("TailControl", "主活动: [$e]")
                    mapOf(
                        "backendState" to "未知",
                        "rawHostName" to "Unknown",
                        "connection" to "离线",
                        "dnsName" to "Unknown",
                        "hostName" to "Unknown",
                        "ip" to "Unknown"
                    )
                }
            }

            // 切回主线程安全更新 State
            backendState.value = result["backendState"] as String
            connection.value = result["connection"] as String
            hostName.value = result["hostName"] as String
            ip.value = result["ip"] as String
        }


        val statusColor = when {
            backendState.value == "服务运行中" && connection.value == "在线" -> Color(0xFF4CAF50) // 绿
            backendState.value == "服务运行中" && connection.value == "离线" -> Color(0xFFFF9800) // 粉
            backendState.value == "守护进程离线" -> Color(0xFFF44336) // 红
            backendState.value == "服务已停止" -> Color(0xFFFF8899) // 粉
            backendState.value == "服务启动中" -> Color(0xFFFF8899) // 粉
            backendState.value == "未登录"  -> Color(0xFFFFC107) // 黄
            else -> Color.Gray
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clickable(
                    onClick = { onClick(hostName.value) },
                    indication = rememberRipple(), // 点击时显示水波纹
                    interactionSource = remember { MutableInteractionSource() }
                ),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            // 如果 netcheckReport 不为空，就显示弹窗
            if (netcheckReport != null) {
                AlertDialog(
                    onDismissRequest = { netcheckReport = null },
                    title = { Text(stringResource(R.string.netcheck_report_title)) },
                    text = {
                        Box(
                            Modifier
                                .heightIn(max = 400.dp)  // 最大高度限制
                                .verticalScroll(rememberScrollState()) // 可滑动
                        ) {
                            SelectionContainer { // 可选择复制
                                Text(netcheckReport!!)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { netcheckReport = null }) {
                            Text(stringResource(R.string.close))
                        }
                    }
                )
            }

            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Canvas(modifier = Modifier.size(12.dp)) {
                    drawCircle(color = statusColor)
                }

                Spacer(Modifier.width(8.dp))
                val displayname = when (backendState.value) {
                    "服务运行中" -> stringResource(R.string.status_service_running)
                    "守护进程离线" -> stringResource(R.string.status_protect_offline)
                    "服务已停止" -> stringResource(R.string.status_service_stopped)
                    "未登录" -> stringResource(R.string.status_service_needslogin)
                    "服务启动中" -> stringResource(R.string.status_service_starting)
                    else -> stringResource(R.string.unknown)
                }
                val displayconnect = when {
                    connection.value == "在线" -> stringResource(R.string.status_online)
                    else -> stringResource(R.string.status_offline)
                }
                Column {
                    Text("${stringResource(R.string.status)}: $displayname - $displayconnect", style = MaterialTheme.typography.titleMedium)
                    Text("${stringResource(R.string.hostname)}: ${hostName.value}", style = MaterialTheme.typography.titleMedium)
                    Text("IP: ${ip.value}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tailscale") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        content = { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding) // 避免被 Scaffold 顶部/底部遮挡
                    .padding(horizontal = 16.dp)
            ) {
                val loadingText = stringResource(R.string.loading)
                val reportFailedText = stringResource(R.string.netcheck_report_failed)
                TailscaleStatusCard(deviceStatus.value) { _ ->
                    // 在 Composable 上下文调用 stringResource
                    netcheckReport = loadingText
                    scope.launch(Dispatchers.IO) {
                        val output = executeRootCommand("tailscale netcheck")
                        val report = output.substringAfter("Report:").trim().ifEmpty { reportFailedText }
                        withContext(Dispatchers.Main) { netcheckReport = report }
                    }
                }


                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            when (tailscaleStatus.value) {
                                "服务已停止","未登录" -> executeRootCommand("tailscale up")
                                "服务运行中" -> executeRootCommand("tailscale down")
                                "服务启动中" -> executeRootCommand("tailscale down")
                                "守护进程离线" -> executeRootCommand("tailscaled.service start && tailscale up")
                                else -> executeRootCommand("tailscaled.service restart && tailscale up")
                            }
                            refreshStatus()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    Text(if (tailscaleStatus.value.contains("运行" )|| tailscaleStatus.value.contains("未登录")) stringResource(R.string.stop_tailscale) else stringResource(R.string.start_tailscale))
                }

                Text(
                    stringResource(R.string.device_list),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(vertical = 4.dp)
                )

                Spacer(Modifier.height(8.dp))

                Box(
                    Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .nestedScroll(refreshState.nestedScrollConnection)
                        .zIndex(-1f)
                ) {
                    val context = LocalContext.current

                    LazyColumn(modifier = Modifier.fillMaxWidth().fillMaxSize()) {
                        items(deviceList.value) { device ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable(
                                        indication = rememberRipple(bounded = true, color = Color(0xFF6200EE)), // 水波纹效果
                                        interactionSource = remember { MutableInteractionSource() },
                                        onClick = {} // 空的 onClick 处理函数
                                    )
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onLongPress = {
                                                // 长按时复制 IP 地址
                                                val clipboard = context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = ClipData.newPlainText("Device IP", device.ip)
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(context, "IP 地址已复制: ${device.ip}", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    },
                                elevation = CardDefaults.cardElevation(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Canvas(modifier = Modifier.size(12.dp)) {
                                        drawCircle(
                                            color = if (device.online) Color(0xFF4CAF50) else Color(0xFFF44336)
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text("${stringResource(R.string.name)}: ${device.name}", style = MaterialTheme.typography.titleMedium)
                                        Text("IP: ${device.ip}", style = MaterialTheme.typography.bodyMedium)
                                        if (!device.online) Text(
                                            "${stringResource(R.string.last_seen)}: ${device.lastSeen}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    PullToRefreshContainer(
                        state = refreshState,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-8).dp)
                    )

                    LaunchedEffect(refreshState.isRefreshing) {
                        if (refreshState.isRefreshing) {
                            refreshStatus()
                            delay(500)
                            refreshState.endRefresh()
                        }
                    }
                }
            }
        }
    )

}


// ------------------------ 传送 ------------------------
@SuppressLint("SdCardPath")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ADropScreen(
    tailscaleStatus: MutableState<String>
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // 控制 dialog 显示
    var showPingDialog by remember { mutableStateOf(false) }
    var pingOutput by remember { mutableStateOf("") }
    var isPinging by remember { mutableStateOf(false) }
    var pingProcess: Process? by remember { mutableStateOf(null) }
    var pingJob by remember { mutableStateOf<Job?>(null) }
    // 获取 SharedPreferences
    val prefs = context.getSharedPreferences("drop_prefs", 0)

    // 初始化开关状态，从 SharedPreferences 读取
    val dropProtectEnabled by produceState(initialValue = prefs.getBoolean("drop_enabled", false)) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "drop_enabled") value = prefs.getBoolean("drop_enabled", false)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    // 初始化路径，从 SharedPreferences 读取，如果没有则使用默认
    var dropProtectPath by remember {
        mutableStateOf(prefs.getString("drop_path", "/sdcard/Download/TailDrop/")!!)
    }
    // 初始化冲突处理行为
    var conflictBehavior by remember {
        mutableStateOf(prefs.getString("conflict_behavior", "rename")!!)
    }

    // 在 DropScreen 内部添加 ping 地址的状态，持久化
    var pingAddress by remember {
        mutableStateOf(prefs.getString("ping_address", "")!!)
    }

    // 初始化下拉菜单开关
    var expanded by remember {
        mutableStateOf(false)
    }
    val conflictOptions = listOf(
        stringResource(R.string.rename) to "rename",
        stringResource(R.string.skip) to "skip",
        stringResource(R.string.overwrite) to "overwrite"
    )
    // 收集保护进程输出
    val outputText by DropOutput.outputFlow.collectAsState()

    // 检查 Tailscale 状态
    LaunchedEffect(Unit) {
        val status = withContext(Dispatchers.IO) {
            runCatching {
                Runtime.getRuntime()
                    .exec(arrayOf("su", "-c", "tailscale status --json"))
                    .inputStream.bufferedReader()
                    .readText()
            }.getOrNull()
        }

        tailscaleStatus.value = try {
            if (status?.contains("failed to") ?: true) {
                "守护进程离线"
            } else {
                val root = JSONObject(status)
                when (root.optString("BackendState", "Unknown")) {
                    "Stopped" -> "服务已停止"
                    "Running" -> "服务运行中"
                    "Starting" -> "服务启动中"
                    "NeedsLogin" -> "未登录"
                    else -> "未知"
                }
            }
        } catch (e: Exception) {
            Log.d("TailControl", "Drop活动: [$e]")
            "未知"
        }
    }

    // 文件夹选择器
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            uri?.let {
                val pickedPath = uri.path?.replace("/tree/primary:", "/sdcard/") ?: return@let
                dropProtectPath = pickedPath
                prefs.edit { putString("drop_path", dropProtectPath) }

                // ✅ 用 context.getString 代替 stringResource
                Toast.makeText(
                    context,
                    "${context.getString(R.string.path_selected)}: $dropProtectPath",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tailscale Drop") },
                modifier = Modifier
                    .fillMaxWidth() // 确保顶栏占据屏幕宽度
            )
        },
        content = { padding ->
            Column(
                modifier = Modifier
                    .padding(padding) // 使用传递过来的 padding，避免重复设置
                    .padding(horizontal = 16.dp) // 控制左右的 padding
                    .verticalScroll(rememberScrollState()) // 使内容可以滚动
                    .fillMaxSize(), // 确保内容区充满剩余空间
                verticalArrangement = Arrangement.spacedBy(12.dp) // 控制各项控件之间的间距
            ) {
                Spacer(Modifier.height(3.dp))
                // 1. 接受文件保护进程
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.elevatedCardElevation(4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(context.getString(R.string.file_receiver_daemon),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold)
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = dropProtectEnabled,
                                    onCheckedChange = { wantOn ->
                                        // 1. 想打开 → 必须验证
                                        if (wantOn && tailscaleStatus.value != "服务运行中") {
                                            Toast.makeText(
                                                context,
                                                "Tailscale ${context.getString(R.string.status_service_stopped)}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            return@Switch          // 直接拒绝，状态保持 off
                                        }

                                        // 2. 通过验证 or 原本就是关 → 真正翻转
                                        prefs.edit { putBoolean("drop_enabled", wantOn) }
                                        val intent = Intent(context, DropProtectService::class.java).apply {
                                            action = if (wantOn) DropProtectService.ACTION_START
                                            else DropProtectService.ACTION_STOP
                                            if (wantOn) {
                                                putExtra(DropProtectService.EXTRA_PATH, dropProtectPath)
                                                putExtra(DropProtectService.EXTRA_BEHAVIOR, conflictBehavior)
                                            }
                                        }
                                        if (wantOn) context.startForegroundService(intent)
                                        else context.startService(intent)
                                    },
                                    enabled = true   // 控件本身永远可点，逻辑内部拦截
                                )
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = { folderPickerLauncher.launch(null) },
                                    modifier = Modifier.width(110.dp)
                                ) {
                                    Text(context.getString(R.string.select_path))
                                }
                            }
                        }
                        Text("${context.getString(R.string.current_path)}：$dropProtectPath", style = MaterialTheme.typography.bodySmall)
                    }
                }

                // 2. 冲突处理方式
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.elevatedCardElevation(4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(context.getString(R.string.conflict_behavior),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold)
                            )
                            Spacer(Modifier.weight(1f))
                            Box { // 按钮 + 下拉菜单锚点
                                Button(
                                    onClick = { expanded = true },
                                    modifier = Modifier.width(110.dp)
                                ) {
                                    Text(conflictOptions.find { it.second == conflictBehavior }?.first ?: "重命名")
                                }
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    conflictOptions.forEach { (label, value) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                conflictBehavior = value
                                                prefs.edit { putString("conflict_behavior", conflictBehavior) }
                                                expanded = false
                                                Toast.makeText(context, "${context.getString(R.string.conflict_behavior_set_to)}: $label", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. 分享文件
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.elevatedCardElevation(4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(context.getString(R.string.share_file),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold)
                                )
                            }
                            Spacer(Modifier.weight(1f)) // 占位，使按钮靠右
                            val filePickerLauncher = rememberLauncherForActivityResult(
                                contract = ActivityResultContracts.GetContent(),
                                onResult = { uri ->
                                    uri?.let {
                                        val intent = Intent(context, FileShareActivity::class.java).apply {
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                        }
                                        context.startActivity(intent)
                                    }
                                }
                            )
                            Button(
                                onClick = { filePickerLauncher.launch("*/*") },
                                modifier = Modifier.width(110.dp),
                                enabled = tailscaleStatus.value == "服务运行中"
                            ) {
                                Text(context.getString(R.string.select_file))
                            }
                        }
                    }
                }

                // 4. Ping 测试
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.elevatedCardElevation(4.dp)
                ){
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Ping",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold)
                            )
                            Spacer(Modifier.width(8.dp))
                            // 输入框
                            OutlinedTextField(
                                value = pingAddress,
                                onValueChange = {
                                    pingAddress = it
                                    prefs.edit { putString("ping_address", pingAddress) }
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                placeholder = { Text(context.getString(R.string.ping_placeholder)) },
                                shape = RoundedCornerShape(12.dp) // 👈 圆角边框
                            )
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {

                                    showPingDialog = true
                                    pingOutput = ""
                                    isPinging = true

                                    // 启动新的协程
                                    val job = coroutineScope.launch {
                                        try {
                                            withContext(Dispatchers.IO) {
                                                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "tailscale ping $pingAddress"))
                                                pingProcess = process  // 保存引用
                                                val reader = process.inputStream.bufferedReader()
                                                var line: String?
                                                while (reader.readLine().also { line = it } != null) {
                                                    // 切回主线程更新 UI
                                                    withContext(Dispatchers.Main) {
                                                        pingOutput += line + "\n"
                                                    }
                                                }
                                                reader.close()
                                                process.waitFor()

                                            }
                                        } catch (e: Exception) {
                                            pingOutput += if (e.message != "read interrupted") "\n[错误] ${e.message}"
                                            else "\n已终止输出"
                                        } finally {
                                            isPinging = false
                                            pingProcess = null
                                            pingJob = null
                                        }
                                    }
                                    pingJob = job
                                },
                                enabled = tailscaleStatus.value == "服务运行中",
                                modifier = Modifier.width(110.dp),
                            ) {
                                Text(context.getString(R.string.test))
                            }
                        }
                    }
                }

                // 弹出对话框
                if (showPingDialog) {
                    AlertDialog(
                        onDismissRequest = { if (!isPinging) showPingDialog = false },
                        title = { Text("Ping ${context.getString(R.string.test)}") },
                        text = {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(4.dp)
                            ) {
                                Text(pingOutput)
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    if (isPinging) {
                                        // 取消：杀掉进程 + 取消协程
                                        pingProcess?.destroy()
                                        pingJob?.cancel()
                                        isPinging = false
                                        pingProcess = null
                                        pingJob = null
                                    } else {
                                        // 确认关闭
                                        showPingDialog = false
                                    }
                                }
                            ) {
                                Text(if (isPinging) context.getString(R.string.cancel) else context.getString(R.string.done))
                            }
                        }

                    )
                }

                // 5. 强制杀掉所有tailscale
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.elevatedCardElevation(4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(context.getString(R.string.fix_duplicate_file),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold)
                                )
                            }
                            Spacer(Modifier.weight(1f)) // 占位，使按钮靠右

// Compose 内状态
                            var showKillDialog by remember { mutableStateOf(false) }
                            val context = LocalContext.current
                            var tailscaleRunning by remember { mutableStateOf(false) }
                            var fewTailscaleProcesses by remember { mutableStateOf(false) }

                            Button(
                                onClick = {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        var output = ""
                                        try {
                                            output = Runtime.getRuntime()
                                                .exec(arrayOf("su", "-c", "pgrep -x tailscale"))
                                                .inputStream.bufferedReader().readText()
                                        } catch (_: Exception) {
                                        }

                                        val lines = output.lines().filter { it.isNotBlank() }
                                        tailscaleRunning = lines.size >= 3
                                        val fewProcesses = lines.size in 1..2

                                        withContext(Dispatchers.Main) {
                                            showKillDialog = true
                                            // 这里可以用一个额外状态记录 fewProcesses
                                            fewTailscaleProcesses = fewProcesses
                                        }
                                    }
                                },
                                modifier = Modifier.width(110.dp)
                            ) {
                                Text(context.getString(R.string.execute))
                            }


                            if (showKillDialog) {
                                AlertDialog(
                                    onDismissRequest = { showKillDialog = false },
                                    title = {
                                        Text(
                                            if (!tailscaleRunning) context.getString(R.string.oops)
                                            else context.getString(R.string.kill_process)
                                        )
                                    },
                                    text = {
                                        Text(
                                            when {
                                                !tailscaleRunning && !fewTailscaleProcesses -> context.getString(R.string.no_tail_process)
                                                fewTailscaleProcesses -> context.getString(R.string.only_one_process)
                                                else -> context.getString(R.string.multi_process)
                                            }
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            if (tailscaleRunning || fewTailscaleProcesses) {
                                                // 杀掉 tailscale
                                                CoroutineScope(Dispatchers.IO).launch {
                                                    try {
                                                        Runtime.getRuntime().exec(arrayOf("su", "-c", "pkill -9 -x tailscale"))
                                                            .waitFor()
                                                        // 关闭 DropProtectService
                                                        val intent = Intent(context, DropProtectService::class.java).apply {
                                                            action = DropProtectService.ACTION_STOP
                                                        }
                                                        context.startService(intent)

                                                        // 更新开关状态
                                                        val prefs = context.getSharedPreferences("drop_prefs", MODE_PRIVATE)
                                                        prefs.edit { putBoolean("drop_enabled", false) }
                                                    } catch (e: Exception) {
                                                        Log.e("TailControl", "kill tailscale failed: $e")
                                                    }
                                                }
                                            }
                                            showKillDialog = false
                                        }) {
                                            Text(if (!tailscaleRunning && !fewTailscaleProcesses) context.getString(R.string.done) else context.getString(R.string.confirm))
                                        }
                                    },
                                    dismissButton = {
                                        if (tailscaleRunning||fewTailscaleProcesses) {
                                            TextButton(onClick = { showKillDialog = false }) {
                                                Text(context.getString(R.string.cancel))
                                            }
                                        }
                                    }
                                )
                            }

                        }
                    }
                }
                // 6. 输出保护进程日志
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.elevatedCardElevation(4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Text("${context.getString(R.string.daemon_output)}:", style = MaterialTheme.typography.bodyMedium)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .border(1.dp, Color.Gray)
                                .verticalScroll(rememberScrollState())
                                .padding(4.dp)
                        ) {
                            Text(outputText)
                        }
                    }
                }

                Text(
                    context.getString(R.string.tip),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth() // ✅ 让 Text 占满父级宽度
                )
            }
        }
    )
}

// ------------------------ 设置 ------------------------
@Composable
fun ASettingsScreen(
    username: MutableState<String>,
    isLoggedIn: MutableState<Boolean>,
    settings: MutableState<TailscaleSettings>,
    hasUnsavedChanges: MutableState<Boolean>
) {
    val context = LocalContext.current
    var showErrorDialog by remember { mutableStateOf(false to "") }
    var showSaveDialog by remember { mutableStateOf(false to "") }
    var showLoginDialog by remember { mutableStateOf(false to "") }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var loginUrl by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            val json = withContext(Dispatchers.IO) {
                executeRootCommand("tailscale status --json")
            }

            val parsed = try { JSONObject(json) } catch (_: Exception) { JSONObject() }
            val backendState = parsed.optString("BackendState", "NeedsLogin")

            if (backendState == "NeedsLogin") {
                isLoggedIn.value = false
                username.value = context.getString(R.string.status_service_needslogin)
            } else {
                isLoggedIn.value = true
                val self = parsed.optJSONObject("Self")
                val userId = self?.optLong("UserID") ?: -1L
                val usersObj = parsed.optJSONObject("User")
                val userJson = usersObj?.optJSONObject(userId.toString())
                username.value = userJson?.optString("DisplayName") ?: context.getString(R.string.unknown)
            }

        } catch (_: Exception) {
            isLoggedIn.value = false
            username.value = context.getString(R.string.unknown)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SettingsScreen(
        username: String,
        isLoggedIn: Boolean,
        initialSettings: TailscaleSettings,
        onSave: (TailscaleSettings) -> Unit,
        onLogin: () -> Unit,
        onRequestLogout: () -> Unit
    ) {
        var localSettings by remember { mutableStateOf(initialSettings) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            context.getString(R.string.tailscale_settings),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth() // 确保顶栏占据屏幕宽度
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {onSave(localSettings)},
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(Icons.Filled.Save, contentDescription = "save")
                }
            },
            floatingActionButtonPosition = FabPosition.End,
            content = { padding ->
                Column(
                    modifier = Modifier
                        .padding(padding) // 使用传递过来的 padding，避免重复设置
                        .padding(horizontal = 16.dp) // 控制左右的 padding
                        .verticalScroll(rememberScrollState()) // 使内容可以滚动
                        .fillMaxSize(), // 确保内容区充满剩余空间
                    verticalArrangement = Arrangement.spacedBy(12.dp) // 控制各项控件之间的间距
                ) {
                    Spacer(Modifier.height(3.dp))
                    // 用户信息卡片，所有内容在一行显示
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.elevatedCardElevation(4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f) // 确保 Column 可以占用剩余空间
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically // 确保文本垂直居中
                                ) {
                                    // "用户："部分，粗体
                                    Text(
                                        text = "${context.getString(R.string.user)}:",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold // 粗体
                                        )
                                    )

                                    // `$username`部分，普通字体并支持滚动
                                    Text(
                                        text = username,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = 15.sp
                                        ),
                                        maxLines = 1, // 确保不换行
                                        modifier = Modifier
                                            .horizontalScroll(rememberScrollState()) // 添加滚动
                                            .padding(start = 4.dp), // 在"用户："和用户名之间留点空隙
                                        softWrap = false, // 禁止换行
                                        overflow = TextOverflow.Ellipsis // 超出部分显示省略号
                                    )
                                }
                            }

                            // 控制按钮的位置
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val context = LocalContext.current
                                Button(onClick = {
                                    try {
                                        val intent = Intent(
                                            Intent.ACTION_VIEW,
                                            "https://login.tailscale.com/admin/machines".toUri()
                                        )
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "${context.getString(R.string.unable_browser)}: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Text(context.getString(R.string.manage))
                                }

                                Button(onClick = { if (isLoggedIn) onRequestLogout() else onLogin() }) {
                                    Text(if (isLoggedIn) context.getString(R.string.logout) else context.getString(R.string.login))
                                }
                            }
                        }

                    }

                    // Settings Options Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.elevatedCardElevation(4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 16.dp, horizontal = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = localSettings.acceptRoutes,
                                    onCheckedChange = {
                                        localSettings = localSettings.copy(acceptRoutes = it)
                                        hasUnsavedChanges.value = true
                                    }

                                )
                                Text("${context.getString(R.string.subnet)} (--accept-routes)")
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = localSettings.acceptDns,
                                    onCheckedChange = {
                                        localSettings = localSettings.copy(acceptDns = it)
                                        hasUnsavedChanges.value = true
                                    }
                                )
                                Text("${context.getString(R.string.accept_dns)} (--accept-dns)")
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = localSettings.advertiseExitNode,
                                    onCheckedChange = {
                                        localSettings = localSettings.copy(advertiseExitNode = it)
                                        hasUnsavedChanges.value = true
                                    }
                                )
                                Text("${context.getString(R.string.advertise_exit_node)} (--advertise-exit-node)")
                            }
                        }
                    }

                    // Input Fields Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.elevatedCardElevation(4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = localSettings.exitNode,
                                onValueChange = {
                                    localSettings = localSettings.copy(exitNode = it)
                                    hasUnsavedChanges.value = true },
                                label = { Text("${context.getString(R.string.exit_node)} (--exit-node)") },
                                placeholder = { Text(context.getString(R.string.exit_node_descripe)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = localSettings.advertiseRoutes,
                                onValueChange = {
                                    localSettings = localSettings.copy(advertiseRoutes = it)
                                    hasUnsavedChanges.value = true},
                                label = { Text("${context.getString(R.string.advertise_subnets_routes)} (--advertise-routes)") },
                                placeholder = { Text(context.getString(R.string.advertise_subnets_routes_descripe)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = localSettings.customName,
                                onValueChange = {
                                    localSettings = localSettings.copy(customName = it)
                                    hasUnsavedChanges.value = true},
                                label = { Text("${context.getString(R.string.hostname_set)} (--hostname)") },
                                placeholder = { Text(context.getString(R.string.hostname_set_descripe)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = localSettings.customParams,
                                onValueChange = {
                                    localSettings = localSettings.copy(customParams = it)
                                    hasUnsavedChanges.value = true},
                                label = { Text(context.getString(R.string.custom_parameters)) },
                                placeholder = { Text("--webclient --update-check=false") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                }

            }

        )
    }
    SettingsScreen(
        username = username.value,
        isLoggedIn = isLoggedIn.value,
        initialSettings = settings.value,
        onSave = { newSettings ->
            Thread {
                val result = executeRootCommand("tailscale set ${newSettings.toArgs()}")
                if (result.isNotBlank()) {
                    showErrorDialog = true to result
                } else {
                    saveSettings(context, newSettings)
                    settings.value = newSettings
                    showSaveDialog = true to result
                }
            }.start()
        },
        onLogin = {
            showLoginDialog = true to context.getString(R.string.waiting_link)
            loginUrl = ""

            Thread {
                try {
                    val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "tailscale login ${settings.value.toArgs()}" ))

                    // 读取 stderr
                    val stderrReader = process.errorStream.bufferedReader()
                    Thread {
                        stderrReader.forEachLine { line ->
                            CoroutineScope(Dispatchers.Main).launch {
                                // 确保这一行里确实有 https:// 再提取
                                if ("https://" in line) {
                                    val url = line.substringAfter("https://")
                                        .substringBefore(' ')          // 遇到空格截断
                                        .takeIf { it.isNotEmpty() } ?: return@launch

                                    loginUrl = "https://$url"
                                    showLoginDialog = true to ("${context.getString(R.string.login_link)}: $loginUrl")
                                    Toast.makeText(context, context.getString(R.string.get_link), Toast.LENGTH_LONG).show()
                                }

                                // 检测登录成功
                                if (line.contains("Success.")) {
                                    showLoginDialog = true to context.getString(R.string.login_success)
                                }
                            }
                        }
                    }.start()

                    process.waitFor()
                    process.destroy()
                } catch (e: Exception) {
                    CoroutineScope(Dispatchers.Main).launch {
                        showLoginDialog = true to "${context.getString(R.string.get_link_failed)}: ${e.message}"
                    }
                }
            }.start()
        },
        onRequestLogout = { showLogoutConfirm = true }
    )

    // tailscale set 异常弹窗
    if (showErrorDialog.first) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false to "" },
            title = { Text(context.getString(R.string.save_failed)) },
            text = { Text("${context.getString(R.string.save_failed_text)}：\n${showErrorDialog.second}") },
            confirmButton = {
                TextButton(onClick = { showErrorDialog = false to "" }) {
                    Text(context.getString(R.string.done))
                }
            }
        )
    }
    // 保存成功弹窗
    if (showSaveDialog.first) {

        AlertDialog(
            onDismissRequest = { showSaveDialog = false to "" },
            title = { Text(context.getString(R.string.save_success)) },
            confirmButton = {
                TextButton(onClick = { showSaveDialog = false to "" }) {
                    Text(context.getString(R.string.done))
                }
            }
        )
    }
    // 登录弹窗（显示链接）
    if (showLoginDialog.first) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Tailscale ${context.getString(R.string.login)}") },
            text = { Text(showLoginDialog.second) },
            confirmButton = {
                if (showLoginDialog.second.contains("成功") || showLoginDialog.second.contains("success")) {
                    TextButton(onClick = { showLoginDialog = false to "" }) {
                        Text(context.getString(R.string.done))
                    }
                } else {
                    Row {
                        TextButton(onClick = {
                            if (loginUrl.isNotEmpty()) {
                                // 复制到剪贴板
                                val clipboard =
                                    context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText(
                                        "Tailscale Login",
                                        loginUrl
                                    )
                                )
                                Toast.makeText(
                                    context,
                                    "已复制登录链接",
                                    Toast.LENGTH_SHORT
                                ).show()

                                // 打开浏览器
                                try {
                                    val intent =
                                        Intent(Intent.ACTION_VIEW, loginUrl.toUri())
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // 防止非 Activity Context 崩溃
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        "${context.getString(R.string.unable_browser)}：${e.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }) {
                            Text(context.getString(R.string.copy_open))
                        }
                        TextButton(onClick = {
                            showLoginDialog = false to ""
                        }) {
                            Text(context.getString(R.string.cancel))
                        }
                    }
                }
            }
        )
    }

    // 登录成功弹窗
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            title = { Text(context.getString(R.string.login_succes2)) },
            text = { Text(context.getString(R.string.login_success)) },
            confirmButton = {
                TextButton(onClick = { showSuccessDialog = false }) {
                    Text(context.getString(R.string.done))
                }
            }
        )
    }

    // 注销确认弹窗
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text(context.getString(R.string.confirm_sign_out)) },
            text = { Text(context.getString(R.string.confirm_sign_out_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    Thread {
                        executeRootCommand("tailscale logout")
                        CoroutineScope(Dispatchers.Main).launch {
                            isLoggedIn.value = false
                            username.value = context.getString(R.string.status_service_needslogin)
                        }
                    }.start()
                }) {
                    Text(context.getString(R.string.logout))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text(context.getString(R.string.cancel))
                }
            }
        )
    }
}

// ------------------------ 辅助 ------------------------
fun saveSettings(context: android.content.Context, settings: TailscaleSettings) {
    val sp = context.getSharedPreferences("tailscale_prefs", MODE_PRIVATE)
    sp.edit().apply {
        putBoolean("acceptRoutes", settings.acceptRoutes)
        putBoolean("acceptDns", settings.acceptDns)
        putString("exitNode", settings.exitNode)
        putBoolean("advertiseExitNode", settings.advertiseExitNode)
        putString("advertiseRoutes", settings.advertiseRoutes)
        putString("customName", settings.customName)
        putString("customParams", settings.customParams)
        apply()
    }
}

fun loadSettings(context: android.content.Context): TailscaleSettings {
    val sp = context.getSharedPreferences("tailscale_prefs", MODE_PRIVATE)
    return TailscaleSettings(
        acceptRoutes = sp.getBoolean("acceptRoutes", true),
        acceptDns = sp.getBoolean("acceptDns", false),
        advertiseExitNode = sp.getBoolean("advertiseExitNode", false),
        exitNode = sp.getString("exitNode", "") ?: "",
        advertiseRoutes = sp.getString("advertiseRoutes", "") ?: "",
        customName = sp.getString("customName", "") ?: "",
        customParams = sp.getString("customParams", "") ?: ""
    )
}

