package com.biofeedbacktest.basichilt.basic

import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.disposables.Disposable

fun main() {

    val obs2 = Observable.create<Int> { emitter ->
        listOf(1, 2, 3).forEach { value ->
            value
        }
        emitter.onComplete()
    }


    //Define obs(Observable): represents a "data source" or "event stream"
    val obs: Observable<Int> = Observable
        .just(1, 2, 3)
        .filter { it > 2 }

    //Define value(Observer): represents a "consumer" or "subscriber" to receive and process events emitted by obs
    val value: Observer<Int> = object : Observer<Int> {
        override fun onSubscribe(d: Disposable) {
            println("subscribe")
        }

        override fun onNext(t: Int) {
            println("receive: $t")

        }

        override fun onError(e: Throwable) {
            println("error: ${e.message}")
        }

        override fun onComplete() {
            println("complete")
        }
    }

    //Register Observer(value) to Observable(obs), establish a connection, and start data
    obs.subscribe(value)


}
