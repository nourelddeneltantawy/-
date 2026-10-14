package com.example.data

import android.util.Log

enum class OperationType {
    GET,
    LIST,
    CREATE,
    UPDATE,
    DELETE,
    WRITE
}

fun handleFirestoreError(error: Exception, operation: OperationType, path: String) {
    Log.w("FirestoreError", "[$operation] failed at path: $path: ${error.message}", error)
}
