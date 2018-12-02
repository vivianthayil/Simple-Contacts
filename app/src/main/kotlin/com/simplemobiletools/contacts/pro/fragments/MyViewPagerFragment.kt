package com.simplemobiletools.contacts.pro.fragments

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.SORT_BY_FIRST_NAME
import com.simplemobiletools.commons.helpers.SORT_BY_SURNAME
import com.simplemobiletools.contacts.pro.R
import com.simplemobiletools.contacts.pro.activities.GroupContactsActivity
import com.simplemobiletools.contacts.pro.activities.MainActivity
import com.simplemobiletools.contacts.pro.activities.SimpleActivity
import com.simplemobiletools.contacts.pro.adapters.ContactsAdapter
import com.simplemobiletools.contacts.pro.adapters.GroupsAdapter
import com.simplemobiletools.contacts.pro.adapters.RecentCallsAdapter
import com.simplemobiletools.contacts.pro.extensions.config
import com.simplemobiletools.contacts.pro.extensions.contactClicked
import com.simplemobiletools.contacts.pro.extensions.getVisibleContactSources
import com.simplemobiletools.contacts.pro.helpers.*
import com.simplemobiletools.contacts.pro.models.Contact
import com.simplemobiletools.contacts.pro.models.Group
import kotlinx.android.synthetic.main.fragment_layout.view.*

abstract class MyViewPagerFragment(context: Context, attributeSet: AttributeSet) : CoordinatorLayout(context, attributeSet) {
    protected var activity: MainActivity? = null
    protected var allContacts = ArrayList<Contact>()

    private var lastHashCode = 0
    private var contactsIgnoringSearch = ArrayList<Contact>()
    private lateinit var config: Config

    var skipHashComparing = false
    var forceListRedraw = false

    fun setupFragment(activity: MainActivity) {
        config = activity.config
        if (this.activity == null) {
            this.activity = activity
            fragment_fab.setOnClickListener {
                fabClicked()
            }

            fragment_placeholder_2.setOnClickListener {
                placeholderClicked()
            }

            fragment_placeholder_2.underlineText()
            updateViewStuff()

            when {
                this is FavoritesFragment -> {
                    fragment_placeholder.text = activity.getString(R.string.no_favorites)
                    fragment_placeholder_2.text = activity.getString(R.string.add_favorites)
                }
                this is GroupsFragment -> {
                    fragment_placeholder.text = activity.getString(R.string.no_group_created)
                    fragment_placeholder_2.text = activity.getString(R.string.create_group)
                }
                this is RecentsFragment -> {
                    fragment_fab.beGone()
                    fragment_placeholder.text = activity.getString(R.string.no_recent_calls_found)
                    fragment_placeholder_2.text = activity.getString(R.string.request_the_required_permissions)
                }
                this is ShortcutsFragment -> {
                    fragment_placeholder.text = activity.getString(R.string.no_favorites)
                    fragment_placeholder_2.text = activity.getString(R.string.add_favorites)
                }
            }
        }
    }

    fun textColorChanged(color: Int) {
        when {
            this is GroupsFragment -> (fragment_list.adapter as GroupsAdapter).updateTextColor(color)
            this is RecentsFragment -> (fragment_list.adapter as RecentCallsAdapter).updateTextColor(color)
            else -> (fragment_list.adapter as ContactsAdapter).apply {
                updateTextColor(color)
                initDrawables()
            }
        }
    }

    fun primaryColorChanged() {
        fragment_fastscroller.updatePrimaryColor()
        fragment_fastscroller.updateBubblePrimaryColor()
        (fragment_list.adapter as? ContactsAdapter)?.apply {
            adjustedPrimaryColor = context.getAdjustedPrimaryColor()
        }
    }

    fun startNameWithSurnameChanged(startNameWithSurname: Boolean) {
        if (this !is GroupsFragment && this !is RecentsFragment) {
            (fragment_list.adapter as? ContactsAdapter)?.apply {
                config.sorting = if (startNameWithSurname) SORT_BY_SURNAME else SORT_BY_FIRST_NAME
                this@MyViewPagerFragment.activity!!.refreshContacts(CONTACTS_TAB_MASK or FAVORITES_TAB_MASK)
            }
        }
    }

