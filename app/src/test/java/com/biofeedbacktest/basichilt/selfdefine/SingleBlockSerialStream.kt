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

fun sendInChunks_WithCreate2(chunks: List<ByteArray>): Single<String> {
    //emitter: 发送信号给下游订阅者
    val single: Single<String> = Single.create<String> { emitter ->
        Observable.fromIterable(chunks)
            .concatMapSingle { chunk ->
                stepA(chunk)
                    //todo 和map的区别
                    .flatMap { stepB() }
                    .doOnSuccess { ack ->
                        println("← ack for ${chunk.size}B: $ack")
                    }
                    .doOnError{err -> println(err) }
            }
            .doOnComplete{emitter.onSuccess("ALL_DONE")}
            .doOnError{error -> emitter.onError(error)}
            .subscribe()
    }

    return single

}


fun sendInChunks_LastAck(chunks: List<ByteArray>): Single<String> {
    return Observable.fromIterable(chunks)
        .concatMapSingle { chunk ->
            stepA(chunk)
                .flatMap { stepB() }  // Single<String>
        }
        .last("ALL_DONE")        // 如果流发了 N 个 ACK，就取最后一个；若没有任何元素，就返回 "ALL_DONE"
}

fun sendInChunks(chunks: List<ByteArray>): Single<String> {
    return Observable.fromIterable(chunks)
        .concatMapSingle { chunk ->
            stepA(chunk)
                .flatMap { stepB() }
                .doOnSuccess { ack -> println("← ack for ${chunk.size}B: $ack") }
        }
        .ignoreElements()                 // 等所有 Single 都完成
        .andThen(Single.just("ALL_DONE")) // 再发出最终结果

}





// ====== 试跑 ======
@SuppressLint("CheckResult")
fun main() {
    val chunks = listOf(
        ByteArray(5), ByteArray(7), ByteArray(3)
    )
    sendInChunks(chunks)
        .subscribeOn(Schedulers.io())
        .observeOn(Schedulers.io())
        .subscribe(
            { result -> println("FINAL: $result") },
            { e -> println("ERROR: ${e.message}") }
        )
    Thread.sleep(2000)
}

