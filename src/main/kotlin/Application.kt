package de.trustylynx

import de.trustylynx.api.accountRoutes
import de.trustylynx.clients.HttpKeyServiceClient
import de.trustylynx.common.configureSerialization
import de.trustylynx.common.configureStatusPages
import de.trustylynx.common.createDataSource
import de.trustylynx.common.createServiceHttpClient
import de.trustylynx.domain.AccountService
import de.trustylynx.persistence.PostgresUserRepository
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.netty.EngineMain
import io.ktor.server.routing.routing

fun main(args: Array<String>) {
    EngineMain.main(args)
}

/** Einziger Ort, an dem Abhängigkeiten gebaut und verdrahtet werden. */
fun Application.module() {
    println("DB_URL aus Env: ${System.getenv("DB_URL")}")
    val config = AppConfig.fromEnv()

    val dataSource = createDataSource(config.db)
    val httpClient = createServiceHttpClient()
    monitor.subscribe(ApplicationStopped) {
        httpClient.close()
        dataSource.close()
    }

    val users = PostgresUserRepository(dataSource)
    val keys = HttpKeyServiceClient(httpClient, config.keyServiceUrl)
    val accounts = AccountService(users, keys)

    configureApp(accounts)
}

/** Plugins und Routen, getrennt von [module], damit Tests Fakes einsetzen können. */
fun Application.configureApp(accounts: AccountService) {
    configureSerialization()
    configureStatusPages()
    routing {
        accountRoutes(accounts)
    }
}
