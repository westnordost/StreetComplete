package de.westnordost.streetcomplete.quests.note_discussion

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.text.format.DateUtils.MINUTE_IN_MILLIS
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.westnordost.streetcomplete.Injector
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.osmnotes.NoteComment
import de.westnordost.streetcomplete.data.osmnotes.edits.NotesWithEditsSource
import de.westnordost.streetcomplete.data.osmnotes.NotesModule
import de.westnordost.streetcomplete.data.quest.OsmNoteQuestKey
import de.westnordost.streetcomplete.data.user.User
import de.westnordost.streetcomplete.databinding.FragmentQuestAnswerBinding
import de.westnordost.streetcomplete.databinding.QuestButtonpanelNoteDiscussionBinding
import de.westnordost.streetcomplete.databinding.QuestNoteDiscussionContentBinding
import de.westnordost.streetcomplete.databinding.QuestNoteDiscussionItemBinding
import de.westnordost.streetcomplete.ktx.createBitmap
import de.westnordost.streetcomplete.ktx.viewBinding
import de.westnordost.streetcomplete.quests.AbstractQuestAnswerFragment
import de.westnordost.streetcomplete.util.TextChangedWatcher
import de.westnordost.streetcomplete.view.CircularOutlineProvider
import de.westnordost.streetcomplete.view.ListAdapter
import de.westnordost.streetcomplete.view.RoundRectOutlineProvider
import java.io.File
import java.time.Instant
import javax.inject.Inject

class NoteDiscussionForm : AbstractQuestAnswerFragment<NoteAnswer>() {

    override val contentLayoutResId = R.layout.quest_note_discussion_content
    override val buttonsResId = R.layout.quest_buttonpanel_note_discussion
    override val defaultExpanded = false

    private val questNoteBinding by viewBinding(QuestNoteDiscussionContentBinding::bind)
    private val questButtonPanelNoteBinding by viewBinding(QuestButtonpanelNoteDiscussionBinding::bind)
    private val fragmentQuestAnswerBinding by viewBinding(FragmentQuestAnswerBinding::bind)

    private lateinit var anonAvatar: Bitmap

    @Inject internal lateinit var noteSource: NotesWithEditsSource

    private val attachPhotoFragment get() =
        childFragmentManager.findFragmentById(R.id.attachPhotoFragment) as? AttachPhotoFragment

    private val noteText: String get() = questNoteBinding.noteInput?.text?.toString().orEmpty().trim()

    init {
        Injector.applicationComponent.inject(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        questButtonPanelNoteBinding.doneButton.setOnClickListener { onClickOk() }
        questButtonPanelNoteBinding.noButton.setOnClickListener { skipQuest() }

        questNoteBinding.noteInput.addTextChangedListener(TextChangedWatcher { updateDoneButtonEnablement() })

        fragmentQuestAnswerBinding.otherAnswersButton.visibility = View.GONE

        updateDoneButtonEnablement()

        anonAvatar = resources.getDrawable(R.drawable.ic_osm_anon_avatar).createBitmap()

        val osmNoteQuestKey = questKey as OsmNoteQuestKey
        inflateNoteDiscussion(noteSource.get(osmNoteQuestKey.noteId)!!.comments)

        if (savedInstanceState == null) {
            childFragmentManager.commit { add<AttachPhotoFragment>(R.id.attachPhotoFragment) }
        }
    }

    private fun inflateNoteDiscussion(comments: List<NoteComment>) {
        val discussionView = layoutInflater.inflate(R.layout.quest_note_discussion_items, fragmentQuestAnswerBinding.scrollViewChild, false) as RecyclerView

        discussionView.isNestedScrollingEnabled = false
        discussionView.layoutManager = LinearLayoutManager(
            context,
            RecyclerView.VERTICAL,
            false
        )
        discussionView.adapter = NoteCommentListAdapter(comments)

        fragmentQuestAnswerBinding.scrollViewChild.addView(discussionView, 0)
    }

    private fun onClickOk() {
        applyAnswer(NoteAnswer(noteText, attachPhotoFragment?.imagePaths.orEmpty()))
    }

    override fun onDiscard() {
        attachPhotoFragment?.deleteImages()
    }

    override fun isRejectingClose(): Boolean {
        val f = attachPhotoFragment
        val hasPhotos = f != null && !f.imagePaths.isEmpty()
        return hasPhotos || noteText.isNotEmpty()
    }

    private fun updateDoneButtonEnablement() {
        questButtonPanelNoteBinding.doneButton.isEnabled = noteText.isNotEmpty()
    }


    private inner class NoteCommentListAdapter(list: List<NoteComment>) :
        ListAdapter<NoteComment>(list) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder<NoteComment> {
            return NoteCommentViewHolder(
                QuestNoteDiscussionItemBinding.inflate(layoutInflater, parent, false)
            )
        }
    }

    private inner class NoteCommentViewHolder(val binding: QuestNoteDiscussionItemBinding) :
        ListAdapter.ViewHolder<NoteComment>(binding) {

        init {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val cornerRadius = resources.getDimension(R.dimen.speech_bubble_rounded_corner_radius)
                val margin = resources.getDimensionPixelSize(R.dimen.horizontal_speech_bubble_margin)
                val marginStart = -resources.getDimensionPixelSize(R.dimen.quest_form_speech_bubble_top_margin)
                binding.commentStatusText.outlineProvider = RoundRectOutlineProvider(cornerRadius)

                val isRTL = itemView.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
                val marginLeft = if (isRTL) 0 else marginStart
                val marginRight = if (isRTL) marginStart else 0
                binding.commentBubble.outlineProvider = RoundRectOutlineProvider(
                    cornerRadius, marginLeft, margin, marginRight, margin
                )
                binding.commentAvatarImageContainer.outlineProvider = CircularOutlineProvider
            }
        }

        override fun onBind(comment: NoteComment) {
            val dateDescription = DateUtils.getRelativeTimeSpanString(comment.timestamp, Instant.now().toEpochMilli(), MINUTE_IN_MILLIS)
            val userName = if (comment.user != null) comment.user.displayName else getString(R.string.quest_noteDiscussion_anonymous)

            val commentActionResourceId = comment.action.actionResourceId
            val hasNoteAction = commentActionResourceId != 0
            binding.commentStatusText.isGone = !hasNoteAction
            if (hasNoteAction) {
                binding.commentStatusText.text = getString(commentActionResourceId, userName, dateDescription)
            }

            val hasComment = comment.text?.isNotEmpty() == true
            binding.commentView.isGone = !hasComment
            if (hasComment) {
                binding.commentText.text = comment.text
                binding.commentInfoText.text = getString(R.string.quest_noteDiscussion_comment2, userName, dateDescription)

                val bitmap = comment.user?.avatar ?: anonAvatar
                binding.commentAvatarImage.setImageBitmap(bitmap)
            }
        }

        private val User.avatar: Bitmap? get() {
            val cacheDir = NotesModule.getAvatarsCacheDirectory(requireContext())
            val file = File(cacheDir.toString() + File.separator + id)
            return if (file.exists()) BitmapFactory.decodeFile(file.path) else null
        }

        private val NoteComment.Action.actionResourceId get() = when (this) {
            NoteComment.Action.CLOSED -> R.string.quest_noteDiscussion_closed2
            NoteComment.Action.REOPENED -> R.string.quest_noteDiscussion_reopen2
            NoteComment.Action.HIDDEN -> R.string.quest_noteDiscussion_hide2
            else -> 0
        }
    }
}
