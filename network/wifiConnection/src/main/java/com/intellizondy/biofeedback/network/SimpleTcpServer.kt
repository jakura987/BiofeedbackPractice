package com.intellizondy.biofeedback.network

import android.os.SystemClock
import com.intellizondy.biofeedback.network.tcp.ClientConn
import timber.log.Timber
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SimpleTcpServer(
    private val port: Int,
    private val listener: Listener
){
    companion object{
        private const val TAG = "TAG"
        private const val BUFFER_SIZE = 4096
    }

    interface Listener{
        fun onClientConnected(peer: String)
        fun onClientDisconnected(peer: String)
        fun onReceive(peer: String, data: ByteArray, length: Int)
    }

    private val pool: ExecutorService = Executors.newCachedThreadPool()
    //给多线程共享变量用的
    @Volatile
    private var running = false

    private var server: ServerSocket ? = null

    private val clients = CopyOnWriteArrayList<ClientConn>()


    @Synchronized
    fun start(): Boolean {
        if (running) {
            Timber.tag(TAG).i("start() ignored: already running")
            return false
        }

        running = true

        pool.execute {
            try {
                Timber.tag(TAG).i("binding port $port ...")

                server = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(port))
                }

                Timber.tag(TAG).i("server started on port $port")


                while (running) {
                    Timber.tag(TAG).i("accept() waiting...")

                    val socket = server?.accept() ?: break
                    socket.tcpNoDelay = true
                    socket.keepAlive = true

                    val conn = ClientConn(socket)

                    Timber.tag(TAG).i("accepted ${conn.peer}")

                    clients.add(conn)
                    removeOldClientsWithSameIp(conn)

                    Timber.tag(TAG).i("client count=${clients.size}")

                    pool.execute {
                        handle(conn)
                    }
                }
            } catch (e: IOException) {
                Timber.tag(TAG).w("accept loop end: ${e.message}")
            } finally {
                running = false
                Timber.tag(TAG).i("server stopped (loop exit)")
            }
        }

        return true
    }

    private fun handle(conn: ClientConn) {
        val socket = conn.socket
        val peer = conn.peer

        Timber.tag(TAG).i("handle() enter: $peer")

        var lastReadAtMs = 0L

        try {
            val input = socket.getInputStream()
            val buffer = ByteArray(BUFFER_SIZE)

            while (running && !socket.isClosed) {
                val n = input.read(buffer)

                Timber.tag(TAG).i("read() return n=$n, peer=$peer")

                if (n == -1) {
                    Timber.w(TAG, "peer EOF, peer=$peer")
                    break
                }

                if (n <= 0) {
                    continue
                }

                val now = SystemClock.elapsedRealtime()
                if (lastReadAtMs != 0L) {
                    val dt = now - lastReadAtMs
                    Timber.tag(TAG).d("READ_GAP dt=${dt}ms, n=$n, peer=$peer")
                }
                lastReadAtMs = now

                val copy = buffer.copyOf(n)

                Timber.tag(TAG).i("recv [$peer]: $n bytes")
                Timber.tag(TAG).i("hex : ${copy.toHexString()}")


                if (looksLikeIdentityFrame(copy)) {
                    parseIdentityFrame(conn, copy)
                    continue
                }

                try {
                    listener.onReceive(peer, copy, copy.size)
                } catch (e: Throwable) {
                    Timber.tag(TAG).e(e, "listener parse error, keep socket alive, peer=$peer")
                }
            }

            Timber.tag(TAG).i("peer closed write: $peer")
        } catch (e: Exception) {
            Timber.tag(TAG).w("conn error [$peer]: ${e.javaClass.simpleName} ${e.message}")
        } finally {
            Timber.tag(TAG).i("handle() leave: $peer")
            closeConn(conn)
            Timber.tag(TAG).i("client removed, left=${clients.size}")
        }
    }

    @Synchronized
    fun send(data: ByteArray): Boolean {
        if (clients.isEmpty()) {
            Timber.tag(TAG).w("send() failed: no active client")
            return false
        }

        var anyOk = false

        for (conn in clients) {
            val socket = conn.socket

            try {
                if (socket.isClosed || !socket.isConnected) {
                    closeConn(conn)
                    continue
                }

                val out = socket.getOutputStream()
                out.write(data)
                out.flush()

                anyOk = true
                Timber.tag(TAG).i("sent to ${conn.peer}, len=${data.size}")
            } catch (e: IOException) {
                Timber.tag(TAG).w("send() error to ${conn.peer}: ${e.message}, removing client")
                closeConn(conn)
            } catch (e: Exception) {
                Timber.tag(TAG).w("send() unexpected error to ${conn.peer}: ${e.message}")
                closeConn(conn)
            }
        }

        Timber.tag(TAG).w("send() complete, anyOk=$anyOk, clientsLeft=${clients.size}")
        return anyOk
    }

    @Synchronized
    fun sendTo(peer: String, data: ByteArray): Boolean {
        Timber.tag(TAG).w("sendTo target=$peer, clients=${getClientPeers()}, len=${data.size}, head=${data.take(16).toByteArray().toHexString()}")

        for (conn in clients) {
            val socket = conn.socket

            try {
                if (socket.isClosed) {
                    closeConn(conn)
                    continue
                }

                if (conn.peer != peer) {
                    continue
                }

                val out = socket.getOutputStream()
                out.write(data)
                out.flush()

                Timber.tag(TAG).w("sendTo OK peer=$peer, len=${data.size}")
                return true
            } catch (e: Exception) {
                Timber.tag(TAG).w("sendTo error peer=$peer: ${e.message}")
                closeConn(conn)
                return false
            }
        }

        Timber.tag(TAG).w("sendTo failed: peer not found, target=$peer, clients=${getClientPeers()}")
        return false
    }

    @Synchronized
    fun closePeer(peer: String): Boolean {
        val conn = clients.firstOrNull { it.peer == peer } ?: return false

        closeConn(conn)

        Timber.tag(TAG).i("closePeer: $peer, left=${clients.size}")
        return true
    }

    @Synchronized
    fun stop() {
        running = false

        try {
            server?.close()
        } catch (_: Exception) {
        }

        server = null

        clients.forEach { conn ->
            closeConn(conn)
        }

        clients.clear()
        pool.shutdownNow()

        Timber.tag(TAG).i("stop() done")
    }

    fun isRunning(): Boolean {
        return running
    }

    fun getClientPeers(): List<String> {
        return clients.mapNotNull { conn ->
            val socket = conn.socket
            if (!socket.isClosed && socket.inetAddress != null) {
                conn.peer
            } else {
                null
            }
        }
    }

    fun getClientDisplayTexts(): List<String> {
        return clients.mapNotNull { conn ->
            val socket = conn.socket
            if (!socket.isClosed && socket.inetAddress != null) {
                val id = conn.deviceId.ifEmpty { "?" }
                "设备ID[$id] : ${conn.peer}"
            } else {
                null
            }
        }
    }

    private fun removeOldClientsWithSameIp(newConn: ClientConn) {
        val newIp = newConn.ip
        if (newIp.isEmpty()) return

        for (old in clients) {
            if (old === newConn) continue
            if (old.ip != newIp) continue

            Timber.tag(TAG).w("same ip reconnect: remove old client ${old.peer} -> keep new ${newConn.peer}")
            closeConn(old)
        }
    }

    private fun closeConn(conn: ClientConn) {
        clients.remove(conn)

        try {
            if (!conn.socket.isClosed) {
                conn.socket.close()
            }
        } catch (_: Exception) {
        }
    }

    private fun looksLikeIdentityFrame(data: ByteArray): Boolean {
        return data.size == 31 &&
                data[0].u8() == 0x5A &&
                data[1].u8() == 0xA5 &&
                data[2].u8() == 0x1B &&
                data[3].u8() == 0x13 &&
                data[4].u8() == 0x00
    }

    private fun parseIdentityFrame(conn: ClientConn, data: ByteArray) {
        val id = (data[5].u8() shl 8) or data[6].u8()

        conn.deviceId = id.toString()
        conn.deviceName = readAsciiTrimZero(data, 13, 16)
        conn.deviceType = data[29].u8()

        Timber.tag(TAG).i("identity updated: peer=${conn.peer}, id=${conn.deviceId}, name=${conn.deviceName}, type=${conn.deviceType}")
    }

    private fun readAsciiTrimZero(data: ByteArray, offset: Int, len: Int): String {
        val end = offset + len
        var realEnd = offset

        while (realEnd < end && data[realEnd].toInt() != 0x00) {
            realEnd++
        }

        return String(
            data,
            offset,
            realEnd - offset,
            StandardCharsets.US_ASCII
        ).trim()
    }

    private fun Byte.u8(): Int {
        return toInt() and 0xFF
    }

    private fun ByteArray.toHexString(): String {
        return joinToString(" ") { "%02X".format(it.u8()) }
    }


}