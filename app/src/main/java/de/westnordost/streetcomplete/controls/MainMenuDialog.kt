package de.westnordost.streetcomplete.controls

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.core.view.doOnPreDraw
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.about.AboutActivity
import de.westnordost.streetcomplete.databinding.DialogMainMenuBinding
import de.westnordost.streetcomplete.settings.SettingsActivity
import de.westnordost.streetcomplete.user.UserActivity

/** Shows a dialog containing the main menu items */
class MainMenuDialog(
    context: Context,
    indexInTeam: Int?,
    onClickDownload: () -> Unit,
    onEnableTeamMode: (Int, Int) -> Unit,
    onDisableTeamMode: () -> Unit,
) : AlertDialog(context, R.style.Theme_Bubble_Dialog) {
    init {
        val binding = DialogMainMenuBinding.bind(LayoutInflater.from(context).inflate(R.layout.dialog_main_menu, null))

        binding.profileButton.setOnClickListener {
            val intent = Intent(context, UserActivity::class.java)
            context.startActivity(intent)
            dismiss()
        }
        binding.enableTeamModeButton.setOnClickListener {
            TeamModeDialog(context, onEnableTeamMode).show()
            dismiss()
        }
        binding.disableTeamModeButton.setOnClickListener {
            onDisableTeamMode()
            dismiss()
        }
        binding.settingsButton.setOnClickListener {
            val intent = Intent(context, SettingsActivity::class.java)
            context.startActivity(intent)
            dismiss()
        }
        binding.aboutButton.setOnClickListener {
            val intent = Intent(context, AboutActivity::class.java)
            context.startActivity(intent)
            dismiss()
        }
        binding.downloadButton.setOnClickListener {
            onClickDownload()
            dismiss()
        }

        if (indexInTeam != null) {
            binding.teamModeColorCircle.setIndexInTeam(indexInTeam)
            binding.bigMenuItemsContainer.removeView(binding.enableTeamModeButton)
        } else {
            binding.bigMenuItemsContainer.removeView(binding.disableTeamModeButton)
        }

        binding.root.doOnPreDraw {
            binding.bigMenuItemsContainer.columnCount = binding.root.width / binding.profileButton.width
        }

        setView(binding.root)
    }
}
