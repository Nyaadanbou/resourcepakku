package cc.mewcraft.resourcepakku.util

import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

fun computeHash(uri: URI, exec: Executor): CompletableFuture<String> {
    val result = CompletableFuture<String>()

    exec.execute(Runnable {
        try {
            val url = uri.toURL()
            val conn = url.openConnection()
            conn.addRequestProperty("User-Agent", "resrcpack")
            conn.getInputStream().use { input ->
                val digest = MessageDigest.getInstance("SHA-1")
                val buf = ByteArray(8192)
                var read: Int
                while ((input.read(buf).also { read = it }) != -1) {
                    digest.update(buf, 0, read)
                }
                result.complete(bytesToString(digest.digest()))
            }
        } catch (ex: IOException) {
            result.completeExceptionally(ex)
        } catch (ex: NoSuchAlgorithmException) {
            result.completeExceptionally(ex)
        }
    })

    return result
}

fun bytesToString(arr: ByteArray): String {
    val builder = StringBuilder(arr.size * 2)
    val fmt = Formatter(builder, Locale.ROOT)
    for (i in arr.indices) {
        fmt.format("%02x", arr[i].toInt() and 0xff)
    }
    return builder.toString()
}