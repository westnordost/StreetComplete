package de.westnordost.streetcomplete.quests

import android.content.Context
import android.os.Bundle
import androidx.recyclerview.widget.GridLayoutManager
import android.view.View
import androidx.preference.PreferenceManager

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.databinding.QuestGenericListBinding
import de.westnordost.streetcomplete.ktx.viewBinding
import de.westnordost.streetcomplete.view.image_select.DisplayItem
import de.westnordost.streetcomplete.view.image_select.ImageSelectAdapter
import java.util.*
import kotlin.collections.ArrayList

/**
 * Abstract class for quests with a list of images and one or several to select.
 *
 * I is the type of each item in the image list (a simple model object). In MVC, this would
 * be the view model.
 *
 * T is the type of the answer object (also a simple model object) created by the quest
 * form and consumed by the quest type. In MVC, this would be the model.
 */
abstract class AImageListQuestAnswerFragment<I,T> : AbstractQuestFormAnswerFragment<T>() {

    override val contentLayoutResId = R.layout.quest_generic_list
    override val defaultExpanded = false

    protected lateinit var imageSelector: ImageSelectAdapter<I>

    private val binding by viewBinding(QuestGenericListBinding::bind)

    private lateinit var favs: LastPickedValuesStore<I>

    protected open val itemsPerRow = 4
    /** return -1 for any number. Default: 1  */
    protected open val maxSelectableItems = 1
    /** return true to move last picked items to the front. On by default. Only respected if the
     *  items do not all fit into one line */
    protected open val moveFavoritesToFront = true

    protected abstract val items: List<DisplayItem<I>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        imageSelector = ImageSelectAdapter(maxSelectableItems)
    }

    override fun onAttach(ctx: Context) {
        super.onAttach(ctx)
        favs = LastPickedValuesStore(PreferenceManager.getDefaultSharedPreferences(ctx.applicationContext))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.list.layoutManager = GridLayoutManager(activity, itemsPerRow)
        binding.list.isNestedScrollingEnabled = false

        binding.selectHintLabel.setText(if (maxSelectableItems == 1) R.string.quest_roofShape_select_one else R.string.quest_select_hint)

        imageSelector.listeners.add(object : ImageSelectAdapter.OnItemSelectionListener {
            override fun onIndexSelected(index: Int) {
                checkIsFormComplete()
            }

            override fun onIndexDeselected(index: Int) {
                checkIsFormComplete()
            }
        })

        binding.showMoreButton.visibility = View.GONE

        imageSelector.items = moveFavouritesToFront(items)
        if (savedInstanceState != null) {
            val selectedIndices = savedInstanceState.getIntegerArrayList(SELECTED_INDICES)!!
            imageSelector.select(selectedIndices)
        }
        binding.list.adapter = imageSelector
    }

    override fun onClickOk() {
        val values = imageSelector.selectedItems
        if (values.isNotEmpty()) {
            favs.add(javaClass.simpleName, values)
            onClickOk(values)
        }
    }

    protected abstract fun onClickOk(selectedItems: List<I>)

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // note: the view might be not available anymore at this point!
        outState.putIntegerArrayList(SELECTED_INDICES, ArrayList(imageSelector.selectedIndices))
    }

    override fun isFormComplete() = imageSelector.selectedIndices.isNotEmpty()

    private fun moveFavouritesToFront(originalList: List<DisplayItem<I>>): List<DisplayItem<I>> {
        val result: LinkedList<DisplayItem<I>> = LinkedList(originalList)

        if (result.size > itemsPerRow && moveFavoritesToFront) {
            favs.moveLastPickedDisplayItemsToFront(javaClass.simpleName, result, originalList)
        }
        return result
    }

    companion object {
        private const val SELECTED_INDICES = "selected_indices"
    }
}
