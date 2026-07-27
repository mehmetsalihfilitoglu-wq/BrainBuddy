package com.edumio.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guards the vc19/vc20 production crash: every titled MaterialAlertDialog died with
 *   java.lang.UnsupportedOperationException
 *   Binary XML file line #38: You must supply a layout_width attribute.
 *
 * Material applies `materialAlertDialogTitleTextStyle` to a view that declares NO layout params:
 *   mtrl_alert_dialog_title.xml:36-38
 *     <androidx.appcompat.widget.DialogTitle
 *         android:id="@+id/alertTitle"
 *         style="?attr/materialAlertDialogTitleTextStyle"/>
 * so the width/height MUST arrive through the style. The app had pointed the attribute at a
 * TextAppearance (which carries none), leaving the title with no width.
 *
 * The durable invariant is therefore about the PARENT CHAIN, not about literal attributes: the style
 * behind that attribute must descend from Material's alert-dialog title style, which supplies them.
 *
 * (Deliberately NOT asserted for materialAlertDialogBodyTextStyle: both places Material applies it —
 * mtrl_alert_dialog.xml:60 and mtrl_alert_select_dialog_item.xml:25-26 — declare layout_width inline,
 * so a TextAppearance is safe there. Verified against the resolved material-1.12.0 resources.)
 */
class DialogStyleResolutionTest {

    private fun res(): File = listOf(File("src/main/res"), File("app/src/main/res"), File("../app/src/main/res"))
        .firstOrNull { it.isDirectory } ?: error("res dir not found (cwd=${File(".").absolutePath})")

    private fun styles(): List<Element> {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File(res(), "values/styles.xml"))
        val nodes = doc.getElementsByTagName("style")
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun themeItem(name: String): String? {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File(res(), "values/themes.xml"))
        val items = doc.getElementsByTagName("item")
        for (i in 0 until items.length) {
            val el = items.item(i) as Element
            if (el.getAttribute("name") == name) return el.textContent.trim()
        }
        // The attribute may live on the dialog ThemeOverlay in styles.xml instead.
        for (s in styles()) {
            val inner = s.getElementsByTagName("item")
            for (j in 0 until inner.length) {
                val el = inner.item(j) as Element
                if (el.getAttribute("name") == name) return el.textContent.trim()
            }
        }
        return null
    }

    private fun styleNamed(name: String): Element? =
        styles().firstOrNull { it.getAttribute("name") == name }

    @Test
    fun theDialogTitleStyleInheritsLayoutParamsFromMaterial() {
        val ref = themeItem("materialAlertDialogTitleTextStyle")
            ?: error("materialAlertDialogTitleTextStyle is not set anywhere")
        assertTrue("must reference a local style, was '$ref'", ref.startsWith("@style/"))

        val styleName = ref.removePrefix("@style/")
        val style = styleNamed(styleName)
            ?: error("materialAlertDialogTitleTextStyle -> '$styleName', which is not defined in values/styles.xml")

        val parent = style.getAttribute("parent")
        assertTrue(
            "'$styleName' must descend from Material's alert-dialog title style so the DialogTitle " +
                "view receives layout_width/layout_height (mtrl_alert_dialog_title.xml line 38 supplies " +
                "none). A TextAppearance parent crashes every titled dialog. Parent was: '$parent'",
            parent.startsWith("MaterialAlertDialog.") && parent.contains("Title"),
        )
        assertTrue(
            "a TextAppearance parent is exactly the vc19/vc20 defect; parent was '$parent'",
            !parent.startsWith("TextAppearance"),
        )
    }

    @Test
    fun theDialogTitleKeepsTheEdumioLook() {
        val styleName = themeItem("materialAlertDialogTitleTextStyle")!!.removePrefix("@style/")
        val items = styleNamed(styleName)!!.getElementsByTagName("item")
        val map = (0 until items.length).associate { i ->
            val el = items.item(i) as Element
            el.getAttribute("name") to el.textContent.trim()
        }
        // Same three values the previous TextAppearance carried — the fix must not restyle dialogs.
        assertEquals("20sp", map["android:textSize"])
        assertEquals("bold", map["android:textStyle"])
        assertEquals("@color/text_primary", map["android:textColor"])
    }

    @Test
    fun noDialogStyleAttributePointsAtABareTextAppearance() {
        // Any attribute Material may apply to a param-less view must not be a TextAppearance. Body is
        // exempt: its call sites declare layout_width inline (verified in material-1.12.0).
        val mustBeWidgetStyle = listOf("materialAlertDialogTitleTextStyle", "alertDialogStyle")
        for (attr in mustBeWidgetStyle) {
            val ref = themeItem(attr) ?: continue
            val name = ref.removePrefix("@style/")
            val parent = styleNamed(name)?.getAttribute("parent") ?: continue
            assertTrue(
                "$attr -> '$name' must not be a TextAppearance (parent '$parent')",
                !parent.startsWith("TextAppearance"),
            )
        }
    }
}
