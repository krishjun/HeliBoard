// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.aiswipe

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.*
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings

/** Cloud choices never move native word candidates. Sentences require a full preview then Insert. */
class AiSwipeSuggestionBar @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs) {
    private var sentenceMode = false
    private var toggle: (() -> Unit)? = null
    private var row: LinearLayout? = null
    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        if (android.os.Build.VERSION.SDK_INT >= 26)
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    }

    fun setMode(sentence: Boolean, action: () -> Unit) { sentenceMode = sentence; toggle = action }

    fun hide() {
        removeAllViews(); row = null // drop closures holding private draft/alternatives
        visibility = GONE
    }

    private fun reset(): LinearLayout {
        removeAllViews()
        Settings.getInstance().current.mColors.setBackground(this, ColorType.MAIN_BACKGROUND)
        layoutParams?.let { if (it.height != dp(112)) { it.height = dp(112); layoutParams = it } }
        toggle?.let { action ->
            addView(button(context.getString(if (sentenceMode) R.string.ai_sentence_word_mode else R.string.ai_sentence_mode), action),
                LayoutParams(dp(84), LayoutParams.MATCH_PARENT))
        }
        return LinearLayout(context).also {
            it.gravity = Gravity.CENTER_VERTICAL
            addView(it, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            row = it; visibility = VISIBLE
        }
    }

    fun showStatus(message: Int) {
        val target = reset()
        target.addView(TextView(context).apply {
            setText(message); textSize = 13f; gravity = Gravity.CENTER
            setTextColor(Settings.getInstance().current.mColors.get(ColorType.KEY_TEXT))
            setPadding(dp(8), 0, dp(8), 0)
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun showSuggestions(word: String, phrase: String?, onWord: () -> Unit, onPhrase: () -> Unit) {
        reset()
        addChoice(context.getString(R.string.ai_swipe_word_chip, word), word, 1f, onWord)
        if (phrase != null) addChoice(context.getString(R.string.ai_swipe_phrase_chip, phrase), phrase, 2f, onPhrase)
    }

    fun showSentences(choices: List<String>, accept: (String) -> Unit) {
        val target = reset()
        val scroll = HorizontalScrollView(context)
        val cards = LinearLayout(context)
        scroll.addView(cards, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        choices.forEachIndexed { index, text ->
            cards.addView(button(context.getString(R.string.ai_sentence_choice, index + 1, text)) {
                showSentencePreview(text, { accept(text) }, { showSentences(choices, accept) })
            }, LayoutParams(dp(230), LayoutParams.MATCH_PARENT))
        }
        target.addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun showSentencePreview(text: String, accept: () -> Unit, back: () -> Unit) {
        val target = reset()
        target.orientation = VERTICAL
        val scroll = ScrollView(context)
        scroll.addView(TextView(context).apply {
            this.text = text; textSize = 17f
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setTextColor(Settings.getInstance().current.mColors.get(ColorType.KEY_TEXT))
            // Full text is readable/scrollable and exposed to accessibility before acceptance.
        })
        target.addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        val actions = LinearLayout(context)
        actions.addView(button(context.getString(R.string.ai_sentence_insert), accept), LayoutParams(0, dp(48), 1f))
        actions.addView(button(context.getString(R.string.ai_sentence_other), back), LayoutParams(0, dp(48), 1f))
        target.addView(actions)
    }

    fun showUndo(action: () -> Unit) {
        reset()
        addChoice(context.getString(R.string.ai_swipe_undo), "", 1f, action)
    }

    private fun button(label: String, action: () -> Unit) = Button(context, null, R.attr.suggestionWordStyle).apply {
        text = label; contentDescription = label; isAllCaps = false; textSize = 14f
        maxLines = 4; ellipsize = TextUtils.TruncateAt.END
        setPadding(dp(6), 0, dp(6), 0)
        setTextColor(Settings.getInstance().current.mColors.get(ColorType.KEY_TEXT))
        setOnClickListener { action() }
    }

    private fun addChoice(label: String, fullText: String, weight: Float, action: () -> Unit) {
        row?.addView(button(label, action).apply {
            if (fullText.isNotEmpty()) setOnLongClickListener {
                Toast.makeText(context, fullText, Toast.LENGTH_LONG).show(); true
            }
        }, LayoutParams(0, LayoutParams.MATCH_PARENT, weight))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
