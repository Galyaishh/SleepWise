package com.example.sleepwisepoc.db

import androidx.room.TypeConverter
import com.example.sleepwisepoc.StageTick
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromTicks(ticks: List<StageTick>): String = gson.toJson(ticks)

    @TypeConverter
    fun toTicks(json: String): List<StageTick> =
        gson.fromJson(json, object : TypeToken<List<StageTick>>() {}.type) ?: emptyList()
}
