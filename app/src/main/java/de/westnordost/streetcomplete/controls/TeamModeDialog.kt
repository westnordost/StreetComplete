package de.westnordost.streetcomplete.controls

import android.content.Context
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isGone
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.GridLayoutManager
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.controls.TeamModeColorCircleView.Companion.MAX_TEAM_SIZE
import de.westnordost.streetcomplete.databinding.DialogTeamModeBinding

/** Shows a dialog containing the team mode settings */
class TeamModeDialog(
    context: Context,
    onEnableTeamMode: (Int, Int) -> Unit
) : AlertDialog(context, R.style.Theme_Bubble_Dialog) {

    private var selectedTeamSize: Int? = null
    private var selectedIndexInTeam: Int? = null
    private val binding : DialogTeamModeBinding

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_team_mode, null)
        binding = DialogTeamModeBinding.bind(view)

        val adapter = TeamModeIndexSelectAdapter()
        adapter.listeners.add(object : TeamModeIndexSelectAdapter.OnSelectedIndexChangedListener {
            override fun onSelectedIndexChanged(index: Int?) {
                selectedIndexInTeam = index
                updateOkButtonEnablement()
            }
        })
        binding.colorCircles.adapter = adapter
        binding.colorCircles.layoutManager = GridLayoutManager(context, 3)

        binding.teamSizeInput.addTextChangedListener { editable ->
            selectedTeamSize = parseTeamSize(editable.toString())
            updateOkButtonEnablement()

            if (selectedTeamSize == null) {
                binding.introText.isGone = false
                binding.teamSizeHint.isGone = false
                binding.colorHint.isGone = true
                binding.colorCircles.isGone = true
            } else {
                binding.introText.isGone = true
                binding.teamSizeHint.isGone = true
                binding.colorHint.isGone = false
                binding.colorCircles.isGone = false
                adapter.count = selectedTeamSize!!
            }
        }

        setButton(BUTTON_POSITIVE, context.resources.getText(android.R.string.ok)) { _, _ ->
            onEnableTeamMode(selectedTeamSize!!, selectedIndexInTeam!!)
            dismiss()
        }

        setOnShowListener { updateOkButtonEnablement() }

        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        setView(binding.root)
    }

    private fun updateOkButtonEnablement() {
        getButton(BUTTON_POSITIVE)?.isEnabled = selectedTeamSize != null && selectedIndexInTeam != null
    }

    private fun parseTeamSize(string: String): Int? {
        return try {
            val number = Integer.parseInt(string)
            if (number in 2..MAX_TEAM_SIZE) number else null
        } catch (e: NumberFormatException) { null }
    }
}

