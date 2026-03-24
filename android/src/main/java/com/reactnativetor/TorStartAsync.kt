package com.reactnativetor

import android.os.AsyncTask
import android.util.Log
import com.facebook.react.bridge.*
import java.io.File
import java.io.IOException
import com.sifir.tor.OwnedTorService
import com.sifir.tor.TorServiceParam

class StartParam(
  val socksPort: Int,
  var path: String,
  val timeoutMs: Double
);

class TorBridgeStartAsync constructor(
  private val param: StartParam,
  private val onSuccess: (service: OwnedTorService) -> Unit,
  private val onError: (e: Throwable) -> Unit
) {
  fun run() {
    try {
      // Clean stale Tor state files to prevent SIGSEGV in native init.
      // The Rust OwnedTorService_init crashes when it encounters pre-existing
      // memory-mapped files (log, lock, cached-*) from a previous run.
      cleanStaleTorState(param.path)
      Log.d("TorBridge", "Starting Tor with ${param.path} ${param.socksPort} ${param.timeoutMs}")
      val torParam = TorServiceParam(param.path, param.socksPort,param.timeoutMs.toLong())
      val ownedTor = OwnedTorService(torParam)
      // OwnedTorService constructor takes ownership of the native pointer and
      // zeros torParam.mNativeObj. Call delete() explicitly so the GC finalizer
      // doesn't attempt a double-free on the already-consumed native memory.
      torParam.delete()
      onSuccess(ownedTor);
    } catch (e: Exception) {
      Log.d("TorBridge:StartAsync", "error onPostExecute$e")
      onError(e as Throwable);
    }
  }

  private fun cleanStaleTorState(cachePath: String) {
    val torDir = File(cachePath)
    if (!torDir.exists()) return
    val staleFiles = torDir.listFiles { file ->
      file.name.startsWith("tor_log") ||
      file.name == "lock" ||
      file.name == "control_auth_cookie" ||
      file.name.startsWith("cached-") ||
      file.name == "state" ||
      file.name == "torrc"
    } ?: return
    for (file in staleFiles) {
      try {
        if (file.isDirectory) file.deleteRecursively() else file.delete()
        Log.d("TorBridge", "Cleaned stale Tor file: ${file.name}")
      } catch (e: Exception) {
        Log.w("TorBridge", "Failed to clean ${file.name}: $e")
      }
    }
  }
}
