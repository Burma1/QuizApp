package com.example.quizapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DiagramIllustration(
    diagramType: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(172.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        when (diagramType) {
            "cone" -> ConeDiagram()
            "heart" -> HeartDiagram()
            "dna" -> ChromosomeDnaDiagram()
            "wind" -> WindTurbineDiagram()
            else -> GenericConceptDiagram()
        }
    }
}

@Composable
fun ConeDiagram() {
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Cone parameters
            val apex = Offset(w * 0.38f, h * 0.12f)
            val baseCenter = Offset(w * 0.38f, h * 0.78f)
            val rx = w * 0.24f
            val ry = h * 0.12f

            val leftBase = Offset(baseCenter.x - rx, baseCenter.y)
            val rightBase = Offset(baseCenter.x + rx, baseCenter.y)

            // Draw base ellipse (lower visible half and upper dashed half)
            val baseRect = Rect(baseCenter.x - rx, baseCenter.y - ry, baseCenter.x + rx, baseCenter.y + ry)

            // Upper half of base ellipse (dashed)
            drawArc(
                color = Color(0xFF263238),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = baseRect.topLeft,
                size = baseRect.size,
                style = Stroke(
                    width = 3.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                )
            )

            // Lower half of base ellipse (solid)
            drawArc(
                color = Color(0xFF263238),
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = baseRect.topLeft,
                size = baseRect.size,
                style = Stroke(width = 4f)
            )

            // Lateral cone edges
            drawLine(
                color = Color(0xFF263238),
                start = apex,
                end = leftBase,
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFF263238),
                start = apex,
                end = rightBase,
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )

            // Height line (dashed pink)
            drawLine(
                color = Color(0xFFE91E63),
                start = apex,
                end = baseCenter,
                strokeWidth = 3.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f),
                cap = StrokeCap.Round
            )

            // Radius line (blue solid)
            drawLine(
                color = Color(0xFF2979FF),
                start = baseCenter,
                end = rightBase,
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )

            // Draw apex point
            drawCircle(color = Color(0xFFE91E63), radius = 5f, center = apex)
            drawCircle(color = Color(0xFF2979FF), radius = 5f, center = baseCenter)
        }

        // Formula & labels overlay
        Text(
            text = "h",
            color = Color(0xFFE91E63),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(end = 50.dp, bottom = 10.dp)
        )
        Text(
            text = "r",
            color = Color(0xFF2979FF),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 20.dp, bottom = 32.dp)
        )
        Text(
            text = "V = ⅓ πr²h",
            color = Color(0xFF1E293B),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 20.dp)
        )
    }
}

@Composable
fun HeartDiagram() {
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w * 0.45f
            val cy = h * 0.5f

            // Venous right side (Blue)
            val bluePath = Path().apply {
                moveTo(cx - 20f, cy - 50f)
                cubicTo(cx - 70f, cy - 40f, cx - 80f, cy + 10f, cx - 50f, cy + 50f)
                cubicTo(cx - 30f, cy + 70f, cx, cy + 80f, cx, cy + 80f)
                lineTo(cx, cy - 30f)
                close()
            }
            drawPath(path = bluePath, color = Color(0xFF00ACC1))
            drawPath(path = bluePath, color = Color(0xFF006064), style = Stroke(width = 4f))

            // Arterial left side (Red / Crimson)
            val redPath = Path().apply {
                moveTo(cx + 20f, cy - 50f)
                cubicTo(cx + 70f, cy - 40f, cx + 80f, cy + 10f, cx + 50f, cy + 50f)
                cubicTo(cx + 30f, cy + 70f, cx, cy + 80f, cx, cy + 80f)
                lineTo(cx, cy - 30f)
                close()
            }
            drawPath(path = redPath, color = Color(0xFFEF5350))
            drawPath(path = redPath, color = Color(0xFFB71C1C), style = Stroke(width = 4f))

            // Aorta arch on top
            val aortaPath = Path().apply {
                moveTo(cx - 15f, cy - 40f)
                cubicTo(cx - 10f, cy - 80f, cx + 40f, cy - 80f, cx + 30f, cy - 45f)
            }
            drawPath(path = aortaPath, color = Color(0xFFE53935), style = Stroke(width = 18f, cap = StrokeCap.Round))

            // Vena cava & pulmonary vessels
            drawLine(
                color = Color(0xFF0288D1),
                start = Offset(cx - 40f, cy - 35f),
                end = Offset(cx - 40f, cy - 75f),
                strokeWidth = 14f,
                cap = StrokeCap.Round
            )

            // Inner ventricular septum dividing line
            drawLine(
                color = Color.White,
                start = Offset(cx, cy - 25f),
                end = Offset(cx, cy + 78f),
                strokeWidth = 6f
            )

            // Pointer arrow to ventricle (as in Screenshot 3!)
            val arrowPath = Path().apply {
                moveTo(w * 0.78f, cy + 40f)
                cubicTo(w * 0.72f, cy + 30f, w * 0.65f, cy + 35f, cx + 32f, cy + 38f)
            }
            drawPath(path = arrowPath, color = Color(0xFF304FFE), style = Stroke(width = 5f, cap = StrokeCap.Round))

            // Arrow head
            val arrowHead = Path().apply {
                moveTo(cx + 32f, cy + 38f)
                lineTo(cx + 44f, cy + 30f)
                moveTo(cx + 32f, cy + 38f)
                lineTo(cx + 44f, cy + 46f)
            }
            drawPath(path = arrowHead, color = Color(0xFF304FFE), style = Stroke(width = 5f, cap = StrokeCap.Round))
        }

        Text(
            text = "Левый желудочек",
            color = Color(0xFF304FFE),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = 4.dp)
        )
    }
}

