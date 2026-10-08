package de.trustylynx

import de.trustylynx.api.accountRoutes
import de.trustylynx.api.internalRoutes
import de.trustylynx.clients.HttpKeyServiceClient
import de.trustylynx.common.configureRequestLogging
import de.trustylynx.common.configureSerialization
import de.trustylynx.common.configureStatusPages
import de.trustylynx.common.createDataSource
import de.trustylynx.common.createServiceHttpClient
import de.trustylynx.common.healthRoute
import de.trustylynx.common.isReachable
import de.trustylynx.common.migrateDatabase
import de.trustylynx.domain.AccountService
import de.trustylynx.domain.SessionService
import de.trustylynx.persistence.PostgresSessionRepository
import de.trustylynx.persistence.PostgresTransactions
import de.trustylynx.persistence.PostgresUserRepository
import de.trustylynx.security.LoginRateLimiter
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.netty.EngineMain
import io.ktor.server.routing.routing
import java.time.Clock

fun main(args: Array<String>) {
    EngineMain.main(args)
}

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
        transactions = PostgresTransactions(dataSource),
        rateLimiter = LoginRateLimiter(clock),
        preloginSecret = config.preloginSecret,
    )

    configureApp(accounts, sessions, config.serviceTokenGateway) { dataSource.isReachable() }
}

fun Application.configureApp(
    accounts: AccountService,
    sessions: SessionService,
    gatewayToken: String,
    isHealthy: suspend () -> Boolean,
) {
    configureRequestLogging()
    configureSerialization()
    configureStatusPages()
    routing {
        healthRoute(isHealthy)
        accountRoutes(accounts, sessions)
        internalRoutes(sessions, gatewayToken)
    }
}
