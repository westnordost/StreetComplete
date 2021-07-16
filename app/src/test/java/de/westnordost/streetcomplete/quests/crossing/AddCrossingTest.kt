package de.westnordost.streetcomplete.quests.crossing

import de.westnordost.streetcomplete.quests.TestMapDataWithGeometry
import de.westnordost.streetcomplete.testutils.node
import de.westnordost.streetcomplete.testutils.p
import de.westnordost.streetcomplete.testutils.way
import org.junit.Assert.*
import org.junit.Test

class AddCrossingTest {
    private val questType = AddCrossing()

    @Test fun `free-floating nodes do not count`() {
        val mapData = TestMapDataWithGeometry(listOf(
            node(1)
        ))
        assertEquals(0, questType.getApplicableElements(mapData).toList().size)
    }

    /*
      ══╪══
    */
    @Test fun `simple crossing counts`() {
        val shared = node(2, p(0.0, 0.0))
        val mapData = TestMapDataWithGeometry(listOf(
            node(1, p(0.0, -1.0)),
            shared,
            node(3, p(0.0, +1.0)),
            node(4, p(-1.0, 0.0)),
            node(5, p(+1.0, 0.0)),
            way(1, nodes = listOf(1, 2, 3), tags = mapOf("highway" to "unclassified")),
            way(2, nodes = listOf(4, 2, 5), tags = mapOf("highway" to "footway")),
        ))
        assertEquals(shared, questType.getApplicableElements(mapData).toList().single())
    }

    /*
      ═══╡
    */
    @Test fun `crossing road on end node does not count`() {
        val shared = node(2, p(0.0, 0.0))
        val mapData = TestMapDataWithGeometry(listOf(
            node(1, p(0.0, -1.0)),
            shared,
            node(4, p(-1.0, 0.0)),
            node(5, p(+1.0, 0.0)),
            way(1, nodes = listOf(1, 2), tags = mapOf("highway" to "unclassified")),
            way(2, nodes = listOf(4, 2, 5), tags = mapOf("highway" to "footway")),
        ))
        assertEquals(0, questType.getApplicableElements(mapData).toList().size)
    }

    /*
      ══╧══
    */
    @Test fun `crossing footway on end node does not count`() {
        val shared = node(2, p(0.0, 0.0))
        val mapData = TestMapDataWithGeometry(listOf(
            node(1, p(0.0, -1.0)),
            shared,
            node(3, p(0.0, +1.0)),
            node(4, p(-1.0, 0.0)),
            way(1, nodes = listOf(1, 2, 3), tags = mapOf("highway" to "unclassified")),
            way(2, nodes = listOf(4, 2), tags = mapOf("highway" to "footway")),
        ))
        assertEquals(0, questType.getApplicableElements(mapData).toList().size)
    }

    /*
      ══╪══ (4 ways)
    */
    @Test fun `crossing with ways split at shared node counts`() {
        val shared = node(2, p(0.0, 0.0))
        val mapData = TestMapDataWithGeometry(listOf(
            node(1, p(0.0, -1.0)),
            shared,
            node(3, p(0.0, +1.0)),
            node(4, p(-1.0, 0.0)),
            node(5, p(+1.0, 0.0)),
            way(1, nodes = listOf(1, 2), tags = mapOf("highway" to "unclassified")),
            way(2, nodes = listOf(3, 2), tags = mapOf("highway" to "unclassified")),
            way(3, nodes = listOf(4, 2), tags = mapOf("highway" to "footway")),
            way(4, nodes = listOf(2, 5), tags = mapOf("highway" to "footway"))
        ))
        assertEquals(shared, questType.getApplicableElements(mapData).toList().single())
    }

