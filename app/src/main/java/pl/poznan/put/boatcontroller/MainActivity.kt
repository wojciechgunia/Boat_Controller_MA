package pl.poznan.put.boatcontroller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import pl.poznan.put.boatcontroller.ui.theme.BoatControllerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_BoatController)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BoatControllerTheme {
                BoatControllerTopBar {
                    val navController = rememberNavController()
                    AppNavHost(navController)
                }
            }
        }
    }
}

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(navController, startDestination = "home") {
        composable("home") { HomeContent(navController) }
        composable("connection") { ConnectionContent(navController) }
    }
}

@Composable
fun HomeContent(navController: NavController) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row {
                Text(text = "Connection:  ", fontSize = 5.em)
                Text(text = "Disconnected", fontSize = 5.em, color = Color.Red)
            }
            Spacer(modifier = Modifier.height(24.dp))

            MenuButton("Connection", R.drawable.bc_connect, navController, navDest = "connection")
            MenuButton("Controller", R.drawable.bc_controller, navController, false)
            MenuButton("VR Cam", R.drawable.bc_vr, navController, false)
            MenuButton("Waypoint", R.drawable.bc_waypoint, navController, false)
        }
    }
}

@Composable
fun MenuButton(text: String,
               icon: Int,
               navController: NavController,
               enabled: Boolean = true,
               navDest: String? = null) {
    Button(
        onClick = {
            if(navDest != null)
                navController.navigate(navDest)
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
fun ConnectionContent(navController: NavController) {
    ConnectionForm(navController)
}

@Composable
fun ConnectionForm(navController: NavController) {
    var serverIp by remember { mutableStateOf("") }
    var serverPort by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var usernameError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    var showDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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


        Row(
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Server IP
                OutlinedTextField(
                    value = serverIp,
                    onValueChange = { serverIp = it },
                    label = { Text("Server IP") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Server Port
                OutlinedTextField(
                    value = serverPort,
                    onValueChange = { serverPort = it },
                    label = { Text("Server Port") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Username
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
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
                        password = it
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

                Spacer(modifier = Modifier.height(24.dp))

                // Connect Button
                Button(
                    onClick = {
                        usernameError = validateUsername(username)
                        passwordError = validatePassword(password)

                        if (usernameError == null && passwordError == null) {
                            // Tu można spróbować połączenia np. przez coroutine
                            // Zamiast tego pokażemy błąd
                            showDialog = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Connect")
                }
            }
        }
    }

    // AlertDialog
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Błąd") },
            text = { Text("Połączenie nie powiodło się") },
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


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    BoatControllerTheme {

    }
}