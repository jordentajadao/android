/**
 * ownCloud Android client application
 *
 * @author Juan Carlos Garrote Gascón
 *
 * Copyright (C) 2022 ownCloud GmbH.
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
 */

package com.owncloud.android.data.transfers.datasources.implementation

import com.owncloud.android.data.transfers.datasources.LocalTransferDataSource
import com.owncloud.android.data.transfers.db.OCTransferEntity
import com.owncloud.android.data.transfers.db.TransferDao
import com.owncloud.android.domain.files.model.OCFile
import com.owncloud.android.domain.transfers.model.OCTransfer
import com.owncloud.android.domain.transfers.model.TransferResult
import com.owncloud.android.domain.transfers.model.TransferStatus

class OCLocalTransferDataSource(
    private val transferDao: TransferDao
): LocalTransferDataSource {
    override fun storeTransfer(transfer: OCTransfer): Long {
        return transferDao.insert(transfer.toEntity())
    }

    override fun updateTransfer(transfer: OCTransfer) {
        transferDao.insert(transfer.toEntity())
    }

    override fun updateTransferStatusToInProgressById(id: Long) {
        transferDao.updateTransferStatusWithId(id, TransferStatus.TRANSFER_IN_PROGRESS.value)
    }

    override fun updateTransferWhenFinished(id: Long, status: TransferStatus,
        transferEndTimestamp: Long, lastResult: TransferResult) {
        transferDao.updateTransferWhenFinished(id, status.value, transferEndTimestamp, lastResult.value)
    }

    override fun removeTransferById(id: Long) {
        transferDao.deleteTransferWithId(id)
    }

    override fun removeAllTransfersFromAccount(accountName: String) {
        transferDao.deleteTransfersWithAccountName(accountName)
    }

    override fun getAllTransfers(): List<OCTransfer> {
        return transferDao.getAllTransfers().map { it.toModel() }
    }

    override fun getLastTransferFor(remotePath: String, accountName: String): OCTransfer? {
        return transferDao.getLastTransferWithRemotePathAndAccountName(remotePath, accountName)?.toModel()
    }

    override fun getCurrentAndPendingTransfers(): List<OCTransfer> {
        return transferDao.getTransfersWithStatus(
            listOf(TransferStatus.TRANSFER_IN_PROGRESS.value, TransferStatus.TRANSFER_QUEUED.value)
        ).map { it.toModel() }
    }

    override fun getFailedTransfers(): List<OCTransfer> {
        return transferDao.getTransfersWithStatus(
            listOf(TransferStatus.TRANSFER_FAILED.value)
        ).map { it.toModel() }
    }

    override fun getFinishedTransfers(): List<OCTransfer> {
        return transferDao.getTransfersWithStatus(
            listOf(TransferStatus.TRANSFER_SUCCEEDED.value)
        ).map { it.toModel() }
    }

    override fun clearFailedTransfers() {
        transferDao.deleteTransfersWithStatus(TransferStatus.TRANSFER_FAILED.value)
    }

    override fun clearSuccessfulTransfers() {
        transferDao.deleteTransfersWithStatus(TransferStatus.TRANSFER_SUCCEEDED.value)
    }

    companion object {
        private fun OCTransferEntity.toModel() = OCTransfer(
            id = id,
            localPath = localPath,
            remotePath = remotePath,
            accountName = accountName,
            fileSize = fileSize,
            status = TransferStatus.fromValue(status),
            localBehaviour = localBehaviour,
            forceOverwrite = forceOverwrite,
            transferEndTimestamp = transferEndTimestamp,
            lastResult = lastResult?.let { TransferResult.fromValue(it) },
            createdBy = createdBy,
            transferId = transferId
        )

        private fun OCTransfer.toEntity() = OCTransferEntity(
            localPath = localPath,
            remotePath = remotePath,
            accountName = accountName,
            fileSize = fileSize,
            status = status.value,
            localBehaviour = localBehaviour,
            forceOverwrite = forceOverwrite,
            transferEndTimestamp = transferEndTimestamp,
            lastResult = lastResult?.value,
            createdBy = createdBy,
            transferId = transferId
        ).apply { this@toEntity.id?.let { this.id = it } }
    }
}
