package pl.poznan.put.boatcontroller.ui.main

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import kotlinx.coroutines.delay
import pl.poznan.put.boatcontroller.ui.controller.ControllerActivity
import pl.poznan.put.boatcontroller.R
import pl.poznan.put.boatcontroller.ui.vr.VRActivity
import pl.poznan.put.boatcontroller.ui.waypoint.WaypointActivity
import pl.poznan.put.boatcontroller.backend.dto.MissionListItemDto
import pl.poznan.put.boatcontroller.domain.dataclass.ShipOption
import pl.poznan.put.boatcontroller.domain.components.BoatControllerTopBar
import pl.poznan.put.boatcontroller.ui.theme.BoatControllerTheme
import pl.poznan.put.boatcontroller.ui.theme.ErrorBackground
import pl.poznan.put.boatcontroller.ui.theme.ErrorText
import pl.poznan.put.boatcontroller.ui.theme.SuccessGreen
import pl.poznan.put.boatcontroller.ui.theme.ErrorRed

val LocalFormEnabled = compositionLocalOf { true }

class MainActivity : ComponentActivity() {
    private val mainVm by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_BoatController)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RequestLocationPermission()
            BoatControllerTheme {
                BoatControllerTopBar {
                    val navController = rememberNavController()
                    AppNavHost(navController, mainVm)
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RequestLocationPermission() {
    val permissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    LaunchedEffect(Unit) {
        permissionState.launchPermissionRequest()
    }

    when {
        permissionState.status.isGranted -> {
        }
        permissionState.status.shouldShowRationale -> {
            Text("We need your location to display your position.")
        }
        else -> {
            Text("No location consent.")
        }
    }
}

@Composable
fun isLandscape(): Boolean {
    val configuration = LocalConfiguration.current
    return configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
}

@Composable
fun AppNavHost(navController: NavHostController, mainVm: MainViewModel) {
    NavHost(navController, startDestination = "home") {
        composable("home") { HomeContent(navController, mainVm) }
        composable("connection") { ConnectionContent(navController, mainVm) }
    }
}

@Composable
fun StatusHeader(
    restConnected: Boolean,
    socketConnected: Boolean,
    isLandscape: Boolean
) {
    val restText = if (restConnected) "Connected" else "Disconnected"
    val restColor = if (restConnected) SuccessGreen else ErrorRed
    val socketText = if (socketConnected) "Connected" else "Disconnected"
    val socketColor = if (socketConnected) SuccessGreen else ErrorRed

    val layoutModifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)

    if (isLandscape) {
        Row(
            modifier = layoutModifier,
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusItem("Server:", restText, restColor)
            Spacer(modifier = Modifier.width(24.dp))
            StatusItem("Boat:", socketText, socketColor)
        }
    } else {
        Column(
            modifier = layoutModifier,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StatusItem("Server:", restText, restColor)
            StatusItem("Boat:", socketText, socketColor)
        }
    }
}

@Composable
fun StatusItem(label: String, status: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, fontSize = 5.em)
        Text(text = status, fontSize = 5.em, color = color)
    }
}

@Composable
fun HomeContent(navController: NavController, mainVm: MainViewModel) {
    val isLandscape = isLandscape()
    val columnsCount = if (isLandscape) 2 else 1
    val canAccessController = mainVm.isLoggedIn && mainVm.selectedMission.name != ""

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columnsCount),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item(span = { GridItemSpan(columnsCount) }) {
                StatusHeader(
                    restConnected = mainVm.isLoggedIn,
                    socketConnected = mainVm.isSocketConnected,
                    isLandscape = isLandscape
                )
            }
            item {
                MenuButton(
                    "Connection",
                    R.drawable.bc_connect,
                    navController,
                    navDest = "connection",
                )
            }
            item {
                MenuButton(
                    "Controller",
                    R.drawable.bc_controller,
                    navController,
                    enabled = if(isLandscape) mainVm.isLoggedIn else canAccessController,
                    navDest = "controller",
                    mainVm = mainVm
                )
            }
            item {
                MenuButton(
                    "VR Cam",
                    R.drawable.bc_vr,
                    navController,
                    enabled = false,
                    navDest = "vr_mode",
                    mainVm = mainVm
                )
            }
            item {
                MenuButton(
                    "Waypoint",
                    R.drawable.bc_waypoint,
                    navController,
                    enabled = if(isLandscape) mainVm.isLoggedIn else canAccessController,
                    navDest = "waypoint",
                    mainVm = mainVm,
                )
            }
        }
    }
}

