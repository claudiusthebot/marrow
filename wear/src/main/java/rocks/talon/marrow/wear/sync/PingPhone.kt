package rocks.talon.marrow.wear.sync

import android.content.Context
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import rocks.talon.marrow.shared.MarrowPaths

/** Sends a /marrow/ping to every connected node. The phone shows a toast. */
suspend fun pingPhone(context: Context): Boolean {
    val nodes = runCatching {
        Wearable.getNodeClient(context).connectedNodes.await()
    }.getOrNull().orEmpty()
    if (nodes.isEmpty()) return false
    var sent = false
    for (node in nodes) {
        runCatching {
            Wearable.getMessageClient(context)
                .sendMessage(node.id, MarrowPaths.PING, ByteArray(0))
                .await()
            sent = true
        }
    }
    return sent
}
