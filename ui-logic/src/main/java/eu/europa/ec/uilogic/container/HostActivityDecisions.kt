/*
 * Copyright (c) 2026 European Commission
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

package eu.europa.ec.uilogic.container

import android.os.Bundle
import eu.europa.ec.uilogic.navigation.helper.DeepLinkType

/**
 * The saved state the host activity passes on when it is created. A configuration change keeps the
 * screens. A process that has not yet started its navigation, such as one Android restarted after
 * ending it in the background, restores none: the wallet is locked then, and a restored screen would
 * start its work, such as fetching a request or advertising over Bluetooth, before the login check.
 */
internal fun stateToRestore(
    savedInstanceState: Bundle?,
    navigationStartedInProcess: Boolean,
): Bundle? = savedInstanceState.takeIf { navigationStartedInProcess }

/**
 * Whether the host activity opens the intent it was created with. An activity recreated after a
 * configuration change, or after Android ended the process, is given the intent of its first start
 * again; that link or request was handled then, and opening it again would replay it.
 */
internal fun opensLaunchIntent(savedInstanceState: Bundle?): Boolean = savedInstanceState == null

/**
 * Whether an incoming link of [type] is dropped. A link that starts no wallet flow
 * ([DeepLinkType.EXTERNAL]) passes none of the manifest's filters, so it can only come from an app
 * that addressed the wallet directly; keeping it would let that app have the wallet open any link
 * once the user logs in.
 */
internal fun dropsIncomingLink(type: DeepLinkType): Boolean = type == DeepLinkType.EXTERNAL

/**
 * Whether declining a Digital Credential API request also finishes the activity. It does when the
 * request itself brought the wallet up: on a [coldBoot], or in a task restored after its process
 * ended, where the wallet was not [walletWasShown]. A wallet already open stays as it was.
 */
internal fun finishesOnDeclinedRequest(coldBoot: Boolean, walletWasShown: Boolean): Boolean =
    coldBoot || !walletWasShown
