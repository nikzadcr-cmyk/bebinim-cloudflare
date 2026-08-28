package com.app.bebinim.data.api

import kotlinx.coroutines.flow.MutableSharedFlow

/** Simple event bus to force logout from anywhere (e.g. token refresh failure). */
object AuthEventBus {
    val logoutRequired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun emitLogoutRequired() {
        logoutRequired.tryEmit(Unit)
    }
}
