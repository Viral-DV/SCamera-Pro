package dev.viral.scamerapro

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class MoreItem(val label: String, val glyph: String)

private val moreItems = listOf(
    MoreItem("ПРО", "◎"),
    MoreItem("ПРОФ.\nВИДЕО", "▶"),
    MoreItem("НОЧЬ", "☾"),
    MoreItem("ЕДА", "✿"),
    MoreItem("ПАНОРАМА", "▭"),
    MoreItem("МАКРО-\nСЪЕМКА", "❀"),
    MoreItem("СВЕРХЗА-\nМЕДЛ.", "◐"),
    MoreItem("ЗАМЕДЛЕН-\nНОЕ ВИДЕО", "◑"),
    MoreItem("ГИПЕРЛАПС", "➤")
)

@Composable
fun MoreSheet(onPick: (String) -> Unit, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        Box(Modifier.fillMaxWidth().padding(bottom = 12.dp), contentAlignment = Alignment.CenterEnd) {
            Text(
                "Изменить",
                color = Color.White,
                fontSize = 19.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(30.dp))
                    .background(Color(0xCC2B2B2B))
                    .clickable(onClick = onEdit)
                    .padding(horizontal = 28.dp, vertical = 16.dp)
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(36.dp))
                .background(Color(0xF0242424))
                .padding(vertical = 18.dp)
        ) {
            moreItems.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    row.forEach { item ->
                        Column(
                            Modifier
                                .weight(1f)
                                .clickable { onPick(item.label.replace("-\n", "").replace("\n", " ")) },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                Modifier.size(48.dp).border(2.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) { Text(item.glyph, color = Color.White, fontSize = 20.sp) }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                item.label,
                                color = Color.White,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
