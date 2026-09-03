package dev.sweety.vector.list

import dev.sweety.vector.common.Vector2d
import dev.sweety.vector.common.Vector3d
import dev.sweety.vector.mutable.MutVec2d
import dev.sweety.vector.mutable.MutVec3d
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

class ListDoubleTest {

    @Test
    fun testAddAndGetComponents() {
        val list = List3d()
        assertEquals(0, list.size())
        assertTrue(list.isEmpty())

        list.add(1.0, 2.0, 3.0)
        list.add(4.0, 5.0, 6.0)

        assertEquals(2, list.size())
        assertFalse(list.isEmpty())

        assertEquals(1.0, list.getX(0))
        assertEquals(2.0, list.getY(0))
        assertEquals(3.0, list.getZ(0))

        assertEquals(4.0, list.getX(1))
        assertEquals(5.0, list.getY(1))
        assertEquals(6.0, list.getZ(1))
    }

    @Test
    fun testAddVectorAndMutableOut() {
        val list = List3d(4)
        val v = Vector3d.of(10.5, 20.5, 30.5)
        list.add(v)

        val out = MutVec3d()
        list.get(0, out)

        assertEquals(10.5, out.x())
        assertEquals(20.5, out.y())
        assertEquals(30.5, out.z())
    }

    @Test
    fun testInPlaceMutation() {
        val list = List3d()
        list.add(1.0, 2.0, 3.0)

        list.set(0, 10.0, 20.0, 30.0)
        assertEquals(10.0, list.getX(0))
        assertEquals(20.0, list.getY(0))
        assertEquals(30.0, list.getZ(0))

        list.add(0, 5.0, 5.0, 5.0)
        assertEquals(15.0, list.getX(0))
        assertEquals(25.0, list.getY(0))
        assertEquals(35.0, list.getZ(0))

        list.mul(0, 2.0)
        assertEquals(30.0, list.getX(0))
        assertEquals(50.0, list.getY(0))
        assertEquals(70.0, list.getZ(0))
    }

    @Test
    fun testDynamicExpansionAndClear() {
        val list = List3d(2)
        for (i in 0 until 100) {
            list.add(i.toDouble(), (i * 2).toDouble(), (i * 3).toDouble())
        }
        assertEquals(100, list.size())
        assertEquals(99.0, list.getX(99))
        assertEquals(198.0, list.getY(99))
        assertEquals(297.0, list.getZ(99))

        list.clear()
        assertEquals(0, list.size())
        assertTrue(list.isEmpty())
    }

    @Test
    fun testForEachZeroAllocationIteration() {
        val list = List3d()
        list.add(1.0, 1.0, 1.0)
        list.add(2.0, 2.0, 2.0)
        list.add(3.0, 3.0, 3.0)

        var sumX = 0.0
        var sumY = 0.0
        var sumZ = 0.0

        list.forEach { x, y, z ->
            sumX += x
            sumY += y
            sumZ += z
        }

        assertEquals(6.0, sumX)
        assertEquals(6.0, sumY)
        assertEquals(6.0, sumZ)
    }

    @Test
    fun testByteBufferBinarySerialization() {
        val list = List3d()
        list.add(1.23, 4.56, 7.89)
        list.add(10.0, 20.0, 30.0)

        val buffer = ByteBuffer.allocate(list.byteSize())
        list.writeTo(buffer)
        buffer.flip()

        val readList = List3d()
        readList.readFrom(buffer, 2)

        assertEquals(2, readList.size())
        assertEquals(1.23, readList.getX(0), 1e-6)
        assertEquals(4.56, readList.getY(0), 1e-6)
        assertEquals(7.89, readList.getZ(0), 1e-6)
        assertEquals(10.0, readList.getX(1), 1e-6)
        assertEquals(20.0, readList.getY(1), 1e-6)
        assertEquals(30.0, readList.getZ(1), 1e-6)
    }

    @Test
    fun test2dAddAndGet() {
        val list = List2d()
        list.add(1.5, 2.5)
        list.add(3.5, 4.5)

        assertEquals(2, list.size())
        assertEquals(1.5, list.getX(0))
        assertEquals(2.5, list.getY(0))
        assertEquals(3.5, list.getX(1))
        assertEquals(4.5, list.getY(1))

        val out = MutVec2d()
        list.get(0, out)
        assertEquals(1.5, out.x())
        assertEquals(2.5, out.y())
    }

    @Test
    fun test2dByteBufferSerialization() {
        val list = List2d()
        list.add(10.0, 20.0)
        list.add(30.0, 40.0)

        val buffer = ByteBuffer.allocate(list.byteSize())
        list.writeTo(buffer)
        buffer.flip()

        val readList = List2d()
        readList.readFrom(buffer, 2)

        assertEquals(2, readList.size())
        assertEquals(10.0, readList.getX(0))
        assertEquals(20.0, readList.getY(0))
        assertEquals(30.0, readList.getX(1))
        assertEquals(40.0, readList.getY(1))
    }
}
