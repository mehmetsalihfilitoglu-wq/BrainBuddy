package com.edumio.app.ui

import androidx.annotation.DrawableRes
import com.edumio.app.R

/**
 * The EDUmio mascot — the app's permanent brand character. This is the single place that maps a
 * product moment to a mascot *expression*, so screens ask for `EduMascot.drawable(HAPPY)` rather than
 * hard-coding a resource. Today every expression resolves to the one master mascot drawable
 * (`ic_edumio_mascot`); when per-expression artwork or Lottie/AnimatedVectorDrawable animations are
 * added, only this file changes — call sites stay the same.
 *
 * Expressions are kept subtle and professional (never childish), per brand guidance.
 */
object EduMascot {

    enum class Expression {
        NEUTRAL,     // default / idle
        HAPPY,       // correct answer
        EXCITED,     // perfect streak / milestone
        THINKING,    // wrong answer / review
        SLEEPING,    // no challenge available today
        CELEBRATE,   // Daily Challenge completed
        PREMIUM,     // premium screens (future: gold graduation ribbon)
        WAVE,        // onboarding greeting
    }

    /** Master mascot for a given expression. Future expression art plugs in here only. */
    @DrawableRes
    fun drawable(@Suppress("UNUSED_PARAMETER") expression: Expression = Expression.NEUTRAL): Int =
        R.drawable.ic_edumio_mascot

    /** Expression for a Daily-Challenge completion, based on how well it went. */
    fun forCompletion(score: Int, total: Int, streak: Int): Expression = when {
        streak >= 3 && score == total -> Expression.EXCITED
        score >= (total + 1) / 2 -> Expression.CELEBRATE
        else -> Expression.THINKING
    }

    /** Expression for the home card, based on today's state. */
    fun forHome(available: Boolean, completed: Boolean): Expression = when {
        completed -> Expression.CELEBRATE
        !available -> Expression.SLEEPING
        else -> Expression.NEUTRAL
    }

    /** Expression for a single answer. */
    fun forAnswer(correct: Boolean): Expression = if (correct) Expression.HAPPY else Expression.THINKING
}
