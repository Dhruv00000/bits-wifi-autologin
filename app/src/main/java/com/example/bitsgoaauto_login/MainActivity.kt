package com.example.bitsgoaauto_login

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockPerson
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bitsgoaauto_login.ui.theme.BITSGoaAutologinTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {

            val viewModel: MainViewModel = viewModel()
            val isServiceEnabled by viewModel.isServiceEnabled.collectAsState()
            val isWifiConnected by viewModel.isWifiConnected.collectAsState()
            val isWifiValidated by viewModel.isWifiValidated.collectAsState()
            val isCaptivePortal by viewModel.isCaptivePortal.collectAsState()
            val ssid by viewModel.ssid.collectAsState()
            val hasCredentials by viewModel.hasCredentials.collectAsState()
            val loginResult by viewModel.loginResult.collectAsState()

            BITSGoaAutologinTheme { //TODO: https://www.youtube.com/watch?v=HmXgVBys7BU

                val currentContext = LocalContext.current
                var showBottomSheet by remember { mutableStateOf(false) }
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
                var showDeleteDialog by remember { mutableStateOf(false) }
                var locationPermissionGranted by remember { mutableStateOf(false) }
                var notificationPermissionGranted by remember {
                    mutableStateOf(
                        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) || (ContextCompat.checkSelfPermission(
                            currentContext,
                            android.Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED)
                    )
                }
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    locationPermissionGranted =
                        permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
                    notificationPermissionGranted =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissions[android.Manifest.permission.POST_NOTIFICATIONS] == true
                        } else {
                            true
                        }
                    if (locationPermissionGranted) {
                        viewModel.refreshConnectivity()
                    }
                }

                LaunchedEffect(Unit) {
                    val permissions = mutableListOf<String>()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                    permissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    permissions.add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                    permissionLauncher.launch(permissions.toTypedArray())
                }

                LaunchedEffect(loginResult) {
                    loginResult?.let { result ->
                        result.onSuccess { response ->
                            Toast.makeText(
                                currentContext,
                                """<message><!\[CDATA\[(.*?)]]></message>""".toRegex(RegexOption.IGNORE_CASE)
                                    .find(response)?.groupValues?.get(1)?.trim()
                                    ?.takeIf { it.isNotBlank() }
                                    ?: "Login executed successfully",
                                Toast.LENGTH_LONG).show()
                        }.onFailure {
                            Toast.makeText(
                                currentContext,
                                "Login failed. Please verify your connection.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        viewModel.clearLoginResult()
                    }
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    "BPGC Wi-Fi Auto-Login",
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (isServiceEnabled) colorScheme.onSurface else colorScheme.onSurfaceVariant
                                )
                            },
                            actions = {
                                Switch(
                                    checked = isServiceEnabled,
                                    onCheckedChange = { viewModel.toggleService(it) },
                                    modifier = Modifier.padding(end = 16.dp)
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = colorScheme.surfaceVariant,
                                titleContentColor = colorScheme.onSurfaceVariant
                            )
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 140.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colorScheme.background)
                            .padding(innerPadding)
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !locationPermissionGranted) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                LocationPermissionsWarningCard()
                            }
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            HeroCard(
                                hasCredentials,
                                isWifiConnected,
                                isServiceEnabled,
                                isWifiValidated,
                                isCaptivePortal,
                                ssid
                            )
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        item {
                            ActionCard(
                                title = "Manually\ntrigger login",
                                icon = Icons.AutoMirrored.Filled.Login,
                                onClick = { viewModel.manualLogin() }
                            )
                        }
                        item {
                            ActionCard(
                                title = if (hasCredentials) "Change\ncredentials" else "Add\ncredentials",
                                icon = Icons.Default.Edit,
                                onClick = { showBottomSheet = true }
                            )
                        }
                        item {
                            ActionCard(
                                title = "Delete\ncredentials",
                                icon = Icons.Default.Delete,
                                onClick = { showDeleteDialog = true }
                            )
                        }
                        item {
                            ActionCard(
                                title = "Open\nlogin portal",
                                icon = Icons.Default.Language,
                                onClick = {
                                    currentContext.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            "https://campnet.bits-goa.ac.in:8090/httpclient.html".toUri()
                                        )
                                    )
                                }
                            )
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SourceCodeCard {
                                currentContext.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        "https://github.com/Dhruv00000/bits-wifi-autologin/issues/".toUri()
                                    )
                                )
                            }
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Spacer(modifier = Modifier.height(24.dp))
                        }

                    }
                }

                if (showDeleteDialog) {
                    DeleteConfirmationDialog(
                        onConfirm = { viewModel.deleteCredentials(); showDeleteDialog = false },
                        onDismiss = { showDeleteDialog = false }
                    )
                }

                if (showBottomSheet) {
                    CredentialsBottomSheet(
                        initialUsername = viewModel.getStoredUsername(),
                        initialPassword = viewModel.getStoredPassword(),
                        onSave = { u, p ->
                            viewModel.saveCredentials(u, p); showBottomSheet = false
                        },
                        onDismiss = { showBottomSheet = false },
                        sheetState = sheetState
                    )
                }
            }
        }
    }
}

