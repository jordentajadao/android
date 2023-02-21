/**
 * ownCloud Android client application
 *
 * @author David González Verdugo
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

package com.owncloud.android.extensions

import android.content.res.Resources
import com.owncloud.android.R
import com.owncloud.android.domain.exceptions.AccountNotNewException
import com.owncloud.android.domain.exceptions.AccountNotTheSameException
import com.owncloud.android.domain.exceptions.BadOcVersionException
import com.owncloud.android.domain.exceptions.ConflictException
import com.owncloud.android.domain.exceptions.CopyIntoDescendantException
import com.owncloud.android.domain.exceptions.CopyIntoSameFolderException
import com.owncloud.android.domain.exceptions.FileAlreadyExistsException
import com.owncloud.android.domain.exceptions.FileNotFoundException
import com.owncloud.android.domain.exceptions.ForbiddenException
import com.owncloud.android.domain.exceptions.IncorrectAddressException
import com.owncloud.android.domain.exceptions.InstanceNotConfiguredException
import com.owncloud.android.domain.exceptions.InvalidOverwriteException
import com.owncloud.android.domain.exceptions.LocalFileNotFoundException
import com.owncloud.android.domain.exceptions.MoveIntoDescendantException
import com.owncloud.android.domain.exceptions.MoveIntoSameFolderException
import com.owncloud.android.domain.exceptions.NoConnectionWithServerException
import com.owncloud.android.domain.exceptions.NoNetworkConnectionException
import com.owncloud.android.domain.exceptions.OAuth2ErrorAccessDeniedException
import com.owncloud.android.domain.exceptions.OAuth2ErrorException
import com.owncloud.android.domain.exceptions.RedirectToNonSecureException
import com.owncloud.android.domain.exceptions.SSLErrorException
import com.owncloud.android.domain.exceptions.SSLRecoverablePeerUnverifiedException
import com.owncloud.android.domain.exceptions.ServerConnectionTimeoutException
import com.owncloud.android.domain.exceptions.ServerNotReachableException
import com.owncloud.android.domain.exceptions.ServerResponseTimeoutException
import com.owncloud.android.domain.exceptions.ServiceUnavailableException
import com.owncloud.android.domain.exceptions.SpaceDisabledException
import com.owncloud.android.domain.exceptions.UnauthorizedException
import com.owncloud.android.domain.exceptions.validation.FileNameException
import java.util.Locale

fun Throwable.parseError(
    genericErrorMessage: String,
    resources: Resources,
    showJustReason: Boolean = false
): CharSequence {
    if (!this.message.isNullOrEmpty()) { // If there's an specific error message from layers below use it
        return this.message as String
    } else { // Build the error message otherwise
        val reason = when (this) {
            is NoConnectionWithServerException -> resources.getString(R.string.network_error_socket_exception)
            is NoNetworkConnectionException -> resources.getString(R.string.error_no_network_connection)
            is ServerResponseTimeoutException -> resources.getString(R.string.network_error_socket_timeout_exception)
            is ServerConnectionTimeoutException -> resources.getString(R.string.network_error_connect_timeout_exception)
            is ServerNotReachableException -> resources.getString(R.string.network_host_not_available)
            is ServiceUnavailableException -> resources.getString(R.string.service_unavailable)
            is ConflictException -> resources.getString(R.string.conflict_title)
            is SSLRecoverablePeerUnverifiedException -> resources.getString(R.string.ssl_certificate_not_trusted)
            is BadOcVersionException -> resources.getString(R.string.auth_bad_oc_version_title)
            is IncorrectAddressException -> resources.getString(R.string.auth_incorrect_address_title)
            is SSLErrorException -> resources.getString(R.string.auth_ssl_general_error_title)
            is UnauthorizedException -> resources.getString(R.string.auth_unauthorized)
            is FileAlreadyExistsException -> resources.getString(R.string.file_already_exists)
            is FileNameException -> {
                val stringId = when (this.type) {
                    FileNameException.FileNameExceptionType.FILE_NAME_EMPTY -> R.string.filename_empty
                    FileNameException.FileNameExceptionType.FILE_NAME_FORBIDDEN_CHARACTERS -> R.string.filename_forbidden_characters_from_server
                    FileNameException.FileNameExceptionType.FILE_NAME_TOO_LONG -> R.string.filename_too_long
                }
                resources.getString(stringId)
            }
            is InvalidOverwriteException -> resources.getString(R.string.file_already_exists)
            is MoveIntoDescendantException -> resources.getString(R.string.move_file_invalid_into_descendent)
            is CopyIntoDescendantException -> resources.getString(R.string.copy_file_invalid_into_descendent)
            is MoveIntoSameFolderException -> resources.getString(R.string.move_file_invalid_overwrite)
            is CopyIntoSameFolderException -> resources.getString(R.string.copy_file_invalid_overwrite)
            is ForbiddenException -> resources.getString(R.string.forbidden_permissions, resources.getString(R.string.uploader_upload_forbidden_permissions))
            is FileNotFoundException -> resources.getString(R.string.common_not_found)
            is InstanceNotConfiguredException -> resources.getString(R.string.auth_not_configured_title)
            is OAuth2ErrorException -> resources.getString(R.string.auth_oauth_error)
            is OAuth2ErrorAccessDeniedException -> resources.getString(R.string.auth_oauth_error_access_denied)
            is AccountNotNewException -> resources.getString(R.string.auth_account_not_new)
            is AccountNotTheSameException -> resources.getString(R.string.auth_account_not_the_same)
            is RedirectToNonSecureException -> resources.getString(R.string.auth_redirect_non_secure_connection_title)
            is LocalFileNotFoundException -> resources.getString(R.string.local_file_not_found_toast)
            is SpaceDisabledException -> resources.getString(R.string.copy_file_space_disabled)
            else -> resources.getString(R.string.common_error_unknown)
        }

        return when {
            showJustReason -> {
                reason
            }
            else -> "$genericErrorMessage ${resources.getString(R.string.error_reason)} ${reason.lowercase(Locale.getDefault())}"
        }
    }
}
