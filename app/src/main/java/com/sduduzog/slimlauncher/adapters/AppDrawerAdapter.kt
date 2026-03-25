package com.sduduzog.slimlauncher.adapters

import android.annotation.SuppressLint
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import com.jkuester.unlauncher.datasource.DataRepository
import com.jkuester.unlauncher.datastore.proto.CorePreferences
import com.jkuester.unlauncher.datastore.proto.UnlauncherApp
import com.jkuester.unlauncher.datastore.proto.UnlauncherApps
import com.jkuester.unlauncher.datastore.proto.UnlauncherFolder
import com.sduduzog.slimlauncher.R
import com.sduduzog.slimlauncher.ui.main.HomeFragment
import com.sduduzog.slimlauncher.utils.firstUppercase
import com.sduduzog.slimlauncher.utils.gravity
import java.util.Locale

private const val FOLDER_PREFIX = "\uD83D\uDCC1 " // 📁
private const val FOLDER_EXPANDED_PREFIX = "\uD83D\uDCC2 " // 📂
private const val FOLDER_ITEM_INDENT = "  "

class AppDrawerAdapter(
    private val listener: HomeFragment.AppDrawerListener,
    lifecycleOwner: LifecycleOwner,
    unlauncherAppsRepo: DataRepository<UnlauncherApps>,
    private val corePreferencesRepo: DataRepository<CorePreferences>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val workAppPrefix = "\uD83C\uDD46 " // Unicode for boxed w
    private val regex = Regex("[!@#\$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/? ]")
    private var apps: List<UnlauncherApp> = listOf()
    private var folders: List<UnlauncherFolder> = listOf()
    private var filteredApps: List<AppDrawerRow> = listOf()
    private var gravity = 3
    private val expandedFolderIds = mutableSetOf<String>()

    init {
        unlauncherAppsRepo.observe { unlauncherApps ->
            apps = unlauncherApps.appsList
            folders = unlauncherApps.foldersList
            val currentFolderIds = folders.map { it.id }.toSet()
            expandedFolderIds.retainAll(currentFolderIds)
            updateFilteredApps()
        }
        corePreferencesRepo.observe { corePrefs ->
            gravity = corePrefs.alignmentFormat.gravity()
            updateFilteredApps()
        }
    }

    override fun getItemCount(): Int = filteredApps.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val drawerRow = filteredApps[position]) {
            is AppDrawerRow.Item -> {
                val unlauncherApp = drawerRow.app
                (holder as ItemViewHolder).bind(unlauncherApp)
                holder.itemView.setOnClickListener {
                    listener.onAppClicked(unlauncherApp)
                }
                holder.itemView.setOnLongClickListener {
                    listener.onAppLongClicked(unlauncherApp, it)
                }
            }

            is AppDrawerRow.Header -> (holder as HeaderViewHolder).bind(drawerRow.letter)

            is AppDrawerRow.FolderRow -> {
                val folder = drawerRow.folder
                (holder as FolderViewHolder).bind(folder, drawerRow.isExpanded)
                holder.itemView.setOnClickListener {
                    toggleFolder(folder.id)
                }
                holder.itemView.setOnLongClickListener {
                    listener.onFolderLongClicked(folder, it)
                }
            }

            is AppDrawerRow.FolderItem -> {
                val unlauncherApp = drawerRow.app
                (holder as FolderItemViewHolder).bind(unlauncherApp)
                holder.itemView.setOnClickListener {
                    listener.onAppClicked(unlauncherApp)
                }
                holder.itemView.setOnLongClickListener {
                    listener.onAppLongClicked(unlauncherApp, it)
                }
            }
        }
    }

    fun getFirstApp(): UnlauncherApp? = filteredApps
        .firstOrNull { it is AppDrawerRow.Item || it is AppDrawerRow.FolderItem }
        ?.let { row ->
            when (row) {
                is AppDrawerRow.Item -> row.app
                is AppDrawerRow.FolderItem -> row.app
                else -> null
            }
        }

    override fun getItemViewType(position: Int): Int = filteredApps[position].rowType.ordinal

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val rowType = RowType.entries.getOrNull(viewType)
            ?: throw IllegalArgumentException("Unknown viewType: $viewType")
        return when (rowType) {
            RowType.App -> ItemViewHolder(
                inflater.inflate(R.layout.app_list_item, parent, false)
            )

            RowType.Header -> HeaderViewHolder(
                inflater.inflate(R.layout.app_drawer_fragment_header_item, parent, false)
            )

            RowType.Folder -> FolderViewHolder(
                inflater.inflate(R.layout.app_list_item, parent, false)
            )

            RowType.FolderApp -> FolderItemViewHolder(
                inflater.inflate(R.layout.app_list_item, parent, false)
            )
        }
    }

    private fun toggleFolder(folderId: String) {
        if (expandedFolderIds.contains(folderId)) {
            expandedFolderIds.remove(folderId)
        } else {
            expandedFolderIds.add(folderId)
        }
        updateFilteredApps()
    }

    private fun onlyFirstStringStartsWith(first: String, second: String, query: String): Boolean =
        first.startsWith(query, true) and !second.startsWith(query, true)

    fun setAppFilter(query: String = "") {
        val filterQuery = regex.replace(query, "")
        updateFilteredApps(filterQuery)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateFilteredApps(filterQuery: String = "") {
        val corePreferences = corePreferencesRepo.get()
        val showDrawerHeadings = corePreferences.showDrawerHeadings
        val searchAllApps = corePreferences.searchAllAppsInDrawer && filterQuery != ""

        val displayableApps = apps
            .filter { app ->
                (app.displayInDrawer || searchAllApps) &&
                    regex.replace(app.displayName, "")
                        .contains(filterQuery, ignoreCase = true)
            }

        val isFiltering = filterQuery != ""
        val skipHeadings = !showDrawerHeadings || isFiltering

        val updatedApps = if (isFiltering) {
            // When filtering, show all matching apps flat (ignore folder grouping)
            displayableApps
                .sortedWith { a, b ->
                    when {
                        onlyFirstStringStartsWith(a.displayName, b.displayName, filterQuery) -> -1
                        onlyFirstStringStartsWith(b.displayName, a.displayName, filterQuery) -> 1
                        else -> a.displayName.compareTo(b.displayName, true)
                    }
                }.map { AppDrawerRow.Item(it) }
        } else if (skipHeadings) {
            buildFlatListWithFolders(displayableApps)
        } else {
            buildHeadedListWithFolders(displayableApps)
        }

        if (updatedApps != filteredApps) {
            filteredApps = updatedApps
            notifyDataSetChanged()
        }
    }

    private fun groupAppsByFolder(
        displayableApps: List<UnlauncherApp>
    ): Pair<Map<String, List<UnlauncherApp>>, List<UnlauncherApp>> {
        val validFolderIds = folders.map { it.id }.toSet()
        val (appsWithKnownFolderId, nonFolderApps) = displayableApps.partition { app ->
            app.hasFolderId() && validFolderIds.contains(app.folderId)
        }
        return appsWithKnownFolderId
            .groupBy { it.folderId }
            .mapValues { (_, apps) ->
                apps.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
            } to nonFolderApps
    }

    private fun buildFlatListWithFolders(displayableApps: List<UnlauncherApp>): List<AppDrawerRow> {
        val (appsByFolderId, nonFolderApps) = groupAppsByFolder(displayableApps)

        val sortedNonFolderApps = nonFolderApps
            .map { Pair(it.displayName.uppercase(Locale.ROOT), AppDrawerRow.Item(it)) }
            .sortedBy { it.first }

        val sortedFolderTriples = folders
            .map { folder ->
                Triple(folder.displayName.uppercase(Locale.ROOT), folder, appsByFolderId[folder.id] ?: emptyList())
            }
            .sortedBy { it.first }

        return mergeAppsAndFolders(sortedNonFolderApps, sortedFolderTriples)
    }

    private fun buildHeadedListWithFolders(displayableApps: List<UnlauncherApp>): List<AppDrawerRow> {
        val (appsByFolderId, nonFolderApps) = groupAppsByFolder(displayableApps)

        val foldersByFirstLetter = folders.map { folder ->
            Triple(
                folder.displayName.firstUppercase(),
                folder,
                appsByFolderId[folder.id] ?: emptyList()
            )
        }.groupBy { it.first }

        val appsByFirstLetter = nonFolderApps.groupBy { app ->
            if (app.displayName.startsWith(workAppPrefix)) {
                workAppPrefix
            } else {
                app.displayName.firstUppercase()
            }
        }

        val allLetters = (appsByFirstLetter.keys + foldersByFirstLetter.keys).toSortedSet()

        return allLetters.flatMap { letter ->
            val letterHeader = listOf(AppDrawerRow.Header(letter))
            val letterFolderRows = (foldersByFirstLetter[letter] ?: emptyList())
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.second.displayName })
                .flatMap { (_, folder, folderApps) ->
                    val isExpanded = expandedFolderIds.contains(folder.id)
                    listOf(AppDrawerRow.FolderRow(folder, isExpanded)) +
                        if (isExpanded) {
                            folderApps.map { AppDrawerRow.FolderItem(it) }
                        } else {
                            emptyList()
                        }
                }
            val letterAppRows = (appsByFirstLetter[letter] ?: emptyList())
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
                .map { AppDrawerRow.Item(it) }

            letterHeader + letterFolderRows + letterAppRows
        }
    }

    private fun mergeAppsAndFolders(
        nonFolderAppRows: List<Pair<String, AppDrawerRow.Item>>,
        sortedFolderTriples: List<Triple<String, UnlauncherFolder, List<UnlauncherApp>>>
    ): List<AppDrawerRow> {
        val result = mutableListOf<AppDrawerRow>()
        var appIdx = 0
        var folderIdx = 0

        while (appIdx < nonFolderAppRows.size || folderIdx < sortedFolderTriples.size) {
            val appKey = nonFolderAppRows.getOrNull(appIdx)?.first
            val folderKey = sortedFolderTriples.getOrNull(folderIdx)?.first

            if (appKey == null || (folderKey != null && folderKey <= appKey)) {
                val (_, folder, folderApps) = sortedFolderTriples[folderIdx]
                val isExpanded = expandedFolderIds.contains(folder.id)
                result.add(AppDrawerRow.FolderRow(folder, isExpanded))
                if (isExpanded) {
                    folderApps.forEach { result.add(AppDrawerRow.FolderItem(it)) }
                }
                folderIdx++
            } else {
                result.add(nonFolderAppRows[appIdx].second)
                appIdx++
            }
        }
        return result
    }

    val searchBoxListener: TextWatcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            // Do nothing
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            // Do nothing
        }

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            setAppFilter(s.toString())
        }
    }

    inner class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        val item: TextView = itemView.findViewById(R.id.app_list_item_name)

        override fun toString(): String = "${super.toString()} '${item.text}'"

        fun bind(item: UnlauncherApp) {
            this.item.text = item.displayName
            this.item.gravity = gravity
        }
    }

    inner class HeaderViewHolder(headerView: View) : RecyclerView.ViewHolder(headerView) {
        private val header: TextView = itemView.findViewById(R.id.aa_list_header_letter)

        override fun toString(): String = "${super.toString()} '${header.text}'"

        fun bind(letter: String) {
            header.text = letter
        }
    }

    inner class FolderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val item: TextView = itemView.findViewById(R.id.app_list_item_name)

        override fun toString(): String = "${super.toString()} '${item.text}'"

        @SuppressLint("SetTextI18n")
        fun bind(folder: UnlauncherFolder, isExpanded: Boolean) {
            val prefix = if (isExpanded) FOLDER_EXPANDED_PREFIX else FOLDER_PREFIX
            item.text = "$prefix${folder.displayName}"
            item.gravity = gravity
        }
    }

    inner class FolderItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val item: TextView = itemView.findViewById(R.id.app_list_item_name)

        override fun toString(): String = "${super.toString()} '${item.text}'"

        @SuppressLint("SetTextI18n")
        fun bind(app: UnlauncherApp) {
            item.text = "$FOLDER_ITEM_INDENT${app.displayName}"
            item.gravity = gravity
        }
    }
}

enum class RowType {
    Header,
    App,
    Folder,
    FolderApp
}

sealed class AppDrawerRow(val rowType: RowType) {
    data class Item(val app: UnlauncherApp) : AppDrawerRow(RowType.App)

    data class Header(val letter: String) : AppDrawerRow(RowType.Header)

    data class FolderRow(val folder: UnlauncherFolder, val isExpanded: Boolean) : AppDrawerRow(RowType.Folder)

    data class FolderItem(val app: UnlauncherApp) : AppDrawerRow(RowType.FolderApp)
}
