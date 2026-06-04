package com.intellizondy.biofeedback.network.tcp

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TCP Server 管理层。
 *
 * 职责：
 * 1. 单例管理 SimpleTcpServer
 * 2. ensureStarted / ensureStopped
 * 3. 保存 selectedPeer
 * 4. 对外提供 sendToSelected / sendToPeer / broadcast
 * 5. 收到 TCP 原始数据后，交给上层协议解析逻辑处理
 *
 * 注意：
 * SimpleTcpServer 是 Java 类时，Kotlin 调用 Java 构造方法不能使用命名参数：
 * SimpleTcpServer(port = port, listener = xxx) ❌
 * SimpleTcpServer(port, xxx) ✅
 */
@Singleton
class WifiTcpServerManager @Inject constructor() {

    companion object {
        private const val TAG = "WifiTcpServerManager"
        private const val DEFAULT_PORT = 8883
    }

    private var server: SimpleTcpServer? = null

    @Volatile
    private var selectedPeer: String? = null

    /**
     * 可选：把 TCP 收到的原始数据继续抛给业务层。
     *
     * 后面你可以在 Activity / ViewModel / ProtocolParser 那边设置：
     * wifiTcpServerManager.setOnReceiveListener { peer, data ->
     *     // stripControlAndEmit(peer, data)
     * }
     */
    @Volatile
    private var onReceiveListener: ((peer: String, data: ByteArray) -> Unit)? = null

    @Synchronized
    fun ensureStarted(port: Int = DEFAULT_PORT) {
        if (server?.isRunning == true) {
            Timber.tag(TAG).i("TCP server already running")
            return
        }

        val listener = object : SimpleTcpServer.Listener {

            override fun onClientConnected(peer: String) {
                Timber.tag(TAG).i("client connected: %s", peer)

                if (selectedPeer == null) {
                    selectedPeer = peer
                    Timber.tag(TAG).i("auto selected peer: %s", peer)
                }
            }

            override fun onClientDisconnected(peer: String) {
                Timber.tag(TAG).i("client disconnected: %s", peer)

                if (selectedPeer == peer) {
                    selectedPeer = null
                    Timber.tag(TAG).w("selected peer disconnected, clear selectedPeer")
                }
            }

            override fun onReceive(peer: String, data: ByteArray, length: Int) {
                val copy = data.copyOf(length)

                Timber.tag(TAG).d(
                    "onReceive peer=%s len=%d head=%s",
                    peer,
                    length,
                    SimpleTcpServer.toHex(copy, minOf(copy.size, 16))
                )

                // 这里不要在 SimpleTcpServer 里解析协议。
                // manager 可以先做轻量分流，也可以继续抛给外部 parser。
                try {
                    onReceiveListener?.invoke(peer, copy)
                } catch (t: Throwable) {
                    Timber.tag(TAG).e(t, "onReceiveListener error, peer=%s", peer)
                }
            }
        }

        // SimpleTcpServer 是 Java 类，所以这里不要写 named arguments。
        server = SimpleTcpServer(port, listener)

        val ok = server?.start() == true
        Timber.tag(TAG).i("ensureStarted result=%s port=%d", ok, port)
    }

    @Synchronized
    fun ensureStopped() {
        server?.stop()
        server = null
        selectedPeer = null
        onReceiveListener = null

        Timber.tag(TAG).i("TCP server stopped")
    }

    fun isRunning(): Boolean {
        return server?.isRunning == true
    }

    fun getClientPeers(): List<String> {
        return server?.clientPeers.orEmpty()
    }

    fun getClientDisplayTexts(): List<String> {
        return server?.clientDisplayTexts.orEmpty()
    }

    fun selectPeer(peer: String) {
        if (peer.isBlank()) {
            Timber.tag(TAG).w("selectPeer ignored: blank peer")
            return
        }

        selectedPeer = peer
        Timber.tag(TAG).i("selectedPeer=%s", peer)
    }

    fun getSelectedPeer(): String? {
        return selectedPeer
    }

    fun sendToSelected(data: ByteArray): Boolean {
        val peer = selectedPeer

        if (peer.isNullOrBlank()) {
            Timber.tag(TAG).w("sendToSelected failed: selectedPeer is null")
            return false
        }

        return sendToPeer(peer, data)
    }

    fun sendToPeer(peer: String, data: ByteArray): Boolean {
        return server?.sendTo(peer, data) == true
    }

    fun broadcast(data: ByteArray): Boolean {
        return server?.send(data) == true
    }

    fun closePeer(peer: String): Boolean {
        if (selectedPeer == peer) {
            selectedPeer = null
        }

        return server?.closePeer(peer) == true
    }

    fun setOnReceiveListener(listener: ((peer: String, data: ByteArray) -> Unit)?) {
        onReceiveListener = listener
    }

    fun clearOnReceiveListener() {
        onReceiveListener = null
    }
}
