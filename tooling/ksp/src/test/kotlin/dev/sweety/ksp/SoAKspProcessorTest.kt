package dev.sweety.ksp

import com.google.devtools.ksp.processing.SymbolProcessorProvider
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.ServiceLoader

class SoAKspProcessorTest {

    @Test
    fun `test provider instantiation`() {
        val provider = SoAKspProcessorProvider()
        assertNotNull(provider)
    }

    @Test
    fun `test spi registration`() {
        val providers = ServiceLoader.load(SymbolProcessorProvider::class.java).toList()
        assertTrue(
            providers.any { it is SoAKspProcessorProvider },
            "SoAKspProcessorProvider must be registered in META-INF/services"
        )
    }
}