@Composable
fun ChromosomeDnaDiagram() {
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w * 0.5f
            val cy = h * 0.5f

            // Chromosome X shape with rounded chromatids
            val chromoColor = Color(0xFF1E88E5)
            val strokeColor = Color(0xFF0D47A1)

            // Arm 1 (Top-Left to Bottom-Right)
            drawLine(
                color = chromoColor,
                start = Offset(cx - 50f, cy - 65f),
                end = Offset(cx + 50f, cy + 65f),
                strokeWidth = 28f,
                cap = StrokeCap.Round
            )
            // Arm 2 (Top-Right to Bottom-Left)
            drawLine(
                color = chromoColor,
                start = Offset(cx + 50f, cy - 65f),
                end = Offset(cx - 50f, cy + 65f),
                strokeWidth = 28f,
                cap = StrokeCap.Round
            )

            // Centromere (Center waist)
            drawCircle(color = Color(0xFF64B5F6), radius = 16f, center = Offset(cx, cy))
            drawCircle(color = strokeColor, radius = 16f, center = Offset(cx, cy), style = Stroke(3f))

            // DNA ribbon accents in background
            val dnaPath = Path().apply {
                moveTo(w * 0.15f, cy - 40f)
                cubicTo(w * 0.25f, cy - 70f, w * 0.35f, cy - 20f, w * 0.45f, cy - 50f)
            }
            drawPath(path = dnaPath, color = Color(0xFF90CAF9), style = Stroke(width = 5f))

            val dnaPath2 = Path().apply {
                moveTo(w * 0.55f, cy + 50f)
                cubicTo(w * 0.65f, cy + 20f, w * 0.75f, cy + 70f, w * 0.85f, cy + 40f)
            }
            drawPath(path = dnaPath2, color = Color(0xFF90CAF9), style = Stroke(width = 5f))
        }

        Text(
            text = "ДНК & Хромосома",
            color = Color(0xFF0D47A1),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 2.dp)
        )
    }
}

@Composable
fun WindTurbineDiagram() {
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w * 0.35f
            val groundY = h * 0.82f

            // Tower
            val towerPath = Path().apply {
                moveTo(cx - 6f, groundY)
                lineTo(cx - 3f, h * 0.25f)
                lineTo(cx + 3f, h * 0.25f)
                lineTo(cx + 6f, groundY)
                close()
            }
            drawPath(path = towerPath, color = Color(0xFF78909C))

            // Turbine blades
            val hub = Offset(cx, h * 0.25f)
            drawCircle(color = Color(0xFF37474F), radius = 7f, center = hub)

            // Blade 1 (up)
            drawLine(color = Color(0xFF00ACC1), start = hub, end = Offset(hub.x, hub.y - 45f), strokeWidth = 8f, cap = StrokeCap.Round)
            // Blade 2 (down-right)
            drawLine(color = Color(0xFF00ACC1), start = hub, end = Offset(hub.x + 38f, hub.y + 25f), strokeWidth = 8f, cap = StrokeCap.Round)
            // Blade 3 (down-left)
            drawLine(color = Color(0xFF00ACC1), start = hub, end = Offset(hub.x - 38f, hub.y + 25f), strokeWidth = 8f, cap = StrokeCap.Round)

            // Flowing Water Waves on right side (Hydro dam)
            for (i in 0..2) {
                val waveY = h * 0.45f + i * 22f
                val wavePath = Path().apply {
                    moveTo(w * 0.6f, waveY)
                    cubicTo(w * 0.7f, waveY - 12f, w * 0.8f, waveY + 12f, w * 0.95f, waveY)
                }
                drawPath(path = wavePath, color = Color(0xFF0288D1), style = Stroke(width = 5f, cap = StrokeCap.Round))
            }
        }

        Text(
            text = "Ветер & Гидроэнергия",
            color = Color(0xFF00695C),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 4.dp)
        )
    }
}

@Composable
fun GenericConceptDiagram() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width * 0.5f
            val cy = size.height * 0.5f
            drawCircle(color = Color(0xFFE8EAF6), radius = 48f, center = Offset(cx, cy))
            drawCircle(color = Color(0xFF3F51B5), radius = 24f, center = Offset(cx, cy))
        }
        Text(
            text = "⚡ Quizapp Concept",
            color = Color(0xFF3F51B5),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
