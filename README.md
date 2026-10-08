# auth-service

Konto und Login für TrustyLynx: registrieren, anmelden, abmelden. Das Gateway prüft hier jeden Login-Token.
Mit dem Tresor hat dieser Service nichts zu tun.

Kotlin 2.4 · Ktor 3.6 · PostgreSQL 17 · Java 21

## Endpoints

| Methode | Pfad | Zweck |
|---|---|---|
| `GET` | `/prelogin?email=…` | Salt und Argon2-Parameter für das Kontopasswort. Unbekannte E-Mails bekommen ein Fake-Salt |
| `POST` | `/register` | Konto und erste Session anlegen, liefert `userId` und Login-Token |
| `POST` | `/login` | Anmelden mit E-Mail und `loginKey`, liefert einen neuen Login-Token |
| `POST` | `/logout` | Session dieses Geräts beenden (`Authorization: Bearer …`) |
| `POST` | `/internal/sessions/verify` | Login-Token prüfen. Nur für das Gateway (`X-Service-Token`) |
| `GET` | `/health` | `200`, wenn Service und Datenbank laufen, sonst `503` |

Das Gateway leitet `/api/…` ohne das Präfix weiter. Felder, Fehler und Beispiele: [API.md](API.md).

## Struktur

```text
src/main/kotlin/
├── Application.kt   Einstieg: baut alle Teile und verdrahtet sie
├── AppConfig.kt     alle Umgebungsvariablen an einer Stelle
├── api/             Routen und Request-/Response-Klassen
├── validation/      Requests prüfen und in Domain-Objekte übersetzen
├── domain/          Use Cases (AccountService, SessionService) und Modelle
├── persistence/     Repositories (Interface + Postgres) und Transaktionen
├── clients/         Aufrufe an andere Services (Key-Service)
├── security/        Login-Verifier (Argon2id), Rate-Limit
└── common/          gleich in allen Services: Fehler, JSON, Env, DB, HTTP-Client, Health, Request-ID
src/main/resources/db/migration/   Datenbankschema (Flyway)
```

Ein Request läuft immer `api` → `validation` → `domain` → `persistence`/`clients`.
Fehler werden als Exception geworfen (`common/ApiException.kt`) und zentral in `common/StatusPages.kt` beantwortet.
Jede Anfrage hat eine `X-Request-Id`. Sie steht in jeder Logzeile und wird an den Key-Service weitergereicht.

## Lokal starten

```bash
docker compose -f docker-compose.dev.yml up -d   # Postgres auf localhost:5432
./gradlew run -Pdevelopment                      # Port 8080, Dev-Defaults aus AppConfig
```

Ohne `-Pdevelopment` müssen alle Pflichtvariablen gesetzt sein. Welche das sind, steht in `.env.example`.
Das Schema legt Flyway beim Start an.

## Tests

```bash
./gradlew test
```

Die Endpoint-Tests laufen ohne Datenbank und ohne Key-Service, `src/test/kotlin/Fakes.kt` ersetzt beide.
`PostgresRepositoryTest` prüft das SQL gegen ein echtes Postgres 17 in Docker (Testcontainers).
Läuft kein Docker, werden diese Tests übersprungen.

## Deploy

Ein Push auf den Branch `deploy` baut das Image auf dem Server und startet es neu (`.github/workflows/deploy.yml`).
Die `.env` liegt nur auf dem Server.

## Offen

- **Guard anbinden:** `POST /internal/devices/check` bei der Registrierung (innerhalb der Transaktion) und beim Login aufrufen.
  Beim Login gilt: `trusted`/`unknown` weiter, `blocked` → `403`, `409` oder `404` → `401`.
  Bis dahin gilt jedes Gerät als `trusted`. Dazu `GUARD_SERVICE_URL` in `AppConfig` und `.env.example` aufnehmen.
- **Key-Service-Pfad:** Der Code ruft `POST /internal/users/create`, die Doku nennt `POST /internal/users/{userId}/keys`. Eins von beiden angleichen.

