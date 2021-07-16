package de.westnordost.streetcomplete.user

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import de.westnordost.streetcomplete.Injector
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.osmnotes.NotesModule
import de.westnordost.streetcomplete.data.UnsyncedChangesCountSource
import de.westnordost.streetcomplete.data.user.*
import de.westnordost.streetcomplete.data.user.achievements.UserAchievementsDao
import de.westnordost.streetcomplete.databinding.FragmentProfileBinding
import de.westnordost.streetcomplete.ktx.createBitmap
import de.westnordost.streetcomplete.ktx.tryStartActivity
import de.westnordost.streetcomplete.ktx.viewBinding
import kotlinx.coroutines.*
import java.io.File
import java.util.Locale
import javax.inject.Inject

/** Shows the user profile: username, avatar, star count and a hint regarding unpublished changes */
class ProfileFragment : Fragment(R.layout.fragment_profile) {

    @Inject internal lateinit var userController: UserController
    @Inject internal lateinit var userStore: UserStore
    @Inject internal lateinit var questStatisticsDao: QuestStatisticsDao
    @Inject internal lateinit var countryStatisticsDao: CountryStatisticsDao
    @Inject internal lateinit var userAchievementsDao: UserAchievementsDao
    @Inject internal lateinit var unsyncedChangesCountSource: UnsyncedChangesCountSource

    private lateinit var anonAvatar: Bitmap

    private val binding by viewBinding(FragmentProfileBinding::bind)

    private val unsyncedChangesCountListener = object : UnsyncedChangesCountSource.Listener {
        override fun onIncreased() { lifecycleScope.launch { updateUnpublishedQuestsText() } }
        override fun onDecreased() { lifecycleScope.launch { updateUnpublishedQuestsText() } }
    }
    private val questStatisticsDaoListener = object : QuestStatisticsDao.Listener {
        override fun onAddedOne(questType: String) { lifecycleScope.launch { updateSolvedQuestsText() }}
        override fun onSubtractedOne(questType: String) { lifecycleScope.launch { updateSolvedQuestsText() } }
        override fun onReplacedAll() { lifecycleScope.launch { updateSolvedQuestsText() } }
    }
    private val userStoreUpdateListener = object : UserStore.UpdateListener {
        override fun onUserDataUpdated() { lifecycleScope.launch { updateUserName() } }
    }
    private val userAvatarListener = object : UserAvatarListener {
        override fun onUserAvatarUpdated() { lifecycleScope.launch { updateAvatar() } }
    }

    init {
        Injector.applicationComponent.inject(this)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        anonAvatar = resources.getDrawable(R.drawable.ic_osm_anon_avatar).createBitmap()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.logoutButton.setOnClickListener {
            userController.logOut()
        }
        binding.profileButton.setOnClickListener {
            openUrl("https://www.openstreetmap.org/user/" + userStore.userName)
        }
    }

    override fun onStart() {
        super.onStart()

        lifecycleScope.launch {
            userStore.addListener(userStoreUpdateListener)
            userController.addUserAvatarListener(userAvatarListener)
            questStatisticsDao.addListener(questStatisticsDaoListener)
            unsyncedChangesCountSource.addListener(unsyncedChangesCountListener)

            updateUserName()
            updateAvatar()
            updateSolvedQuestsText()
            updateUnpublishedQuestsText()
            updateDaysActiveText()
            updateGlobalRankText()
            updateLocalRankText()
            updateAchievementLevelsText()
        }
    }

    override fun onStop() {
        super.onStop()
        unsyncedChangesCountSource.removeListener(unsyncedChangesCountListener)
        questStatisticsDao.removeListener(questStatisticsDaoListener)
        userStore.removeListener(userStoreUpdateListener)
        userController.removeUserAvatarListener(userAvatarListener)
    }

    private fun updateUserName() {
        binding.userNameTextView.text = userStore.userName
    }

    private fun updateAvatar() {
        val cacheDir = NotesModule.getAvatarsCacheDirectory(requireContext())
        val avatarFile = File(cacheDir.toString() + File.separator + userStore.userId)
        val avatar = if (avatarFile.exists()) BitmapFactory.decodeFile(avatarFile.path) else anonAvatar
        binding.userAvatarImageView.setImageBitmap(avatar)
    }

    private suspend fun updateSolvedQuestsText() {
        binding.solvedQuestsText.text = withContext(Dispatchers.IO) { questStatisticsDao.getTotalAmount().toString() }
    }

    private suspend fun updateUnpublishedQuestsText() {
        val unsyncedChanges = unsyncedChangesCountSource.getCount()
        binding.unpublishedQuestsText.text = getString(R.string.unsynced_quests_description, unsyncedChanges)
        binding.unpublishedQuestsText.isGone = unsyncedChanges <= 0
    }

    private fun updateDaysActiveText() {
        val daysActive = userStore.daysActive
        binding.daysActiveContainer.isGone = daysActive <= 0
        binding.daysActiveText.text = daysActive.toString()
    }

    private fun updateGlobalRankText() {
        val rank = userStore.rank
        binding.globalRankContainer.isGone = rank <= 0 || questStatisticsDao.getTotalAmount() <= 100
        binding.globalRankText.text = "#$rank"
    }

    private suspend fun updateLocalRankText() {
        val statistics = withContext(Dispatchers.IO) {
            countryStatisticsDao.getCountryWithBiggestSolvedCount()
        }
        if (statistics == null) binding.localRankContainer.isGone = true
        else {
            val shouldShow = statistics.rank != null && statistics.rank > 0 && statistics.solvedCount > 50
            val countryLocale = Locale("", statistics.countryCode)
            binding.localRankContainer.isGone = !shouldShow
            binding.localRankText.text = "#${statistics.rank}"
            binding.localRankLabel.text = getString(R.string.user_profile_local_rank, countryLocale.displayCountry)
        }
    }

    private suspend fun updateAchievementLevelsText() {
        val levels = withContext(Dispatchers.IO) { userAchievementsDao.getAll().values.sum() }
        binding.achievementLevelsContainer.isGone = levels <= 0
        binding.achievementLevelsText.text = "$levels"
    }

    private fun openUrl(url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        return tryStartActivity(intent)
    }

}
