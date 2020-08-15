package de.westnordost.streetcomplete.data.user

import android.util.Log
import de.westnordost.countryboundaries.CountryBoundaries
import de.westnordost.countryboundaries.getIds
import de.westnordost.osmapi.map.data.LatLon
import de.westnordost.streetcomplete.data.user.achievements.AchievementGiver
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.concurrent.FutureTask
import javax.inject.Inject
import javax.inject.Named

/** Manages the updating of statistics, locally and pulling a complete update from backend  */
class StatisticsUpdater @Inject constructor(
    private val questStatisticsDao: QuestStatisticsDao,
    private val countryStatisticsDao: CountryStatisticsDao,
    private val achievementGiver: AchievementGiver,
    private val userStore: UserStore,
    private val statisticsDownloader: StatisticsDownloader,
    private val countryBoundaries: FutureTask<CountryBoundaries>,
    @Named("QuestAliases") private val questAliases: List<Pair<String, String>>
){
    fun addOne(questType: String, position: LatLon) {
        updateDaysActive()

        questStatisticsDao.addOne(questType)
        getRealCountryCode(position)?.let { countryStatisticsDao.addOne(it) }

        achievementGiver.updateQuestTypeAchievements(questType)
    }

    fun subtractOne(questType: String, position: LatLon) {
        updateDaysActive()
        questStatisticsDao.subtractOne(questType)
        getRealCountryCode(position)?.let { countryStatisticsDao.subtractOne(it) }
    }

    private fun getRealCountryCode(position: LatLon): String? =
        countryBoundaries.get().getIds(position).firstOrNull {
            // skip non-countries
            it != "FX" && it != "EU" &&
            // skip country subdivisions (f.e. US-TX)
            !it.contains('-')
        }

    private fun updateDaysActive() {
        val now = ZonedDateTime.now(ZoneOffset.UTC)
        val lastUpdate = ZonedDateTime.ofInstant(userStore.lastStatisticsUpdate, ZoneOffset.UTC)
        userStore.lastStatisticsUpdate = now.toInstant()
        if (!lastUpdate.isSameDay(now)) {
            userStore.daysActive++
            achievementGiver.updateDaysActiveAchievements()
        }
    }

    fun updateFromBackend(userId: Long) {
        try {
            val statistics = statisticsDownloader.download(userId)
            val backendIsStillAnalyzing = statistics.isAnalyzing
            userStore.isSynchronizingStatistics = backendIsStillAnalyzing
            if (backendIsStillAnalyzing) {
                Log.i(TAG, "Backend is still analyzing changeset history")
                return
            }

            val backendDataIsUpToDate = statistics.lastUpdate.toEpochMilli() / 1000 >=
                userStore.lastStatisticsUpdate.toEpochMilli() / 1000
            if (!backendDataIsUpToDate) {
                Log.i(TAG, "Backend data is not up-to-date")
                return
            }

            val newQuestTypeStatistics = statistics.questTypes.toMutableMap()
            mergeQuestAliases(newQuestTypeStatistics)
            questStatisticsDao.replaceAll(newQuestTypeStatistics)
            countryStatisticsDao.replaceAll(statistics.countries)
            userStore.rank = statistics.rank
            userStore.daysActive = statistics.daysActive
            userStore.lastStatisticsUpdate = statistics.lastUpdate
            // when syncing statistics from server, any granted achievements should be
            // granted silently (without notification) because no user action was involved
            achievementGiver.updateAllAchievements(silent = true)
            achievementGiver.updateAchievementLinks()
        }  catch (e: Exception) {
            Log.w(TAG, "Unable to download statistics", e)
        }
    }

    private fun mergeQuestAliases(map: MutableMap<String, Int>)  {
        for ((oldName, newName) in questAliases) {
            val count = map[oldName]
            if (count != null) {
                map.remove(oldName)
                map[newName] = (map[newName] ?: 0) + count
            }
        }
    }

    companion object {
        private const val TAG = "StatisticsUpdater"
    }
}

private fun ZonedDateTime.isSameDay(other: ZonedDateTime) = year == other.year && dayOfYear == other.year
