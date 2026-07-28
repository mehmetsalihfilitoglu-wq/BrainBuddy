package com.edumio.app.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Crash guard for layout inflation.
 *
 * PRODUCTION INCIDENT (vc19, Crashlytics, 8 events / 3 users):
 *   java.lang.UnsupportedOperationException
 *   Binary XML file line #38: You must supply a layout_width attribute.
 *
 * That message comes from android.content.res.TypedArray.getLayoutDimension(), which
 * ViewGroup.LayoutParams(Context, AttributeSet) calls while the inflater builds a child's layout
 * params. It fires when a view's android:layout_width / android:layout_height cannot be resolved
 * from the XML attribute set.
 *
 * The trap: those two attributes were supplied only through `style="@style/..."`. Unlike ordinary
 * view attributes, layout_* params are read by the PARENT during generateLayoutParams and are not
 * reliably resolved from a style — so a layout that previews correctly in Android Studio can still
 * throw at runtime on a device.
 *
 * This test asserts the invariant directly on the shipped XML: EVERY view in EVERY layout declares
 * both attributes inline. It is deliberately a source-level test — it needs no device, so it runs in
 * CI on every build.
 */
class LayoutInflationSafetyTest {

    private val ANDROID = "http://schemas.android.com/apk/res/android"

    /** Tags that legitimately carry no layout params (they are not laid out by a parent). */
    private val exempt = setOf(
        "merge",        // children are re-parented; merge itself is never laid out
        "include",      // may inherit params from the included root
        "requestFocus", // directive, not a view
        "tag",          // directive, not a view
    )

    private fun layoutDirs(): List<File> {
        val res = listOf(File("src/main/res"), File("app/src/main/res"), File("../app/src/main/res"))
            .firstOrNull { it.isDirectory } ?: error("res dir not found (cwd=${File(".").absolutePath})")
        return res.listFiles()!!.filter { it.isDirectory && it.name.startsWith("layout") }
    }

    private fun elementsOf(f: File): List<Pair<Element, Boolean>> {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val doc = factory.newDocumentBuilder().parse(f)
        val out = ArrayList<Pair<Element, Boolean>>()
        var first = true
        fun walk(n: Node) {
            var c = n.firstChild
            while (c != null) {
                if (c.nodeType == Node.ELEMENT_NODE) {
                    out.add((c as Element) to first); first = false
                    walk(c)
                }
                c = c.nextSibling
            }
        }
        val root = doc.documentElement
        out.add(root to true); first = false
        walk(root)
        return out
    }

    @Test
    fun everyViewDeclaresLayoutWidthAndHeightInline() {
        val offenders = ArrayList<String>()
        var files = 0
        var views = 0

        for (dir in layoutDirs()) {
            for (f in dir.listFiles()!!.filter { it.extension == "xml" }) {
                files++
                for ((el, _) in elementsOf(f)) {
                    val tag = el.tagName
                    if (tag in exempt || tag.substringAfterLast('.') in exempt) continue
                    views++
                    val w = el.getAttributeNS(ANDROID, "layout_width")
                    val h = el.getAttributeNS(ANDROID, "layout_height")
                    if (w.isNullOrEmpty() || h.isNullOrEmpty()) {
                        val missing = listOfNotNull(
                            "layout_width".takeIf { w.isNullOrEmpty() },
                            "layout_height".takeIf { h.isNullOrEmpty() },
                        ).joinToString("+")
                        val viaStyle = el.getAttribute("style").takeIf { it.isNotEmpty() }
                            ?.let { " (relies on $it — styles do NOT reliably supply layout params)" } ?: ""
                        offenders += "${dir.name}/${f.name} <$tag> missing $missing$viaStyle"
                    }
                }
            }
        }

        assertTrue("scanned no layouts — the test is not looking where it thinks", files > 0 && views > 0)
        assertTrue(
            "these views would throw UnsupportedOperationException " +
                "(\"You must supply a layout_width attribute\") when inflated:\n  " +
                offenders.joinToString("\n  "),
            offenders.isEmpty(),
        )
        println("[INFLATION-SAFETY] $files layout files, $views views — all declare layout_width + layout_height inline")
    }

    /**
     * The styles that caused the vc19 incident may keep their visual properties, but must never be the
     * ONLY source of layout params again. Layout params belong in the layout, not in a style.
     */
    @Test
    fun edumioStylesDoNotSmuggleLayoutParams() {
        val res = listOf(File("src/main/res"), File("app/src/main/res"), File("../app/src/main/res"))
            .firstOrNull { it.isDirectory }!!
        val styles = File(res, "values/styles.xml")
        assertTrue("values/styles.xml must exist", styles.isFile)
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val doc = factory.newDocumentBuilder().parse(styles)
        val nodes = doc.getElementsByTagName("style")

        val offenders = ArrayList<String>()
        for (i in 0 until nodes.length) {
            val style = nodes.item(i) as Element
            val name = style.getAttribute("name")
            val items = style.getElementsByTagName("item")
            for (j in 0 until items.length) {
                val item = items.item(j) as Element
                val attr = item.getAttribute("name")
                if (attr == "android:layout_width" || attr == "android:layout_height") {
                    offenders += "$name defines $attr"
                }
            }
        }
        assertTrue(
            "layout params must live in the layout XML, not in a style — offenders:\n  " +
                offenders.joinToString("\n  "),
            offenders.isEmpty(),
        )
    }
}
