# auth-service

Konto und Login für TrustyLynx: registrieren, anmelden, abmelden. Das Gateway prüft hier jeden Login-Token.
Mit dem Tresor hat dieser Service nichts zu tun.

Kotlin 2.4 · Ktor 3.6 · PostgreSQL 17 · Java 21

## Endpoints

| Methode | Pfad | Zweck |
|---|---|---|
| `GET` | `/prelogin?email=…` | Salt und Argon2-Parameter für das Kontopasswort. Unbekannte E-Mails bekommen ein Fake-Salt |
| `POST` | `/register` | Konto mit TOTP und erste Session anlegen, liefert `userId` und Login-Token |
| `POST` | `/login` | Anmelden mit E-Mail, `loginKey` und TOTP-Code, liefert einen neuen Login-Token |
| `POST` | `/logout` | Session dieses Tokens beenden (`Authorization: Bearer …`) |
| `POST` | `/account/totp` | TOTP für ein Konto von vor TOTP nachträglich einrichten (`Authorization: Bearer …`) |
| `POST` | `/internal/sessions/verify` | Login-Token prüfen. Nur für das Gateway (`X-Service-Token`) |
| `GET` | `/health` | `200`, wenn Service und Datenbank laufen, sonst `503` |

Das Gateway leitet `/api/…` ohne das Präfix weiter. Felder, Fehler und Beispiele stehen im Repo `documentation` unter `api/auth-service/`.

## Struktur

```text
src/main/kotlin/
├── Application.kt   Start: liest die Konfiguration, baut alle Teile, verbindet sie
├── AppConfig.kt     alle Umgebungsvariablen an einer Stelle
├── routes/          HTTP: Request lesen, Eingaben prüfen, Service aufrufen, antworten
├── services/        Fachlogik (AccountService, SessionService)
├── repositories/    SQL mit JDBC, Datenbank-Setup. Nutzer und erste Session in einer Transaktion
├── clients/         Aufrufe an andere Services (Key-Service)
├── models/          Requests/Responses, Tabellenzeilen, Fehlerklassen
├── security/        Hashes und Base64, Login-Verifier (Argon2id), TOTP (RFC 6238), Rate-Limit
└── plugins/         Ktor-Einrichtung: JSON, Fehlerantworten, CORS, Logging
src/main/resources/db/migration/   Datenbankschema (Flyway)
```

Ein Request läuft immer `routes` → `services` → `repositories`/`clients`.
Fehler werden als Exception geworfen (`models/ApiException.kt`) und zentral in `plugins/StatusPages.kt` beantwortet.
Interfaces gibt es nur für Repositories und den Key-Service-Client, damit die Tests ohne Datenbank und Netzwerk laufen.
Jede Anfrage hat eine `X-Request-Id`. Sie steht in jeder Logzeile und wird an den Key-Service weitergereicht.

Alle Services sollen so aufgebaut sein. Die Anleitung dafür steht in [SERVICE-STRUKTUR.md](SERVICE-STRUKTUR.md).

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

- **TOTP:** Das Secret liegt im Klartext in `users.totp_secret` (Verschlüsselung ist für Phase 2 geplant). Es gibt noch keine Wiederherstellungscodes und keinen Weg, das Secret zu tauschen oder TOTP abzuschalten.
- **Key-Service-Ausfall:** Bricht die Verbindung zum Key-Service ab, antwortet der Service mit `500 internal_error` statt `502 upstream_error`. `502` kommt nur, wenn der Key-Service antwortet und die Antwort unbrauchbar ist.
- **Key-Service-Pfad:** Der Code ruft `POST /internal/users/create`, die Doku nennt `POST /internal/users/{userId}/keys`. Eins von beiden angleichen.

