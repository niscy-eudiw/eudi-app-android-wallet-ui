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

package eu.europa.ec.uilogic.config

import eu.europa.ec.uilogic.navigation.Screen

interface ConfigUILogic {
    /*
       Define the dashboard identifier
    */
    val dashboardScreenIdentifier: Screen

    /*
   Define the issuance identifier
   */
    val issuanceScreenIdentifier: Screen

    /**
     * Screens a locked wallet may show: the startup screen and the steps to log in or create a
     * PIN. A locked wallet restored on any other screen is sent back to login.
     */
    val loginScreens: List<Screen>

    /**
     * Screens on which an issuance, presentation or signing is in progress. On them the background
     * lock waits for `AuthenticationConfig.protectedFlowLockTimeout` instead of
     * `backgroundLockTimeout`, because issuance and signing send the user to a website and back.
     * The idle lock applies as on any other screen.
     */
    val protectedFlowScreens: List<Screen>
}