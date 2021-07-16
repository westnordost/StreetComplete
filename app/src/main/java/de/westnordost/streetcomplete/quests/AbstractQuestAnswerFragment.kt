package de.westnordost.streetcomplete.quests

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.PopupMenu
import androidx.annotation.AnyThread
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.text.parseAsHtml
import androidx.core.view.isGone
import com.google.android.flexbox.FlexboxLayout
import de.westnordost.osmfeatures.FeatureDictionary
import de.westnordost.streetcomplete.Injector
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.meta.CountryInfos
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.ElementType
import de.westnordost.streetcomplete.data.osm.mapdata.Way
import de.westnordost.streetcomplete.data.osm.osmquests.OsmElementQuestType
import de.westnordost.streetcomplete.data.quest.*
import de.westnordost.streetcomplete.databinding.FragmentQuestAnswerBinding
import de.westnordost.streetcomplete.ktx.geometryType
import de.westnordost.streetcomplete.ktx.isArea
import de.westnordost.streetcomplete.ktx.isSomeKindOfShop
import de.westnordost.streetcomplete.ktx.viewBinding
import de.westnordost.streetcomplete.quests.shop_type.ShopGoneDialog
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.concurrent.FutureTask
import javax.inject.Inject

/** Abstract base class for any bottom sheet with which the user answers a specific quest(ion)  */
abstract class AbstractQuestAnswerFragment<T> : AbstractBottomSheetFragment(), IsShowingQuestDetails {

    // dependencies
    private val countryInfos: CountryInfos
    private val questTypeRegistry: QuestTypeRegistry
    private val featureDictionaryFuture: FutureTask<FeatureDictionary>
    private val binding by viewBinding(FragmentQuestAnswerBinding::bind)

    private var _countryInfo: CountryInfo? = null // lazy but resettable because based on lateinit var
        get() {
            if(field == null) {
                val latLon = elementGeometry.center
                field = countryInfos.get(latLon.longitude, latLon.latitude)
            }
            return field
        }
    protected val countryInfo get() = _countryInfo!!

    protected val featureDictionary: FeatureDictionary get() = featureDictionaryFuture.get()

    // views
    private lateinit var content: ViewGroup
    private lateinit var buttonPanel: FlexboxLayout
    private lateinit var otherAnswersButton: Button

    // passed in parameters
    override lateinit var questKey: QuestKey
    protected lateinit var elementGeometry: ElementGeometry private set
    private lateinit var questType: QuestType<T>
    private var initialMapRotation = 0f
    private var initialMapTilt = 0f
    protected var osmElement: Element? = null
        private set

    private var currentContext = WeakReference<Context>(null)
    private var currentCountryContext: ContextWrapper? = null

    private val englishResources: Resources
        get() {
            val conf = Configuration(resources.configuration)
            conf.setLocale(Locale.ENGLISH)
            val localizedContext = super.requireContext().createConfigurationContext(conf)
            return localizedContext.resources
        }

    private var startedOnce = false
    private var answered = 0

    // overridable by child classes
    open val contentLayoutResId: Int? = null
    open val buttonsResId: Int? = null
    open val otherAnswers = listOf<OtherAnswer>()
    open val contentPadding = true
    open val defaultExpanded = true

    interface Listener {
        /** Called when the user answered the quest with the given id. What is in the bundle, is up to
         * the dialog with which the quest was answered  */
        fun onAnsweredQuest(questKey: QuestKey, answer: Any)

        /** Called when the user chose to leave a note instead  */
        fun onComposeNote(questKey: QuestKey, questTitle: String)

        /** Called when the user chose to split the way  */
        fun onSplitWay(osmQuestKey: OsmQuestKey)

        /** Called when the user chose to skip the quest  */
        fun onSkippedQuest(questKey: QuestKey)

        /** Called when the node shall be deleted */
        fun onDeletePoiNode(osmQuestKey: OsmQuestKey)

        /** Called when a new feature has been selected for an element (a shop of some kind) */
        fun onReplaceShopElement(osmQuestKey: OsmQuestKey, tags: Map<String, String>)
    }
    private val listener: Listener? get() = parentFragment as? Listener ?: activity as? Listener

