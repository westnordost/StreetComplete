package de.westnordost.streetcomplete.quests.postbox_collection_times

import android.os.Bundle
import android.view.Menu
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.databinding.QuestCollectionTimesBinding
import de.westnordost.streetcomplete.ktx.viewBinding
import de.westnordost.streetcomplete.quests.AbstractQuestFormAnswerFragment
import de.westnordost.streetcomplete.quests.OtherAnswer
import de.westnordost.streetcomplete.util.AdapterDataChangedWatcher
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


class AddCollectionTimesForm : AbstractQuestFormAnswerFragment<CollectionTimesAnswer>() {

    override val contentLayoutResId = R.layout.quest_collection_times

    private val binding by viewBinding(QuestCollectionTimesBinding::bind)

    override val otherAnswers = listOf(
        OtherAnswer(R.string.quest_collectionTimes_answer_no_times_specified) { confirmNoTimes() }
    )

    private lateinit var collectionTimesAdapter: CollectionTimesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val viewData = loadCollectionTimesData(savedInstanceState)
        collectionTimesAdapter = CollectionTimesAdapter(viewData, requireContext(), countryInfo)
        collectionTimesAdapter.registerAdapterDataObserver( AdapterDataChangedWatcher { checkIsFormComplete() })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.collectionTimesList.layoutManager = LinearLayoutManager(activity, RecyclerView.VERTICAL, false)
        binding.collectionTimesList.adapter = collectionTimesAdapter
        binding.collectionTimesList.isNestedScrollingEnabled = false
        checkIsFormComplete()

        binding.addTimesButton.setOnClickListener { onClickAddButton(it) }
    }

    private fun onClickAddButton(v: View) {
        val rows = collectionTimesAdapter.collectionTimesRows

        val addTimeAvailable = rows.isNotEmpty()

        if (addTimeAvailable) {
            val popup = PopupMenu(requireContext(), v)
            if (addTimeAvailable) popup.menu.add(Menu.NONE, 0, Menu.NONE, R.string.quest_openingHours_add_hours)
            popup.menu.add(Menu.NONE, 1, Menu.NONE, R.string.quest_openingHours_add_weekdays)
            popup.setOnMenuItemClickListener { item ->
                when(item.itemId) {
                    0 -> collectionTimesAdapter.addNewHours()
                    1 -> collectionTimesAdapter.addNewWeekdays()
                }
                true
            }
            popup.show()
        } else {
            collectionTimesAdapter.addNewWeekdays()
        }
    }

    private fun loadCollectionTimesData(savedInstanceState: Bundle?): List<WeekdaysTimesRow> =
        savedInstanceState?.let { Json.decodeFromString(it.getString(TIMES_DATA)!!) } ?: listOf()

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(TIMES_DATA, Json.encodeToString(collectionTimesAdapter.collectionTimesRows))
    }

    override fun onClickOk() {
        applyAnswer(CollectionTimes(collectionTimesAdapter.createCollectionTimes()))
    }

    private fun confirmNoTimes() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.quest_generic_confirmation_title)
            .setPositiveButton(R.string.quest_generic_confirmation_yes) { _, _ -> applyAnswer(NoCollectionTimesSign) }
            .setNegativeButton(R.string.quest_generic_confirmation_no, null)
            .show()
    }

    override fun isFormComplete() = collectionTimesAdapter.createCollectionTimes().isNotEmpty()

    companion object {
        private const val TIMES_DATA = "times_data"
    }
}
