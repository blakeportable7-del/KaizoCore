package com.ironmonone.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ironmonone.app.gen3.Gen3
import com.ironmonone.app.gen3.Gen3Button

/**
 * RetroAchievements: sign in, see the game's set, choose hardcore.
 *
 * Signed out: a username and password form. The password goes to the
 * native client for one login request and is not kept; what is kept is
 * the session token the server returns.
 * Signed in: the user and score, the loaded game's progress, the list of
 * achievements grouped as rcheevos groups them, and the hardcore switch.
 */
@Composable
fun RetroAchievementsDialog(
    summary: RetroAchievements.Summary,
    achievements: List<RetroAchievements.Achievement>,
    busy: String?,
    error: String?,
    tracked: Boolean,
    onLogin: (String, String) -> Unit,
    onLogout: () -> Unit,
    onHardcore: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    ShellDialog("RetroAchievements", onDismiss = onDismiss) {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
            if (!summary.loggedIn) {
                Text(
                    "Sign in with your retroachievements.org account. The password is sent once and not stored.",
                    style = MaterialTheme.typography.bodySmall, color = Gen3.Ink,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(user, { user = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    password, { password = it }, label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                if (busy != null) Text(busy, style = MaterialTheme.typography.bodySmall, color = Gen3.Ink)
                if (error != null) Text(error, style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color(0xFFB03030))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Gen3Button("SIGN IN", enabled = busy == null && user.isNotBlank() && password.isNotBlank(), accent = true) {
                        onLogin(user.trim(), password); password = ""
                    }
                    Gen3Button("CLOSE", onClick = onDismiss)
                }
            } else {
                Text("${summary.user}  ·  ${summary.score} points", style = MaterialTheme.typography.bodyMedium, color = Gen3.Ink)
                Spacer(Modifier.height(4.dp))
                when {
                    tracked -> Text(
                        "IronMON run: achievements are not loaded here. A randomized ROM has no set, and the run stays unquestionable. Play a game from the library to earn them.",
                        style = MaterialTheme.typography.bodySmall, color = Gen3.Ink,
                    )
                    summary.gameLoaded -> Text(
                        "${summary.game}: ${summary.unlocked} of ${summary.total} unlocked, ${summary.pointsUnlocked} of ${summary.pointsTotal} points.",
                        style = MaterialTheme.typography.bodySmall, color = Gen3.Ink,
                    )
                    summary.loadError.isNotBlank() -> Text(summary.loadError, style = MaterialTheme.typography.bodySmall, color = Gen3.Ink)
                    busy != null -> Text(busy, style = MaterialTheme.typography.bodySmall, color = Gen3.Ink)
                    else -> Text("No achievement set for this game (not in the RetroAchievements database, or a patched file).", style = MaterialTheme.typography.bodySmall, color = Gen3.Ink)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Gen3Button(if (summary.hardcore) "HARDCORE ON" else "HARDCORE OFF", accent = summary.hardcore) { onHardcore(!summary.hardcore) }
                    Text(
                        if (summary.hardcore) "No cheats, rewind, slow motion or state loads." else "Softcore: everything allowed, unlocks marked softcore.",
                        style = MaterialTheme.typography.bodySmall, color = Gen3.Ink,
                    )
                }
                if (achievements.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    var lastBucket = ""
                    achievements.forEach { a ->
                        if (a.bucket != lastBucket) {
                            lastBucket = a.bucket
                            Spacer(Modifier.height(4.dp))
                            Text(a.bucket.uppercase(), style = MaterialTheme.typography.labelSmall, color = Gen3.Ink)
                        }
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                            Text(if (a.unlocked) "✓" else "·", style = MaterialTheme.typography.bodyMedium, color = Gen3.Ink, modifier = Modifier.width(18.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${a.title}  (${a.points})", style = MaterialTheme.typography.bodyMedium, color = Gen3.Ink)
                                if (a.description.isNotBlank()) Text(a.description, style = MaterialTheme.typography.bodySmall, color = Gen3.Ink)
                                if (!a.unlocked && a.progress > 0f) Text("${a.progress.toInt()}%", style = MaterialTheme.typography.bodySmall, color = Gen3.Ink)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Gen3Button("SIGN OUT", onClick = onLogout)
                    Gen3Button("CLOSE", onClick = onDismiss)
                }
            }
        }
    }
}
