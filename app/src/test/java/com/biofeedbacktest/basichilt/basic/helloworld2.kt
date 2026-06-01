package com.biofeedbacktest.basichilt.basic

import io.reactivex.Observable
import java.lang.RuntimeException

fun main(){
    Observable.just(1, 2, 3)
        .map { t ->
            if (t == 3) throw RuntimeException("encounter 3, error")
            else t
        }
        .subscribe(
            { value -> println("receive: $value") },
            { e -> println("Error:${e.message}") },
            { println("Complete") },
        )
}


