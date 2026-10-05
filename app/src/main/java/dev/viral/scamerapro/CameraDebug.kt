package dev.viral.scamerapro

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

fun cameraReport(context: Context): String {
    val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val sb = StringBuilder()
    sb.appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MODEL}")
    sb.appendLine()
    for (id in cm.cameraIdList) {
        try {
            val c = cm.getCameraCharacteristics(id)
            val facing = when (c.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_BACK -> "back"
                CameraCharacteristics.LENS_FACING_FRONT -> "front"
                else -> "external"
            }
            sb.appendLine("ID $id  ($facing)")
            val focals = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            if (focals != null) {
                sb.appendLine("  focal: " + focals.joinToString { String.format(Locale.US, "%.2f mm", it) })
            }
            val apertures = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
            if (apertures != null) {
                sb.appendLine("  aperture: " + apertures.joinToString { String.format(Locale.US, "f/%.1f", it) })
            }
        } catch (e: Exception) {
            sb.appendLine("ID $id: error ${e.message}")
        }
        sb.appendLine()
    }
    return sb.toString()
}

@Composable
fun DebugDialog(
    lenses: LensInfo,
    ultraAvail: Boolean,
    camRatio: Float,
    displayZoom: Float,
    lens: Lens,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Camera Debug Info", fontWeight = FontWeight.Bold) },
        text = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Active Lens: $lens", color = Color.Green, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    Text("Ultra Available: $ultraAvail", color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    Text("Main ID: ${lenses.mainId}", color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    Text("Ultra ID: ${lenses.ultraId}", color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    Text("Camera Ratio: $camRatio", color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    Text("Display Zoom: $displayZoom", color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK", color = Accent)
            }
        }
    )
}
