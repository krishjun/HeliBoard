// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.aiswipe

import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

internal object AiSwipeAppCheck {
    fun install(app: FirebaseApp) {
        FirebaseAppCheck.getInstance(app).apply {
            installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
            setTokenAutoRefreshEnabled(false)
        }
    }
}
