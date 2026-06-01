package com.biofeedbacktest.basichilt

import android.annotation.SuppressLint
import io.reactivex.Observable

@SuppressLint("CheckResult")
fun main(){
    Observable.create<Int>{emitter ->
        emitter.onNext(1)
        emitter.onNext(2)
        emitter.onNext(3)
    }
        .map { integer ->
            "use Map transformationOperator make event $integer from $integer to String type $integer"
        }
        .subscribe{s ->
            println(s)
        }


}