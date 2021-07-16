package de.westnordost.streetcomplete.user

import android.os.Bundle
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.TAB_LABEL_VISIBILITY_LABELED
import com.google.android.material.tabs.TabLayout.TAB_LABEL_VISIBILITY_UNLABELED
import com.google.android.material.tabs.TabLayoutMediator
import de.westnordost.streetcomplete.HasTitle
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.databinding.FragmentUserBinding
import de.westnordost.streetcomplete.ktx.viewBinding

/** Shows the viewpager with the user profile, user statistics, achievements and links */
class UserFragment : Fragment(R.layout.fragment_user), HasTitle {

    override val title: String get() = getString(R.string.user_profile)

    private val binding by viewBinding(FragmentUserBinding::bind)

    /* --------------------------------------- Lifecycle --------------------------------------- */

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.viewPager.adapter = object : FragmentStateAdapter(requireActivity()) {
            override fun getItemCount() = PAGES.size
            override fun createFragment(position: Int) = PAGES[position].creator()
        }
        binding.tabLayout.tabGravity = TabLayout.GRAVITY_FILL
        binding.tabLayout.tabMode = TabLayout.MODE_FIXED
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab: TabLayout.Tab, position: Int ->
            val page = PAGES[position]
            tab.setIcon(page.icon)
            tab.setText(page.title)
            tab.tabLabelVisibility =
                if (resources.getBoolean(R.bool.show_user_tabs_text)) TAB_LABEL_VISIBILITY_LABELED
                else TAB_LABEL_VISIBILITY_UNLABELED
        }.attach()
    }

    private data class Page(@StringRes val title: Int, @DrawableRes val icon: Int, val creator: () -> Fragment)


    companion object {
        private val PAGES = listOf(
            Page(R.string.user_profile_title, R.drawable.ic_profile_white_48dp) {  ProfileFragment() },
            Page(R.string.user_quests_title, R.drawable.ic_star_white_48dp) { QuestStatisticsFragment() },
            Page(R.string.user_achievements_title, R.drawable.ic_achievements_white_48dp) { AchievementsFragment() },
            Page(R.string.user_links_title, R.drawable.ic_bookmarks_white_48dp) { LinksFragment() }
        )
    }
}
