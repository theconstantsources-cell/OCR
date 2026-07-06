package com.scanhid.ocr.bluetooth

/**
 * Maps characters to USB HID Usage IDs (Keyboard/Keypad page, 0x07) plus the
 * modifier bitmask needed to type them. Covers standard US-QWERTY printable
 * ASCII, which is what OCR of typed/printed documents realistically produces.
 * Unmapped characters (accents, non-Latin scripts, emoji, ...) are skipped by
 * the caller rather than crashing the type-out.
 */
object HidKeyCodes {

    const val MODIFIER_NONE = 0x00
    const val MODIFIER_LEFT_SHIFT = 0x02

    const val KEYCODE_ENTER = 0x28
    const val KEYCODE_TAB = 0x2B

    /** Returns (modifier, usageCode) for [c], or null if there is no mapping. */
    fun forChar(c: Char): Pair<Int, Int>? {
        return when (c) {
            in 'a'..'z' -> MODIFIER_NONE to (0x04 + (c - 'a'))
            in 'A'..'Z' -> MODIFIER_LEFT_SHIFT to (0x04 + (c - 'A'))
            in '1'..'9' -> MODIFIER_NONE to (0x1E + (c - '1'))
            '0' -> MODIFIER_NONE to 0x27
            '\n' -> MODIFIER_NONE to KEYCODE_ENTER
            '\t' -> MODIFIER_NONE to KEYCODE_TAB
            ' ' -> MODIFIER_NONE to 0x2C
            '-' -> MODIFIER_NONE to 0x2D
            '_' -> MODIFIER_LEFT_SHIFT to 0x2D
            '=' -> MODIFIER_NONE to 0x2E
            '+' -> MODIFIER_LEFT_SHIFT to 0x2E
            '[' -> MODIFIER_NONE to 0x2F
            '{' -> MODIFIER_LEFT_SHIFT to 0x2F
            ']' -> MODIFIER_NONE to 0x30
            '}' -> MODIFIER_LEFT_SHIFT to 0x30
            '\\' -> MODIFIER_NONE to 0x31
            '|' -> MODIFIER_LEFT_SHIFT to 0x31
            ';' -> MODIFIER_NONE to 0x33
            ':' -> MODIFIER_LEFT_SHIFT to 0x33
            '\'' -> MODIFIER_NONE to 0x34
            '"' -> MODIFIER_LEFT_SHIFT to 0x34
            '`' -> MODIFIER_NONE to 0x35
            '~' -> MODIFIER_LEFT_SHIFT to 0x35
            ',' -> MODIFIER_NONE to 0x36
            '<' -> MODIFIER_LEFT_SHIFT to 0x36
            '.' -> MODIFIER_NONE to 0x37
            '>' -> MODIFIER_LEFT_SHIFT to 0x37
            '/' -> MODIFIER_NONE to 0x38
            '?' -> MODIFIER_LEFT_SHIFT to 0x38
            '!' -> MODIFIER_LEFT_SHIFT to 0x1E
            '@' -> MODIFIER_LEFT_SHIFT to 0x1F
            '#' -> MODIFIER_LEFT_SHIFT to 0x20
            '$' -> MODIFIER_LEFT_SHIFT to 0x21
            '%' -> MODIFIER_LEFT_SHIFT to 0x22
            '^' -> MODIFIER_LEFT_SHIFT to 0x23
            '&' -> MODIFIER_LEFT_SHIFT to 0x24
            '*' -> MODIFIER_LEFT_SHIFT to 0x25
            '(' -> MODIFIER_LEFT_SHIFT to 0x26
            ')' -> MODIFIER_LEFT_SHIFT to 0x27
            else -> null
        }
    }
}
