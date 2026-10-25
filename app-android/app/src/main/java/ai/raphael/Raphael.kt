package ai.raphael

import android.content.Context
import ai.raphael.audio.AudioRouter
import ai.raphael.service.TurnController
import ai.raphael.wake.VadGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Process-wide singletons. The talk logic lives here (not in the screen) so it
 * keeps running in the background service when the UI is closed.
 */
object Raphael {
    lateinit var router: AudioRouter
        private set
    lateinit var controller: TurnController
        private set

    fun init(context: Context) {
        if (::controller.isInitialized) return
        val app = context.applicationContext
        router = AudioRouter(app)
        controller = TurnController(router, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate), VadGate(app))
    }
}