@Composable
fun LocationPermissionsWarningCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colorScheme.errorContainer)
    ) {
        Text(
            text = "Precise location permissions are not granted. These are required for full app functionality (Check github for more information).",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth()
        )
    }
}

@Composable
fun HeroCard(
    hasCredentials: Boolean,
    isWifiConnected: Boolean,
    isServiceEnabled: Boolean,
    isWifiValidated: Boolean,
    isCaptivePortal: Boolean,
    ssid: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2.5f),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = when {
                        !(isServiceEnabled && hasCredentials) -> Color(0xFFef1810)
                        isCaptivePortal || (isWifiConnected && !isWifiValidated) -> Color(0xFFe5e500)
                        isWifiConnected && isWifiValidated -> Color(0xFF80ef80)
                        else -> Color(0xFFef1810)
                    }
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {

            Icon(
                imageVector = when {
                    !hasCredentials -> Icons.Default.LockPerson
                    !isServiceEnabled -> Icons.Default.Cancel
                    isCaptivePortal || (isWifiConnected && !isWifiValidated) -> Icons.Default.Language
                    isWifiConnected && isWifiValidated -> Icons.Default.Wifi
                    else -> Icons.Default.WifiOff
                },
                contentDescription = "Status Icon",
                tint = Color.Black,
                modifier = Modifier.size(72.dp)
            )

            Spacer(modifier = Modifier.width(20.dp))

            Text(
                text = when {
                    !hasCredentials -> "Please add\ncredentials first"
                    !isServiceEnabled -> "The app has\nbeen disabled"
                    isCaptivePortal -> "Attempting to\nsign-in..."
                    isWifiConnected && !isWifiValidated -> "Checking login\nportal..."
                    isWifiConnected && isWifiValidated -> {
                        val name = ssid.ifBlank { "Wi-Fi" }
                        "Connected to\n$name"
                    }

                    else -> "Offline"
                },
                color = Color.Black,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                softWrap = true
            )

        }
    }
}

@Composable
fun ActionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.aspectRatio(1f),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.primaryContainer,
            contentColor = colorScheme.onPrimaryContainer
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = title, modifier = Modifier.size(56.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )
        }
    }
}

@Composable
fun SourceCodeCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.primaryContainer,
            contentColor = colorScheme.onPrimaryContainer
        )
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.BugReport,
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = "Report bugs", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
fun DeleteConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Credentials") },
        text = { Text("Are you sure you want to delete stored credentials?") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error)
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialsBottomSheet(
    initialUsername: String,
    initialPassword: String,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit,
    sheetState: androidx.compose.material3.SheetState
) {
    var username by remember { mutableStateOf(initialUsername) }
    var password by remember { mutableStateOf(initialPassword) }
    var passwordVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .padding(bottom = 16.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Enter credentials", style = MaterialTheme.typography.titleMedium)
                Icon(
                    Icons.Default.Close,
                    null,
                    Modifier.clickable {
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                    })
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                leadingIcon = { Icon(Icons.Default.Person, null) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            null
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { onSave(username, password) },
                modifier = Modifier.fillMaxWidth(),
                enabled = username.isNotBlank() && password.isNotBlank()
            ) {
                Text("Save Credentials")
            }

        }
    }
}
