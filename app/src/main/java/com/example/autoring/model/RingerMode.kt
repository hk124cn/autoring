package com.example.autoring.model

enum class RingerMode(val value: String, val label: String) {
    NORMAL("NORMAL", "响铃"),
    RING_VIBRATE("RING_VIBRATE", "响铃+震动"),
    VIBRATE("VIBRATE", "震动"),
    SILENT("SILENT", "静音");

    companion object {
        fun from(value: String): RingerMode =
            entries.firstOrNull { it.value == value } ?: NORMAL
    }
}
