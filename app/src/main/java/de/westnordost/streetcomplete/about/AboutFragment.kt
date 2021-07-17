package de.westnordost.streetcomplete.about

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.core.widget.TextViewCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import de.westnordost.streetcomplete.BuildConfig
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.ktx.tryStartActivity
import de.westnordost.streetcomplete.util.sendEmail
import de.westnordost.streetcomplete.view.ListAdapter
import kotlinx.android.synthetic.main.cell_labeled_icon_select_right.view.*
import java.util.Locale

/** Shows the about screen */
class AboutFragment : PreferenceFragmentCompat() {

    interface Listener {
        fun onClickedChangelog()
        fun onClickedCredits()
        fun onClickedPrivacyStatement()
    }
    private val listener: Listener? get() = parentFragment as? Listener ?: activity as? Listener

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.about)

        findPreference<Preference>("version")?.summary = getString(R.string.about_summary_current_version, "v" + BuildConfig.VERSION_NAME)
        findPreference<Preference>("version")?.setOnPreferenceClickListener {
            listener?.onClickedChangelog()
            true
        }

        findPreference<Preference>("license")?.setOnPreferenceClickListener {
            openUrl("https://www.gnu.org/licenses/gpl-3.0.html")
        }

        findPreference<Preference>("authors")?.setOnPreferenceClickListener {
            listener?.onClickedCredits()
            true
        }

        findPreference<Preference>("privacy")?.setOnPreferenceClickListener {
            listener?.onClickedPrivacyStatement()
            true
        }

        findPreference<Preference>("repository")?.setOnPreferenceClickListener {
            openUrl("https://github.com/streetcomplete/StreetComplete/")
        }

        findPreference<Preference>("faq")?.setOnPreferenceClickListener {
            openUrl("https://wiki.openstreetmap.org/wiki/StreetComplete/FAQ")
        }

        findPreference<Preference>("translate")?.summary = resources.getString(
            R.string.about_description_translate,
            Locale.getDefault().displayLanguage,
            resources.getInteger(R.integer.translation_completeness)
        )
        findPreference<Preference>("translate")?.setOnPreferenceClickListener {
            openUrl("https://poeditor.com/join/project/IE4GC127Ki")
        }

        findPreference<Preference>("report_error")?.setOnPreferenceClickListener {
            openUrl("https://github.com/streetcomplete/StreetComplete/issues/")
        }

        findPreference<Preference>("email_feedback")?.setOnPreferenceClickListener {
            sendFeedbackEmail()
        }

        findPreference<Preference>("rate")?.isVisible = isInstalledViaGooglePlay()
        findPreference<Preference>("rate")?.setOnPreferenceClickListener {
            openGooglePlayStorePage()
        }

        findPreference<Preference>("donate")?.setOnPreferenceClickListener {
            showDonateDialog()
            true
        }
    }

    override fun onStart() {
        super.onStart()
        activity?.setTitle(R.string.action_about2)
    }

    private fun isInstalledViaGooglePlay(): Boolean {
        val appCtx = context?.applicationContext ?: return false
        val installerPackageName = appCtx.packageManager.getInstallerPackageName(appCtx.packageName)
        return installerPackageName == "com.android.vending"
    }

    private fun openUrl(url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        tryStartActivity(intent)
        return true
    }

    private fun sendFeedbackEmail(): Boolean {
        sendEmail(requireActivity(),"osm@westnordost.de", "Feedback")
        return true
    }

    private fun openGooglePlayStorePage(): Boolean {
        val appPackageName = context?.applicationContext?.packageName ?: return false
        return openUrl("market://details?id=$appPackageName")
    }

    private fun showDonateDialog() {
        val ctx = context ?: return

        val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_donate, null)
        val listView = view.findViewById<RecyclerView>(R.id.donateList)
        listView.adapter = DonationPlatformAdapter(DonationPlatform.values().asList())

        AlertDialog.Builder(ctx)
                .setTitle(R.string.about_title_donate)
                .setView(view)
                .show()
    }

    private inner class DonationPlatformAdapter(list: List<DonationPlatform>): ListAdapter<DonationPlatform>(list) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.cell_labeled_icon_select_right, parent, false))

        inner class ViewHolder(itemView: View) : ListAdapter.ViewHolder<DonationPlatform>(itemView) {
            override fun onBind(with: DonationPlatform) {
                itemView.imageView.setImageResource(with.iconId)
                itemView.textView.text = with.title
                itemView.setOnClickListener { openUrl(with.url) }
                TextViewCompat.setTextAppearance(itemView.textView, R.style.TextAppearance_Title)
            }
        }
    }
}

private enum class DonationPlatform(val title: String, @DrawableRes val iconId: Int, val url: String) {
    GITHUB("GitHub Sponsors", R.drawable.ic_github, "https://github.com/sponsors/westnordost"),
    LIBERAPAY("Liberapay", R.drawable.ic_liberapay, "https://liberapay.com/westnordost"),
    PATREON("Patreon", R.drawable.ic_patreon, "https://patreon.com/westnordost")
}