@Composable
fun MenuButton(
    text: String,
    icon: Int,
    navController: NavController,
    enabled: Boolean = true,
    navDest: String? = null,
    mainVm: MainViewModel? = null
) {
    val context = LocalContext.current
    Button(
        onClick = {
            if (navDest != null && navDest == "connection") {
                navController.navigate(navDest)
            } else if (navDest != null && navDest == "waypoint") {
                context.startActivity(Intent(context, WaypointActivity::class.java), null)
            } else if (navDest != null && navDest == "controller") {
                context.startActivity(Intent(context, ControllerActivity::class.java), null)
            } else if (navDest != null && navDest == "vr_mode") {
                if (mainVm != null) {
                    context.startActivity(Intent(context, VRActivity::class.java), null)
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(100.dp),
        shape = RoundedCornerShape(10.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
    ) {
        Column{
            Row{
                Text(text = text, fontSize = 10.em, modifier = Modifier.padding(end = 10.dp))
                Icon(
                    painter = painterResource(id = icon),
                    contentDescription = "Timer Icon",
                    modifier = Modifier.size(55.dp)
                )
            }
            if (text == "VR Cam") {
                Text(text = "Coming soon!", fontSize = 4.em, modifier = Modifier.padding(end = 6.dp))
            }
        }
    }
    Spacer(modifier = Modifier.height(10.dp))
}

@Composable
fun ConnectionContent(navController: NavController, mainVm: MainViewModel) {
    ConnectionForm(navController, mainVm)
}

@Composable
fun ConnectionForm(navController: NavController, mainVm: MainViewModel) {
    var showSettings by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(0.dp, 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { navController.navigate("home") }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.bc_back),
                        contentDescription = "Back",
                        modifier = Modifier
                            .size(55.dp)
                    )
                }

                Text(text = "Connection", fontSize = 6.em)
                Spacer(modifier = Modifier.width(2.dp))
            }
        }
        item {
            Box(modifier = Modifier.fillMaxSize()) {
                if (!mainVm.isLoggedIn) {
                    LoginForm(
                        mainVm = mainVm,
                        onConnect = { mainVm.updateLoggedIn(true); },
                        onShowSettings = { showSettings = true }
                    )
                } else {
                    MissionForm(
                        mainVm = mainVm,
                        onChangeSelectedMission = { navController.navigate("home") },
                        onDisconnect = { mainVm.updateLoggedIn(false) }
                    )
                }

                SettingsPanel(
                    mainVm = mainVm,
                    visible = showSettings,
                    onClose = { showSettings = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShipSelect(
    mainVm: MainViewModel,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedText by remember { mutableStateOf("") }
    var ships = emptyList<ShipOption>()

    LaunchedEffect(mainVm.selectedShip.name) {
        if (mainVm.selectedShip.name.isNotEmpty()) {
            val role = if (mainVm.isCaptain) "Captain" else "Observer"
            selectedText = "${mainVm.selectedShip.name} ($role)"
        }
    }
    if(mainVm.ships.isNotEmpty()) {
        ships = (mainVm.ships.map { ship ->
            val role = if (ship.connections == 0) "Captain" else "Observer"
            ShipOption(
                name = ship.name,
                role = role,
            )
        } + ShipOption(
            name = "Demo",
            role = "Captain",
        ))
    } else {
        ships = (ships + ShipOption(
            name = "Demo",
            role = "Captain",
        ))
    }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = {
            if (enabled) {
                expanded = !expanded
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
                readOnly = true,
                enabled = enabled,
            label = { Text("Select ship") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryEditable)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth()
        ) {
            ships.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(option.name)
                            if (option.role == "Captain") {
                                Icon(
                                    painter = painterResource(id = R.drawable.controller),
                                    contentDescription = "Captain"
                                )
                            } else {
                                Icon(
                                    painter = painterResource(id = R.drawable.bc_visibility),
                                    contentDescription = "Observer"
                                )
                            }
                        }
                    },
                    onClick = {
                        if (enabled) {
                            selectedText = "${option.name} (${option.role})"
                            mainVm.updateSelectedShip(option.name, option.role)
                            expanded = false
                        }
                    },
                    enabled = enabled
                )
            }
        }
    }
}

@Composable
fun FormEnabled(
    enabled: Boolean,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalFormEnabled provides enabled,
        content = content
    )
}

