package com.edumio.app.quiz

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import android.widget.ImageView

/**
 * Single shared loader for question figures.
 *
 * Previously seven screens copy-pasted the same `assets.open + BitmapFactory.decodeStream` block with
 * inconsistent, mostly fixed-height sizing (200dp / 160dp white boxes), which clipped tall figures and
 * shrank "options-in-image" questions until they were unreadable. Centralizing the load means the
 * memory-safe downsample lives in one place; each screen keeps its ImageView as `wrap_content` +
 * `adjustViewBounds` + `fitCenter`, so the figure always renders at its natural aspect ratio — scaled to
 * the content width, never cropped, stretched or forced into a fixed box, and it scrolls with the page.
 */
object QuestionImageBinder {

    /** Cap the long edge on decode so huge assets don't OOM, but keep it high enough that math notation
     *  and fine diagram detail stay sharp. */
    private const val MAX_EDGE_PX = 2048

    /** Loads [imageAsset] into [imageView] (GONE if null/blank/unreadable). Aspect ratio is preserved by
     *  the view's fitCenter + adjustViewBounds; this only decodes the bitmap safely. */
    fun bind(imageView: ImageView, imageAsset: String?, contentDescription: CharSequence? = null) {
        val path = imageAsset?.trim()
        if (path.isNullOrBlank()) { imageView.visibility = View.GONE; return }
        val bmp = try { decodeSampled(imageView.context, path) } catch (_: Throwable) { null }
        if (bmp == null) { imageView.visibility = View.GONE; return }
        imageView.setImageBitmap(bmp)
        contentDescription?.let { imageView.contentDescription = it }
        imageView.visibility = View.VISIBLE
    }

    private fun decodeSampled(context: Context, assetPath: String): Bitmap? {
        val assets = context.assets
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        assets.open(assetPath).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        while (longEdge > 0 && longEdge / (sample * 2) >= MAX_EDGE_PX) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return assets.open(assetPath).use { BitmapFactory.decodeStream(it, null, opts) }
    }
}
