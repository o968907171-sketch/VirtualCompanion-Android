package com.example.virtualcompanion.model

object EmotionCatalog {
    const val CALM = "平靜"
    const val HAPPY = "開心"
    const val SAD = "難過"
    const val ANGRY = "生氣"
    const val CONFUSED = "困惑"
    const val EMBARRASSED = "害羞"
    const val SLEEPING = "睡覺"
    const val SURPRISED = "驚訝"
    const val PUZZLED = "疑惑"
    const val DISGUSTED = "厭惡"
    const val AFRAID = "害怕"
    const val PROUD = "得意"
    const val CLINGY = "撒嬌"
    const val BORED = "無聊"
    const val EXCITED = "興奮"
    const val TIRED = "疲倦"
    const val JEALOUS = "吃醋"
    const val WORRIED = "擔心"
    const val NERVOUS = "緊張"
    const val LOVING = "愛慕"
    const val RELAXED = "放鬆"
    const val DISAPPOINTED = "失望"
    const val TOUCHED = "感動"
    const val PLAYFUL = "調皮"
    const val THINKING = "思考"
    const val DAYDREAMING = "發呆"
    const val CURIOUS = "好奇"
    const val ANNOYED = "嫌棄"
    const val SHY_HAPPY = "害羞開心"
    const val SULKING = "鬧彆扭"
    const val SERIOUS = "認真"
    const val SHOCKED = "震驚"
    const val PROTECTIVE = "關心守護"
    const val LONELY = "寂寞"
    const val EXPECTING = "期待"
    const val SMUG = "壞笑"
    const val SLEEPY = "想睡"
    const val SPECIAL = "特殊"

    /**
     * 立繪槽位清單：前 7 個是最基本、最常見的桌寵需求；
     * 使用者可在設定裡把槽位數增加到最多 38 個。
     */
    val builtIns = listOf(
        CALM, HAPPY, SAD, ANGRY, CONFUSED, EMBARRASSED, SLEEPING,
        SURPRISED, PUZZLED, DISGUSTED, AFRAID, PROUD, CLINGY, BORED,
        EXCITED, TIRED, JEALOUS, WORRIED, NERVOUS, LOVING, RELAXED,
        DISAPPOINTED, TOUCHED, PLAYFUL, THINKING, DAYDREAMING, CURIOUS,
        ANNOYED, SHY_HAPPY, SULKING, SERIOUS, SHOCKED, PROTECTIVE,
        LONELY, EXPECTING, SMUG, SLEEPY, SPECIAL
    )

    const val MIN_PORTRAIT_SLOTS = 7
    const val MAX_PORTRAIT_SLOTS = 38
}
