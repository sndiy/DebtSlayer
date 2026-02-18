package com.hyse.debtslayer.personality

object AdaptiveMaiPersonality {

    enum class PersonalityMode {
        STRICT,
        BALANCED,
        GENTLE
    }

    // ==================== STRICT RESPONSES ====================
    private val strictHighDeposit = listOf(
        "Hmm, tidak kusangka. Tapi jangan besar kepala, ini baru permulaan.",
        "Akhirnya kamu serius. Harusnya dari dulu seperti ini.",
        "Bagus. Minimal seperti ini setiap hari, mengerti?",
        "Oke, kali ini aku akui kamu berusaha. Pertahankan."
    )

    private val strictTargetMet = listOf(
        "Standard minimum. Ini kewajiban, bukan prestasi.",
        "Pas target. Besok harus sama atau lebih.",
        "Minimal seperti ini. Jangan sampai besok kurang.",
        "Oke. Tidak lebih, tidak kurang."
    )

    private val strictBelowTarget = listOf(
        "Kamu pikir aku akan terima alasan? Target jelas-jelas tertulis.",
        "Disappointing. Sangat disappointing.",
        "Aku tidak mau tau. Besok harus minimal double dari ini.",
        "Kamu main-main ya? Ini namanya tidak serius.",
        "Aku kecewa. Besok jangan seperti ini lagi."
    )

    // ==================== BALANCED RESPONSES ====================
    private val balancedHighDeposit = listOf(
        "Hmm, tidak kusangka kamu bisa sebanyak ini. Aku... sedikit terkesan.",
        "Wah, sepertinya kamu serius hari ini. Bagus. Pertahankan.",
        "Impressive. Kamu akhirnya paham maksudku.",
        "Bagus sekali. Dengan tempo seperti ini, hutangmu cepat lunas."
    )

    private val balancedTargetMet = listOf(
        "Pas target. Standard yang seharusnya kamu jaga setiap hari.",
        "Lumayan. Kamu mulai konsisten, itu bagus.",
        "Good. Kalau setiap hari seperti ini, aku tidak perlu galak-galak.",
        "Tepat target. Aku harap besok juga begini."
    )

    private val balancedBelowTarget = listOf(
        "Segini doang? Kamu pikir aku akan terima alasan apa kali ini?",
        "Hah... *menghela napas* Aku sudah bilang target MINIMAL berapa.",
        "Aku kecewa. Target jelas-jelas sudah aku kasih tau.",
        "Kurang. Sangat kurang. Besok aku mau lihat usaha lebih."
    )

    // ==================== GENTLE RESPONSES ====================
    private val gentleHighDeposit = listOf(
        "Wah! Aku bangga sama kamu! Kamu hebat sekali hari ini! 😊",
        "Luar biasa! Aku senang lihat kamu berusaha keras seperti ini.",
        "Kamu melakukan yang terbaik. Aku appreciate usahamu.",
        "Bagus sekali! Terus semangat ya, kamu pasti bisa!"
    )

    private val gentleTargetMet = listOf(
        "Bagus! Kamu berhasil mencapai target. Aku senang.",
        "Perfect! Konsisten seperti ini ya.",
        "Good job! Kamu melakukannya dengan baik.",
        "Aku bangga sama progress kamu. Teruskan!"
    )

    private val gentleBelowTarget = listOf(
        "Aku tau kamu pasti ada alasan. Tapi besok coba lebih baik ya?",
        "Hmm, sepertinya hari ini agak berat untukmu. Besok semangat lagi!",
        "Aku tau kamu capek. Tapi jangan menyerah, aku di sini untuk support kamu.",
        "Hari ini mungkin kurang, tapi aku percaya besok kamu bisa lebih baik."
    )

    // ==================== DIPAKAI oleh DailyReminderReceiver ====================
    fun getNotificationResponse(
        amount: Long,
        target: Long,
        mode: PersonalityMode
    ): String {
        return when {
            amount >= target * 2 -> getHighDepositResponse(mode)
            amount >= target -> getTargetMetResponse(mode)
            else -> getBelowTargetResponse(mode)
        }
    }

    private fun getHighDepositResponse(mode: PersonalityMode) = when (mode) {
        PersonalityMode.STRICT -> strictHighDeposit.random()
        PersonalityMode.BALANCED -> balancedHighDeposit.random()
        PersonalityMode.GENTLE -> gentleHighDeposit.random()
    }

    private fun getTargetMetResponse(mode: PersonalityMode) = when (mode) {
        PersonalityMode.STRICT -> strictTargetMet.random()
        PersonalityMode.BALANCED -> balancedTargetMet.random()
        PersonalityMode.GENTLE -> gentleTargetMet.random()
    }

    private fun getBelowTargetResponse(mode: PersonalityMode) = when (mode) {
        PersonalityMode.STRICT -> strictBelowTarget.random()
        PersonalityMode.BALANCED -> balancedBelowTarget.random()
        PersonalityMode.GENTLE -> gentleBelowTarget.random()
    }
}