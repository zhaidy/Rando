package com.personal.rando.ride

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 骑行记录一条一个 JSON 文件，放在应用私有目录 */
class RideStore(context: Context) {

    private val dir = File(context.applicationContext.filesDir, "rides")

    suspend fun save(ride: Ride) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        // 先写临时文件再改名，写到一半被杀也不会留下坏文件
        val tmp = File(dir, "${ride.id}.json.tmp")
        tmp.writeText(RideJson.encode(ride))
        tmp.renameTo(File(dir, "${ride.id}.json"))
    }

    /** 新的在前；读不出来的文件跳过 */
    suspend fun list(): List<Ride> = withContext(Dispatchers.IO) {
        dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty()
            .mapNotNull { f -> runCatching { RideJson.decode(f.readText()) }.getOrNull() }
            .sortedByDescending { it.id }
    }

    suspend fun get(id: Long): Ride? = withContext(Dispatchers.IO) {
        File(dir, "$id.json").takeIf { it.exists() }?.let { runCatching { RideJson.decode(it.readText()) }.getOrNull() }
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        File(dir, "$id.json").delete()
    }
}
