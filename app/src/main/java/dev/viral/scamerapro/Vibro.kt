package dev.viral.scamerapro

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object Vibro {
    fun click(context: Context) {
        vibrate(context, VibrationEffect.EFFECT_CLICK, 15L)
    }

    fun tick(context: Context) {
        vibrate(context, VibrationEffect.EFFECT_TICK, 8L)
    }

    private fun vibrate(context: Context, effectId: Int, fallbackMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator.vibrate(VibrationEffect.createPredefined(effectId))
                } else {
                    vibrator.vibrate(VibrationEffect.createOneShot(fallbackMs, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator.vibrate(VibrationEffect.createPredefined(effectId))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(VibrationEffect.createOneShot(fallbackMs, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            }
        } catch (_: Exception) { }
    }
}