    init {
        val fields = InjectedFields()
        Injector.applicationComponent.inject(fields)
        countryInfos = fields.countryInfos
        featureDictionaryFuture = fields.featureDictionaryFuture
        questTypeRegistry = fields.questTypeRegistry
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val args = requireArguments()
        questKey = Json.decodeFromString(args.getString(ARG_QUEST_KEY)!!)
        osmElement = args.getString(ARG_ELEMENT)?.let { Json.decodeFromString(it) }
        elementGeometry = Json.decodeFromString(args.getString(ARG_GEOMETRY)!!)
        questType = questTypeRegistry.getByName(args.getString(ARG_QUESTTYPE)!!) as QuestType<T>
        initialMapRotation = args.getFloat(ARG_MAP_ROTATION)
        initialMapTilt = args.getFloat(ARG_MAP_TILT)
        _countryInfo = null // reset lazy field
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_quest_answer, container, false)
        content = view.findViewById(R.id.content)
        buttonPanel = view.findViewById(R.id.buttonPanel)
        otherAnswersButton = buttonPanel.findViewById(R.id.otherAnswersButton)

        contentLayoutResId?.let { setContentView(it) }
        buttonsResId?.let { setButtonsView(it) }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.titleLabel.text = resources.getHtmlQuestTitle(questType, osmElement, featureDictionaryFuture)

        val levelLabelText = getLocationLabelText()
        binding.locationLabel.isGone = levelLabelText == null
        if (levelLabelText != null) {
            binding.locationLabel.text = levelLabelText
        }

        // no content? -> hide the content container
        if (content.childCount == 0) {
            content.visibility = View.GONE
        }