    fun refreshContacts(contacts: ArrayList<Contact>) {
        if ((config.showTabs and CONTACTS_TAB_MASK == 0 && this is ContactsFragment) ||
                (config.showTabs and FAVORITES_TAB_MASK == 0 && this is FavoritesFragment) ||
                (config.showTabs and RECENTS_TAB_MASK == 0 && this is RecentsFragment) ||
                (config.showTabs and GROUPS_TAB_MASK == 0 && this is GroupsFragment)) {
            return
        }

        if (config.lastUsedContactSource.isEmpty()) {
            val grouped = contacts.asSequence().groupBy { it.source }.maxWith(compareBy { it.value.size })
            config.lastUsedContactSource = grouped?.key ?: ""
        }

        allContacts = contacts

        val filtered = when {
            this is GroupsFragment -> contacts
            this is FavoritesFragment -> contacts.filter { it.starred == 1 } as ArrayList<Contact>
            this is RecentsFragment -> ArrayList()
            else -> {
                val contactSources = activity!!.getVisibleContactSources()
                contacts.filter { contactSources.contains(it.source) } as ArrayList<Contact>
            }
        }

        if (filtered.hashCode() != lastHashCode || skipHashComparing) {
            skipHashComparing = false
            lastHashCode = filtered.hashCode()
            activity?.runOnUiThread {
                setupContacts(filtered)
            }
        }
    }

    private fun setupContacts(contacts: ArrayList<Contact>) {
        if (this is GroupsFragment) {
            setupGroupsAdapter(contacts)
        } else if (this !is RecentsFragment) {
            setupContactsFavoritesAdapter(contacts)
        }

        if (this is ContactsFragment || this is FavoritesFragment || this is ShortcutsFragment) {
            contactsIgnoringSearch = (fragment_list?.adapter as? ContactsAdapter)?.contactItems ?: ArrayList()
        }
    }

    private fun setupGroupsAdapter(contacts: ArrayList<Contact>) {
        ContactsHelper(activity!!).getStoredGroups {
            var storedGroups = it
            contacts.forEach {
                it.groups.forEach {
                    val group = it
                    val storedGroup = storedGroups.firstOrNull { it.id == group.id }
                    storedGroup?.addContact()
                }
            }

            storedGroups = storedGroups.asSequence().sortedWith(compareBy { it.title.normalizeString() }).toMutableList() as ArrayList<Group>

            fragment_placeholder_2.beVisibleIf(storedGroups.isEmpty())
            fragment_placeholder.beVisibleIf(storedGroups.isEmpty())
            fragment_list.beVisibleIf(storedGroups.isNotEmpty())

            val currAdapter = fragment_list.adapter
            if (currAdapter == null) {
                GroupsAdapter(activity as SimpleActivity, storedGroups, activity, fragment_list, fragment_fastscroller) {
                    Intent(activity, GroupContactsActivity::class.java).apply {
                        putExtra(GROUP, it as Group)
                        activity!!.startActivity(this)
                    }
                }.apply {
                    addVerticalDividers(true)
                    fragment_list.adapter = this
                }

                fragment_fastscroller.setScrollToY(0)
                fragment_fastscroller.setViews(fragment_list) {
                    val item = (fragment_list.adapter as GroupsAdapter).groups.getOrNull(it)
                    fragment_fastscroller.updateBubbleText(item?.getBubbleText() ?: "")
                }
            } else {
                (currAdapter as GroupsAdapter).apply {
                    showContactThumbnails = activity.config.showContactThumbnails
                    updateItems(storedGroups)
                }
            }
        }
    }

