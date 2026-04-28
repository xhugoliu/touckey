package io.github.xhugoliu.touckey.feature.control

import io.github.xhugoliu.touckey.core.model.ConnectionStatus
import io.github.xhugoliu.touckey.core.model.HostDevice
import io.github.xhugoliu.touckey.input.ActionDispatcher
import io.github.xhugoliu.touckey.input.DispatchResult
import io.github.xhugoliu.touckey.input.InputAction
import io.github.xhugoliu.touckey.session.HostControlState
import io.github.xhugoliu.touckey.session.HostOperationKind
import io.github.xhugoliu.touckey.session.HostPendingOperation
import io.github.xhugoliu.touckey.session.SessionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlPresenterTest {
    private val presenter = ControlPresenter(NoopDispatcher)

    @Test
    fun `connected host drives connection panel and input availability from host control state`() {
        val state =
            presenter.buildUiState(
                readySnapshot().copy(
                    host = mac,
                    hostControl =
                        HostControlState(
                            currentHost = mac,
                            recentHosts = listOf(mac),
                        ),
                ),
            )

        assertTrue(state.isInputEnabled)
        assertNull(state.setupPrompt)
        assertEquals("MacBook Pro", state.connection.label)
        assertEquals(ControlStatusAccent.Positive, state.connection.accent)
        assertEquals(ControlConnectionActionId.Disconnect, state.connection.actions.single().id)
    }

    @Test
    fun `pending host operation disables input without changing setup prompt behavior`() {
        val state =
            presenter.buildUiState(
                readySnapshot().copy(
                    host = mac,
                    hostControl =
                        HostControlState(
                            currentHost = mac,
                            recentHosts = listOf(mac),
                            pendingOperation =
                                HostPendingOperation(
                                    kind = HostOperationKind.Disconnecting,
                                    targetAddress = mac.address,
                                    targetName = mac.name,
                                ),
                        ),
                ),
            )

        assertFalse(state.isInputEnabled)
        assertNull(state.setupPrompt)
        assertEquals("Disconnecting", state.connection.label)
        assertFalse(state.connection.actions.single().enabled)
    }

    @Test
    fun `ready state offers reconnect action separately from setup prompt actions`() {
        val state =
            presenter.buildUiState(
                readySnapshot().copy(
                    hostControl =
                        HostControlState(
                            recentHosts = listOf(mac),
                        ),
                ),
            )

        assertFalse(state.isInputEnabled)
        assertEquals("Pair with your desktop", state.setupPrompt?.title)
        assertEquals(ControlEnvironmentActionId.MakeDiscoverable, state.setupPrompt?.actions?.first()?.id)
        assertEquals(ControlConnectionActionId.ReconnectLast, state.connection.actions.first().id)
    }

    private fun readySnapshot(): SessionSnapshot =
        SessionSnapshot.initial().copy(
            status = ConnectionStatus.Ready,
            detail = "ready",
            hasRequiredPermissions = true,
            isBluetoothEnabled = true,
            isProfileReady = true,
            isAppRegistered = true,
            adapterName = "Touckey Phone",
        )

    private object NoopDispatcher : ActionDispatcher {
        override fun dispatch(action: InputAction): DispatchResult = DispatchResult(true, "ok")
    }

    private companion object {
        val mac =
            HostDevice(
                name = "MacBook Pro",
                address = "00:11:22:33:44:55",
                platformLabel = "蓝牙主机",
            )
    }
}
