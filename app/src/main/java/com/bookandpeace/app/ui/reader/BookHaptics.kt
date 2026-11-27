package com.bookandpeace.app.ui.reader

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * Tactile physical-book haptic feedback engine.
 * Uses Android's crisp HD VibrationEffect primitives (on Pixel/modern LRA devices)
 * with graceful fallback to standard tactile effects so turning pages feels like
 * real paper lifting and settling — never a harsh buzzer.
 */
internal class BookHaptics(
    private val context: Context,
    private val view: View,
    var enabled: Boolean
) {
    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    private val hasVibrator: Boolean = vibrator?.hasVibrator() == true
    private var lastLiftTimestampMs: Long = 0L

    /** Subtle fingertip paper-catch impulse when lifting/starting to turn a page */
    fun onPageLift() {
        if (!enabled) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastLiftTimestampMs < 180L) return
        lastLiftTimestampMs = now
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hasVibrator) {
            val v = vibrator ?: return
            if (v.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)) {
                runCatching {
                    val effect = VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.34f)
                        .compose()
                    v.vibrate(effect)
                    return
                }
            }
        }
        vibrateFallback(durationMs = 7L, amplitude = 42, fallbackViewConstant = HapticFeedbackConstants.CLOCK_TICK)
    }

    /** Crisp two-stage paper-flutter & landing settle when a page completes its turn */
    fun onPageSettle() {
        if (!enabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hasVibrator) {
            val v = vibrator ?: return
            if (v.areAllPrimitivesSupported(
                    VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
                    VibrationEffect.Composition.PRIMITIVE_TICK
                )
            ) {
                runCatching {
                    val effect = VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.28f)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.56f, 20)
                        .compose()
                    v.vibrate(effect)
                    return
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasVibrator) {
            runCatching {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                return
            }
        }
        vibrateFallback(durationMs = 11L, amplitude = 72, fallbackViewConstant = HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /** Slightly richer tactile thud when crossing into a new chapter (like turning a thicker card) */
    fun onChapterBoundary() {
        if (!enabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hasVibrator) {
            val v = vibrator ?: return
            if (v.areAllPrimitivesSupported(
                    VibrationEffect.Composition.PRIMITIVE_CLICK,
                    VibrationEffect.Composition.PRIMITIVE_LOW_TICK
                )
            ) {
                runCatching {
                    val effect = VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.62f)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.36f, 32)
                        .compose()
                    v.vibrate(effect)
                    return
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasVibrator) {
            runCatching {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                return
            }
        }
        vibrateFallback(durationMs = 16L, amplitude = 105, fallbackViewConstant = HapticFeedbackConstants.CONTEXT_CLICK)
    }

    /** Gentle resistance bump at the very first or last page of the book */
    fun onBookBoundary() {
        if (!enabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hasVibrator) {
            val v = vibrator ?: return
            if (v.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL)) {
                runCatching {
                    val effect = VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.45f)
                        .compose()
                    v.vibrate(effect)
                    return
                }
            }
        }
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        vibrateFallback(durationMs = 14L, amplitude = 60, fallbackViewConstant = constant)
    }

    /** Micro-tick for UI controls (menu toggle, A-/A+, theme switch) */
    fun onUiTick() {
        if (!enabled) return
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun vibrateFallback(durationMs: Long, amplitude: Int, fallbackViewConstant: Int) {
        if (hasVibrator && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val succeeded = runCatching {
                val amp = if (vibrator?.hasAmplitudeControl() == true) amplitude else VibrationEffect.DEFAULT_AMPLITUDE
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, amp))
                true
            }.getOrDefault(false)
            if (succeeded) return
        }
        view.performHapticFeedback(fallbackViewConstant)
    }
}

@Composable
internal fun rememberBookHaptics(enabled: Boolean): BookHaptics {
    val context = LocalContext.current
    val view = LocalView.current
    val haptics = remember(context, view) { BookHaptics(context, view, enabled) }
    haptics.enabled = enabled
    return haptics
}
