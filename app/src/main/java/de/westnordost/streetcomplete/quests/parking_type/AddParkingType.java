package de.westnordost.streetcomplete.quests.parking_type;

import android.os.Bundle;

import java.util.List;

import javax.inject.Inject;

import de.westnordost.streetcomplete.data.osm.SimpleOverpassQuestType;
import de.westnordost.streetcomplete.data.osm.changes.StringMapChangesBuilder;
import de.westnordost.streetcomplete.data.osm.download.OverpassMapDataDao;
import de.westnordost.streetcomplete.quests.AbstractQuestAnswerFragment;

public class AddParkingType extends SimpleOverpassQuestType
{
	@Inject public AddParkingType(OverpassMapDataDao overpassServer)
	{
		super(overpassServer);
	}

	@Override
	protected String getTagFilters()
	{
		return "nodes, ways, relations with amenity=parking and !parking";
	}

	public AbstractQuestAnswerFragment createForm()
	{
		return new AddParkingTypeForm();
	}

	public void applyAnswerTo(Bundle answer, StringMapChangesBuilder changes)
	{
		List<String> values = answer.getStringArrayList(AddParkingTypeForm.OSM_VALUES);
		if(values != null  && values.size() == 1)
		{
			changes.add("parking", values.get(0));
		}
	}

	@Override public String getCommitMessage()
	{
		return "Add parking type";
	}

	@Override public String getIconName() {
		return "parking";
	}
}
