// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.aiswipe

import android.content.Context
import helium314.keyboard.latin.utils.prefs
import java.io.File

/** A restored settings backup cannot silently grant permission to upload another user's draft. */
object AiSwipeConsent {
    @Volatile private var granted: Boolean? = null
    private fun marker(context: Context) = File(context.noBackupFilesDir, "ai-swipe-consent-v2")

    fun isGranted(context: Context): Boolean {
        val consent = granted ?: marker(context).isFile.also { granted = it }
        return consent && context.prefs().getBoolean(AI_SWIPE_ENABLED, false)
    }

    fun grant(context: Context): Boolean = try {
        marker(context).writeText("2")
        granted = true
        context.prefs().edit().putBoolean(AI_SWIPE_ENABLED, true).apply()
        true
    } catch (_: Exception) { false }

    fun revoke(context: Context) {
        granted = false
        marker(context).delete()
        context.prefs().edit().putBoolean(AI_SWIPE_ENABLED, false).apply()
    }
}
