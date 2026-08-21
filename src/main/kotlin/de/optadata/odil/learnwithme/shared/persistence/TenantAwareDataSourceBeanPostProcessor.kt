package de.optadata.odil.learnwithme.shared.persistence

import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.stereotype.Component
import javax.sql.DataSource

/**
 * Umhüllt Spring Boots auto-konfigurierte `DataSource`-Bean mit [TenantAwareDataSource] (Epic G).
 *
 * Bewusst als [BeanPostProcessor] statt einer eigenen `@Bean DataSource`-Fabrikmethode: so bleibt
 * Boots gesamte Auflösungslogik (`@ServiceConnection` in Tests, `spring.datasource.*`-Properties,
 * Connection-Pool-Konfiguration) unangetastet — der Wrapper kommt erst nach der fertigen Bean
 * dazu. Kompromiss: Hikari-spezifische Micrometer-Pool-Metriken (`DataSourcePoolMetricsAutoConfiguration`
 * erkennt nur `HikariDataSource` selbst) greifen danach nicht mehr — kein Story-Bedarf in Epic G,
 * kein funktionaler Verlust.
 */
@Component
class TenantAwareDataSourceBeanPostProcessor : BeanPostProcessor {

    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
        if (bean is DataSource && bean !is TenantAwareDataSource) {
            return TenantAwareDataSource(bean)
        }
        return bean
    }
}
