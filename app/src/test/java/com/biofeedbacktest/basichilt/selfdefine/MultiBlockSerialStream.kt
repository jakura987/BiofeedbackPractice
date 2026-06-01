package com.biofeedbacktest.basichilt.selfdefine

import android.annotation.SuppressLint
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit

// ====== 你要替换的两步（这里用延时模拟）======
private fun stepA(chunk: ByteArray): Single<Unit> =
    Single.timer(50, TimeUnit.MILLISECONDS).map { /* 执行动作 */ Unit }

private fun stepB(): Single<String> =
    Single.timer(80, TimeUnit.MILLISECONDS).map { "ACK" }

// ====== 模板 A：外层 Single.create 包住内层串行流 ======
fun sendInChunks_WithCreate(chunks: List<ByteArray>): Single<String> =
    Single.create { emitter ->
        val d: Disposable =
            Observable.fromIterable(chunks)          // Observable<ByteArray>
                .concatMapSingle { chunk ->          // 严格按顺序
                    stepA(chunk)                     // Single<Unit>
                        .flatMap { stepB() }         // Single<String> (ACK)
                        .doOnSuccess { ack -> println("← ack for ${chunk.size}B: $ack") }
                }
                .doOnComplete { emitter.onSuccess("ALL_DONE") }
                .doOnError    { e -> emitter.onError(e) }
                .subscribe()

        // 让上游取消时，能取消这条内部订阅（避免泄漏）
        emitter.setDisposable(d)
    }



// ====== 试跑 ======
@SuppressLint("CheckResult")
fun main() {
    val chunks = listOf(
        ByteArray(5), ByteArray(7), ByteArray(3)
    )
    sendInChunks_WithCreate(chunks)
        .subscribeOn(Schedulers.io())
        .observeOn(Schedulers.io())
        .subscribe(
            { println("FINAL: $it") },
            { e -> println("ERROR: ${e.message}") }
        )
    Thread.sleep(600)
}
