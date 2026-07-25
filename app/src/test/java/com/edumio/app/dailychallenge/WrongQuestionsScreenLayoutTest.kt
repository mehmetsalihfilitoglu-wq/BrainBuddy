package com.edumio.app.dailychallenge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Layout regression guard for the "Yanlış Sorularım" (Wrong Questions) screen.
 *
 * The reported defect: "Soruyu tekrar çöz" was cut off at the bottom. Root cause was in the markup, not
 * the renderer — the two action buttons were pinned to android:layout_height="46dp", which overrode
 * BBButton's minHeight; MaterialButton then subtracts its default 6dp top + 6dp bottom insets, leaving a
 * ~34dp content box. The 17-character Turkish label also could not fit on one line in half a card row on
 * a 360dp phone, so it wrapped to a second line that the fixed height then clipped.
 *
 * These assertions read the SHIPPED XML, so the defect cannot silently return: a fixed height, a lost
 * 48dp floor, unequal buttons, or the statistics reverting to floating text all fail the build.
 */
class WrongQuestionsScreenLayoutTest {

    private val ANDROID = "http://schemas.android.com/apk/res/android"

    private fun resRoot(): File = listOf(
        File("src/main/res"), File("app/src/main/res"), File("../app/src/main/res"),
    ).firstOrNull { it.isDirectory } ?: error("res root not found (cwd=${File(".").absolutePath})")

    private fun parse(rel: String): Element {
        val f = File(resRoot(), rel)
        assertTrue("$rel must exist", f.isFile)
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return factory.newDocumentBuilder().parse(f).documentElement
    }

    private fun Element.descendants(): List<Element> {
        val out = ArrayList<Element>()
        fun walk(n: Node) {
            var c = n.firstChild
            while (c != null) {
                if (c.nodeType == Node.ELEMENT_NODE) { out.add(c as Element); walk(c) }
                c = c.nextSibling
            }
        }
        walk(this)
        return out
    }

    private fun Element.androidAttr(name: String): String? =
        getAttributeNS(ANDROID, name).takeIf { it.isNotEmpty() }

    private fun Element.idName(): String? = androidAttr("id")?.substringAfterLast('/')

    private fun Element.styleRef(): String? = getAttribute("style").takeIf { it.isNotEmpty() }

    /** values/dimens.xml as name -> literal (e.g. "touch_target" -> "48dp"). */
    private fun dimens(): Map<String, String> =
        parse("values/dimens.xml").descendants()
            .filter { it.tagName == "dimen" }
            .associate { it.getAttribute("name") to it.textContent.trim() }

    /** The `<item>` values of one style in values/styles.xml, keyed by item name. */
    private fun styleItems(styleName: String): Map<String, String> {
        val style = parse("values/styles.xml").descendants()
            .firstOrNull { it.tagName == "style" && it.getAttribute("name") == styleName }
            ?: error("style '$styleName' not found in values/styles.xml")
        return style.descendants()
            .filter { it.tagName == "item" }
            .associate { it.getAttribute("name") to it.textContent.trim() }
    }

    /** Resolves "@dimen/foo" against dimens.xml; passes literals ("48dp") straight through. */
    private fun resolveDimen(value: String, dimens: Map<String, String>): String =
        if (value.startsWith("@dimen/")) dimens[value.removePrefix("@dimen/")]
            ?: error("unresolved $value") else value

    private fun dp(value: String): Int {
        assertTrue("expected a dp value, got '$value'", value.endsWith("dp"))
        return value.removeSuffix("dp").toFloat().toInt()
    }

    private val ACTION_STYLE = "@style/Widget.EDUmio.WrongQuestion.Action"

    private fun actionButtons(): List<Element> =
        parse("layout/item_wrong_question.xml").descendants()
            .filter { it.idName() == "wqRetry" || it.idName() == "wqSolution" }

    // ── 1. "Soruyu tekrar çöz" is not clipped ───────────────────────────────────────────────────

    @Test
    fun actionButtonsDeclareNoFixedHeight_soTheLabelCannotBeClipped() {
        val buttons = actionButtons()
        assertEquals("both action buttons must exist", 2, buttons.size)
        for (b in buttons) {
            val h = b.androidAttr("layout_height")
            // A fixed dp height is exactly what cut the label off; height must be content-driven.
            assertFalse(
                "${b.idName()}: layout_height must not be a fixed dp value (was '$h')",
                h != null && h.endsWith("dp"),
            )
            assertEquals("${b.idName()}: must use the shared action component", ACTION_STYLE, b.styleRef())
        }
        val styleHeight = styleItems("Widget.EDUmio.WrongQuestion.Action")["android:layout_height"]
        assertEquals("the shared action component sizes to its content", "wrap_content", styleHeight)
    }

