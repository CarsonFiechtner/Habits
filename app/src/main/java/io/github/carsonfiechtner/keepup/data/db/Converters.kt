package io.github.carsonfiechtner.keepup.data.db

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate

/** Dates are stored as epoch days, instants as epoch millis (design doc §9.2). */
class Converters {
    @TypeConverter fun localDateToEpochDay(date: LocalDate?): Long? = date?.toEpochDay()
    @TypeConverter fun epochDayToLocalDate(epochDay: Long?): LocalDate? = epochDay?.let(LocalDate::ofEpochDay)
    @TypeConverter fun instantToMillis(instant: Instant?): Long? = instant?.toEpochMilli()
    @TypeConverter fun millisToInstant(millis: Long?): Instant? = millis?.let(Instant::ofEpochMilli)
}
