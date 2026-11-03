package de.trustylynx

import de.trustylynx.clients.HttpKeyServiceClient
import de.trustylynx.clients.createServiceHttpClient
import de.trustylynx.plugins.CorsConfig
import de.trustylynx.plugins.configureCors
import de.trustylynx.plugins.configureRequestLogging
import de.trustylynx.plugins.configureSerialization
import de.trustylynx.plugins.configureStatusPages
import de.trustylynx.repositories.PostgresSessionRepository
import de.trustylynx.repositories.PostgresUserRepository
import de.trustylynx.repositories.createDataSource
import de.trustylynx.repositories.isReachable
import de.trustylynx.repositories.migrateDatabase
import de.trustylynx.routes.accountRoutes
import de.trustylynx.routes.healthRoute
import de.trustylynx.routes.internalRoutes
import de.trustylynx.security.LoginRateLimiter
import de.trustylynx.services.AccountService
import de.trustylynx.services.SessionService
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.netty.EngineMain
import io.ktor.server.routing.routing
import java.time.Clock

fun main(args: Array<String>) {
    EngineMain.main(args)
}

/** Start des Service: liest die Konfiguration, baut alle Teile und verbindet sie miteinander. */
fun Application.module() {
    val config = AppConfig.fromEnv()

    val dataSource = createDataSource(config.db)
    migrateDatabase(dataSource)
    val httpClient = createServiceHttpClient()
    monitor.subscribe(ApplicationStopped) {
        httpClient.close()
        dataSource.close()
    }

    val clock = Clock.systemUTC()
    val keys = HttpKeyServiceClient(httpClient, config.keyServiceUrl, config.serviceTokenAuth)
    val sessions = SessionService(PostgresSessionRepository(dataSource), clock, config.loginTokenTtl)
    val accounts = AccountService(
        users = PostgresUserRepository(dataSource),
        sessions = sessions,
        keys = keys,
        rateLimiter = LoginRateLimiter(clock),
        preloginSecret = config.preloginSecret,
        clock = clock,
    )

    configureApp(accounts, sessions, config.serviceTokenGateway, config.cors) { dataSource.isReachable() }
}

/** Plugins und Routen. Getrennt von [module], damit die Tests sie mit Fakes statt Datenbank starten können. */
fun Application.configureApp(
    accounts: AccountService,
    sessions: SessionService,
    gatewayToken: String,
    cors: CorsConfig,
    isHealthy: suspend () -> Boolean,
) {
    configureRequestLogging()
    configureCors(cors)
    configureSerialization()
    configureStatusPages()
    routing {
        healthRoute(isHealthy)
        accountRoutes(accounts, sessions)
        internalRoutes(sessions, gatewayToken)
    }
}