    @Test
    fun sharedActionStyleReservesRealVerticalSpaceForTheText() {
        val d = dimens()
        val s = styleItems("Widget.EDUmio.WrongQuestion.Action")

        // MaterialButton's default 6dp insets silently shrink the usable box; they must be zeroed so
        // the declared height IS the usable height.
        assertEquals("insetTop must be zeroed", 0, dp(s.getValue("android:insetTop")))
        assertEquals("insetBottom must be zeroed", 0, dp(s.getValue("android:insetBottom")))

        val padTop = dp(resolveDimen(s.getValue("android:paddingTop"), d))
        val padBottom = dp(resolveDimen(s.getValue("android:paddingBottom"), d))
        assertTrue("needs real top padding (was ${padTop}dp)", padTop >= 8)
        assertTrue("needs real bottom padding (was ${padBottom}dp)", padBottom >= 8)

        assertEquals("text must be vertically centred", "center", s["android:gravity"])
        assertEquals("label stays on one line", "1", s["android:maxLines"])
    }

    // ── 2. Both buttons meet the 48dp minimum and are equal height ──────────────────────────────

    @Test
    fun bothActionButtonsMeetTheFortyEightDpTouchMinimum() {
        val d = dimens()
        val minHeight = dp(resolveDimen(
            styleItems("Widget.EDUmio.WrongQuestion.Action").getValue("android:minHeight"), d,
        ))
        assertTrue("action buttons must be at least 48dp tall (was ${minHeight}dp)", minHeight >= 48)
        // Every button carrying the style inherits that floor; none may opt out with a smaller height.
        for (b in actionButtons()) {
            assertTrue(
                "${b.idName()}: must not override minHeight below 48dp",
                b.androidAttr("minHeight")?.let { dp(resolveDimen(it, d)) >= 48 } ?: true,
            )
        }
    }

    @Test
    fun bothActionButtonsAreTheSameSize() {
        val buttons = actionButtons()
        // Identical component + no per-instance size/typography overrides == identical measured height.
        assertEquals("both use one component", 1, buttons.map { it.styleRef() }.toSet().size)
        for (b in buttons) {
            for (attr in listOf("layout_height", "layout_width", "textSize", "minHeight", "padding",
                    "paddingTop", "paddingBottom", "insetTop", "insetBottom")) {
                assertTrue(
                    "${b.idName()}: must not override '$attr' per-instance (breaks equal sizing)",
                    b.androidAttr(attr) == null,
                )
            }
        }
        assertEquals(
            "the shared component spans the full card width, so both buttons are equally wide",
            "match_parent",
            styleItems("Widget.EDUmio.WrongQuestion.Action")["android:layout_width"],
        )
    }

    @Test
    fun actionsStackVerticallyRatherThanCuttingText() {
        // descendants() is in document order, so the LAST container holding the button is the innermost.
        val row = parse("layout/item_wrong_question.xml").descendants()
            .last { e -> e.descendants().any { it.idName() == "wqRetry" } }
        // The row cannot safely fit two 17-character Turkish labels side by side on a 360dp phone;
        // per the fix rule we stack instead of truncating.
        assertEquals("action row must stack vertically", "vertical", row.androidAttr("orientation"))
    }

    @Test
    fun everyWrongQuestionFlowScreenUsesTheSameActionComponent() {
        // "Soruyu tekrar çöz" also appears on the solution screen reached via "Çözümü gör".
        val solRetry = parse("layout/activity_solution.xml").descendants()
            .firstOrNull { it.idName() == "solRetryBtn" }
        assertNotNull("solution screen retry button must exist", solRetry)
        assertEquals(
            "solution screen must use the same action component",
            ACTION_STYLE, solRetry!!.styleRef(),
        )
        assertFalse(
            "solution screen retry must not pin a fixed height",
            solRetry.androidAttr("layout_height")?.endsWith("dp") == true,
        )
    }

    // ── 3. Top statistics render as three cards ─────────────────────────────────────────────────

    private fun statCards(): List<Element> =
        parse("layout/activity_wrong_questions.xml").descendants()
            .filter { it.styleRef() == "@style/Widget.EDUmio.StatCard" }

    @Test
    fun topStatisticsAreThreeCardsNotFloatingText() {
        val cards = statCards()
        assertEquals("Aktif / Sırada / Çözüldü must each be a card", 3, cards.size)
        for (c in cards) {
            assertTrue(
                "stat cards must be MaterialCardView (was ${c.tagName})",
                c.tagName.endsWith("MaterialCardView"),
            )
        }
    }

