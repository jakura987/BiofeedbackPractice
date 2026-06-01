package com.biofeedbacktest.basichilt.basic

import io.reactivex.Single

fun main(){

    Single.just(42)
        .subscribe(
            {value ->  println("onSuccess: $value") },
            { err -> println("onError: ${err.message}") }
        )


    Single.create<Int> {
        emitter -> emitter.onSuccess(43)
    }.subscribe(
        {value -> println("OnSuccess: $value")},
        { println("OnError: ${it.message}")}
    )
}