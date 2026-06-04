package com.intellizondy.biofeedback.network.tcp

import io.reactivex.Completable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TCP Server 管理层。
 *
 * 正式项目风格：
 * 1. ensureStarted() 负责对外幂等启动
 * 2. startIfNeeded() 返回 Completable，内部真正创建 SimpleTcpServer
 * 3. 启动任务丢到 Schedulers.io()
 * 4. SimpleTcpServer 只负责 TCP 收发；协议解析放到上层 onReceiveListener / parser
 */
@Singleton
class WifiTcpServerManager @Inject constructor() {

    companion object {
        private const val TAG = "WifiTcpServerManager"
        private const val DEFAULT_PORT = 8883
    }

    private var server: SimpleTcpServer? = null

    private val startStopDisposables = CompositeDisposable()

    private val _running = BehaviorSubject.createDefault(false)

    @Volatile
    private var selectedPeer: String? = null

    @Volatile
    private var onReceiveListener: ((peer: String, data: ByteArray) -> Unit)? = null

    fun runningObservable(): BehaviorSubject<Boolean> {
        return _running
    }

    /**
     * 对外启动入口。
     *
     * 这个方法本身不阻塞主线程；
     * 真正的 startIfNeeded 会在 IO 线程执行。
     */
    fun ensureStarted(port: Int = DEFAULT_PORT) {
        if (server?.isRunning() == true) {
            Timber.tag(TAG).i("TCP server already running on port=%d", port)
            return
        }

        // 清掉上一次可能残留的启动/停止订阅
        startStopDisposables.clear()

        startStopDisposables.add(
            startIfNeeded(port)
                .subscribeOn(Schedulers.io())
                .subscribe(
                    {
                        Timber.tag(TAG).i("TCP server ready on port=%d", port)

                    },
                    { e ->
                        Timber.tag(TAG).w(e, "TCP server start failed port=%d", port)
                        _running.onNext(false)
                    }
                )
        )
    }

    /**
     * 真正创建并启动 SimpleTcpServer。
     *
     * 注意：
     * SimpleTcpServer.start() 内部已经会开启 acceptLoop 线程；
     * 这里包 Completable 是为了保持正式项目风格，便于以后接 Rx 链路、心跳、状态监听。
     */
    @Synchronized
    fun startIfNeeded(port: Int = DEFAULT_PORT): Completable =
        Completable.fromAction {
            if (server?.isRunning() == true) {
                return@fromAction
            }

            Timber.tag(TAG).i("Starting TCP server on 0.0.0.0:%d", port)

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
                        "TCP_IN peer=%s len=%d hex=%s",
                        peer,
                        copy.size,
                        SimpleTcpServer.toHex(copy, copy.size)
                    )

                    try {
                        // 这里先只转发原始 TCP 数据。
                        // 后面正式协议可以在这里接：
                        // val filtered = stripControlAndEmit(peer, copy)
                        // if (filtered.isNotEmpty()) _incoming.onNext(TcpPacket(peer, filtered))
                        onReceiveListener?.invoke(peer, copy)
                    } catch (t: Throwable) {
                        Timber.tag(TAG).e(t, "onReceiveListener error, peer=%s", peer)
                    }
                }
            }

            // SimpleTcpServer 是 Java 类，Kotlin 调 Java 构造器不要用命名参数。
            server = SimpleTcpServer(port, listener).also {
                it.start()
            }

            _running.onNext(true)
        }.subscribeOn(Schedulers.io())

    /**
     * 对外停止入口。
     */
    fun ensureStopped() {
        startStopDisposables.clear()

        stopIfNeeded()
            .subscribeOn(Schedulers.io())
            .subscribe(
                {
                    Timber.tag(TAG).i("TCP server stopped")
                },
                { e ->
                    Timber.tag(TAG).w(e, "TCP server stop failed")
                }
            )
            .let { startStopDisposables.add(it) }
    }

    /**
     * 真正停止 TCP Server。
     */
    @Synchronized
    fun stopIfNeeded(): Completable =
        Completable.fromAction {
            server?.stop()
            server = null

            selectedPeer = null
            onReceiveListener = null

            _running.onNext(false)
        }.subscribeOn(Schedulers.io())

    fun isRunning(): Boolean {
        return server?.isRunning() == true
    }

    fun getClientPeers(): List<String> {
        return server?.getClientPeers().orEmpty()
    }

    fun getClientDisplayTexts(): List<String> {
        return server?.getClientDisplayTexts().orEmpty()
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
