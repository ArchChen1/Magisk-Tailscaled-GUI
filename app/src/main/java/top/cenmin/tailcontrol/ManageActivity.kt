package top.cenmin.tailcontrol

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.*
import androidx.compose.foundation.text.selection.SelectionContainer
class ManageActivity : ComponentActivity() {

    private lateinit var loginArgs: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loginArgs = intent.getStringExtra("LOGIN_ARGS") ?: ""

        setContent {
            MaterialTheme {
                ManageScreen(loginArgs = loginArgs)
            }
        }
    }
}

/* ---------------- 数据模型 ---------------- */

data class AccountItem(
    val id: String,
    val account: String,
    val isCurrent: Boolean
)

/* ---------------- UI ---------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageScreen(
    loginArgs: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var accountList by remember { mutableStateOf<List<AccountItem>>(emptyList()) }

    // 登录 / 注销状态弹窗
    var showLoginDialog by remember { mutableStateOf(false to "") }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var loginUrl by remember { mutableStateOf("") }

    fun refresh() {
        scope.launch { accountList = loadAccounts() }
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = { TopAppBar(title = { Text(context.getString(R.string.tailscale_accounts)) }) }
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {

            /* -------- 账户列表 -------- */

            items(accountList) { item ->
                AccountCard(
                    item = item,
                    onClick = {
                        if (!item.isCurrent) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.switching),
                                Toast.LENGTH_SHORT
                            ).show()
                            scope.launch(Dispatchers.IO) {
                                executeRootCommand("tailscale switch ${item.id}")
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.switched),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    refresh()
                                }
                            }
                        }
                    }
                )
            }

            item { Spacer(Modifier.height(16.dp)) }
            /* -------- 管理账户 -------- */

            item {
                val context = LocalContext.current

                ActionCard(
                    text = context.getString(R.string.admin_console),
                    color = Color.Unspecified
                ) {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        "https://tailscale.com/admin".toUri()
                    ).apply {
                        // 防止在非 Activity Context 下崩溃
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.unable_browser),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }


            /* -------- 添加新账户 -------- */

            item {
                ActionCard(
                    text = context.getString(R.string.add_new_acc),
                    color = Color.Unspecified
                ) {
                    showLoginDialog = true to context.getString(R.string.waiting_link)
                    loginUrl = ""

                    Thread {
                        try {
                            val process = Runtime.getRuntime().exec(
                                arrayOf("su", "-c", "tailscale login $loginArgs")
                            )

                            process.errorStream.bufferedReader().forEachLine { line ->
                                CoroutineScope(Dispatchers.Main).launch {
                                    if ("https://" in line) {
                                        val url = "https://" + line.substringAfter("https://")
                                            .substringBefore(' ')
                                        loginUrl = url
                                        showLoginDialog = true to
                                                "${context.getString(R.string.login_link)}: $url"
                                    }

                                    if (line.contains("Success.")) {
                                        showLoginDialog = true to
                                                context.getString(R.string.login_success)
                                        refresh()
                                    }
                                }
                            }

                            process.waitFor()
                        } catch (e: Exception) {
                            CoroutineScope(Dispatchers.Main).launch {
                                showLoginDialog = true to e.message.orEmpty()
                            }
                        }
                    }.start()
                }
            }

            /* -------- 注销 -------- */

            if (accountList.any { it.isCurrent }) {
                item {
                    ActionCard(
                        text = context.getString(R.string.logout),
                        color = Color.Red
                    ) {
                        showLogoutConfirm = true
                    }
                }
            }
        }
    }

    /* ---------------- 弹窗 ---------------- */

    if (showLoginDialog.first) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Tailscale ${context.getString(R.string.login)}") },
            text = {
                SelectionContainer {
                    Text(showLoginDialog.second)
                }
            },
            confirmButton = {
                if (
                    showLoginDialog.second.contains("成功") ||
                    showLoginDialog.second.contains("success", ignoreCase = true)
                ) {
                    TextButton(onClick = { showLoginDialog = false to "" }) {
                        Text(context.getString(R.string.done))
                    }
                } else {
                    Row {
                        TextButton(onClick = {
                            if (loginUrl.isNotEmpty()) {
                                val clipboard = context
                                    .getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText(
                                        "Tailscale Login",
                                        loginUrl
                                    )
                                )
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.url_copied),
                                    Toast.LENGTH_SHORT
                                ).show()

                                try {
                                    val intent =
                                        Intent(Intent.ACTION_VIEW, loginUrl.toUri())
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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

                        TextButton(onClick = { showLoginDialog = false to "" }) {
                            Text(context.getString(R.string.cancel))
                        }
                    }
                }
            }
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text(context.getString(R.string.confirm_sign_out)) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    Thread {
                        executeRootCommand("tailscale logout")
                        CoroutineScope(Dispatchers.Main).launch {
                            refresh()
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

/* ---------------- 组件 ---------------- */

@Composable
fun AccountCard(
    item: AccountItem,
    onClick: () -> Unit
) {
    val backgroundColor =
        if (item.isCurrent) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        }

    val contentColor =
        if (item.isCurrent) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .padding(horizontal = 8.dp)
            .clickable(enabled = !item.isCurrent) {
                onClick()
            },
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor,
            contentColor = contentColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (item.isCurrent) 6.dp else 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.account,
                    style = MaterialTheme.typography.titleMedium
                )

                if (item.isCurrent) {
                    Text(
                        text = LocalContext.current.getString(R.string.current),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4CAF50)
                    )
                }
            }
        }
    }
}

@Composable
fun ActionCard(text: String, color: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Text(
            text = text,
            color = color,
            modifier = Modifier.padding(16.dp)
        )
    }
}

/* ---------------- 数据加载 ---------------- */

suspend fun loadAccounts(): List<AccountItem> = withContext(Dispatchers.IO) {
    val result = mutableListOf<AccountItem>()
    val output = executeRootCommand("tailscale switch --list")

    output.lineSequence().drop(1).forEach { line ->
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.size >= 3) {
            val raw = parts[2]
            result.add(
                AccountItem(
                    id = parts[0],
                    account = raw.removeSuffix("*"),
                    isCurrent = raw.endsWith("*")
                )
            )
        }
    }
    result
}