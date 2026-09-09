// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.aiswipe

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings

/** Separate from native candidates: cloud updates never move a word under a pending native tap. */
class AiSwipeSuggestionBar @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs) {
    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        if (android.os.Build.VERSION.SDK_INT >= 26)
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    }

    private fun theme() {
        Settings.getInstance().current.mColors.setBackground(this, ColorType.MAIN_BACKGROUND)
    }

    fun hide() {
        removeAllViews() // also drops closures containing draft text
        visibility = GONE
    }

    fun showStatus(message: Int) {
        removeAllViews()
        theme()
        addView(TextView(context).apply {
            setText(message)
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Settings.getInstance().current.mColors.get(ColorType.KEY_TEXT))
            setPadding(dp(8), 0, dp(8), 0)
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        visibility = VISIBLE
    }

    fun showSuggestions(word: String, phrase: String?, onWord: () -> Unit, onPhrase: () -> Unit) {
        removeAllViews()
        theme()
        addChoice(context.getString(R.string.ai_swipe_word_chip, word), word, 1f, onWord)
        if (phrase != null) addChoice(context.getString(R.string.ai_swipe_phrase_chip, phrase), phrase, 2f, onPhrase)
        visibility = VISIBLE
    }

    fun showUndo(action: () -> Unit) {
        removeAllViews()
        theme()
        addChoice(context.getString(R.string.ai_swipe_undo), "", 1f, action)
        visibility = VISIBLE
    }

    private fun addChoice(label: String, fullText: String, weight: Float, action: () -> Unit) {
        addView(Button(context, null, R.attr.suggestionWordStyle).apply {
            text = label
            contentDescription = label
            isAllCaps = false
            textSize = 14f
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(6), 0, dp(6), 0)
            setTextColor(Settings.getInstance().current.mColors.get(ColorType.KEY_TEXT))
            setOnClickListener { action() }
            if (fullText.isNotEmpty()) setOnLongClickListener {
                Toast.makeText(context, fullText, Toast.LENGTH_LONG).show()
                true
            }
        }, LayoutParams(0, LayoutParams.MATCH_PARENT, weight))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
