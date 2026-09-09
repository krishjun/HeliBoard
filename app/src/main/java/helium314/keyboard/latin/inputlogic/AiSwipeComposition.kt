// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.inputlogic

/** Read-only snapshots; the AI controller must not mutate the internal composer directly. */
internal val InputLogic.aiSwipeWord: String get() = mWordComposer.typedWord
internal val InputLogic.isAiSwipeBatch: Boolean get() = mWordComposer.isBatchMode
internal val InputLogic.isAiSwipeComposing: Boolean get() = mWordComposer.isComposingWord
