package pl.poznan.put.boatcontroller

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.poznan.put.boatcontroller.data.UserData
import pl.poznan.put.boatcontroller.ui.theme.BoatControllerTheme
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

class MainActivity : ComponentActivity() {
    private val mainVm by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_BoatController)
        super.onCreate(savedInstanceState)
        mainVm.loadUserData()
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
                            if (mainVm.isLoggedIn) {
                                MenuButton(
                                    "Disconnect",
                                    R.drawable.bc_disconnect,
                                    navController,
                                    navDest = "disconnect",
                                    mainVm = mainVm
                                )
                            } else {
                                MenuButton(
                                    "Connection",
                                    R.drawable.bc_connect,
                                    navController,
                                    navDest = "connection",
                                )
                            }
                            MenuButton(
                                "Controller",
                                R.drawable.bc_controller,
                                navController,
                                navDest = "controller"
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            MenuButton("VR Cam", R.drawable.bc_vr, navController, mainVm.isLoggedIn)
                            MenuButton(
                                "Waypoint",
                                R.drawable.bc_waypoint,
                                navController,
                                mainVm.isLoggedIn
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

                    if (mainVm.isLoggedIn) {
                        MenuButton(
                            "Disconnect",
                            R.drawable.bc_disconnect,
                            navController,
                            navDest = "disconnect",
                            mainVm = mainVm
                        )
                    } else {
                        MenuButton(
                            "Connection",
                            R.drawable.bc_connect,
                            navController,
                            navDest = "connection",
                        )
                    }

                    MenuButton("Controller", R.drawable.bc_controller, navController, navDest = "controller")
                    MenuButton("VR Cam", R.drawable.bc_vr, navController, mainVm.isLoggedIn)
                    MenuButton("Waypoint", R.drawable.bc_waypoint, navController, mainVm.isLoggedIn)
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
            } else if (navDest != null && navDest == "disconnect") {
                if (mainVm != null) {
                    mainVm.socketClose()
                    mainVm.updateLoggedIn(false)
                }
            } else if (navDest != null && navDest == "controller") {
                context.startActivity(Intent(context, ControllerActivity::class.java), null)
            }

        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(100.dp),
        shape = RoundedCornerShape(10.dp),
        enabled = enabled,
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
    var usernameError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    var serverIp = mainVm.serverIp
    var serverPort = mainVm.serverPort
    var username = mainVm.username
    var password = mainVm.password

    var showDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { navController.popBackStack() }
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
            Row(
                modifier = Modifier.fillMaxSize(),
            ) {
                if(isLandscape()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row (
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ){
                            Column(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Server IP
                                OutlinedTextField(
                                    value = serverIp,
                                    onValueChange = { mainVm.updateServerIP(it) },
                                    label = { Text("Server IP") },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Username
                                OutlinedTextField(
                                    value = username,
                                    onValueChange = {
                                        mainVm.updateUsername(it)
                                        usernameError = validateUsername(it)
                                    },
                                    label = { Text("Username") },
                                    isError = usernameError != null,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (usernameError != null) {
                                    Text(
                                        usernameError!!,
                                        color = Color.Red,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Server Port
                                OutlinedTextField(
                                    value = serverPort,
                                    onValueChange = { mainVm.updateServerPort(it) },
                                    label = { Text("Server Port") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Password
                                OutlinedTextField(
                                    value = password,
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
                                    Text(
                                        passwordError!!,
                                        color = Color.Red,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        //Remember connection data
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = mainVm.isRemembered,
                                onCheckedChange = { mainVm.updateIsRemembered(it) }
                            )
                            Text("Remember connection data")
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        var isLoading by remember { mutableStateOf(false) }

                        Button(
                            onClick = {
                                if (mainVm.isRemembered) {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        mainVm.changeUserData(
                                            userData = UserData(
                                                1,
                                                mainVm.username,
                                                mainVm.password,
                                                mainVm.serverIp,
                                                mainVm.serverPort,
                                                mainVm.isRemembered
                                            )
                                        )
                                    }
                                } else {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        mainVm.changeIsRemembered(false)
                                    }
                                }

                                usernameError = validateUsername(mainVm.username)
                                passwordError = validatePassword(mainVm.password)

                                if (usernameError == null && passwordError == null) {
                                    isLoading = true
                                    CoroutineScope(Dispatchers.IO).launch {
                                        val result =
                                            loginToServer(
                                                mainVm,
                                                serverIp,
                                                serverPort,
                                                username,
                                                password
                                            )
                                        withContext(Dispatchers.Main) {
                                            isLoading = false
                                            //                                    println(result)
                                            mainVm.updateLoggedIn(result)
                                            if (result == false) {
                                                showDialog = true
                                            } else {
                                                navController.navigate("home")
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .height(60.dp),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(
                                if (isLoading) "Connecting..." else "Connect",
                                fontSize = 6.em
                            )
                        }
                    }

                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Server IP
                        OutlinedTextField(
                            value = serverIp,
                            onValueChange = { mainVm.updateServerIP(it) },
                            label = { Text("Server IP") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Server Port
                        OutlinedTextField(
                            value = serverPort,
                            onValueChange = { mainVm.updateServerPort(it) },
                            label = { Text("Server Port") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Username
                        OutlinedTextField(
                            value = username,
                            onValueChange = {
                                mainVm.updateUsername(it)
                                usernameError = validateUsername(it)
                            },
                            label = { Text("Username") },
                            isError = usernameError != null,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (usernameError != null) {
                            Text(
                                usernameError!!,
                                color = Color.Red,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Password
                        OutlinedTextField(
                            value = password,
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
                            Text(
                                passwordError!!,
                                color = Color.Red,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        //Remember connection data
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = mainVm.isRemembered,
                                onCheckedChange = { mainVm.updateIsRemembered(it) }
                            )
                            Text("Remember connection data")
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        var isLoading by remember { mutableStateOf(false) }

                        Button(
                            onClick = {
                                if (mainVm.isRemembered) {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        mainVm.changeUserData(
                                            userData = UserData(
                                                1,
                                                mainVm.username,
                                                mainVm.password,
                                                mainVm.serverIp,
                                                mainVm.serverPort,
                                                mainVm.isRemembered
                                            )
                                        )
                                    }
                                } else {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        mainVm.changeIsRemembered(false)
                                    }
                                }

                                usernameError = validateUsername(mainVm.username)
                                passwordError = validatePassword(mainVm.password)

                                if (usernameError == null && passwordError == null) {
                                    isLoading = true
                                    CoroutineScope(Dispatchers.IO).launch {
                                        val result =
                                            loginToServer(
                                                mainVm,
                                                serverIp,
                                                serverPort,
                                                username,
                                                password
                                            )
                                        withContext(Dispatchers.Main) {
                                            isLoading = false
                                            //                                    println(result)
                                            mainVm.updateLoggedIn(result)
                                            if (result == false) {
                                                showDialog = true
                                            } else {
                                                navController.navigate("home")
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .height(60.dp),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(
                                if (isLoading) "Connecting..." else "Connect",
                                fontSize = 6.em
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Błąd logowania") },
            text = { Text("Połączenie nie powiodło się lub dostęp został odrzucony.") },
            confirmButton = {
                Button(onClick = { showDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

// === Walidatory ===

fun validateUsername(username: String): String? {
    val regex = Regex("^[a-z0-9]{4,20}$")
    return if (!regex.matches(username)) {
        "Username może zawierać tylko małe litery i cyfry (4-20 znaków)"
    } else null
}

fun validatePassword(password: String): String? {
    val regex = Regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,40}$")
    return if (!regex.matches(password)) {
        "Hasło musi mieć min. 8 znaków, w tym małą, wielką literę i cyfrę (max. 40 znaków)"
    } else null
}

fun loginToServer(
    mainVm: MainViewModel,
    ip: String,
    port: String,
    username: String,
    password: String
): Boolean {
    return try {
        val socket = Socket(ip, port.toInt())

        val output = PrintWriter(socket.getOutputStream(), true)
        val input = BufferedReader(InputStreamReader(socket.getInputStream()))

        val message = "Login;$username;$password"
        output.println(message)

        val response = input.readLine()

        if (response?.trim() == "Permission granted") {
            mainVm.updateSocket(socket)
        }
        when (response?.trim()) {
            "Permission granted" -> true
            "Permission denied" -> false
            else -> false
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    BoatControllerTheme {

    }
}