package de.westnordost.streetcomplete.controls

import android.net.ConnectivityManager
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.content.getSystemService
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import de.westnordost.streetcomplete.Injector
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.download.DownloadController
import de.westnordost.streetcomplete.data.osm.mapdata.BoundingBox
import de.westnordost.streetcomplete.data.visiblequests.TeamModeQuestFilter
import de.westnordost.streetcomplete.databinding.FragmentMainMenuButtonBinding
import de.westnordost.streetcomplete.ktx.popIn
import de.westnordost.streetcomplete.ktx.popOut
import de.westnordost.streetcomplete.ktx.toast
import de.westnordost.streetcomplete.ktx.viewBinding
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Fragment that shows the main menu button and manages its logic */
class MainMenuButtonFragment : Fragment(R.layout.fragment_main_menu_button) {

    @Inject internal lateinit var teamModeQuestFilter: TeamModeQuestFilter
    @Inject internal lateinit var downloadController: DownloadController

    interface Listener {
        fun getDownloadArea(): BoundingBox?
    }

    private val listener: Listener? get() = parentFragment as? Listener ?: activity as? Listener

    private val binding by viewBinding(FragmentMainMenuButtonBinding::bind)

    private val teamModeListener = object : TeamModeQuestFilter.TeamModeChangeListener {
        override fun onTeamModeChanged(enabled: Boolean) { lifecycleScope.launch { setTeamMode(enabled) } }
    }

    /* --------------------------------------- Lifecycle ---------------------------------------- */

    init {
        Injector.applicationComponent.inject(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.mainMenuButton.setOnClickListener { onClickMainMenu() }
    }

    override fun onStart() {
        super.onStart()

        // in onStart and not onViewCreated because the notification that team mode is active should
        // pop in always when the app comes back from the background again
        if (teamModeQuestFilter.isEnabled) {
            setTeamMode(true)
        }

        teamModeQuestFilter.addListener(teamModeListener)
    }

    override fun onStop() {
        super.onStop()
        teamModeQuestFilter.removeListener(teamModeListener)
    }

    /* ------------------------------------------------------------------------------------------ */

    private fun setTeamMode(enabled: Boolean) {
        if (enabled) {
            context?.toast(R.string.team_mode_active)
            binding.teamModeColorCircle?.popIn()
            binding.teamModeColorCircle?.setIndexInTeam(teamModeQuestFilter.indexInTeam)
        } else {
            context?.toast(R.string.team_mode_deactivated)
            binding.teamModeColorCircle?.popOut()
        }
    }

    internal fun onClickMainMenu() {
        MainMenuDialog(
            requireContext(),
            if (teamModeQuestFilter.isEnabled) teamModeQuestFilter.indexInTeam else null,
            this::onClickDownload,
            teamModeQuestFilter::enableTeamMode,
            teamModeQuestFilter::disableTeamMode
        ).show()
    }

    /* ------------------------------------ Download Button  ------------------------------------ */

    private fun onClickDownload() {
        if (isConnected()) downloadDisplayedArea()
        else context?.toast(R.string.offline)
    }

    private fun isConnected(): Boolean {
        val connectivityManager = context?.getSystemService<ConnectivityManager>()
        val activeNetworkInfo = connectivityManager?.activeNetworkInfo
        return activeNetworkInfo != null && activeNetworkInfo.isConnected
    }

    private fun downloadDisplayedArea() {
        val downloadArea = listener?.getDownloadArea() ?: return

        if (downloadController.isPriorityDownloadInProgress) {
            context?.let {
                AlertDialog.Builder(it)
                    .setMessage(R.string.confirmation_cancel_prev_download_title)
                    .setPositiveButton(R.string.confirmation_cancel_prev_download_confirmed) { _, _ ->
                        downloadController.download(downloadArea, true)
                    }
                    .setNegativeButton(R.string.confirmation_cancel_prev_download_cancel, null)
                    .show()
            }
        } else {
            downloadController.download(downloadArea, true)
        }
    }
}
