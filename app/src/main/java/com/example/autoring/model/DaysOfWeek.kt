package com.example.autoring.model

import java.util.Calendar

object DaysOfWeek {
    const val MON = 1
    const val TUE = 2
    const val WED = 4
    const val THU = 8
    const val FRI = 16
    const val SAT = 32
    const val SUN = 64

    val ALL = MON or TUE or WED or THU or FRI or SAT or SUN
    val WEEKDAYS = MON or TUE or WED or THU or FRI
    val WEEKEND = SAT or SUN

    fun bitForCalendarDay(calDay: Int): Int = when (calDay) {
        Calendar.MONDAY -> MON
        Calendar.TUESDAY -> TUE
        Calendar.WEDNESDAY -> WED
        Calendar.THURSDAY -> THU
        Calendar.FRIDAY -> FRI
        Calendar.SATURDAY -> SAT
        Calendar.SUNDAY -> SUN
        else -> 0
    }

    fun labels(bitmask: Int): List<String> {
        val map = listOf(
            MON to "一", TUE to "二", WED to "三", THU to "四",
            FRI to "五", SAT to "六", SUN to "日"
        )
        return map.filter { bitmask and it.first != 0 }.map { it.second }
    }
}
