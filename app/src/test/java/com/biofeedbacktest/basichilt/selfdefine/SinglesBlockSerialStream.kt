package com.biofeedbacktest.basichilt.selfdefine

import android.annotation.SuppressLint
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit

// ====== 你要替换的两步（这里用延时模拟）======
private fun stepA(chunk: ByteArray): Single<Unit> =
    Single.timer(50, TimeUnit.MILLISECONDS).map { /* 执行动作 */ Unit }


private fun stepB(): Single<String> {
    var single: Single<String> = Single.timer(80, TimeUnit.MILLISECONDS).map { "ACK" }
    return single
}


// ====== 模板 A：外层 Single.create 包住内层串行流 ======
//create + 内部 Observable → 内部要 .subscribe()
//直接链式 Rx → 外部 .subscribe() 就够。

fun sendInChunks_EmitAllAcks(chunks: List<ByteArray>): Observable<String> {
    //emitter: 发送信号给下游订阅者
    var observable: Observable<String> = Observable.fromIterable(chunks)
        .concatMapSingle { chunk ->
            stepA(chunk)
                .flatMap { stepB() }
                .doOnSuccess{ack -> println("chunk size = ${chunk.size}:$ack") }
        }
    return observable

}

//todo test项目里的方法
//有内部流要手动驱动（Observable.fromIterable…）→ 用 Single.create{…} 时要在内部 .subscribe()。
//
//只有一次同步调用 → 在 create 内直接 emitter.onSuccess/onError，不需要 .subscribe()。
//
//doOnComplete 属于 Observable，Single 只有 doOnSuccess／doOnError／doFinally。



// ====== 试跑 ======
@SuppressLint("CheckResult")
fun main() {
    val chunks = listOf(
        ByteArray(5), ByteArray(7), ByteArray(3)
    )
    sendInChunks_EmitAllAcks(chunks)
        .subscribeOn(Schedulers.io())
        .observeOn(Schedulers.io())
        .subscribe(
            { ack -> println("MAIN got ACL: $ack") },
            { e -> println("ERROR: ${e.message}") },
            { println("MAIN complete all chunks") }
        )
    Thread.sleep(600)
}

