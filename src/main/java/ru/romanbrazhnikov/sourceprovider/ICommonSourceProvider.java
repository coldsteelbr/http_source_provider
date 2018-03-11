package ru.romanbrazhnikov.sourceprovider;

import io.reactivex.Single;

// TODO: USE THIS INTERFACE
public interface ICommonSourceProvider {
    Single<String> requestNext();
    boolean hasMore();
}
