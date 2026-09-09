// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.aiswipe

import android.text.InputType
import android.view.inputmethod.EditorInfo

object AiSwipePolicy {
    /** Must run BEFORE reading any editor context. Unknown/specialized fields fail closed. */
    fun allowsField(inputType: Int, imeOptions: Int, consent: Boolean,
                    incognito: Boolean, locked: Boolean): Boolean {
        if (!consent || incognito || locked) return false
        if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        if (inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0) return false
        if (imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0) return false
        return inputType and InputType.TYPE_MASK_VARIATION in setOf(
            InputType.TYPE_TEXT_VARIATION_NORMAL, InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE,
            InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE, InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT)
    }

    // Defense in depth, not a claim to detect every secret in an otherwise normal text field.
    private val sensitive = Regex("@|://|[0-9]{4,}|[A-Za-z0-9_+/=-]{24,}")
    fun allowsContext(text: String): Boolean = !sensitive.containsMatchIn(text)
}
