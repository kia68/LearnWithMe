package de.optadata.odil.learnwithme

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

/**
 * `@ConfigurationPropertiesScan` statt `@Component` auf den `@ConfigurationProperties`-Klassen
 * selbst: `@Component`-registrierte `@ConfigurationProperties` mit nicht-trivialen (verschachtelten)
 * Konstruktorparametern lösen Spring Boots normale `@Autowired`-Konstruktor-Auflösung aus statt
 * Konstruktor-Binding — die verschachtelten Value-Typen (z. B. `EloProperties`) werden dann als
 * fehlende Beans gesucht statt aus Properties gebunden. Betraf `AiRoutingProperties` (Epic C) und
 * `AdaptivityProperties`/`SelectionProperties` (Epic D) gleichermaßen; ohne laufenden Spring-Kontext
 * (Docker-Lücke) war das bis zur ersten echten CI-Ausführung unsichtbar.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
class LearnWithMeApplication

fun main(args: Array<String>) {
    runApplication<LearnWithMeApplication>(*args)
}
