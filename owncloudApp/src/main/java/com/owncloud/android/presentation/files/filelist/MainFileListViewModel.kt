/**
 * ownCloud Android client application
 *
 * @author Fernando Sanz Velasco
 * @author Jose Antonio Barros Ramos
 * Copyright (C) 2021 ownCloud GmbH.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.owncloud.android.presentation.files.filelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.owncloud.android.data.preferences.datasources.SharedPreferencesProvider
import com.owncloud.android.datamodel.FileDataStorageManager.Companion.ROOT_PARENT_ID
import com.owncloud.android.domain.availableoffline.usecases.GetFilesAvailableOfflineFromAccountAsStreamUseCase
import com.owncloud.android.domain.files.model.FileListOption
import com.owncloud.android.domain.files.model.OCFile
import com.owncloud.android.domain.files.model.OCFile.Companion.ROOT_PATH
import com.owncloud.android.domain.files.model.OCFileWithSyncInfo
import com.owncloud.android.domain.files.usecases.GetFileByIdUseCase
import com.owncloud.android.domain.files.usecases.GetFileByRemotePathUseCase
import com.owncloud.android.domain.files.usecases.GetFolderContentAsStreamUseCase
import com.owncloud.android.domain.files.usecases.GetSharedByLinkForAccountAsStreamUseCase
import com.owncloud.android.domain.files.usecases.SortFilesWithSyncInfoUseCase
import com.owncloud.android.presentation.files.SortOrder
import com.owncloud.android.presentation.files.SortOrder.Companion.PREF_FILE_LIST_SORT_ORDER
import com.owncloud.android.presentation.files.SortType
import com.owncloud.android.presentation.files.SortType.Companion.PREF_FILE_LIST_SORT_TYPE
import com.owncloud.android.presentation.settings.advanced.SettingsAdvancedFragment.Companion.PREF_SHOW_HIDDEN_FILES
import com.owncloud.android.providers.CoroutinesDispatcherProvider
import com.owncloud.android.usecases.synchronization.SynchronizeFolderUseCase
import com.owncloud.android.usecases.synchronization.SynchronizeFolderUseCase.SyncFolderMode.SYNC_CONTENTS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.owncloud.android.domain.files.usecases.SortType.Companion as SortTypeDomain

class MainFileListViewModel(
    private val getFolderContentAsStreamUseCase: GetFolderContentAsStreamUseCase,
    private val getSharedByLinkForAccountAsStreamUseCase: GetSharedByLinkForAccountAsStreamUseCase,
    private val getFilesAvailableOfflineFromAccountAsStreamUseCase: GetFilesAvailableOfflineFromAccountAsStreamUseCase,
    private val getFileByIdUseCase: GetFileByIdUseCase,
    private val getFileByRemotePathUseCase: GetFileByRemotePathUseCase,
    private val sortFilesWithSyncInfoUseCase: SortFilesWithSyncInfoUseCase,
    private val coroutinesDispatcherProvider: CoroutinesDispatcherProvider,
    private val sharedPreferencesProvider: SharedPreferencesProvider,
    private val synchronizeFolderUseCase: SynchronizeFolderUseCase,
    private val workManager: WorkManager,
    accountNameParam: String,
    initialFolderToDisplay: OCFile,
) : ViewModel() {

    private val showHiddenFiles: Boolean = sharedPreferencesProvider.getBoolean(PREF_SHOW_HIDDEN_FILES, false)

    private val accountName: MutableStateFlow<String> = MutableStateFlow(accountNameParam)
    val currentFolderDisplayed: MutableStateFlow<OCFile> = MutableStateFlow(initialFolderToDisplay)
    val fileListOption: MutableStateFlow<FileListOption> = MutableStateFlow(FileListOption.ALL_FILES)
    private val searchFilter: MutableStateFlow<String> = MutableStateFlow("")
    private val sortTypeAndOrder = MutableStateFlow(Pair(SortType.SORT_TYPE_BY_NAME, SortOrder.SORT_ORDER_ASCENDING))

    /** File list ui state combines the other fields and generate a new state whenever any of them changes */
    val fileListUiState: StateFlow<FileListUiState> =
        combine(
            currentFolderDisplayed,
            accountName,
            fileListOption,
            searchFilter,
            sortTypeAndOrder,
        ) { currentFolderDisplayed, accountName, fileListOption, searchFilter, sortTypeAndOrder ->
            composeFileListUiStateForThisParams(
                currentFolderDisplayed = currentFolderDisplayed,
                accountName = accountName,
                fileListOption = fileListOption,
                searchFilter = searchFilter,
                sortTypeAndOrder = sortTypeAndOrder,
            )
        }
            .flatMapLatest { it }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = FileListUiState.Loading
            )

    init {
        val sortTypeSelected = SortType.values()[sharedPreferencesProvider.getInt(PREF_FILE_LIST_SORT_TYPE, SortType.SORT_TYPE_BY_NAME.ordinal)]
        val sortOrderSelected =
            SortOrder.values()[sharedPreferencesProvider.getInt(PREF_FILE_LIST_SORT_ORDER, SortOrder.SORT_ORDER_ASCENDING.ordinal)]
        sortTypeAndOrder.update { Pair(sortTypeSelected, sortOrderSelected) }
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            synchronizeFolderUseCase.execute(
                SynchronizeFolderUseCase.Params(
                    remotePath = initialFolderToDisplay.remotePath,
                    accountName = initialFolderToDisplay.owner,
                    syncMode = SYNC_CONTENTS,
                )
            )
        }
    }

    fun navigateToFolderId(folderId: Long) {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            val result = getFileByIdUseCase.execute(GetFileByIdUseCase.Params(fileId = folderId))
            result.getDataOrNull()?.let {
                updateFolderToDisplay(it)
            }
        }
    }

    fun getFile(): OCFile {
        return currentFolderDisplayed.value
    }

    fun setGridModeAsPreferred() {
        savePreferredLayoutManager(true)
    }

    fun setListModeAsPreferred() {
        savePreferredLayoutManager(false)
    }

    private fun savePreferredLayoutManager(isGridModeSelected: Boolean) {
        sharedPreferencesProvider.putBoolean(RECYCLER_VIEW_PREFERRED, isGridModeSelected)
    }

    fun isGridModeSetAsPreferred() = sharedPreferencesProvider.getBoolean(RECYCLER_VIEW_PREFERRED, false)

    private fun sortList(filesWithSyncInfo: List<OCFileWithSyncInfo>, sortTypeAndOrder: Pair<SortType, SortOrder>): List<OCFileWithSyncInfo> {
        return sortFilesWithSyncInfoUseCase.execute(
            SortFilesWithSyncInfoUseCase.Params(
                listOfFiles = filesWithSyncInfo,
                sortType = SortTypeDomain.fromPreferences(sortTypeAndOrder.first.ordinal),
                ascending = sortTypeAndOrder.second == SortOrder.SORT_ORDER_ASCENDING
            )
        )
    }

    fun manageBrowseUp() {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            val currentFolder = currentFolderDisplayed.value
            val parentId = currentFolder.parentId
            val parentDir: OCFile?

            // browsing back to not shared by link or av offline should update to root
            if (parentId != null && parentId != ROOT_PARENT_ID.toLong()) {
                // Browsing to parent folder. Not root
                val fileByIdResult = getFileByIdUseCase.execute(GetFileByIdUseCase.Params(parentId))
                when (fileListOption.value) {
                    FileListOption.ALL_FILES -> {
                        parentDir = fileByIdResult.getDataOrNull()
                    }
                    FileListOption.SHARED_BY_LINK -> {
                        val fileById = fileByIdResult.getDataOrNull()!!
                        parentDir = if (!fileById.sharedByLink || fileById.sharedWithSharee != true) {
                            getFileByRemotePathUseCase.execute(GetFileByRemotePathUseCase.Params(fileById.owner, ROOT_PATH)).getDataOrNull()
                        } else fileById
                    }
                    FileListOption.AV_OFFLINE -> {
                        val fileById = fileByIdResult.getDataOrNull()!!
                        parentDir = if (!fileById.isAvailableOffline) {
                            getFileByRemotePathUseCase.execute(GetFileByRemotePathUseCase.Params(fileById.owner, ROOT_PATH)).getDataOrNull()
                        } else fileById
                    }
                }
            } else if (parentId == ROOT_PARENT_ID.toLong()) {
                // Browsing to parent folder. Root
                val rootFolderForAccountResult = getFileByRemotePathUseCase.execute(
                    GetFileByRemotePathUseCase.Params(
                        remotePath = ROOT_PATH,
                        owner = currentFolder.owner,
                    )
                )
                parentDir = rootFolderForAccountResult.getDataOrNull()
            } else {
                // Browsing to non existing parent folder.
                TODO()
            }

            updateFolderToDisplay(parentDir!!)
        }
    }

    fun updateFolderToDisplay(newFolderToDisplay: OCFile) {
        currentFolderDisplayed.update { newFolderToDisplay }
        searchFilter.update { "" }
    }

    fun updateSearchFilter(newSearchFilter: String) {
        searchFilter.update { newSearchFilter }
    }

    fun updateFileListOption(newFileListOption: FileListOption) {
        fileListOption.update { newFileListOption }
    }

    fun updateSortTypeAndOrder(sortType: SortType, sortOrder: SortOrder) {
        sharedPreferencesProvider.putInt(PREF_FILE_LIST_SORT_TYPE, sortType.ordinal)
        sharedPreferencesProvider.putInt(PREF_FILE_LIST_SORT_ORDER, sortOrder.ordinal)
        sortTypeAndOrder.update { Pair(sortType, sortOrder) }
    }

    private fun composeFileListUiStateForThisParams(
        currentFolderDisplayed: OCFile,
        accountName: String,
        fileListOption: FileListOption,
        searchFilter: String?,
        sortTypeAndOrder: Pair<SortType, SortOrder>
    ): Flow<FileListUiState> =
        when (fileListOption) {
            FileListOption.ALL_FILES -> retrieveFlowForAllFiles(currentFolderDisplayed, accountName)
            FileListOption.SHARED_BY_LINK -> retrieveFlowForShareByLink(currentFolderDisplayed, accountName)
            FileListOption.AV_OFFLINE -> retrieveFlowForAvailableOffline(currentFolderDisplayed, accountName)
        }.toFileListUiState(
            currentFolderDisplayed,
            accountName,
            fileListOption,
            searchFilter,
            sortTypeAndOrder,
        )

    private fun retrieveFlowForAllFiles(
        currentFolderDisplayed: OCFile,
        accountName: String,
    ): Flow<List<OCFileWithSyncInfo>> =
        getFolderContentAsStreamUseCase.execute(
            GetFolderContentAsStreamUseCase.Params(
                folderId = currentFolderDisplayed.id
                    ?: getFileByRemotePathUseCase.execute(GetFileByRemotePathUseCase.Params(accountName, ROOT_PATH)).getDataOrNull()!!.id!!
            )
        )

    /**
     * In root folder, all the shared by link files should be shown. Otherwise, the folder content should be shown.
     * Logic to handle the browse back in [manageBrowseUp]
     */
    private fun retrieveFlowForShareByLink(
        currentFolderDisplayed: OCFile,
        accountName: String,
    ): Flow<List<OCFileWithSyncInfo>> =
        if (currentFolderDisplayed.remotePath == ROOT_PATH) {
            getSharedByLinkForAccountAsStreamUseCase.execute(GetSharedByLinkForAccountAsStreamUseCase.Params(accountName))
        } else {
            retrieveFlowForAllFiles(currentFolderDisplayed, accountName)
        }

    /**
     * In root folder, all the available offline files should be shown. Otherwise, the folder content should be shown.
     * Logic to handle the browse back in [manageBrowseUp]
     */
    private fun retrieveFlowForAvailableOffline(
        currentFolderDisplayed: OCFile,
        accountName: String,
    ): Flow<List<OCFileWithSyncInfo>> =
        if (currentFolderDisplayed.remotePath == ROOT_PATH) {
            getFilesAvailableOfflineFromAccountAsStreamUseCase.execute(GetFilesAvailableOfflineFromAccountAsStreamUseCase.Params(accountName))
        } else {
            retrieveFlowForAllFiles(currentFolderDisplayed, accountName)
        }

    private fun Flow<List<OCFileWithSyncInfo>>.toFileListUiState(
        currentFolderDisplayed: OCFile,
        accountName: String,
        fileListOption: FileListOption,
        searchFilter: String?,
        sortTypeAndOrder: Pair<SortType, SortOrder>,
    ) = this.map { folderContent ->
        FileListUiState.Success(
            accountName = accountName,
            folderToDisplay = currentFolderDisplayed,
            folderContent = folderContent.filter { fileWithSyncInfo ->
                fileWithSyncInfo.file.fileName.contains(
                    searchFilter ?: "",
                    ignoreCase = true
                ) && (showHiddenFiles || !fileWithSyncInfo.file.fileName.startsWith("."))
            }.let { sortList(it, sortTypeAndOrder) },
            fileListOption = fileListOption,
            searchFilter = searchFilter,
        )
    }

    sealed interface FileListUiState {
        object Loading : FileListUiState
        data class Success(
            val accountName: String,
            val folderToDisplay: OCFile?,
            val folderContent: List<OCFileWithSyncInfo>,
            val fileListOption: FileListOption,
            val searchFilter: String?,
        ) : FileListUiState
    }

    companion object {
        private const val RECYCLER_VIEW_PREFERRED = "RECYCLER_VIEW_PREFERRED"
    }
}

