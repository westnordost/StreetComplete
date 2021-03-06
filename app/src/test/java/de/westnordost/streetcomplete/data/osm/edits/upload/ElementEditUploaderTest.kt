package de.westnordost.streetcomplete.data.osm.edits.upload

import de.westnordost.osmapi.common.errors.OsmConflictException
import de.westnordost.osmapi.map.data.Element
import de.westnordost.osmapi.map.data.OsmLatLon
import de.westnordost.osmapi.map.data.OsmNode
import de.westnordost.streetcomplete.any
import de.westnordost.streetcomplete.data.MapDataApi
import de.westnordost.streetcomplete.data.osm.edits.ElementEdit
import de.westnordost.streetcomplete.data.osm.edits.ElementEditAction
import de.westnordost.streetcomplete.data.osm.edits.delete.DeletePoiNodeAction
import de.westnordost.streetcomplete.data.osm.edits.upload.changesets.OpenQuestChangesetsManager
import de.westnordost.streetcomplete.data.quest.TestQuestTypeA
import de.westnordost.streetcomplete.data.upload.ConflictException
import de.westnordost.streetcomplete.mock
import de.westnordost.streetcomplete.on
import org.junit.Before

import org.junit.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.doThrow

class ElementEditUploaderTest {

    private lateinit var changesetManager: OpenQuestChangesetsManager
    private lateinit var mapDataApi: MapDataApi
    private lateinit var uploader: ElementEditUploader

    @Before fun setUp() {
        changesetManager = mock()
        mapDataApi = mock()

        uploader = ElementEditUploader(changesetManager, mapDataApi)
    }

    @Test(expected = ConflictException::class)
    fun `throws deleted exception if node is no more`() {
        on(mapDataApi.getNode(anyLong())).thenReturn(null)
        uploader.upload(edit(elementType = Element.Type.NODE, elementId = 12), mock())
    }

    @Test(expected = ConflictException::class)
    fun `throws deleted exception if way is no more`() {
        on(mapDataApi.getWay(anyLong())).thenReturn(null)
        uploader.upload(edit(elementType = Element.Type.WAY, elementId = 12), mock())
    }

    @Test(expected = ConflictException::class)
    fun `throws deleted exception if relation is no more`() {
        on(mapDataApi.getRelation(anyLong())).thenReturn(null)
        uploader.upload(edit(elementType = Element.Type.RELATION, elementId = 12), mock())
    }

    @Test(expected = ConflictException::class)
    fun `passes on element conflict exception`() {
        val node = OsmNode(1L, 1, OsmLatLon(0.0,0.0), null)
        on(mapDataApi.getNode(anyLong())).thenReturn(node)
        on(changesetManager.getOrCreateChangeset(any(), any())).thenReturn(1)
        on(changesetManager.createChangeset(any(), any())).thenReturn(1)
        on(mapDataApi.uploadChanges(anyLong(), any(), any()))
            .thenThrow(OsmConflictException(1,"",""))
            .thenThrow(OsmConflictException(1,"",""))

        uploader.upload(edit(elementType = Element.Type.NODE, elementId = 1), mock())
    }

    @Test fun `handles changeset conflict exception`() {
        val node = OsmNode(1L, 1, OsmLatLon(0.0,0.0), null)
        on(mapDataApi.getNode(anyLong())).thenReturn(node)
        on(changesetManager.getOrCreateChangeset(any(), any())).thenReturn(1)
        on(changesetManager.createChangeset(any(), any())).thenReturn(1)
        doThrow(OsmConflictException(1,"","")).doAnswer {  }
            .on(mapDataApi).uploadChanges(anyLong(), any(), any())

        uploader.upload(edit(elementType = Element.Type.NODE, elementId = 1), mock())
    }
}

private val QUEST_TYPE = TestQuestTypeA()

private fun edit(
    elementType: Element.Type = Element.Type.NODE,
    elementId: Long = -1L,
    pos: OsmLatLon = OsmLatLon(0.0,0.0),
    timestamp: Long = 123L,
    action: ElementEditAction = DeletePoiNodeAction(1),
    isSynced: Boolean = false
) = ElementEdit(
    1L,
    QUEST_TYPE,
    elementType,
    elementId,
    "survey",
    pos,
    timestamp,
    isSynced,
    action
)