package com.biofeedbacktest.basichilt

import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit


data class TaskResult(val name: String, val success: Boolean)

private val disposables = CompositeDisposable()


fun main() {
    val tasks = listOf("1","2","3","4","5")

    Observable.fromIterable(tasks)
        .concatMapSingle { task ->
            sendTask(task)
                .subscribeOn(Schedulers.io())
                .delay(100, TimeUnit.MILLISECONDS)
        }
        .observeOn(Schedulers.trampoline())
        .blockingSubscribe()
}


fun sendTask(task: String): Single<String> {
    return Single.fromCallable {
        println("→ Sending task $task")
        task
    }
        .subscribeOn(Schedulers.newThread())
        .doOnSuccess { ack ->
            println("← ACK for task $ack")
            println("success: $ack")
        }
}
