package com.edumio.app.ui

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.edumio.app.R

/**
 * The ONE filter / segmented-control chip row for the whole app (UX rules 1, 2, 3, 4, 5, 9, 12).
 *
 * Every filter, sort and category selector renders through here, so the app can never again ship a row
 * where the user cannot tell which option is active. A chip is:
 *  - SELECTED → filled brand colour + white bold label (an unmistakable step, not a subtle tint),
 *  - unselected → quiet surface + hairline border + muted label,
 *  - always >= 48dp tall including padding (Android touch-target guidance) with dp — never raw pixels,
 *  - pill-rounded so it reads as a control, and visually distinct from action buttons (which are the
 *    taller, elevated [R.style.BBButton]) and from read-only stat chips.
 *
 * Selection is owned here: tapping re-renders the row immediately, so feedback is instant.
 */
object FilterChips {

    /**
     * Fills [container] with one chip per option and keeps exactly one selected.
     *
     * @param options label → value pairs, in display order.
     * @param selected the currently-active value (compared with ==).
     * @param onSelect invoked with the newly-chosen value; the row re-renders itself first.
     */
    fun <T> bind(
        container: LinearLayout,
        options: List<Pair<String, T>>,
        selected: T,
        onSelect: (T) -> Unit,
    ) {
        container.removeAllViews()
        val ctx = container.context
        options.forEach { (label, value) ->
            val chip = chipView(ctx, label, selected = value == selected)
            chip.setOnClickListener { view ->
                Interactions.tap(view) // same haptic + press-bounce as every other tappable surface
                // Re-render the whole row first so the new selection is visible immediately,
                // even if the caller's work (a DB query) takes a moment.
                bind(container, options, value, onSelect)
                onSelect(value)
            }
            container.addView(chip)
        }
    }

    /** A single chip laid out to the canonical spec. */
    private fun chipView(ctx: Context, label: String, selected: Boolean): TextView =
        TextView(ctx).apply {
            text = label
            isSelected = selected // drives the state-list background + text colour
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_PX, ctx.resources.getDimension(R.dimen.chip_text_size))
            setTextColor(ContextCompat.getColorStateList(ctx, R.color.filter_chip_text))
            setBackgroundResource(R.drawable.bg_filter_chip)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            val padH = ctx.dp(R.dimen.chip_padding_h)
            setPaddingRelative(padH, 0, padH, 0)
            minHeight = ctx.dp(R.dimen.chip_min_height)
            minimumHeight = ctx.dp(R.dimen.chip_min_height)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                ctx.dp(R.dimen.chip_min_height),
            ).apply { marginEnd = ctx.dp(R.dimen.chip_gap) }
        }

    private fun Context.dp(dimenRes: Int): Int = resources.getDimensionPixelSize(dimenRes)
}