        if (defaultExpanded) expand()
    }

    private fun assembleOtherAnswers() : List<OtherAnswer> {
        val answers = mutableListOf<OtherAnswer>()

        val cantSay = OtherAnswer(R.string.quest_generic_answer_notApplicable) { onClickCantSay() }
        answers.add(cantSay)

        createSplitWayAnswer()?.let { answers.add(it) }
        createDeleteOrReplaceElementAnswer()?.let { answers.add(it) }

        answers.addAll(otherAnswers)
        return answers
    }

    private fun createSplitWayAnswer(): OtherAnswer? {
        val isSplitWayEnabled = (questType as? OsmElementQuestType)?.isSplitWayEnabled == true
        if (!isSplitWayEnabled) return null

        val way = osmElement as? Way ?: return null

        /* splitting up a closed roundabout can be very complex if it is part of a route
           relation, so it is not supported
           https://wiki.openstreetmap.org/wiki/Relation:route#Bus_routes_and_roundabouts
        */
        val isClosedRoundabout = way.nodeIds.firstOrNull() == way.nodeIds.lastOrNull() &&
            way.tags["junction"] == "roundabout"
        if (isClosedRoundabout) return null

        if (way.isArea()) return null

        return OtherAnswer(R.string.quest_generic_answer_differs_along_the_way) {
            onClickSplitWayAnswer()
        }
    }

    private fun createDeleteOrReplaceElementAnswer(): OtherAnswer? {
        val isDeletePoiEnabled =
            (questType as? OsmElementQuestType)?.isDeleteElementEnabled == true
                && osmElement?.type == ElementType.NODE
        val isReplaceShopEnabled = (questType as? OsmElementQuestType)?.isReplaceShopEnabled == true
        if (!isDeletePoiEnabled && !isReplaceShopEnabled) return null
        check(!(isDeletePoiEnabled && isReplaceShopEnabled)) {
            "Only isDeleteElementEnabled OR isReplaceShopEnabled may be true at the same time"
        }

        return OtherAnswer(R.string.quest_generic_answer_does_not_exist) {
            if (isDeletePoiEnabled) deletePoiNode()
            else if (isReplaceShopEnabled) replaceShopElement()
        }
    }

    private fun showOtherAnswers() {
        val answers = assembleOtherAnswers()
        val popup = PopupMenu(requireContext(), otherAnswersButton)
        for (i in answers.indices) {
            val otherAnswer = answers[i]
            val order = answers.size - i
            popup.menu.add(Menu.NONE, i, order, otherAnswer.titleResourceId)
        }
        popup.show()

        popup.setOnMenuItemClickListener { item ->
            answers[item.itemId].action()
            true
        }
    }

    private fun getLocationLabelText(): CharSequence? {
        // prefer to show the level if both are present because it is a more precise indication
        // where it is supposed to be
        return getLevelLabelText() ?: getHouseNumberLabelText()
    }

    private fun getLevelLabelText(): CharSequence? {
        val tags = osmElement?.tags ?: return null
        /* prefer addr:floor etc. over level as level is rather an index than how the floor is
           denominated in the building and thus may (sometimes) not coincide with it. E.g.
           addr:floor may be "M" while level is "2" */
        val level = tags["addr:floor"] ?: tags["level:ref"] ?: tags["level"]
        if (level != null) {
            return resources.getString(R.string.on_level, level)
        }
        val tunnel = tags["tunnel"]
        if(tunnel != null && tunnel == "yes" || tags["location"] == "underground") {
            return resources.getString(R.string.underground)
        }
        return null
    }

    private fun getHouseNumberLabelText(): CharSequence? {
        val tags = osmElement?.tags ?: return null

        val houseName = tags["addr:housename"]
        val conscriptionNumber = tags["addr:conscriptionnumber"]
        val streetNumber = tags["addr:streetnumber"]
        val houseNumber = tags["addr:housenumber"]

        if (houseName != null) {
            return resources.getString(R.string.at_housename, "<i>" + Html.escapeHtml(houseName) + "</i>")
                .parseAsHtml()
        }
        if (conscriptionNumber != null) {
            if (streetNumber != null) {
                return resources.getString(R.string.at_conscription_and_street_number, conscriptionNumber, streetNumber)
            } else {
                return resources.getString(R.string.at_conscription_number, conscriptionNumber)
            }
        }
        if (houseNumber != null) {
            return resources.getString(R.string.at_housenumber, houseNumber)
        }
        return null
    }

    override fun onStart() {
        super.onStart()
        if(!startedOnce) {
            onMapOrientation(initialMapRotation, initialMapTilt)
            startedOnce = true
        }

        val answers = assembleOtherAnswers()
        if (answers.size == 1) {
            otherAnswersButton.setText(answers.first().titleResourceId)
            otherAnswersButton.setOnClickListener { answers.first().action() }
        } else {
            otherAnswersButton.setText(R.string.quest_generic_otherAnswers)
            otherAnswersButton.setOnClickListener { showOtherAnswers() }
        }
    }

    /** Note: Due to Android architecture limitations, a layout inflater based on this ContextWrapper
     * will not resolve any resources specified in the XML according to MCC  */
    override fun onGetLayoutInflater(savedInstanceState: Bundle?): LayoutInflater {
        // will always return a layout inflater for the current country
        return super.onGetLayoutInflater(savedInstanceState).cloneInContext(context)
    }

    override fun getContext(): Context? {
        val context = super.getContext()
        if (currentContext.get() !== context) {
            currentContext = WeakReference<Context>(context)
            currentCountryContext = if (context != null) createCurrentCountryContextWrapper(context) else null
        }
        return currentCountryContext
    }

    private fun createCurrentCountryContextWrapper(context: Context): ContextWrapper {
        val conf = Configuration(context.resources.configuration)
        conf.mcc = countryInfo.mobileCountryCode ?: 0
        val res = context.createConfigurationContext(conf).resources
        return object : ContextWrapper(context) {
            override fun getResources(): Resources {
                return res
            }
        }
    }

    protected fun onClickCantSay() {
        context?.let { AlertDialog.Builder(it)
            .setTitle(R.string.quest_leave_new_note_title)
            .setMessage(R.string.quest_leave_new_note_description)
            .setNegativeButton(R.string.quest_leave_new_note_no) { _, _ -> skipQuest() }
            .setPositiveButton(R.string.quest_leave_new_note_yes) { _, _ -> composeNote() }
            .show()
        }
    }

    protected fun composeNote() {
        if (answered++ == 0) {
            val questTitle = englishResources.getQuestTitle(questType, osmElement, featureDictionaryFuture)
            listener?.onComposeNote(questKey, questTitle)
        }
    }

    private fun onClickSplitWayAnswer() {
        context?.let { AlertDialog.Builder(it)
            .setMessage(R.string.quest_split_way_description)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (answered++ == 0) listener?.onSplitWay(questKey as OsmQuestKey)
            }
            .show()
        }
    }

    protected fun applyAnswer(data: T) {
        if (answered++ == 0) listener?.onAnsweredQuest(questKey, data as Any)
    }

    protected fun skipQuest() {
        if (answered++ == 0) listener?.onSkippedQuest(questKey)
    }

    protected fun replaceShopElement() {
        val ctx = context ?: return
        val element = osmElement ?: return
        val isoCountryCode = countryInfo.countryCode.substringBefore('-')

        if (element.isSomeKindOfShop()) {
            ShopGoneDialog(
                ctx,
                element.geometryType,
                isoCountryCode,
                featureDictionary,
                onSelectedFeature = { tags ->
                    if (answered++ == 0) listener?.onReplaceShopElement(questKey as OsmQuestKey, tags)
                },
                onLeaveNote = this::composeNote
            ).show()
        } else {
            composeNote()
        }
    }

    protected fun deletePoiNode() {
        val context = context ?: return

        AlertDialog.Builder(context)
            .setMessage(R.string.osm_element_gone_description)
            .setPositiveButton(R.string.osm_element_gone_confirmation) { _, _ ->
                if (answered++ == 0) listener?.onDeletePoiNode(questKey as OsmQuestKey)
            }
            .setNeutralButton(R.string.leave_note) { _, _ ->
                composeNote()
            }.show()
    }

    protected fun setContentView(resourceId: Int): View {
        if (content.childCount > 0) {
            content.removeAllViews()
        }
        content.visibility = View.VISIBLE
        updateContentPadding()
        return layoutInflater.inflate(resourceId, content)
    }

    private fun updateContentPadding() {
        if(!contentPadding) {
            content.setPadding(0,0,0,0)
        } else {
            val horizontal = resources.getDimensionPixelSize(R.dimen.quest_form_horizontal_padding)
            val vertical = resources.getDimensionPixelSize(R.dimen.quest_form_vertical_padding)
            content.setPadding(horizontal, vertical, horizontal, vertical)
        }
    }

    protected fun setButtonsView(resourceId: Int) {
        removeButtonsView()
        activity?.layoutInflater?.inflate(resourceId, buttonPanel)
    }

    protected fun removeButtonsView() {
        if (buttonPanel.childCount > 1) {
            buttonPanel.removeViews(1, buttonPanel.childCount - 1)
        }
    }

    @AnyThread open fun onMapOrientation(rotation: Float, tilt: Float) {
        // default empty implementation
    }

    class InjectedFields {
        @Inject internal lateinit var countryInfos: CountryInfos
        @Inject internal lateinit var questTypeRegistry: QuestTypeRegistry
        @Inject internal lateinit var featureDictionaryFuture: FutureTask<FeatureDictionary>
    }

    companion object {
        private const val ARG_QUEST_KEY = "quest_key"
        private const val ARG_ELEMENT = "element"
        private const val ARG_GEOMETRY = "geometry"
        private const val ARG_QUESTTYPE = "quest_type"
        private const val ARG_MAP_ROTATION = "map_rotation"
        private const val ARG_MAP_TILT = "map_tilt"

        fun createArguments(quest: Quest, element: Element?, rotation: Float, tilt: Float) = bundleOf(
            ARG_QUEST_KEY to Json.encodeToString(quest.key),
            ARG_ELEMENT to element?.let { Json.encodeToString(element) },
            ARG_GEOMETRY to Json.encodeToString(quest.geometry),
            ARG_QUESTTYPE to quest.type::class.simpleName!!,
            ARG_MAP_ROTATION to rotation,
            ARG_MAP_TILT to tilt
        )
    }
}

data class OtherAnswer(val titleResourceId: Int, val action: () -> Unit)
