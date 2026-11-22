// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.composeApp.resources.Res
import io.github.velosako3.blossom.composeApp.resources.signin_cookie_bad
import io.github.velosako3.blossom.composeApp.resources.signin_cookie_hint
import io.github.velosako3.blossom.composeApp.resources.signin_cookie_label
import io.github.velosako3.blossom.composeApp.resources.signin_save
import io.github.velosako3.blossom.composeApp.resources.signin_ytm_title
import io.github.velosako3.blossom.core.designsystem.components.GlassButton
import io.github.velosako3.blossom.core.designsystem.components.GlassTextField
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.core.platform.SecureStore
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource


/**
 * Desktop has no embedded browser, so YouTube Music sign-in takes the `Cookie` header of a signed-in
 * music.youtube.com tab (copied from the browser's developer tools). It's kept in the secure store only.
 */
@Composable
actual fun AccountSignInScreen(account: Account, secure: SecureStore, onDone: (Boolean) -> Unit, modifier: Modifier, topBar: @Composable () -> Unit) {
    if (account == Account.SPOTIFY) { LaunchedEffect(Unit) { onDone(false) }; return }
    var cookie by remember { mutableStateOf("") }
    var bad by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxSize()) {
        topBar()
        Column(Modifier.padding(16.dp).widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(Res.string.signin_ytm_title), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(Res.string.signin_cookie_hint), color = BlossomTheme.colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            GlassTextField(cookie, { cookie = it; bad = false }, placeholder = stringResource(Res.string.signin_cookie_label),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), visualTransformation = PasswordVisualTransformation())
            if (bad) Text(stringResource(Res.string.signin_cookie_bad), color = BlossomTheme.colors.error, style = MaterialTheme.typography.bodyMedium)
            GlassButton({
                val value = cookie.trim().removePrefix("Cookie:").trim()
                if (!isYtmCookie(value)) bad = true else scope.launch { secure.put(account.storeKey(), value); onDone(true) }
            }, enabled = cookie.isNotBlank(), prominent = true) { Text(stringResource(Res.string.signin_save)) }
        }
    }
}

actual val spotifySignInSupported: Boolean = false

actual fun clearWebSession(account: Account) = Unit
