package com.edumio.app.quiz

/**
 * Shared answer-option helpers so every quiz / review / solution screen labels options identically.
 */
object OptionLabels {

    /**
     * True when the stored choices are ONLY the bare letters A, B, C, … — i.e. the real answer options
     * live inside the question figure and each stored choice is just its letter (verified: 61 IMAT figure
     * questions; 0 in TIL-I / CEnT-S / EDUmio-original).
     *
     * For these questions the UI must:
     *  - show the option button as the bare letter, never "A) A" (no duplicated label), and
     *  - NOT shuffle the options — the letter names a fixed region of the figure, so display order must
     *    stay identity or the letters would point at the wrong part of the image.
     */
    fun isLetterOptions(choices: List<String>): Boolean =
        choices.isNotEmpty() && choices.withIndex().all { (i, c) -> c.trim() == ('A' + i).toString() }
}
