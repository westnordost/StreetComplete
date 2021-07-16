package de.westnordost.streetcomplete.quests

import android.content.res.Configuration
import android.os.Bundle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.add
import androidx.fragment.app.commit

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.databinding.FormLeaveNoteBinding
import de.westnordost.streetcomplete.databinding.QuestButtonpanelDoneCancelBinding
import de.westnordost.streetcomplete.ktx.viewBinding
import de.westnordost.streetcomplete.quests.note_discussion.AttachPhotoFragment
import de.westnordost.streetcomplete.util.TextChangedWatcher

/** Abstract base class for a bottom sheet that lets the user create a note */
abstract class AbstractCreateNoteFragment : AbstractBottomSheetFragment() {

    private val attachPhotoFragment: AttachPhotoFragment?
        get() = childFragmentManager.findFragmentById(R.id.attachPhotoFragment) as AttachPhotoFragment

    private val formLeaveNoteBinding by viewBinding(FormLeaveNoteBinding::bind)
    private val questButtonpanelDoneCancelBinding by viewBinding(QuestButtonpanelDoneCancelBinding::bind)

    private val noteText get() = formLeaveNoteBinding.noteInput?.text?.toString().orEmpty().trim()

    protected abstract val layoutResId: Int

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(layoutResId, container, false)

        val bottomSheet = view.findViewById<LinearLayout>(R.id.bottomSheet)
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            BottomSheetBehavior.from(bottomSheet).state = BottomSheetBehavior.STATE_EXPANDED
        }

        val content = view.findViewById<ViewGroup>(R.id.content)
        content.removeAllViews()
        inflater.inflate(R.layout.form_leave_note, content)

        val buttonPanel = view.findViewById<ViewGroup>(R.id.buttonPanel)
        buttonPanel.removeAllViews()
        inflater.inflate(R.layout.quest_buttonpanel_done_cancel, buttonPanel)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState == null) {
            childFragmentManager.commit { add<AttachPhotoFragment>(R.id.attachPhotoFragment) }
        }

        formLeaveNoteBinding.noteInput.addTextChangedListener(TextChangedWatcher { updateDoneButtonEnablement() })

        questButtonpanelDoneCancelBinding.cancelButton.setOnClickListener { activity?.onBackPressed() }
        questButtonpanelDoneCancelBinding.doneButton.setOnClickListener { onClickOk() }

        updateDoneButtonEnablement()
    }

    private fun onClickOk() {
        onComposedNote(noteText, attachPhotoFragment?.imagePaths.orEmpty())
    }

    override fun onDiscard() {
        attachPhotoFragment?.deleteImages()
    }

    override fun isRejectingClose() =
        noteText.isNotEmpty() || attachPhotoFragment?.imagePaths?.isNotEmpty() == true

    private fun updateDoneButtonEnablement() {
        questButtonpanelDoneCancelBinding.doneButton.isEnabled = !noteText.isEmpty()
    }

    protected abstract fun onComposedNote(text: String, imagePaths: List<String>)
}
