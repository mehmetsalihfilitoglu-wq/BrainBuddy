package com.edumio.app.db

/**
 * Questions withheld from serving because their SOURCE IMAGE is physically cropped in a way that makes
 * the answer impossible to determine reliably.
 *
 * These are not content errors and not renderer bugs — the required pixels were never captured when the
 * figure was extracted, so no display setting can restore them. Rather than show a student a question
 * they cannot possibly solve, the seeder stamps [REASON] into QuestionEntity.unservableReason, which the
 * Daily-Challenge candidate query already filters out (it requires unservableReason IS NULL OR '').
 *
 * The source records are NOT deleted. When a corrected, un-cropped asset ships, removing the id here
 * (and bumping the bank's seed version) reactivates the question automatically.
 *
 * Classification rule used: block ONLY where the answer cannot be reliably determined — a missing
 * formula/symbol, a sliced table header, truncated answer options with no textual fallback, or a
 * different question bleeding into the frame. Cosmetically imperfect but answerable figures stay.
 */
object UnservableQuestions {

    const val REASON = "IMAGE_CROPPED_UNSERVABLE"

    /**
     * IMAT (11). Evidence per id from the real-asset pixel audit.
     *  - chemistry_042: the isotope/ion symbol the question is about is above the crop; only a stray
     *    superscript minus survives. Stem is a "[particle symbol not extracted]" placeholder.
     *  - mathematics_070 / 076 / 077 / 055: the governing expression (inequality, exponents, fraction
     *    numerators, the averaged quantity) sits on the sliced first line; stems are "[expression]"
     *    placeholders and the choices are bare A–E, so no second copy exists anywhere.
     *  - chemistry_047: options A and C are truncated off the left/right edges and the choices are bare
     *    A–E, so the figure is the sole carrier of every option; also bleeds an unrelated question.
     *  - physics_057: the crop starts on the table's first rule, so the header naming the two numeric
     *    columns (and their units) is absent; the stem does not restate them.
     *  - biology_017 / biology_020 / critical_thinking_007: 2023 screenshot batch, cropped on all four
     *    sides; sliced table headers, every line missing leading characters, and a DIFFERENT question's
     *    stem and options bleeding into the frame.
     *  - physics_058: the wood-density symbol is only ~40% legible on the sliced first line while the
     *    oil density is clean, so a student cannot reliably tell the two symbols apart in the options.
     */
    private val IMAT = setOf(
        "imat_2017_past_paper_chemistry_042",
        "imat_2012_specimen_mathematics_070",
        "imat_2012_specimen_mathematics_076",
        "imat_2012_specimen_mathematics_077",
        "imat_2017_past_paper_mathematics_055",
        "imat_2023_past_paper_chemistry_047",
        "imat_2021_past_paper_physics_057",
        "imat_2023_past_paper_biology_017",
        "imat_2023_past_paper_biology_020",
        "imat_2023_past_paper_critical_thinking_007",
        "imat_2017_past_paper_physics_058",
    )

    /**
     * TIL-I (9). All "physically-cropped-source": the stem or required figure content was never in the
     * captured file. Two are especially clear — the JSON itself admits "[Question stem not visible on
     * page]" and "[Stem appears on the preceding page, not in this chunk]" (that one additionally
     * pre-highlights the correct option in the image).
     */
    private val TIL_I = setOf(
        "til_i_ex_repr_008",
        "til_i_ex_math_069",
        "til_i_ex_math_081",
        "til_i_ex_math_116",
        "til_i_ex_math_097",
        "til_i_orig_math_0187",
        "til_i_orig_repr_0132",
        "til_i_train_math_a1_q18",
        "til_i_mock_logic_01",
    )

    /**
     * CEnT-S (3). Content rendered past the fixed 900px canvas at generation time, where the lost
     * content is the question: two stems explicitly delegate the quantitative setup to a figure whose
     * caption text is cut off, and in the third the clipped object IS the subject of the question.
     * The other four flagged CEnT-S figures remain servable — their answer survives the crop.
     */
    private val CENT_S = setOf(
        "cents_s_orig_elite_0604",
        "cents_s_orig_phys_0181",
        "cents_s_orig_elite_0257",
    )

    /** Every withheld id, across all banks. */
    val ALL: Set<String> = IMAT + TIL_I + CENT_S

    /** True when this question must not be served. */
    fun isUnservable(questionId: String): Boolean = questionId in ALL

    /** The reason to stamp, or null when the question is fine. */
    fun reasonFor(questionId: String): String? = if (isUnservable(questionId)) REASON else null
}
