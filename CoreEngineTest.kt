package com.example.virtualcompanion.engine

import com.example.virtualcompanion.model.CharacterProfile
import com.example.virtualcompanion.model.EmotionCatalog
import com.example.virtualcompanion.model.SocialRelationship
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreEngineTest {
    @Test
    fun romanticMessageCanBecomeEmbarrassed() {
        val p = CharacterProfile("char-a", relationship = "戀人 / 情侶")
        assertEquals(EmotionCatalog.EMBARRASSED, EmotionEngine.infer("我好喜歡你", p))
    }

    @Test
    fun tiredMessageBecomesTired() {
        val p = CharacterProfile("char-a")
        assertEquals(EmotionCatalog.TIRED, EmotionEngine.infer("我今天真的好累", p))
    }

    @Test
    fun socialRelationshipIsClamped() {
        val r = SocialRelationship(
            sourceCharacterId = "a",
            targetCharacterId = "b",
            affinity = 999,
            trust = -999,
            jealousy = 999,
            rivalry = 999,
            irritation = 999
        ).normalize()
        assertEquals(100, r.affinity)
        assertEquals(-100, r.trust)
        assertEquals(100, r.jealousy)
        assertEquals(100, r.rivalry)
        assertEquals(100, r.irritation)
    }

    @Test
    fun playerCanDeescalateConflict() {
        val self = CharacterProfile("a", characterName = "A")
        val other = CharacterProfile("b", characterName = "B")
        val deltas = SocialDynamicsEngine.onUserMessage(self, listOf(other), "你們不要吵了，和好", 3)
        assertTrue(deltas.isNotEmpty())
        assertTrue(deltas.first().irritation < 0)
        assertTrue(deltas.first().jealousy < 0)
    }
}
