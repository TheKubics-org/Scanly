package com.thekubics.scanly.data.local.converter

import androidx.room.TypeConverter
import com.thekubics.scanly.domain.model.FilterType
import java.util.Date

class Converters {

    @TypeConverter
    fun fromFilterType(value: FilterType): String {
        return value.name
    }

    @TypeConverter
    fun toFilterType(value: String): FilterType {
        return try {
            FilterType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            FilterType.ORIGINAL
        }
    }

    @TypeConverter
    fun fromDate(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun toDate(millis: Long?): Date? {
        return millis?.let { Date(it) }
    }
}