@Composable
fun LoginForm(
    mainVm: MainViewModel,
    onConnect: () -> Unit,
    onShowSettings: () -> Unit
) {
    var usernameError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    val isConnecting = mainVm.isConnecting
    val formEnabled = LocalFormEnabled.current && !isConnecting


    FormEnabled(enabled = !isConnecting) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .padding(top = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(
                onClick = onShowSettings, modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(50.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ),
                enabled = formEnabled
            ) {
                Text("Change connection settings ", fontSize = 4.em)
                Icon(
                    painter = painterResource(id = R.drawable.settings),
                    contentDescription = "settings",
                    modifier = Modifier
                        .size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = mainVm.username,
                onValueChange = {
                    if (formEnabled) {
                        mainVm.updateUsername(it)
                        usernameError = validateUsername(it)
                    }
                },
                label = { Text("Username") },
                isError = usernameError != null,
                modifier = Modifier.fillMaxWidth(),
                enabled = formEnabled
            )
            if (usernameError != null) {
                Text(usernameError!!, color = ErrorRed)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = mainVm.password,
                onValueChange = {
                    if (formEnabled) {
                        mainVm.updatePassword(it)
                        passwordError = validatePassword(it)
                    }
                },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                isError = passwordError != null,
                modifier = Modifier.fillMaxWidth(),
                enabled = formEnabled
            )
            if (passwordError != null) {
                Text(passwordError!!, color = ErrorRed)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = mainVm.isRemembered,
                    onCheckedChange = { if (formEnabled) mainVm.updateIsRemembered(it) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                        uncheckedColor = Color.Gray
                    )
                )
                Text("Remember login data")
            }

            Spacer(modifier = Modifier.height(24.dp))

            ShipSelect(mainVm, enabled = formEnabled)

            Spacer(modifier = Modifier.height(24.dp))

            if (isConnecting) {
                Text(
                    text = "Connecting to ${mainVm.serverIp}:${mainVm.serverPort}...",
                    color = Color.Gray,
                    fontSize = 4.em
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Button(
                onClick = {
                    if (formEnabled && usernameError == null && passwordError == null && mainVm.selectedShip.name != "") {
                        mainVm.connect()
                        if (mainVm.isLoggedIn && !mainVm.error) {
                            onConnect()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(60.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                 ),
             enabled = formEnabled && mainVm.password != "" && mainVm.username != "" && mainVm.selectedShip.name != ""
            ) {
                Text(if (isConnecting) "Connecting... " else "Connect ", fontSize = 8.em)
                Icon(
                    painter = painterResource(id = R.drawable.connect),
                    contentDescription = "Connect",
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }

    val errorText = mainVm.errorMessage?.takeIf { it.isNotBlank() }
    if (errorText != null) {
        ErrorBubble(
            visible = mainVm.error,
            message = errorText,
            onClose = { mainVm.clearError() }
        )
    }
}

@Composable
fun ErrorBubble(
    modifier: Modifier = Modifier,
    message: String,
    visible: Boolean = false,
    onClose: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .wrapContentWidth()
                .wrapContentHeight()
                .background(ErrorBackground)
                .semantics { contentDescription = "Error bubble" }
        ) {
            Column(
                modifier = Modifier
                    .background(ErrorBackground)
                    .padding(12.dp)
                    .widthIn(min = 220.dp, max = 420.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Error",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.alignByBaseline()
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(
                        onClick = {
                            onClose()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .semantics { contentDescription = "Close error" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = message,
                    color = ErrorText,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                )
            }
        }
    }
}

@Composable
fun SettingsPanel(mainVm: MainViewModel, visible: Boolean, onClose: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally { it },
        exit = slideOutHorizontally { it },
        modifier = Modifier
            .fillMaxSize()
    ) {
        val darkTheme = isSystemInDarkTheme()
        Column(
            modifier = Modifier.background(if (darkTheme) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Connection settings", fontSize = 6.em, modifier = Modifier.padding(top = 25.dp, bottom = 25.dp))
            OutlinedTextField(
                value = mainVm.serverIp,
                onValueChange = { mainVm.updateServerIP(it) },
                label = { Text("Server IP") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = mainVm.serverPort,
                onValueChange = { mainVm.updateServerPort(it) },
                label = { Text("Server Port") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = mainVm.socketIp,
                onValueChange = { mainVm.updateSocketIP(it) },
                label = { Text("Socket IP (boat)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = mainVm.serverSocketPort,
                onValueChange = { mainVm.updateServerSocketPort(it) },
                label = { Text("Socket Port") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(50.dp))

            Button(
                onClick = {
                    onClose()
                    mainVm.changeServerData()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)) {
                Text("Save",fontSize = 8.em)
                Icon(
                    painter = painterResource(id = R.drawable.save),
                    contentDescription = "Save ",
                    modifier = Modifier.size(36.dp))
            }
            Spacer(modifier = Modifier.height(25.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissionForm(mainVm: MainViewModel, onChangeSelectedMission: () -> Unit, onDisconnect: () -> Unit) {
    var filter by remember { mutableStateOf("") }
    var selectedMissionLocal by remember { mutableStateOf<MissionListItemDto>(MissionListItemDto(-1,"")) }
    var expanded by remember { mutableStateOf(false) }
    var isCreatingMission by remember { mutableStateOf(false) }
    var missionToCreate by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(mainVm.selectedMission.id) {
        if (mainVm.selectedMission.id != -1 && selectedMissionLocal.id == -1) {
            selectedMissionLocal = mainVm.selectedMission
            filter = mainVm.selectedMission.name
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Button(
            onClick = {
                mainVm.disconnect()
                onDisconnect()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(60.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Disconnect ",fontSize = 8.em)
            Icon(
                painter = painterResource(id = R.drawable.disconnect),
                contentDescription = "Disconnect",
                modifier = Modifier.size(34.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            Row {
                Text("Welcome, ", style = MaterialTheme.typography.headlineMedium)
                Text(mainVm.username, color=MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium)
            }
            Row {
                Text("Current boat: ", style = MaterialTheme.typography.headlineMedium)
                Text(mainVm.selectedShip.name, color=MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium)
            }
            Row {
                Text("Your role: ", style = MaterialTheme.typography.headlineMedium)
                Text(if(mainVm.isCaptain) "Captain" else "Observer", color=MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium)
            }
            Row {
                Text("Mission: ", style = MaterialTheme.typography.headlineMedium)
                Text(if(mainVm.selectedMission.id!=-1) mainVm.selectedMission.name else "-", color=MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(thickness = 1.dp, color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = filter,
                onValueChange = {
                    filter = it
                    expanded = true
                },
                label = { Text("Select mission") },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryEditable)
                    .fillMaxWidth(),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                if (filter.isNotBlank() && mainVm.missions.none { it.name.equals(filter, ignoreCase = true) }) {
                    DropdownMenuItem(
                        text = { Text(if (isCreatingMission) "Creating \"$filter\"..." else "+ Add \"$filter\"") },
                        onClick = {
                            if (!isCreatingMission) {
                                missionToCreate = filter
                                isCreatingMission = true
                            }
                        },
                        enabled = !isCreatingMission
                    )
                }

                mainVm.missions
                    .filter { it.name.contains(filter, ignoreCase = true) }
                    .forEach { mission ->
                        DropdownMenuItem(
                            text = { Text(mission.name) },
                            onClick = {
                                selectedMissionLocal = mission
                                filter = mission.name
                                expanded = false
                            }
                        )
                    }
            }
        }

        LaunchedEffect(missionToCreate) {
            missionToCreate?.let { missionName ->
                try {
                    val newMission = mainVm.createMission(missionName)
                    if (newMission != null && newMission.id != -1) {
                        delay(100)
                        selectedMissionLocal = newMission
                        filter = newMission.name
                        expanded = false
                    } else {
                        isCreatingMission = false
                    }
                } catch (_: Exception) {
                    isCreatingMission = false
                } finally {
                    missionToCreate = null
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text("Selected: ${ if(!selectedMissionLocal.name.isEmpty()) selectedMissionLocal.name else '-'}", style = MaterialTheme.typography.bodyMedium, modifier=Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(thickness = 1.dp, color = Color.White)
        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = {
                mainVm.updateSelectedMission(selectedMissionLocal)
                if (mainVm.selectedMission.id != -1) {
                    onChangeSelectedMission()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(60.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ),
            enabled = selectedMissionLocal.id != -1
        ) {
            Text("Set mission ",fontSize = 8.em)
            Icon(
                painter = painterResource(id = R.drawable.save),
                contentDescription = "Save",
                modifier = Modifier.size(34.dp))
        }
    }
}

fun validateUsername(username: String): String? {
    val regex = Regex("^[a-z0-9]{4,20}$")
    return if (!regex.matches(username)) {
        "Username can only contain lowercase letters and numbers (4-20 characters)"
    } else null
}

fun validatePassword(password: String): String? {
    return if (password.length < 8) {
        "Password must be at least 8 characters long"
    } else null
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    BoatControllerTheme {

    }
}