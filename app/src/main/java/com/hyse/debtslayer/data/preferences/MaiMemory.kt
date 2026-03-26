// app/src/main/java/com/hyse/debtslayer/data/preferences/MaiMemory.kt
package com.hyse.debtslayer.data.preferences

data class MaiMemory(
    val nickname: String = "",
    val firstDepositDate: String = "",
    val bestDayAmount: Long = 0L,
    val bestDayDate: String = "",
    val favoriteDayOfWeek: String = "",
    val daysMetTarget: Int = 0,
    val milestone25Reached: Boolean = false,
    val milestone50Reached: Boolean = false,
    val milestone75Reached: Boolean = false,
    val totalDepositDays: Int = 0,
    val lastDepositDate: String = "",
    val lastDepositAmount: Long = 0L
) {
    fun toJson(): String {
        return """{"nickname":"$nickname","firstDepositDate":"$firstDepositDate","bestDayAmount":$bestDayAmount,"bestDayDate":"$bestDayDate","favoriteDayOfWeek":"$favoriteDayOfWeek","daysMetTarget":$daysMetTarget,"milestone25Reached":$milestone25Reached,"milestone50Reached":$milestone50Reached,"milestone75Reached":$milestone75Reached,"totalDepositDays":$totalDepositDays,"lastDepositDate":"$lastDepositDate","lastDepositAmount":$lastDepositAmount}"""
    }

    companion object {
        fun fromJson(json: String): MaiMemory {
            return try {
                val obj = org.json.JSONObject(json)
                MaiMemory(
                    nickname           = obj.optString("nickname", ""),
                    firstDepositDate   = obj.optString("firstDepositDate", ""),
                    bestDayAmount      = obj.optLong("bestDayAmount", 0L),
                    bestDayDate        = obj.optString("bestDayDate", ""),
                    favoriteDayOfWeek  = obj.optString("favoriteDayOfWeek", ""),
                    daysMetTarget      = obj.optInt("daysMetTarget", 0),
                    milestone25Reached = obj.optBoolean("milestone25Reached", false),
                    milestone50Reached = obj.optBoolean("milestone50Reached", false),
                    milestone75Reached = obj.optBoolean("milestone75Reached", false),
                    totalDepositDays   = obj.optInt("totalDepositDays", 0),
                    lastDepositDate    = obj.optString("lastDepositDate", ""),
                    lastDepositAmount  = obj.optLong("lastDepositAmount", 0L)
                )
            } catch (e: Exception) {
                MaiMemory()
            }
        }
    }
}