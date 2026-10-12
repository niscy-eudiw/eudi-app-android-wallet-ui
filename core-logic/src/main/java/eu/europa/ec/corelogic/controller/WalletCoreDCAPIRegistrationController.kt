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

package eu.europa.ec.corelogic.controller

import eu.europa.ec.eudi.wallet.dcapi.registration.DCAPIRegistration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The wallet's registration with the Digital Credential API. Wallet Core updates the registration
 * after every change to its documents, in a coroutine it does not wait for. The updates run here one
 * at a time, so each reads the documents as they are when it starts and a slow update cannot undo a
 * later one. The registry is kept by the system outside the app's data, so the factory reset clears
 * it through [clearAround].
 */
interface WalletCoreDCAPIRegistrationController : DCAPIRegistration {

    /** Supplies the registration that updates the registry, once the wallet exists. */
    fun attach(createRegistration: () -> DCAPIRegistration)

    /**
     * Runs [block] while no update can run, then clears the registry. Updates Wallet Core starts in the
     * meantime wait and run afterwards, against the documents as they are then. A registry that cannot
     * be cleared, for example because the system service is unavailable, does not fail the call.
     */
    suspend fun <T> clearAround(block: suspend () -> T): T
}

class WalletCoreDCAPIRegistrationControllerImpl(
    private val clearRegistry: suspend () -> Unit,
) : WalletCoreDCAPIRegistrationController {

    private val mutex = Mutex()

    @Volatile
    private var createRegistration: (() -> DCAPIRegistration)? = null

    @Volatile
    private var registration: DCAPIRegistration? = null

    override fun attach(createRegistration: () -> DCAPIRegistration) {
        this.createRegistration = createRegistration
        registration = null
    }

    override suspend fun registerCredentials() {
        mutex.withLock {
            currentRegistration()?.registerCredentials()
        }
    }

    override suspend fun unregisterCredentials() {
        mutex.withLock { clearRegistry() }
    }

    override suspend fun <T> clearAround(block: suspend () -> T): T = mutex.withLock {
        val result = block()
        try {
            clearRegistry()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        result
    }

    private fun currentRegistration(): DCAPIRegistration? {
        val create = createRegistration ?: return null
        return registration ?: create().also { registration = it }
    }
}
