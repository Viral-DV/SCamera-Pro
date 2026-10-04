package dev.viral.scamerapro

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The bar that opens from the four-dots button: settings, flash, timer, ratio, resolution, close. */
@Composable
fun QuickPanel(
    flashOn: Boolean,
    timerSec: Int,
    ratio: FrameRatio,
    onSettings: () -> Unit,
    onFlash: () -> Unit,
    onTimer: () -> Unit,
    onRatio: () -> Unit,
    onRes: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(62.dp)
            .glass(RoundedCornerShape(31.dp))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        QItem(Modifier.weight(1f), onSettings) { GearIcon(Modifier.size(26.dp)) }
        QItem(Modifier.weight(1f), onFlash) { BoltIcon(on = flashOn, modifier = Modifier.size(26.dp)) }
        QItem(Modifier.weight(1f), onTimer) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TimerIcon(Modifier.size(26.dp), color = if (timerSec > 0) Accent else Color.White)
                if (timerSec > 0) {
                    Text("${timerSec}с", color = Accent, fontSize = 14.sp, modifier = Modifier.padding(start = 3.dp))
                }
            }
        }
        QItem(Modifier.weight(1f), onRatio) {
            Text(ratio.label, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        }
        QItem(Modifier.weight(1f), onRes) {
            Text("12M", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0x40FFFFFF))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) { CloseIcon(Modifier.size(15.dp)) }
    }
}

@Composable
private fun QItem(
    modifier: Modifier,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier.height(62.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content
    )
}