    private fun setupContactsFavoritesAdapter(contacts: ArrayList<Contact>) {
        setupViewVisibility(contacts)
        val currAdapter = fragment_list.adapter
        if (currAdapter == null || forceListRedraw) {
            forceListRedraw = false
            val location = if (this is FavoritesFragment) LOCATION_FAVORITES_TAB else LOCATION_CONTACTS_TAB
            ContactsAdapter(activity as SimpleActivity, contacts, activity, location, null, fragment_list, fragment_fastscroller) {
                activity?.contactClicked(it as Contact)
            }.apply {
                addVerticalDividers(true)
                fragment_list.adapter = this
            }

            fragment_fastscroller.setScrollToY(0)
            fragment_fastscroller.setViews(fragment_list) {
                val item = (fragment_list.adapter as ContactsAdapter).contactItems.getOrNull(it)
                fragment_fastscroller.updateBubbleText(item?.getBubbleText() ?: "")
            }
        } else {
            (currAdapter as ContactsAdapter).apply {
                startNameWithSurname = config.startNameWithSurname
                showPhoneNumbers = config.showPhoneNumbers
                showContactThumbnails = config.showContactThumbnails
                updateItems(contacts)
            }
        }
    }

    fun showContactThumbnailsChanged(showThumbnails: Boolean) {
        if (this is GroupsFragment) {
            (fragment_list.adapter as? GroupsAdapter)?.apply {
                showContactThumbnails = showThumbnails
                notifyDataSetChanged()
            }
        } else if (this !is RecentsFragment) {
            (fragment_list.adapter as? ContactsAdapter)?.apply {
                showContactThumbnails = showThumbnails
                notifyDataSetChanged()
            }
        }
    }

    fun onActivityResume() {
        updateViewStuff()
    }

    fun finishActMode() {
        (fragment_list.adapter as? MyRecyclerViewAdapter)?.finishActMode()
    }

    fun onSearchQueryChanged(text: String) {
        val shouldNormalize = text.normalizeString() == text
        (fragment_list.adapter as? ContactsAdapter)?.apply {
            val filtered = contactsIgnoringSearch.filter {
                getProperText(it.getNameToDisplay(), shouldNormalize).contains(text, true) ||
                        getProperText(it.nickname, shouldNormalize).contains(text, true) ||
                        it.doesContainPhoneNumber(text) ||
                        it.emails.any { it.value.contains(text, true) } ||
                        it.addresses.any { getProperText(it.value, shouldNormalize).contains(text, true) } ||
                        it.IMs.any { it.value.contains(text, true) } ||
                        getProperText(it.notes, shouldNormalize).contains(text, true) ||
                        getProperText(it.organization.company, shouldNormalize).contains(text, true) ||
                        getProperText(it.organization.jobPosition, shouldNormalize).contains(text, true) ||
                        it.websites.any { it.contains(text, true) }
            } as ArrayList

            filtered.sortBy { !getProperText(it.getNameToDisplay(), shouldNormalize).startsWith(text, true) }

            if (filtered.isEmpty() && this@MyViewPagerFragment is FavoritesFragment) {
                fragment_placeholder.text = activity.getString(R.string.no_items_found)
            }

            fragment_placeholder.beVisibleIf(filtered.isEmpty())
            updateItems(filtered, text.normalizeString())
        }
    }

    private fun getProperText(text: String, shouldNormalize: Boolean) = if (shouldNormalize) text.normalizeString() else text

    fun onSearchOpened() {
        contactsIgnoringSearch = (fragment_list?.adapter as? ContactsAdapter)?.contactItems ?: ArrayList()
    }

    fun onSearchClosed() {
        (fragment_list.adapter as? ContactsAdapter)?.updateItems(contactsIgnoringSearch)
        setupViewVisibility(contactsIgnoringSearch)

        if (this is FavoritesFragment) {
            fragment_placeholder.text = activity?.getString(R.string.no_favorites)
        }
    }

    private fun updateViewStuff() {
        context.updateTextColors(fragment_wrapper.parent as ViewGroup)
        fragment_fastscroller.updateBubbleColors()
        fragment_fastscroller.allowBubbleDisplay = config.showInfoBubble
        fragment_placeholder_2.setTextColor(context.getAdjustedPrimaryColor())
    }

    private fun setupViewVisibility(contacts: ArrayList<Contact>) {
        fragment_placeholder_2.beVisibleIf(contacts.isEmpty())
        fragment_placeholder.beVisibleIf(contacts.isEmpty())
        fragment_list.beVisibleIf(contacts.isNotEmpty())
    }

    abstract fun fabClicked()

    abstract fun placeholderClicked()
}
