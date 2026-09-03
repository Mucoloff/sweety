package dev.sweety.vector.list

import dev.sweety.vector.common.Vector2f
import dev.sweety.vector.common.Vector3f
import dev.sweety.vector.common.Vector2i
import dev.sweety.vector.common.Vector3i
import dev.sweety.vector.mutable.MutVec2f
import dev.sweety.vector.mutable.MutVec3f
import dev.sweety.vector.mutable.MutVec2i
import dev.sweety.vector.mutable.MutVec3i
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

class ListFloatIntLongTest {

    @Test
    fun testList3f() {
        val list = List3f()
        list.add(1f, 2f, 3f)
        list.add(4f, 5f, 6f)

        assertEquals(2, list.size())
        assertEquals(1f, list.getX(0))
        assertEquals(2f, list.getY(0))
        assertEquals(3f, list.getZ(0))

        val buffer = ByteBuffer.allocate(list.byteSize())
        list.writeTo(buffer)
        buffer.flip()

        val readList = List3f()
        readList.readFrom(buffer, 2)
        assertEquals(2, readList.size())
        assertEquals(4f, readList.getX(1))
        assertEquals(5f, readList.getY(1))
        assertEquals(6f, readList.getZ(1))
    }

    @Test
    fun testList2f() {
        val list = List2f()
        list.add(1.5f, 2.5f)
        val out = MutVec2f()
        list.get(0, out)
        assertEquals(1.5f, out.x())
        assertEquals(2.5f, out.y())
    }

    @Test
    fun testList3i() {
        val list = List3i()
        list.add(10, 20, 30)
        list.set(0, 100, 200, 300)
        assertEquals(100, list.getX(0))
        assertEquals(200, list.getY(0))
        assertEquals(300, list.getZ(0))

        val out = MutVec3i()
        list.get(0, out)
        assertEquals(100, out.x())
        assertEquals(200, out.y())
        assertEquals(300, out.z())
    }

    @Test
    fun testList2i() {
        val list = List2i()
        list.add(5, 10)
        assertEquals(5, list.getX(0))
        assertEquals(10, list.getY(0))
    }

    @Test
    fun testList3lAndList2l() {
        val list3l = List3l()
        list3l.add(1000L, 2000L, 3000L)
        assertEquals(1000L, list3l.getX(0))
        assertEquals(2000L, list3l.getY(0))
        assertEquals(3000L, list3l.getZ(0))

        val list2l = List2l()
        list2l.add(500L, 600L)
        assertEquals(500L, list2l.getX(0))
        assertEquals(600L, list2l.getY(0))
    }
}