    /*
       │ ╱
       │❬
       │ ╲
    */
    @Test fun `touching but not crossing footway does not count`() {
        val shared = node(2, p(0.0, 0.0))
        val mapData = TestMapDataWithGeometry(listOf(
            node(1, p(0.0, -1.0)),
            shared,
            node(3, p(0.0, +1.0)),
            node(4, p(-1.0, 0.0)),
            node(5, p(-1.0, +1.0)), // <--
            way(1, nodes = listOf(1, 2, 3), tags = mapOf("highway" to "unclassified")),
            way(2, nodes = listOf(4, 2, 5), tags = mapOf("highway" to "footway")),
        ))
        assertEquals(0, questType.getApplicableElements(mapData).toList().size)
    }

    /*
       │ ╱
       │❬
       │ ╲
    */
    @Test fun `touching but not crossing road does not count`() {
        val shared = node(2, p(0.0, 0.0))
        val mapData = TestMapDataWithGeometry(listOf(
            node(1, p(0.0, -1.0)),
            shared,
            node(3, p(1.0, -1.0)), // <--
            node(4, p(-1.0, 0.0)),
            node(5, p(+1.0, 0.0)),
            way(1, nodes = listOf(1, 2, 3), tags = mapOf("highway" to "unclassified")),
            way(2, nodes = listOf(4, 2, 5), tags = mapOf("highway" to "footway")),
        ))
        assertEquals(0, questType.getApplicableElements(mapData).toList().size)
    }

    /*
       │ ╱
    ───│❬────
       │ ╲
    */
    @Test fun `one of several footways crosses the road counts`() {
        val shared = node(2, p(0.0, 0.0))
        val mapData = TestMapDataWithGeometry(listOf(
            node(1, p(0.0, -1.0)),
            shared,
            node(3, p(0.0, +1.0)),
            node(4, p(-1.0, 0.0)),
            node(5, p(-1.0, +1.0)),
            node(6, p(-1.0, -1.0)),
            node(7, p(+1.0, 0.0)),
            way(1, nodes = listOf(1, 2, 3), tags = mapOf("highway" to "unclassified")),
            way(2, nodes = listOf(4, 2), tags = mapOf("highway" to "footway")),
            way(3, nodes = listOf(5, 2), tags = mapOf("highway" to "footway")),
            way(4, nodes = listOf(6, 2), tags = mapOf("highway" to "footway")),
            way(5, nodes = listOf(7, 2), tags = mapOf("highway" to "footway")),
        ))
        assertEquals(shared, questType.getApplicableElements(mapData).toList().single())
    }

    /*
        ║
      ══╬══
      ╱ ║ ╲
    */
    @Test fun `one footway crossing any of the roads count`() {
        val shared = node(2, p(0.0, 0.0))
        val mapData = TestMapDataWithGeometry(listOf(
            node(1, p(0.0, -1.0)),
            shared,
            node(3, p(0.0, +1.0)),
            node(4, p(-1.0, 0.0)),
            node(5, p(+1.0, 0.0)),
            node(6, p(-1.0, -1.0)),
            node(7, p(-1.0, +1.0)),
            way(1, nodes = listOf(1, 2, 3), tags = mapOf("highway" to "unclassified")),
            way(2, nodes = listOf(4, 2, 5), tags = mapOf("highway" to "unclassified")),
            way(3, nodes = listOf(6, 2, 7), tags = mapOf("highway" to "footway")),
        ))
        assertEquals(shared, questType.getApplicableElements(mapData).toList().single())
    }

    @Test fun `crossing at likely transition point between sidewalk-tagging-schemes do not count`() {
        val shared = node(2, p(0.0, 0.0))
        val mapData = TestMapDataWithGeometry(listOf(
            node(1, p(0.0, -1.0)),
            shared,
            node(3, p(0.0, +1.0)),
            node(4, p(-1.0, 0.0)),
            node(5, p(+1.0, 0.0)),
            way(1, nodes = listOf(1, 2), tags = mapOf("highway" to "unclassified", "sidewalk" to "both")),
            way(2, nodes = listOf(3, 2), tags = mapOf("highway" to "unclassified")),
            way(3, nodes = listOf(4, 2, 5), tags = mapOf("highway" to "footway")),
        ))
        assertEquals(0, questType.getApplicableElements(mapData).toList().size)
    }
}
