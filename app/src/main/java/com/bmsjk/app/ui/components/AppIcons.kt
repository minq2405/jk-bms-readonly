package com.bmsjk.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Lightweight 2dp-stroke vector icons matching the SVG set in tools/app_preview.html.
 */
object AppIcons {

    val Battery: ImageVector by lazy {
        strokeIcon("Battery") {
            path(stroke = SolidColor(Color.White), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(4f, 7f); lineTo(18f, 7f); quadTo(20f, 7f, 20f, 9f)
                lineTo(20f, 15f); quadTo(20f, 17f, 18f, 17f)
                lineTo(4f, 17f); quadTo(2f, 17f, 2f, 15f)
                lineTo(2f, 9f); quadTo(2f, 7f, 4f, 7f); close()
                moveTo(22f, 11f); lineTo(22f, 13f)
                moveTo(6f, 10f); lineTo(6f, 14f)
                moveTo(10f, 10f); lineTo(10f, 14f)
            }
        }
    }

    val Bolt: ImageVector by lazy {
        strokeIcon("Bolt") {
            path(stroke = SolidColor(Color.White), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(13f, 2f); lineTo(4f, 14f); lineTo(11f, 14f)
                lineTo(10f, 22f); lineTo(19f, 10f); lineTo(12f, 10f); close()
            }
        }
    }

    val Gauge: ImageVector by lazy {
        strokeIcon("Gauge") {
            path(stroke = SolidColor(Color.White), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(12f, 14f); lineTo(16f, 10f)
                moveTo(4f, 18f); curveTo(2f, 13f, 5f, 6f, 12f, 6f)
                curveTo(19f, 6f, 22f, 13f, 20f, 18f)
            }
        }
    }

    val Thermometer: ImageVector by lazy {
        strokeIcon("Thermometer") {
            path(stroke = SolidColor(Color.White), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(14f, 14.5f); lineTo(14f, 4f); curveTo(14f, 2.5f, 10f, 2.5f, 10f, 4f)
                lineTo(10f, 14.5f); curveTo(7.5f, 16f, 8.5f, 21f, 12f, 21f)
                curveTo(15.5f, 21f, 16.5f, 16f, 14f, 14.5f); close()
            }
        }
    }

    val Grid: ImageVector by lazy {
        strokeIcon("Grid") {
            path(stroke = SolidColor(Color.White), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                rect(3f, 3f, 7f, 7f)
                rect(14f, 3f, 7f, 7f)
                rect(3f, 14f, 7f, 7f)
                rect(14f, 14f, 7f, 7f)
            }
        }
    }

    val Chart: ImageVector by lazy {
        strokeIcon("Chart") {
            path(stroke = SolidColor(Color.White), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(3f, 3f); lineTo(3f, 21f); lineTo(21f, 21f)
                moveTo(7f, 15f); lineTo(11f, 11f); lineTo(15f, 14f); lineTo(20f, 8f)
            }
        }
    }

    val Lock: ImageVector by lazy {
        strokeIcon("Lock") {
            path(stroke = SolidColor(Color.White), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                rect(5f, 11f, 14f, 10f)
                moveTo(8f, 11f); lineTo(8f, 7f); curveTo(8f, 3.5f, 16f, 3.5f, 16f, 7f); lineTo(16f, 11f)
            }
        }
    }

    val Delta: ImageVector by lazy {
        strokeIcon("Delta") {
            path(stroke = SolidColor(Color.White), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(12f, 3f); lineTo(3f, 20f); lineTo(21f, 20f); close()
            }
        }
    }

    val Bluetooth: ImageVector by lazy {
        strokeIcon("Bluetooth") {
            path(stroke = SolidColor(Color.White), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(7f, 7f); lineTo(17f, 17f); lineTo(12f, 22f); lineTo(12f, 2f); lineTo(17f, 7f); lineTo(7f, 17f)
            }
        }
    }

    val Search: ImageVector by lazy {
        strokeIcon("Search") {
            path(stroke = SolidColor(Color.White), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(18f, 11f); curveTo(18f, 14.87f, 14.87f, 18f, 11f, 18f)
                curveTo(7.13f, 18f, 4f, 14.87f, 4f, 11f)
                curveTo(4f, 7.13f, 7.13f, 4f, 11f, 4f)
                curveTo(14.87f, 4f, 18f, 7.13f, 18f, 11f)
                moveTo(21f, 21f); lineTo(16.65f, 16.65f)
            }
        }
    }

    val Refresh: ImageVector by lazy {
        strokeIcon("Refresh") {
            path(stroke = SolidColor(Color.White), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(21f, 12f); curveTo(21f, 16.97f, 16.97f, 21f, 12f, 21f)
                curveTo(7.03f, 21f, 3f, 16.97f, 3f, 12f)
                curveTo(3f, 7.03f, 7.03f, 3f, 12f, 3f)
                curveTo(15f, 3f, 17.6f, 4.4f, 19.3f, 6.6f)
                moveTo(21f, 3f); lineTo(21f, 9f); lineTo(15f, 9f)
            }
        }
    }

    val QrCode: ImageVector by lazy {
        strokeIcon("QrCode") {
            path(stroke = SolidColor(Color.White), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                rect(3f, 3f, 7f, 7f)
                rect(14f, 3f, 7f, 7f)
                rect(3f, 14f, 7f, 7f)
                moveTo(14f, 14f); lineTo(17f, 14f); lineTo(17f, 17f)
                moveTo(21f, 14f); lineTo(21f, 17f)
                moveTo(14f, 21f); lineTo(21f, 21f); lineTo(21f, 19f)
            }
        }
    }

    val Warning: ImageVector by lazy {
        strokeIcon("Warning") {
            path(stroke = SolidColor(Color.White), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(12f, 3f); lineTo(2f, 20f); lineTo(22f, 20f); close()
                moveTo(12f, 9f); lineTo(12f, 14f)
                moveTo(12f, 17f); lineTo(12f, 17.5f)
            }
        }
    }

    private fun androidx.compose.ui.graphics.vector.PathBuilder.rect(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {
        moveTo(x, y)
        lineTo(x + w, y)
        lineTo(x + w, y + h)
        lineTo(x, y + h)
        close()
    }

    private fun strokeIcon(
        name: String,
        block: ImageVector.Builder.() -> Unit
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 18.dp,
        defaultHeight = 18.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply(block).build()
}
