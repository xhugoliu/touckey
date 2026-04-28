package io.github.xhugoliu.touckey.input

import io.github.xhugoliu.touckey.core.model.ConnectionStatus
import io.github.xhugoliu.touckey.core.model.HostDevice
import io.github.xhugoliu.touckey.hid.HidGateway
import io.github.xhugoliu.touckey.hid.HidSendResult
import io.github.xhugoliu.touckey.session.HostControlState
import io.github.xhugoliu.touckey.session.HostOperationKind
import io.github.xhugoliu.touckey.session.HostPendingOperation
import io.github.xhugoliu.touckey.session.SessionCommandResult
import io.github.xhugoliu.touckey.session.SessionController
import io.github.xhugoliu.touckey.session.SessionHostCommand
import io.github.xhugoliu.touckey.session.SessionSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class QueuedActionDispatcherTest {
    @Test
    fun `rejects actions before a host is connected`() {
        val hidGateway = FakeHidGateway()
        val dispatcher =
            QueuedActionDispatcher(
                hidGateway = hidGateway,
                sessionController = FakeSessionController(SessionSnapshot.initial().copy(status = ConnectionStatus.Ready)),
            )

        val action = InputAction.MouseButtonClickAction(MouseButton.Left)
        val result = dispatcher.dispatch(action)

        assertFalse(result.accepted)
        assertEquals("当前还没有活跃主机连接，Left 点击 不会被发送。", result.message)
        assertNull(hidGateway.lastAction)
    }

    @Test
    fun `forwards actions to hid gateway once connected`() {
        val action = InputAction.ConsumerControlAction("PlayPause")
        val hidGateway =
            FakeHidGateway(
                result = HidSendResult(accepted = true, detail = "已发送 PlayPause。"),
            )
        val dispatcher =
            QueuedActionDispatcher(
                hidGateway = hidGateway,
                sessionController =
                    FakeSessionController(
                        SessionSnapshot.initial().copy(
                            status = ConnectionStatus.Ready,
                            host = testHost,
                            hostControl = HostControlState(currentHost = testHost),
                        ),
                    ),
            )

        val result = dispatcher.dispatch(action)

        assertTrue(result.accepted)
        assertEquals("已发送 PlayPause。", result.message)
        assertSame(action, hidGateway.lastAction)
    }

    @Test
    fun `rejects actions while a host operation is pending`() {
        val hidGateway = FakeHidGateway()
        val dispatcher =
            QueuedActionDispatcher(
                hidGateway = hidGateway,
                sessionController =
                    FakeSessionController(
                        SessionSnapshot.initial().copy(
                            status = ConnectionStatus.Ready,
                            host = testHost,
                            hostControl =
                                HostControlState(
                                    currentHost = testHost,
                                    pendingOperation =
                                        HostPendingOperation(
                                            kind = HostOperationKind.Disconnecting,
                                            targetAddress = testHost.address,
                                            targetName = testHost.name,
                                        ),
                                ),
                        ),
                    ),
            )

        val result = dispatcher.dispatch(InputAction.ConsumerControlAction("PlayPause"))

        assertFalse(result.accepted)
        assertEquals("当前还没有活跃主机连接，PlayPause 不会被发送。", result.message)
        assertNull(hidGateway.lastAction)
    }

    private class FakeHidGateway(
        private val result: HidSendResult = HidSendResult(accepted = true, detail = "ok"),
    ) : HidGateway {
        var lastAction: InputAction? = null

        override fun send(action: InputAction): HidSendResult {
            lastAction = action
            return result
        }
    }

    private class FakeSessionController(
        snapshot: SessionSnapshot,
    ) : SessionController {
        override val snapshots: StateFlow<SessionSnapshot> =
            MutableStateFlow(snapshot)

        override fun refreshState() {
        }

        override fun ensureRegistered(): SessionCommandResult = SessionCommandResult(false, "unused")

        override fun performHostCommand(command: SessionHostCommand): SessionCommandResult = SessionCommandResult(false, "unused")
    }

    private companion object {
        val testHost =
            HostDevice(
                name = "MacBook Pro",
                address = "00:11:22:33:44:55",
                platformLabel = "蓝牙主机",
            )
    }
}
