// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import helium314.keyboard.latin.R
import helium314.keyboard.latin.aiswipe.AiSwipeConsent
import helium314.keyboard.latin.aiswipe.AiSwipeProviderFactory
import helium314.keyboard.settings.Setting

@Composable
fun AiSwipePreference(setting: Setting) {
    val ctx = LocalContext.current
    var consent by remember { mutableStateOf(AiSwipeConsent.isGranted(ctx)) }
    var dialog by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val configured = AiSwipeProviderFactory.isConfigured()
    fun toggle(value: Boolean) {
        if (value) dialog = true
        else { AiSwipeConsent.revoke(ctx); consent = false }
    }
    Preference(name = setting.title, description = setting.description, onClick = { toggle(!consent) }) {
        Switch(checked = consent, onCheckedChange = ::toggle)
    }
    if (dialog) AlertDialog(
        onDismissRequest = { dialog = false },
        title = { Text(stringResource(R.string.ai_swipe_title)) },
        text = { Text(stringResource(when {
            !configured -> R.string.ai_swipe_setup_required
            error -> R.string.ai_swipe_consent_error
            else -> R.string.ai_swipe_consent
        })) },
        confirmButton = {
            if (configured) TextButton(onClick = {
                consent = AiSwipeConsent.grant(ctx)
                error = !consent
                if (consent) dialog = false
            }) { Text(stringResource(R.string.ai_swipe_agree)) }
        },
        dismissButton = { TextButton(onClick = { dialog = false }) { Text(stringResource(android.R.string.cancel)) } }
    )
}
