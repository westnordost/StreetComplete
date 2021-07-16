package de.westnordost.streetcomplete.edithistory

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import de.westnordost.streetcomplete.Injector
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.edithistory.Edit
import de.westnordost.streetcomplete.data.edithistory.EditHistorySource
import de.westnordost.streetcomplete.data.edithistory.EditKey
import de.westnordost.streetcomplete.databinding.FragmentCreditsBinding
import de.westnordost.streetcomplete.databinding.FragmentEditHistoryListBinding
import de.westnordost.streetcomplete.ktx.viewBinding
import de.westnordost.streetcomplete.view.insets_animation.respectSystemInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Shows a list of the edit history */
class EditHistoryFragment : Fragment(R.layout.fragment_edit_history_list) {

    @Inject internal lateinit var editHistorySource: EditHistorySource

    interface Listener {
        /** Called when an edit has been selected and the undo-button appeared */
        fun onSelectedEdit(edit: Edit)
        /** Called when the edit that was selected has been removed */
        fun onDeletedSelectedEdit()
        /** Called when the edit history is empty now */
        fun onEditHistoryIsEmpty()
    }
    private val listener: Listener? get() = parentFragment as? Listener ?: activity as? Listener

    private val binding by viewBinding(FragmentEditHistoryListBinding::bind)

    private val adapter = EditHistoryAdapter(this::onSelected, this::onSelectionDeleted, this::onUndo)

    private val editHistoryListener = object : EditHistorySource.Listener {
        override fun onAdded(edit: Edit) { lifecycleScope.launch { adapter.onAdded(edit) } }
        override fun onSynced(edit: Edit) { lifecycleScope.launch { adapter.onSynced(edit) } }

        override fun onDeleted(edits: List<Edit>) {
            lifecycleScope.launch {
                adapter.onDeleted(edits)
                if (editHistorySource.getCount() == 0) {
                    listener?.onEditHistoryIsEmpty()
                }
            }
        }

        override fun onInvalidated() {
            lifecycleScope.launch {
                val edits = withContext(Dispatchers.IO) { editHistorySource.getAll() }
                adapter.setEdits(edits)
                if (edits.isEmpty()) {
                    listener?.onEditHistoryIsEmpty()
                }
            }
        }
    }

    init {
        Injector.applicationComponent.inject(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editHistorySource.addListener(editHistoryListener)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.editHistoryList.respectSystemInsets { left, top, right, bottom ->
            setPadding(left, top, 0, bottom)
        }
        lifecycleScope.launch {
            val edits = withContext(Dispatchers.IO) { editHistorySource.getAll() }
            adapter.setEdits(edits)
            binding.editHistoryList.adapter = adapter
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        editHistorySource.removeListener(editHistoryListener)
    }

    fun select(editKey: EditKey) {
        val edit = editHistorySource.get(editKey) ?: return
        adapter.select(edit)
    }

    private fun onSelected(edit: Edit) {
        listener?.onSelectedEdit(edit)
    }

    private fun onSelectionDeleted() {
        listener?.onDeletedSelectedEdit()
    }

    private fun onUndo(edit: Edit) {
        UndoDialog(requireContext(), edit).show()
    }
}
