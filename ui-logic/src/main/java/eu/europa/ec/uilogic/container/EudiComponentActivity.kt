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

import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import eu.europa.ec.authenticationlogic.controller.session.SessionController
import eu.europa.ec.businesslogic.controller.storage.PrefKeys
import eu.europa.ec.businesslogic.provider.UuidProvider
import eu.europa.ec.corelogic.controller.WalletResetStatus
import eu.europa.ec.corelogic.di.getOrNullKoinScope
import eu.europa.ec.resourceslogic.theme.ThemeManager
import eu.europa.ec.uilogic.extension.exposeTestTagsAsResourceId
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.IssuanceScreens
import eu.europa.ec.uilogic.navigation.RouterHost
import eu.europa.ec.uilogic.navigation.helper.DeepLinkAction
import eu.europa.ec.uilogic.navigation.helper.DeepLinkType
import eu.europa.ec.uilogic.navigation.helper.IntentType
import eu.europa.ec.uilogic.navigation.helper.handleDeepLinkAction
import eu.europa.ec.uilogic.navigation.helper.hasDeepLink
import eu.europa.ec.uilogic.navigation.helper.hasIntentAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.annotation.KoinViewModel
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The wallet's host activity. Besides hosting the navigation graph, it drives the session lock:
 * when the app returns to the foreground an expired session is sent to login, and an idle check
 * runs while the activity is visible. The app counts as in the background only when the whole
 * process is (another in-app activity, such as the RQES screens, does not count); the background
 * itself is recorded by [registerSessionBackgroundObserver], registered once for the process.
 */
open class EudiComponentActivity : FragmentActivity() {

    private val routerHost: RouterHost by inject()
    private val sessionController: SessionController by inject()
    private val walletResetStatus: WalletResetStatus by inject()
    private val viewModel: EudiComponentActivityViewModel by viewModel()

    private var idleLockJob: Job? = null

