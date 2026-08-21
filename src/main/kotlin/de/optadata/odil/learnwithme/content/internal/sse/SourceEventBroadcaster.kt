package de.optadata.odil.learnwithme.content.internal.sse

import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * B1: SSE-Fortschritt für `GET /sources/{id}/events`. In-Memory-Registry pro Prozess.
 *
 * ⚠ Bekannte Grenze: Läuft der Ingestion-Job in einer anderen Prozessinstanz als der,
 * die die SSE-Verbindung hält (§6.2: API und Worker als getrennte Rollen desselben
 * Artefakts), kommen Events dort nicht an. Im `local`-Profil laufen beide in einem
 * Prozess (siehe application.yml, `learnwithme.jobs.enabled`), das deckt Entwicklung und
 * diese Umgebung ab. Für echten Multi-Instanz-Betrieb bräuchte es einen Broadcast über
 * Postgres `LISTEN`/`NOTIFY` oder einen Message-Bus — bewusst nicht Teil von Epic B.
 */
@Component
class SourceEventBroadcaster {

    private val emitters = ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>>()

    fun subscribe(sourceId: UUID): SseEmitter {
        val emitter = SseEmitter(0L) // kein Timeout — Client/Nginx-Proxy entscheidet
        val list = emitters.computeIfAbsent(sourceId) { CopyOnWriteArrayList() }
        list.add(emitter)
        emitter.onCompletion { list.remove(emitter) }
        emitter.onTimeout { list.remove(emitter) }
        emitter.onError { list.remove(emitter) }
        return emitter
    }

    fun push(sourceId: UUID, status: String) {
        val list = emitters[sourceId] ?: return
        for (emitter in list) {
            try {
                emitter.send(SseEmitter.event().name("status").data(status))
                if (status == "READY" || status == "FAILED" || status == "PARTIAL") emitter.complete()
            } catch (_: Exception) {
                list.remove(emitter)
            }
        }
    }
}
