package com.intellizondy.biofeedback.network.tcp

import android.os.SystemClock
import java.net.Socket


class ClientConn (
    val socket: Socket
){
    @Volatile
    var peer: String = "${socket.inetAddress.hostAddress}:${socket.port}"

    @Volatile
    var deviceId: String = ""

    @Volatile
    var deviceName: String = ""

    @Volatile
    var connectedAt: Long = SystemClock.elapsedRealtime()

    @Volatile
    var deviceType: Int = -1

    val ip: String
        get() = socket.inetAddress?.hostAddress ?: ""
}
