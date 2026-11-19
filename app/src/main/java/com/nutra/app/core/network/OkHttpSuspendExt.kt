package com.nutra.app.core.network

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resumeWithException

internal suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation {
        runCatching { cancel() }
    }
    enqueue(
        object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) {
                    cont.resume(response) { _, res, _ ->
                        runCatching { res.close() }
                    }
                } else {
                    runCatching { response.close() }
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) {
                    cont.resumeWithException(e)
                }
            }
        },
    )
}
