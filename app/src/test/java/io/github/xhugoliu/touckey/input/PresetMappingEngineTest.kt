package io.github.xhugoliu.touckey.input

import io.github.xhugoliu.touckey.config.InMemoryConfigRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PresetMappingEngineTest {
    private val engine = PresetMappingEngine(InMemoryConfigRepository())

    @Test
    fun `quick actions match the initial preset count`() {
        assertEquals(4, engine.quickActions().size)
    }

    @Test
    fun `can resolve a known quick action`() {
        assertNotNull(engine.resolve("play_pause"))
    }
}
