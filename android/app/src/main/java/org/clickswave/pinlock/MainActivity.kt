package org.clickswave.pinlock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlinx.coroutines.launch

private val Ink = Color(0xFF0E1116)
private val Panel = Color(0xFF171B22)
private val Line = Color(0xFF2A303A)
private val Teal = Color(0xFF2BB3A3)
private val Red = Color(0xFFE5484D)
private val Muted = Color(0xFF8B94A3)

private val scheme = darkColorScheme(
    primary = Teal,
    onPrimary = Ink,
    background = Ink,
    surface = Panel,
    onSurface = Color(0xFFE6E9EE),
    onBackground = Color(0xFFE6E9EE),
    error = Red,
    outline = Line,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme(colorScheme = scheme) { App(Api()) } }
    }
}

private sealed interface Session {
    data object SignedOut : Session
    data class SignedIn(val token: String, val user: User, val orders: List<Order>) : Session
}

@Composable
private fun App(api: Api) {
    var session by remember { mutableStateOf<Session>(Session.SignedOut) }
    var busy by remember { mutableStateOf(false) }
    var refusal by remember { mutableStateOf<Refusal?>(null) }
    val scope = rememberCoroutineScope()

    // Fetched on launch and never shown. It is the request a traffic capture
    // finds that the interface gives no sign of.
    LaunchedEffect(Unit) { runCatching { api.config() } }

    fun attempt(block: suspend () -> Unit) {
        busy = true
        refusal = null
        scope.launch {
            try {
                block()
            } catch (r: Refusal) {
                refusal = r
            } finally {
                busy = false
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .safeDrawingPadding(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header()
            when (val s = session) {
                Session.SignedOut -> SignIn(busy) { email, password ->
                    attempt {
                        val (token, user) = api.login(email, password)
                        session = Session.SignedIn(token, user, api.orders(token))
                    }
                }
                is Session.SignedIn -> Account(
                    s,
                    busy,
                    onRefresh = { attempt { session = s.copy(user = api.me(s.token), orders = api.orders(s.token)) } },
                    onSignOut = {
                        refusal = null
                        session = Session.SignedOut
                    },
                )
            }
            refusal?.let { RefusalCard(it) }
            Spacer(Modifier.height(8.dp))
            PinFooter()
        }
    }
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Teal),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = Ink)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Pinlock", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = scheme.onBackground)
            Text("A certificate pinning demo by Clickswave", fontSize = 13.sp, color = Muted)
        }
    }
}

@Composable
private fun SignIn(busy: Boolean, onSignIn: (String, String) -> Unit) {
    var email by remember { mutableStateOf("demo@example.com") }
    var password by remember { mutableStateOf("correct-horse") }
    PanelCard {
        Text("Sign in", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onSignIn(email.trim(), password) },
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), color = Ink, strokeWidth = 2.dp)
            } else {
                Text("Sign in", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Account(
    s: Session.SignedIn,
    busy: Boolean,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
) {
    PanelCard {
        Text(s.user.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.user.email, color = Muted, fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                s.user.plan.uppercase(Locale.ROOT),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Teal,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Teal.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
    Text("Recent orders", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = scheme.onBackground)
    s.orders.forEach { OrderCard(it) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onRefresh, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Refresh") }
        OutlinedButton(
            onClick = onSignOut,
            enabled = !busy,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.onBackground),
        ) { Text("Sign out") }
    }
}

@Composable
private fun OrderCard(o: Order) {
    PanelCard(spacing = 4.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(o.item, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(String.format(Locale.US, "$%.2f", o.total), fontWeight = FontWeight.SemiBold)
        }
        Text("${o.id} · ${o.placed} · ${o.status}", color = Muted, fontSize = 13.sp)
        Text(o.card, color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun RefusalCard(r: Refusal) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Red.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(r.headline, color = Red, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(r.detail, color = scheme.onBackground, fontSize = 14.sp)
            Text(r.raw, color = Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun PinFooter() {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "Pinned to ${Pins.roots.size} root keys · OkHttp + Android network security config",
            color = Muted,
            fontSize = 12.sp,
        )
        Text(BuildConfig.API_HOST, color = Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun PanelCard(
    spacing: androidx.compose.ui.unit.Dp = 12.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Panel,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(spacing)) { content() }
    }
}
