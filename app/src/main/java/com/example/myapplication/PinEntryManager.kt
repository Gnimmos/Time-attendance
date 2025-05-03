package com.example.myapplication

/**
 * Manages a 4-digit PIN entry with limited attempts.
 *
 * @param maxAttempts how many wrong PINs before lockout
 * @param onSuccess   called when 4 digits entered and valid
 * @param onFailure   called when invalid and attempts remain
 * @param onLocked    called when invalid and no attempts remain
 */
class PinEntryManager(
    private val maxAttempts: Int = 3,
    private val isValidPin: (String) -> Boolean,
    private val onSuccess: (pin: String) -> Unit,
    private val onFailure: (remaining: Int) -> Unit,
    private val onLocked: () -> Unit
) {
    private val builder = StringBuilder()
    private var attemptsLeft = maxAttempts

    /** Call for any numeric/del/x key */
    fun onKey(key: String) {
        when (key) {
            "⌫" -> backspace()
            "x"  -> clear()
            else  -> inputDigit(key)
        }
    }

    private fun inputDigit(d: String) {
        if (builder.length >= 4 || attemptsLeft == 0) return
        builder.append(d)
        if (builder.length == 4) validate()
    }

    private fun backspace() {
        if (builder.isNotEmpty()) builder.deleteCharAt(builder.lastIndex)
    }

    fun clear() {
        builder.clear()
        attemptsLeft = maxAttempts
    }

    /** Expose current length so UI can update dots */
    fun length() = builder.length

    private fun validate() {
        val pin = builder.toString()
        if (isValidPin(pin)) {
            onSuccess(pin)
        } else {
            attemptsLeft--
            if (attemptsLeft > 0) {
                onFailure(attemptsLeft)
            } else {
                onLocked()
            }
        }
        builder.clear()
    }
}
