package com.scanhid.ocr.bluetooth

/**
 * Standard USB HID "boot keyboard" report descriptor, wrapped with an explicit
 * Report ID so it can be used with Android's BluetoothHidDevice API.
 *
 * Report layout (matches [KEYBOARD_REPORT_ID]):
 *   byte 0: modifier keys bitmask (ctrl/shift/alt/gui, left+right)
 *   byte 1: reserved (always 0)
 *   bytes 2-7: up to 6 simultaneously pressed key usage codes
 */
object HidReportDescriptor {

    const val KEYBOARD_REPORT_ID: Byte = 0x01
    const val REPORT_SIZE_BYTES = 8

    val DESCRIPTOR: ByteArray = byteArrayOf(
        0x05, 0x01,                    // Usage Page (Generic Desktop)
        0x09, 0x06,                    // Usage (Keyboard)
        0xA1.toByte(), 0x01,           // Collection (Application)
        0x85.toByte(), KEYBOARD_REPORT_ID, //   Report ID (1)
        0x05, 0x07,                    //   Usage Page (Key Codes)
        0x19, 0xE0.toByte(),           //   Usage Minimum (224)
        0x29, 0xE7.toByte(),           //   Usage Maximum (231)
        0x15, 0x00,                    //   Logical Minimum (0)
        0x25, 0x01,                    //   Logical Maximum (1)
        0x75, 0x01,                    //   Report Size (1)
        0x95.toByte(), 0x08,           //   Report Count (8) -- modifier byte
        0x81.toByte(), 0x02,           //   Input (Data, Variable, Absolute)
        0x95.toByte(), 0x01,           //   Report Count (1)
        0x75, 0x08,                    //   Report Size (8) -- reserved byte
        0x81.toByte(), 0x01,           //   Input (Constant)
        0x95.toByte(), 0x05,           //   Report Count (5)
        0x75, 0x01,                    //   Report Size (1) -- LED output (unused)
        0x05, 0x08,                    //   Usage Page (LEDs)
        0x19, 0x01,                    //   Usage Minimum (1)
        0x29, 0x05,                    //   Usage Maximum (5)
        0x91.toByte(), 0x02,           //   Output (Data, Variable, Absolute)
        0x95.toByte(), 0x01,           //   Report Count (1)
        0x75, 0x03,                    //   Report Size (3) -- LED padding
        0x91.toByte(), 0x01,           //   Output (Constant)
        0x95.toByte(), 0x06,           //   Report Count (6) -- key array
        0x75, 0x08,                    //   Report Size (8)
        0x15, 0x00,                    //   Logical Minimum (0)
        0x25, 0x65,                    //   Logical Maximum (101)
        0x05, 0x07,                    //   Usage Page (Key Codes)
        0x19, 0x00,                    //   Usage Minimum (0)
        0x29, 0x65,                    //   Usage Maximum (101)
        0x81.toByte(), 0x00,           //   Input (Data, Array)
        0xC0.toByte()                  // End Collection
    )
}
