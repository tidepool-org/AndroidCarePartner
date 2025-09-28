package org.tidepool.carepartner

internal fun <R, T> Result<List<T>>.mapList(block: (T) -> R): Result<List<R>> =
    map { list -> list.map { block(it) } }

internal fun <T> Result<List<T>>.filterList(block: (T) -> Boolean): Result<List<T>> =
    map { list -> list.filter { block(it) } }