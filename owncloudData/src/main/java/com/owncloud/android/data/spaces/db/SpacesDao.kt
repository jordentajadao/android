/**
 * ownCloud Android client application
 *
 * @author Abel García de Prada
 * @author Juan Carlos Garrote Gascón
 *
 * Copyright (C) 2023 ownCloud GmbH.
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

package com.owncloud.android.data.spaces.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.owncloud.android.data.ProviderMeta
import com.owncloud.android.data.spaces.db.SpacesEntity.Companion.DRIVE_TYPE_PERSONAL
import com.owncloud.android.data.spaces.db.SpacesEntity.Companion.DRIVE_TYPE_PROJECT
import com.owncloud.android.data.spaces.db.SpacesEntity.Companion.SPACES_ACCOUNT_NAME
import com.owncloud.android.data.spaces.db.SpacesEntity.Companion.SPACES_DRIVE_TYPE
import com.owncloud.android.data.spaces.db.SpacesEntity.Companion.SPACES_ID
import com.owncloud.android.data.spaces.db.SpacesEntity.Companion.SPACES_ROOT_WEB_DAV_URL
import kotlinx.coroutines.flow.Flow

@Dao
interface SpacesDao {
    @Transaction
    fun insertOrDeleteSpaces(
        listOfSpacesEntities: List<SpacesEntity>,
        listOfSpecialEntities: List<SpaceSpecialEntity>,
    ) {
        val currentAccountName = listOfSpacesEntities.first().accountName
        val currentSpaces = getAllSpacesForAccount(currentAccountName)

        // Delete spaces that are not attached to the current account anymore
        val spacesToDelete = currentSpaces.filterNot { oldSpace ->
            listOfSpacesEntities.any { it.id == oldSpace.id }
        }

        spacesToDelete.forEach { spaceToDelete ->
            deleteSpaceForAccountById(accountName = spaceToDelete.accountName, spaceId = spaceToDelete.id)
        }

        // Upsert new spaces
        upsertSpaces(listOfSpacesEntities)
        upsertSpecials(listOfSpecialEntities)
    }

    @Transaction
    fun upsertSpaces(listOfSpacesEntities: List<SpacesEntity>) = com.owncloud.android.data.upsert(
        items = listOfSpacesEntities,
        insertMany = ::insertOrIgnoreSpaces,
        updateMany = ::updateSpaces
    )

    @Transaction
    fun upsertSpecials(listOfSpecialEntities: List<SpaceSpecialEntity>) = com.owncloud.android.data.upsert(
        items = listOfSpecialEntities,
        insertMany = ::insertOrIgnoreSpecials,
        updateMany = ::updateSpecials
    )

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertOrIgnoreSpaces(listOfSpacesEntities: List<SpacesEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertOrIgnoreSpecials(listOfSpecialEntities: List<SpaceSpecialEntity>): List<Long>

    @Update
    fun updateSpaces(listOfSpacesEntities: List<SpacesEntity>)

    @Update
    fun updateSpecials(listOfSpecialEntities: List<SpaceSpecialEntity>)

    @Query(SELECT_ALL_SPACES)
    fun getAllSpaces(): List<SpacesEntity>

    @Query(SELECT_ALL_SPACES_FOR_ACCOUNT)
    fun getAllSpacesForAccount(
        accountName: String,
    ): List<SpacesEntity>

    @Query(SELECT_PERSONAL_SPACE_FOR_ACCOUNT)
    fun getPersonalSpaceForAccount(
        accountName: String,
    ): List<SpacesEntity>

    @Query(SELECT_PROJECT_SPACES_FOR_ACCOUNT)
    fun getProjectSpacesWithSpecialsForAccount(
        accountName: String,
    ): List<SpacesWithSpecials>

    @Query(SELECT_PROJECT_SPACES_FOR_ACCOUNT)
    fun getProjectSpacesWithSpecialsForAccountAsFlow(
        accountName: String,
    ): Flow<List<SpacesWithSpecials>>

    @Query(SELECT_PERSONAL_AND_PROJECT_SPACES_FOR_ACCOUNT)
    fun getPersonalAndProjectSpacesForAccount(
        accountName: String,
    ): List<SpacesEntity>

    @Query(SELECT_PERSONAL_AND_PROJECT_SPACES_FOR_ACCOUNT)
    fun getPersonalAndProjectSpacesWithSpecialsForAccountAsFlow(
        accountName: String,
    ): Flow<List<SpacesWithSpecials>>

    @Query(SELECT_SPACE_BY_ID_FOR_ACCOUNT)
    fun getSpaceWithSpecialsByIdForAccount(
        spaceId: String?,
        accountName: String,
    ): SpacesWithSpecials

    @Query(SELECT_WEB_DAV_URL_FOR_SPACE)
    fun getWebDavUrlForSpace(
        spaceId: String?,
        accountName: String,
    ): String?

    @Query(DELETE_ALL_SPACES_FOR_ACCOUNT)
    fun deleteSpacesForAccount(accountName: String)

    @Query(DELETE_SPACE_FOR_ACCOUNT_BY_ID)
    fun deleteSpaceForAccountById(accountName: String, spaceId: String)

    companion object {
        private const val SELECT_ALL_SPACES = """
            SELECT *
            FROM ${ProviderMeta.ProviderTableMeta.SPACES_TABLE_NAME}
        """

        private const val SELECT_ALL_SPACES_FOR_ACCOUNT = """
            SELECT *
            FROM ${ProviderMeta.ProviderTableMeta.SPACES_TABLE_NAME}
            WHERE $SPACES_ACCOUNT_NAME = :accountName
        """

        private const val SELECT_PERSONAL_SPACE_FOR_ACCOUNT = """
            SELECT *
            FROM ${ProviderMeta.ProviderTableMeta.SPACES_TABLE_NAME}
            WHERE $SPACES_ACCOUNT_NAME = :accountName AND $SPACES_DRIVE_TYPE LIKE '$DRIVE_TYPE_PERSONAL'
        """

        private const val SELECT_PROJECT_SPACES_FOR_ACCOUNT = """
            SELECT *
            FROM ${ProviderMeta.ProviderTableMeta.SPACES_TABLE_NAME}
            WHERE $SPACES_ACCOUNT_NAME = :accountName AND $SPACES_DRIVE_TYPE LIKE '$DRIVE_TYPE_PROJECT'
            ORDER BY name COLLATE NOCASE ASC
        """

        private const val SELECT_PERSONAL_AND_PROJECT_SPACES_FOR_ACCOUNT = """
            SELECT *
            FROM ${ProviderMeta.ProviderTableMeta.SPACES_TABLE_NAME}
            WHERE $SPACES_ACCOUNT_NAME = :accountName AND ($SPACES_DRIVE_TYPE LIKE '$DRIVE_TYPE_PROJECT' OR $SPACES_DRIVE_TYPE LIKE '$DRIVE_TYPE_PERSONAL')
            ORDER BY $SPACES_DRIVE_TYPE ASC, name COLLATE NOCASE ASC
        """

        private const val SELECT_SPACE_BY_ID_FOR_ACCOUNT = """
            SELECT *
            FROM ${ProviderMeta.ProviderTableMeta.SPACES_TABLE_NAME}
            WHERE $SPACES_ID = :spaceId AND $SPACES_ACCOUNT_NAME = :accountName
        """

        // TODO: Use it for personal space too (remove last AND condition)
        private const val SELECT_WEB_DAV_URL_FOR_SPACE = """
            SELECT $SPACES_ROOT_WEB_DAV_URL
            FROM ${ProviderMeta.ProviderTableMeta.SPACES_TABLE_NAME}
            WHERE $SPACES_ID = :spaceId AND $SPACES_ACCOUNT_NAME = :accountName AND $SPACES_DRIVE_TYPE NOT LIKE '$DRIVE_TYPE_PERSONAL'
        """

        private const val DELETE_ALL_SPACES_FOR_ACCOUNT = """
            DELETE
            FROM ${ProviderMeta.ProviderTableMeta.SPACES_TABLE_NAME}
            WHERE $SPACES_ACCOUNT_NAME = :accountName
        """

        private const val DELETE_SPACE_FOR_ACCOUNT_BY_ID = """
            DELETE
            FROM ${ProviderMeta.ProviderTableMeta.SPACES_TABLE_NAME}
            WHERE $SPACES_ACCOUNT_NAME = :accountName AND $SPACES_ID LIKE :spaceId
        """
    }
}
