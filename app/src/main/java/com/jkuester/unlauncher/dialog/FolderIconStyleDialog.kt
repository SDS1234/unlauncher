package com.jkuester.unlauncher.dialog

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.jkuester.unlauncher.datasource.DataRepository
import com.jkuester.unlauncher.datasource.setFolderIconStyle
import com.jkuester.unlauncher.datastore.proto.CorePreferences
import com.jkuester.unlauncher.datastore.proto.FolderIconStyle
import com.jkuester.unlauncher.fragment.WithFragmentLifecycle
import com.sduduzog.slimlauncher.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class FolderIconStyleDialog : DialogFragment() {
    @Inject @WithFragmentLifecycle
    lateinit var corePreferencesRepo: DataRepository<CorePreferences>

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = AlertDialog
        .Builder(context)
        .setTitle(R.string.choose_folder_icon_style_dialog_title)
        .setSingleChoiceItems(
            R.array.folder_icon_style_array,
            corePreferencesRepo.get().folderIconStyle.number,
            this::onSelection
        )
        .create()

    private fun onSelection(dialogInterface: DialogInterface, i: Int) = dialogInterface
        .dismiss()
        .also { corePreferencesRepo.updateAsync(setFolderIconStyle(FolderIconStyle.forNumber(i))) }
}