    private val processLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            checkSessionOnForeground()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleObserver)
        forgetKeptLinkOnReset()
    }

    /** A factory reset forgets the link kept for later, so that it does not open after onboarding. */
    private fun forgetKeptLinkOnReset() {
        lifecycleScope.launch {
            walletResetStatus.isResetting.collect { resetting ->
                if (resetting) viewModel.cacheIntent(null)
            }
        }
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(processLifecycleObserver)
        super.onDestroy()
    }

    /**
     * Keeps [intent] until a screen opens it after login. While the wallet is locked a kept link
     * is not cleared, so that it survives until the user has logged in.
     */
    internal fun cacheIntent(intent: Intent?) {
        if (intent == null && !sessionController.isUnlocked) return
        viewModel.cacheIntent(intent)
    }

    /**
     * The kept link, or null while the wallet is locked: a screen restored before login, such as
     * the dashboard after the activity is recreated, must not open it.
     */
    internal fun getCachedIntent(): Intent? =
        if (sessionController.isUnlocked) viewModel.getCachedIntent() else null

    @Composable
    protected fun Content(
        intent: Intent?,
        builder: NavGraphBuilder.(NavController) -> Unit,
    ) {
        ThemeManager.instance.Theme {
            Surface(
                modifier = Modifier
                    .exposeTestTagsAsResourceId()
                    .fillMaxSize(),
                color = MaterialTheme.colorScheme.surface
            ) {
                routerHost.StartFlow {
                    builder(it)
                }
                LaunchedEffect(Unit) {
                    viewModel.onFlowStart()
                    routerHost.requireLoginIfLocked()
                    handleDeepLink(intent, coldBoot = true)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    override fun onStart() {
        super.onStart()
        sessionController.onUserInteraction()
        scheduleIdleLock()
    }

    override fun onStop() {
        super.onStop()
        idleLockJob?.cancel()
        sessionController.recordProtectedFlow(routerHost.isInProtectedFlow())
    }

    /**
     * Applies the background timeout when the app returns to the foreground, before anything can
     * navigate, and sends a locked wallet to login. A recreated activity has no navigation state
     * until its first composition; its login check then runs from [Content].
     */
    private fun checkSessionOnForeground() {
        sessionController.onForeground()
        if (routerHost.isNavigationReady() && viewModel.hasFlowStarted()) {
            routerHost.requireLoginIfLocked()
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        sessionController.onUserInteraction()
    }

    private fun scheduleIdleLock() {
        idleLockJob?.cancel()
        idleLockJob = lifecycleScope.launch {
            while (true) {
                delay(sessionController.timeUntilIdleLock().coerceAtLeast(1.seconds))
                if (viewModel.hasFlowStarted() && sessionController.lockIfIdle()) {
                    routerHost.requireLoginIfLocked()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        checkSessionOnForeground()
        if (viewModel.hasFlowStarted()) {
            handleDeepLink(intent)
        } else {
            runPendingDeepLink(intent)
        }
    }

    private fun runPendingDeepLink(intent: Intent?) {
        lifecycleScope.launch {
            var count = 0
            while (!viewModel.hasFlowStarted() && count <= 10) {
                count++
                delay(500.milliseconds)
            }
            if (count <= 10) {
                handleDeepLink(intent)
            }
        }
    }

    /**
     * Runs [block] once the navigation controller exists. An activity recreated while the app was
     * in the background, for example after a configuration change, gets it only at its first
     * composition, which can come after the intent that resumes an issuance. Gives up after about
     * five seconds.
     */
    private fun runWhenNavigationReady(block: () -> Unit) {
        if (routerHost.isNavigationReady()) {
            block()
            return
        }
        lifecycleScope.launch {
            var attempts = 0
            while (!routerHost.isNavigationReady() && attempts < 50) {
                attempts++
                delay(100.milliseconds)
            }
            if (routerHost.isNavigationReady()) {
                block()
            }
        }
    }

    /**
     * Opens or keeps the link or Digital Credential API request in [intent]. One that arrives during
     * a factory reset is dropped: the documents it would use are being deleted, and opening it would
     * take the user away from the reset screen.
     */
    private fun handleDeepLink(intent: Intent?, coldBoot: Boolean = false) {
        if (walletResetStatus.isResetting.value) {
            setIntent(Intent())
            return
        }
        hasDeepLink(intent?.data)?.let {
            if (it.type == DeepLinkType.ISSUANCE && !coldBoot) {
                val link = it.link
                runWhenNavigationReady {
                    handleDeepLinkAction(
                        routerHost.getNavController(),
                        link
                    )
                }
            } else if (
                it.type == DeepLinkType.CREDENTIAL_OFFER
                && !routerHost.userIsLoggedInWithDocuments()
                && routerHost.userIsLoggedInWithNoDocuments()
            ) {
                cacheIntent(intent)
                routerHost.popToIssuanceOnboardingScreen()
            } else if (it.type == DeepLinkType.OPENID4VP
                && routerHost.userIsLoggedInWithDocuments()
                && (routerHost.isScreenOnBackStackOrForeground(IssuanceScreens.AddDocument)
                        || routerHost.isScreenOnBackStackOrForeground(IssuanceScreens.DocumentOffer)
                        || routerHost.isScreenOnBackStackOrForeground(DashboardScreens.DocumentDetails))
            ) {
                handleDeepLinkAction(
                    routerHost.getNavController(),
                    DeepLinkAction(it.link, DeepLinkType.DYNAMIC_PRESENTATION)
                )
            } else if (it.type != DeepLinkType.ISSUANCE) {
                cacheIntent(intent)
                if (routerHost.userIsLoggedInWithDocuments()) {
                    routerHost.popToDashboardScreen()
                }
            }
            setIntent(Intent())
        } ?: hasIntentAction(intent)?.let {
            when (it.type) {
                IntentType.DC_API -> {
                    cacheIntent(it.intent)
                    if (routerHost.userIsLoggedInWithDocuments()) {
                        routerHost.popToDashboardScreen()
                    }
                }
            }
            setIntent(Intent())
        }
    }
}

@KoinViewModel
internal class EudiComponentActivityViewModel(
    private val prefKeys: PrefKeys,
    uuidProvider: UuidProvider
) : ViewModel() {

    private val sessionId: String = uuidProvider.provideUuid()

    private var flowStarted: Boolean = false
    private var pendingIntent: Intent? = null

    override fun onCleared() {
        getOrNullKoinScope(sessionId)?.close()
    }

    fun onCreate() {
        setSessionId()
    }

    fun onResume() {
        setSessionId()
    }

    fun onFlowStart() {
        flowStarted = true
    }

    fun cacheIntent(intent: Intent?) {
        pendingIntent = intent
    }

    fun getCachedIntent(): Intent? = pendingIntent

    fun hasFlowStarted(): Boolean = flowStarted

    private fun setSessionId() {
        runBlocking(Dispatchers.IO) {
            prefKeys.setSessionId(sessionId)
        }
    }
}