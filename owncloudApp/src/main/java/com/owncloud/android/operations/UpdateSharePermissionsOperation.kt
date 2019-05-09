/**
 * ownCloud Android client application
 *
 * @author David A. Velasco
 * @author David González Verdugo
 * Copyright (C) 2019 ownCloud GmbH.
 *
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 *
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http:></http:>//www.gnu.org/licenses/>.
 */

package com.owncloud.android.operations

import com.owncloud.android.lib.common.OwnCloudClient
import com.owncloud.android.lib.common.operations.RemoteOperationResult
import com.owncloud.android.lib.resources.files.FileUtils
import com.owncloud.android.lib.resources.shares.GetRemoteShareOperation
import com.owncloud.android.lib.resources.shares.RemoteShare
import com.owncloud.android.lib.resources.shares.ShareParserResult
import com.owncloud.android.lib.resources.shares.UpdateRemoteShareOperation
import com.owncloud.android.operations.common.SyncOperation

/**
 * Updates an existing private share for a given file
 *
 * Constructor
 *
 * @param shareId       Private [RemoteShare] to update. Mandatory argument
 */
class UpdateSharePermissionsOperation(private val shareId: Long) : SyncOperation<ShareParserResult>() {
    private var permissions: Int = -1
    lateinit var path: String

    /**
     * Set permissions to update in private share.
     *
     * @param permissions   Permissions to set to the private share.
     * Values <= 0 result in no update applied to the permissions.
     */
    fun setPermissions(permissions: Int) {
        this.permissions = permissions
    }

    override fun run(client: OwnCloudClient): RemoteOperationResult<ShareParserResult> {

        val share = storageManager.getShareById(shareId) ?: return RemoteOperationResult(
            RemoteOperationResult.ResultCode.SHARE_NOT_FOUND
        ) // ShareType.USER | ShareType.GROUP

        path = share.path

        // Update remote share with password
        val updateOp = UpdateRemoteShareOperation(share.remoteId)
        updateOp.permissions = permissions
        var result = updateOp.execute(client)

        if (result.isSuccess) {
            val getShareOp = GetRemoteShareOperation(share.remoteId)
            result = getShareOp.execute(client)
            if (result.isSuccess) {
                val remoteShare = result.data.shares[0]
                // TODO check permissions are being saved
                updateData(remoteShare)
            }
        }

        return result
    }

    private fun updateData(share: RemoteShare) {
        // Update DB with the response
        share.path = path   // TODO - check if may be moved to UpdateRemoteShareOperation
        share.isFolder = path.endsWith(FileUtils.PATH_SEPARATOR)
        storageManager.saveShare(share)
    }
}
