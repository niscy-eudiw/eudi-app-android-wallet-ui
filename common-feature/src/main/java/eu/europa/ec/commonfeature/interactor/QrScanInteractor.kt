/*
 * Copyright (c) 2025 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.commonfeature.interactor

import android.content.Context
import android.net.Uri
import eu.europa.ec.businesslogic.validator.FormValidator
import eu.europa.ec.eudi.rqesui.infrastructure.EudiRQESUi
import eu.europa.ec.eudi.rqesui.infrastructure.RemoteUri

interface QrScanInteractor : FormValidator {
    fun launchRqesSdk(context: Context, uri: Uri)
}

class QrScanInteractorImpl(
    private val formValidator: FormValidator
) : FormValidator by formValidator, QrScanInteractor {

    override fun launchRqesSdk(context: Context, uri: Uri) {
        EudiRQESUi.initiate(
            context = context,
            remoteUri = RemoteUri(uri)
        )
    }
}