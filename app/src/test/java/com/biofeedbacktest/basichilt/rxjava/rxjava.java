package com.biofeedbacktest.basichilt.rxjava;

import org.junit.Test;

import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;

public class rxjava {

    @Test
    public void testFromCallableSync() {
        Single.fromCallable(() -> {
            System.out.println("sync thread=" + Thread.currentThread().getName());
            return "A";

        }).subscribe(result -> System.out.println("result: " + result));

    }

    @Test
    public void testFromCallableAsync() throws InterruptedException {
        Single.fromCallable(() -> {
                    System.out.println("async thread=" + Thread.currentThread().getName());
                    return "B";
                })
                .subscribeOn(Schedulers.io())
                .subscribe(result -> System.out.println("async result" + result));
        Thread.sleep(500);
    }
}