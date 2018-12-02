package com.simplemobiletools.contacts.pro.adapters

import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter
import com.simplemobiletools.contacts.pro.R
import com.simplemobiletools.contacts.pro.activities.MainActivity
import com.simplemobiletools.contacts.pro.extensions.config
import com.simplemobiletools.contacts.pro.fragments.MyViewPagerFragment
import com.simplemobiletools.contacts.pro.helpers.*

class ViewPagerAdapter(val activity: MainActivity) : PagerAdapter() {
    private val showTabs = activity.config.showTabs

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val layout = getFragment(position)
        val view = activity.layoutInflater.inflate(layout, container, false)
        container.addView(view)

        (view as MyViewPagerFragment).apply {
            setupFragment(activity)
        }

        return view
    }

    override fun destroyItem(container: ViewGroup, position: Int, item: Any) {
        container.removeView(item as View)
    }

    override fun getCount() = tabsList.filter { it and showTabs != 0 }.size

    override fun isViewFromObject(view: View, item: Any) = view == item

    private fun getFragment(position: Int): Int {
        val fragments = arrayListOf<Int>()
        if (showTabs and CONTACTS_TAB_MASK != 0) {
            fragments.add(R.layout.fragment_contacts)
        }

        if (showTabs and FAVORITES_TAB_MASK != 0) {
            fragments.add(R.layout.fragment_favorites)
        }

        if (showTabs and RECENTS_TAB_MASK != 0) {
            fragments.add(R.layout.fragment_recents)
        }

        if (showTabs and GROUPS_TAB_MASK != 0) {
            fragments.add(R.layout.fragment_groups)
        }

        if (showTabs and SHORTCUTS_TAB_MASK != 0) {
            fragments.add(R.layout.fragment_shortcuts)
        }

        return fragments[position]
    }
}