    @Test
    fun eachStatCardShowsALargeNumberAboveASmallerLabel() {
        val expected = mapOf(
            "wpActive" to "@string/wp_active_count",
            "wpDue" to "@string/wp_due_count",
            "wpResolved" to "@string/wp_resolved_count",
        )
        val valueSize = styleItems("Widget.EDUmio.StatCardValue").getValue("android:textSize")
        val labelSize = styleItems("Widget.EDUmio.StatCardLabel").getValue("android:textSize")
        assertTrue(
            "the number must be the primary element (value $valueSize vs label $labelSize)",
            valueSize.removeSuffix("sp").toFloat() > labelSize.removeSuffix("sp").toFloat(),
        )

        for (card in statCards()) {
            val texts = card.descendants().filter { it.tagName == "TextView" }
            assertEquals("each card holds exactly a value and a label", 2, texts.size)
            val (value, label) = texts[0] to texts[1]
            assertEquals("value comes first", "@style/Widget.EDUmio.StatCardValue", value.styleRef())
            assertEquals("label sits underneath", "@style/Widget.EDUmio.StatCardLabel", label.styleRef())
            val id = value.idName()
            assertTrue("unexpected stat id '$id'", id in expected)
            assertEquals("$id: label text", expected[id], label.androidAttr("text"))
            // The number is bound at runtime; the label is static. Neither may carry the other's job.
            assertTrue("$id: the value must not hard-code text", value.androidAttr("text") == null)
        }
    }

    @Test
    fun statCardsAreEqualWidthEqualHeightAndNotClickable() {
        val s = styleItems("Widget.EDUmio.StatCard")
        assertEquals("equal width via weight", "0dp", s["android:layout_width"])
        assertEquals("equal width via weight", "1", s["android:layout_weight"])
        assertEquals("equal height: all cards match the tallest", "match_parent", s["android:layout_height"])
        assertTrue(
            "read-only counters must not gain a clickable foreground",
            s["android:foreground"] == null && s["android:clickable"] == null,
        )

        val row = parse("layout/activity_wrong_questions.xml").descendants()
            .last { e -> e.descendants().any { it.styleRef() == "@style/Widget.EDUmio.StatCard" } }
        assertEquals("stat cards sit in one row", "horizontal", row.androidAttr("orientation"))
        assertEquals(
            "baselineAligned must be off or match_parent heights collapse",
            "false", row.androidAttr("baselineAligned"),
        )
    }

    // ── 4. Visual consistency with the question cards below ─────────────────────────────────────

    @Test
    fun statCardsMatchTheQuestionCardsSurfaceAndTheScreensPagePadding() {
        val questionCard = parse("layout/item_wrong_question.xml")
        val questionRadius = questionCard.getAttributeNS(
            "http://schemas.android.com/apk/res-auto", "cardCornerRadius",
        )
        val stat = styleItems("Widget.EDUmio.StatCard")
        assertEquals("same corner radius as the question cards", questionRadius, stat["cardCornerRadius"])
        assertEquals("same white surface", "@color/white", stat["cardBackgroundColor"])

        val d = dimens()
        assertEquals(
            "no heavy shadow",
            0, dp(resolveDimen(stat.getValue("cardElevation"), d)),
        )

        // Header block and the question list must share one horizontal page padding, or the stat cards
        // sit wider than the cards beneath them.
        val paddings = parse("layout/activity_wrong_questions.xml").descendants()
            .mapNotNull { it.androidAttr("paddingStart") }
            .filter { it.startsWith("@dimen/") }
            .toSet()
        assertEquals(
            "header and list must use the same page padding token (found $paddings)",
            setOf("@dimen/page_padding_h"), paddings,
        )
    }

    // ── 5. Large font scale ─────────────────────────────────────────────────────────────────────

    @Test
    fun screenStaysUsableAtLargeFontScale() {
        val action = styleItems("Widget.EDUmio.WrongQuestion.Action")
        // Buttons grow with the text (no fixed height) and the label shrinks within a floor rather
        // than being truncated.
        assertEquals("wrap_content", action["android:layout_height"])
        assertEquals("uniform", action["autoSizeTextType"])
        val min = action.getValue("autoSizeMinTextSize").removeSuffix("sp").toFloat()
        val max = action.getValue("autoSizeMaxTextSize").removeSuffix("sp").toFloat()
        assertTrue("autosize range must be valid ($min..$max)", min in 10f..max && max <= 16f)

        // Nothing in a stat card is height-constrained, so a wrapped label expands the card.
        for (style in listOf("Widget.EDUmio.StatCardContent", "Widget.EDUmio.StatCardValue",
                "Widget.EDUmio.StatCardLabel")) {
            val h = styleItems(style)["android:layout_height"]
            assertEquals("$style must size to its content", "wrap_content", h)
        }
        for (card in statCards()) {
            for (e in card.descendants()) {
                assertFalse(
                    "<${e.tagName}> in a stat card must not pin a dp height",
                    e.androidAttr("layout_height")?.endsWith("dp") == true,
                )
            }
        }
    }

    @Test
    fun statCardHelperStylesDoNotAccidentallyInheritTheCardStyle() {
        // Dot-notation style names inherit implicitly; "Widget.EDUmio.StatCard.Value" would silently
        // pick up 0dp width + weight + match_parent height from the card and break the layout.
        val names = parse("values/styles.xml").descendants()
            .filter { it.tagName == "style" }
            .map { it.getAttribute("name") }
        val trap = names.filter { it.startsWith("Widget.EDUmio.StatCard.") }
        assertTrue("these names implicitly inherit Widget.EDUmio.StatCard: $trap", trap.isEmpty())
    }
}
