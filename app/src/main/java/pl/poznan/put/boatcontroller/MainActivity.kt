package pl.poznan.put.boatcontroller

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.LaunchedEffect
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
import pl.poznan.put.boatcontroller.dataclass.MissionListItemDto
import pl.poznan.put.boatcontroller.dataclass.ShipOption
import pl.poznan.put.boatcontroller.ui.theme.BoatControllerTheme

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
    val permissionState = rememberPermissionState(android.Manifest.permission.ACCESS_FINE_LOCATION)

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
fun HomeContent(navController: NavController, mainVm: MainViewModel) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if(isLandscape()) {
            LazyColumn {
                item {
                    Row (
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.Center
                    ){
                        Text(text = "Connection:  ", fontSize = 5.em)
                        if (mainVm.isLoggedIn) {
                            Text(text = "Connected", fontSize = 5.em, color = Color.Green)
                        } else {
                            Text(text = "Disconnected", fontSize = 5.em, color = Color.Red)
                        }
                    }
                    Row {
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            MenuButton(
                                "Connection",
                                R.drawable.bc_connect,
                                navController,
                                navDest = "connection",
                            )

                            MenuButton(
                                "Controller",
                                R.drawable.bc_controller,
                                navController,
                                mainVm.isLoggedIn,
                                navDest = "controller",
                                mainVm = mainVm
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            MenuButton(
                                "VR Cam",
                                R.drawable.bc_vr,
                                navController,
                                mainVm.isLoggedIn,
                                navDest = "vr_mode",
                                mainVm = mainVm
                            )
                            MenuButton(
                                "Waypoint",
                                R.drawable.bc_waypoint,
                                navController,
                                mainVm.isLoggedIn,
                                navDest = "waypoint",
                                mainVm = mainVm,
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Row {
                        Text(text = "Connection:  ", fontSize = 5.em)
                        if (mainVm.isLoggedIn) {
                            Text(text = "Connected", fontSize = 5.em, color = Color.Green)
                        } else {
                            Text(text = "Disconnected", fontSize = 5.em, color = Color.Red)
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))

                    MenuButton(
                        "Connection",
                        R.drawable.bc_connect,
                        navController,
                        navDest = "connection",
                    )

                    MenuButton(
                        "Controller",
                        R.drawable.bc_controller,
                        navController,
                        mainVm.isLoggedIn && mainVm.selectedMission.name != "",
                        navDest = "controller",
                        mainVm = mainVm
                    )

                    MenuButton(
                        "VR Cam",
                        R.drawable.bc_vr,
                        navController,
                        mainVm.isLoggedIn && mainVm.selectedMission.name != "",
                        navDest = "vr_mode",
                        mainVm = mainVm
                    )

                    MenuButton(
                      "Waypoint",
                      R.drawable.bc_waypoint,
                      navController,
                      mainVm.isLoggedIn && mainVm.selectedMission.name != "",
                      navDest = "waypoint",
                      mainVm = mainVm,
                  )
                }
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
                if (mainVm != null) {
                    val intent = Intent(context, WaypointActivity::class.java)
                    intent.putExtra("selectedMission", mainVm.selectedMission.id)
                    context.startActivity(intent, null)
                }
            } else if (navDest != null && navDest == "controller") {
                if (mainVm != null) {
                    val intent = Intent(context, ControllerActivity::class.java)
                    intent.putExtra("selectedMission", mainVm.selectedMission.id)
                    context.startActivity(intent, null)
                }
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
            containerColor = Color(0xFF4170A6),
            contentColor = Color.White
        ),
    ) {
        Text(text = text, fontSize = 10.em, modifier = Modifier.padding(end = 10.dp))
        Icon(
            painter = painterResource(id = icon),
            contentDescription = "Timer Icon",
            modifier = Modifier.size(55.dp)
        )
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
fun ShipSelect(mainVm: MainViewModel) {
    var expanded by remember { mutableStateOf(false) }
    var selectedText by remember { mutableStateOf("") }
    var ships = emptyList<ShipOption>()
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
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            label = { Text("Select ship") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryEditable)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
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
                        selectedText = "${option.name} (${option.role})"
                        mainVm.updateSelectedShip(option.name, option.role)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun LoginForm(
    mainVm: MainViewModel,
    onConnect: () -> Unit,
    onShowSettings: () -> Unit
) {
    var usernameError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .padding(top = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(onClick = onShowSettings, modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(50.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4170A6),
                contentColor = Color.White
            ),) {
            Text("Change connection settings ",fontSize = 4.em)
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
                mainVm.updateUsername(it)
                usernameError = validateUsername(it)
            },
            label = { Text("Username") },
            isError = usernameError != null,
            modifier = Modifier.fillMaxWidth(),
        )
        if (usernameError != null) {
            Text(usernameError!!, color = Color.Red)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = mainVm.password,
            onValueChange = {
                mainVm.updatePassword(it)
                passwordError = validatePassword(it)
            },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            isError = passwordError != null,
            modifier = Modifier.fillMaxWidth()
        )
        if (passwordError != null) {
            Text(passwordError!!, color = Color.Red)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = mainVm.isRemembered,
                onCheckedChange = { mainVm.updateIsRemembered(it) },
                colors = CheckboxDefaults.colors(
                    checkedColor = Color(0xFF4170A6),
                    checkmarkColor = Color.White,
                    uncheckedColor = Color.Gray
                )
            )
            Text("Remember login data")
        }

        Spacer(modifier = Modifier.height(24.dp))

        ShipSelect(mainVm)

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (usernameError == null && passwordError == null && mainVm.selectedShip.name != "") {
                    isLoading = true
                    Log.d("Connection", "Start")
                    mainVm.connect()
                    isLoading = false
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
                containerColor = Color(0xFF4170A6),
                contentColor = Color.White
            ),
            enabled = !isLoading && mainVm.password!="" && mainVm.username!="" && mainVm.selectedShip.name!=""
        ) {
            Text(if (isLoading) "Connecting... " else "Connect ",fontSize = 8.em)
            Icon(
                painter = painterResource(id = R.drawable.connect),
                contentDescription = "Connect",
                modifier = Modifier.size(36.dp))
        }
    }
    ErrorBubble(visible = mainVm.error, onClose = { mainVm.updateError(false) })
}

@Composable
fun ErrorBubble(
    modifier: Modifier = Modifier,
    message: String = "An error occurred during connection. Check the connection parameters and login credentials.",
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
                .background(Color(0xFF301D1E))
                .semantics { contentDescription = "Error bubble" }
        ) {
            Column(
                modifier = Modifier
                    .background(Color(0xFF301D1E))
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
                    color = Color(0xFFEEEEEE),
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
            modifier = Modifier.background(if (darkTheme) Color.Black else Color.LightGray, RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)).padding(24.dp),
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
                    containerColor = Color(0xFF4170A6),
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
                containerColor = Color(0xFF4170A6),
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
            Text("Welcome ${mainVm.username}", style = MaterialTheme.typography.headlineMedium)
            Text("Ship: ${mainVm.selectedShip.name}", style = MaterialTheme.typography.headlineSmall)
            Text("Role: ${if(mainVm.isCaptain) "Captain" else "Observer"}", style = MaterialTheme.typography.headlineSmall)
            Text("Mission: ${if(mainVm.selectedMission.id!=-1) mainVm.selectedMission.name else "-"}", style = MaterialTheme.typography.headlineSmall)
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
                        text = { Text("+ Add \"$filter\"") },
                        onClick = {
                            mainVm.createMission(filter)
                        }
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

        Spacer(modifier = Modifier.height(10.dp))

        Text("Selected: ${ if(!selectedMissionLocal.name.isEmpty()) selectedMissionLocal.name else '-'}", style = MaterialTheme.typography.bodyMedium, modifier=Modifier.fillMaxWidth())

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
                containerColor = Color(0xFF4170A6),
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

// === Validators ===

fun validateUsername(username: String): String? {
    val regex = Regex("^[a-z0-9]{4,20}$")
    return if (!regex.matches(username)) {
        "Username can only contain lowercase letters and numbers (4-20 characters)"
    } else null
}

fun validatePassword(password: String): String? {
    val regex = Regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*[@!#$%^&*?])(?=.*\\d).{8,40}$")
    return if (!regex.matches(password)) {
        "Password must be at least 8 characters long, including lowercase, uppercase, and digits (max. 40 characters)"
    } else null
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    BoatControllerTheme {

    }
}