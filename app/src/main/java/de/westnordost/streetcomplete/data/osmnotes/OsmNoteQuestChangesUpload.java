package de.westnordost.streetcomplete.data.osmnotes;

import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;

import de.westnordost.streetcomplete.data.QuestStatus;
import de.westnordost.osmapi.common.errors.OsmConflictException;
import de.westnordost.osmapi.map.data.LatLon;
import de.westnordost.osmapi.notes.Note;
import de.westnordost.osmapi.notes.NotesDao;
import de.westnordost.streetcomplete.util.ImageUploadHelper;

public class OsmNoteQuestChangesUpload
{
	private static final String TAG = "NoteCommentUpload";

	private final NotesDao osmDao;
	private final OsmNoteQuestDao questDB;
	private final NoteDao noteDB;

	private String imageLinkText;
	private Boolean waitForImageUpload = false;

	@Inject public OsmNoteQuestChangesUpload(NotesDao osmDao, OsmNoteQuestDao questDB, NoteDao noteDB)
	{
		this.osmDao = osmDao;
		this.questDB = questDB;
		this.noteDB = noteDB;
	}

	public void upload(AtomicBoolean cancelState)
	{
		int created = 0, obsolete = 0;
		for(OsmNoteQuest quest : questDB.getAll(null, QuestStatus.ANSWERED))
		{
			if(cancelState.get()) break;

			if(uploadNoteChanges(quest) != null)
			{
				created++;
			}
			else
			{
				obsolete++;
			}
		}
		String logMsg = "Commented on " + created + " notes";
		if(obsolete > 0)
		{
			logMsg += " but dropped " + obsolete + " comments because the notes have already been closed";
		}
		Log.i(TAG, logMsg);
	}

	Note uploadNoteChanges(OsmNoteQuest quest)
	{
		String text = quest.getComment();

		try
		{
			Note newNote;
			if (quest.getImagePaths() != null)
			{
				waitForImageUpload = true;

				new ImageUploadHelper(quest.getImagePaths(), new ImageUploadHelper.ImageUploadListener() {
					@Override
					public void onImageUploaded(String linksToImages) {
						imageLinkText = linksToImages;
						waitForImageUpload = false;
					}
					@Override
					public void onUploadFailed() {
						imageLinkText = "";
						waitForImageUpload = false;
					}
				}).execute();

				while (waitForImageUpload) {}
				newNote = osmDao.comment(quest.getNote().id, text + "\n" + imageLinkText);
			} else
			{
				newNote = osmDao.comment(quest.getNote().id, text);
			}

			/* Unlike OSM quests, note quests are never deleted when the user contributed to it
			   but must remain in the database with the status CLOSED as long as they are not
			   solved. The reason is because as long as a note is unsolved, the problem at that
			   position persists and thus it should still block other quests to be created.
			   (Reminder: Note quests block other quests)
			  */
			// so, not this: questDB.delete(quest.getId());
			quest.setStatus(QuestStatus.CLOSED);
			quest.setNote(newNote);
			questDB.update(quest);
			noteDB.put(newNote);

			return newNote;
		}
		catch(OsmConflictException e)
		{
			// someone else already closed the note -> our contribution is probably worthless. Delete
			questDB.delete(quest.getId());
			noteDB.delete(quest.getNote().id);

			Log.i(TAG, "Dropped the comment " + getNoteQuestStringForLog(quest) +
					" because the note has already been closed");

			return null;
		}
	}

	private static String getNoteQuestStringForLog(OsmNoteQuest n)
	{
		LatLon pos = n.getMarkerLocation();
		return "\"" + n.getComment() + "\" at " + pos.getLatitude() + ", " + pos.getLongitude();
	}

}
