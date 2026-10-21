# auth-service · API

Der auth-service verwaltet Konten und Login-Sessions: registrieren, anmelden, abmelden.
Das Gateway prüft über ihn jeden Login-Token. Mit dem Tresor hat dieser Service nichts zu tun.

| Methode | Pfad | Zweck | Aufrufer |
|---|---|---|---|
| `GET` | [`/prelogin`](#get-prelogin) | Salt und Argon2-Parameter für das Kontopasswort | Client |
| `POST` | [`/register`](#post-register) | Konto und erste Session anlegen | Client |
| `POST` | [`/login`](#post-login) | Anmelden, liefert einen Login-Token | Client |
| `POST` | [`/logout`](#post-logout) | Session dieses Tokens beenden | Client |
| `POST` | [`/internal/sessions/verify`](#post-internalsessionsverify) | Login-Token prüfen | nur Gateway |
| `GET` | [`/health`](#get-health) | Läuft der Service? | Docker, Gateway |

## Konventionen

- **Pfade:** Der Client ruft `https://<host>/api/…` auf, das Gateway leitet ohne `/api` weiter. Aus `/api/login` wird hier `/login`.
- **Format:** JSON in UTF-8. Binärdaten als Base64 (Standard-Alphabet, mit Padding), Zeitpunkte in Unix-Sekunden.
- **E-Mail:** Wird überall auf `trim` + `lowercase` normalisiert. ` Alice@Example.com ` und `alice@example.com` sind dasselbe Konto.
- **Request-ID:** Optionaler Header `X-Request-Id` (1–128 Zeichen aus `A–Z a–z 0–9 . _ -`). Fehlt er oder ist er ungültig, vergibt der Service eine UUID.
  Die ID kommt in jeder Antwort als `X-Request-Id` zurück, steht im Log und geht an den Key-Service weiter.
- **CORS:** Browser dürfen von `http://localhost:<port>`, `http://127.0.0.1:<port>` und den Origins aus `CORS_ALLOWED_ORIGINS` zugreifen.
  Erlaubte Request-Header: `Content-Type`, `Authorization`, `X-Request-Id`. Lesbar sind `X-Request-Id` und `Retry-After`.
  Andere Origins bekommen `403`. Requests ohne `Origin`-Header (Gateway, curl) sind nicht betroffen.

### Login-Token

- **Aufbau:** 32 Zufallsbytes als Base64, also 44 Zeichen. Der Token ist undurchsichtig, der Client wertet ihn nicht aus.
- **Speicherung:** Der Server speichert nur den SHA-256-Hash.
- **Laufzeit:** 12 Stunden ab Ausstellung, einstellbar über `LOGIN_TOKEN_TTL_SECONDS`. Es gibt keinen Refresh.
- **Mehrere Sessions:** Jede Registrierung und jeder Login legt eine neue Session an. Ein Nutzer kann mehrere Sessions gleichzeitig haben, z. B. Web-App und Extension. Logout beendet nur die Session des Tokens.
- **Verwendung:** Der Client schickt ihn als `Authorization: Bearer <token>`.

## Fehler

Jeder Fehler hat denselben Body. `reason` gibt es nur bei `400`.

```json
{ "error": "bad_request", "reason": "invalid email" }
```

| Status | `error` | Wann |
|---|---|---|
| `400` | `bad_request` | Format falsch, Details in `reason` (Tabelle unten) |
| `401` | `unauthorized` | `loginKey` falsch, E-Mail unbekannt, Token ungültig oder abgelaufen. Die Antwort sagt nicht, was davon |
| `403` | `forbidden` | interner Aufruf ohne gültigen `X-Service-Token` |
| `409` | `already_exists` | E-Mail ist schon registriert |
| `429` | `too_many_requests` | zu viele fehlgeschlagene Logins, mit Header `Retry-After` (Sekunden) |
| `502` | `upstream_error` | Key-Service nicht erreichbar oder unbrauchbare Antwort |
| `500` | `internal_error` | unerwarteter Fehler im Service |

Mögliche Werte für `reason` bei `400`:

| `reason` | Ursache |
|---|---|
| `malformed request body` | JSON kaputt, Pflichtfeld fehlt oder unbekanntes Feld im Body |
| `missing email` | Query-Parameter `email` fehlt (`/prelogin`) |
| `invalid email` | keine gültige E-Mail oder länger als 254 Zeichen |
| `invalid base64 length, expected N bytes` | Base64-Feld hat die falsche Länge |
| `invalid base64 encoding` | Base64-Feld lässt sich nicht dekodieren |
| `decoded value must be N bytes` | dekodiert, aber falsche Byte-Anzahl |
| `unsupported kdf parameters` | `kdf` weicht von den Standardwerten ab |
| `invalid token` | `token` leer oder länger als 512 Zeichen (`/internal/sessions/verify`) |

---

## `GET /prelogin`

Erster Schritt jedes Logins. Der Client braucht Salt und Argon2-Parameter, um aus dem Kontopasswort den `loginKey` abzuleiten.
Der Endpoint braucht keinen Token und ändert nichts.

**Unbekannte E-Mail:** Die Antwort sieht genauso aus wie bei einem echten Konto. Sie enthält ein Fake-Salt, das pro E-Mail immer gleich ist, und die Standardparameter.
Es gibt nie `404`, sonst ließe sich abfragen, welche E-Mails registriert sind.

**Query-Parameter**

| Name | Pflicht | Beschreibung |
|---|---|---|
| `email` | ja | E-Mail des Kontos, URL-kodiert |

**`200 OK`**

| Feld | Typ | Beschreibung |
|---|---|---|
| `salt` | string (Base64, 16 Byte) | `salt_login` |
| `kdf.alg` | string | immer `argon2id` |
| `kdf.m` | integer | Speicher in KiB, `65536` (64 MiB) |
| `kdf.t` | integer | Durchläufe, `3` |
| `kdf.p` | integer | Parallelität, `1` |
| `kdf.len` | integer | Länge des Ergebnisses in Byte, `32` |

**Fehler:** `400`

```bash
curl "https://<host>/api/prelogin?email=user%40example.de"
```

```json
{
  "salt": "GipiGbwf3c3vp5uWK+pfOg==",
  "kdf": { "alg": "argon2id", "m": 65536, "t": 3, "p": 1, "len": 32 }
}
```

---

## `POST /register`

Legt das Konto an und meldet den Nutzer direkt an. Den Tresor legt der Client danach beim Vault-Service an.

Ablauf im Service:

1. Prüfen, ob die E-Mail frei ist.
2. `userId` vergeben und den Login-Verifier berechnen. Dafür holt der Service `h = HMAC(pepper_login, loginKey)` vom Key-Service und speichert `Argon2id(h, salt_s_login)`. Der `loginKey` selbst wird nicht gespeichert.
3. Beim Key-Service den Serverschlüssel für den Nutzer anlegen.
4. Konto und Session in einer Transaktion speichern und den Login-Token zurückgeben.

Schlägt ein Schritt fehl, wird kein Konto gespeichert.

**Header**

| Name | Pflicht | Wert |
|---|---|---|
| `Content-Type` | ja | `application/json` |

**Body**

| Feld | Typ | Pflicht | Regel |
|---|---|---|---|
| `email` | string | ja | gültige E-Mail, höchstens 254 Zeichen |
| `salt` | string (Base64) | ja | 16 Byte, `salt_login`, vom Client gewürfelt |
| `kdf` | object | ja | genau `{"alg":"argon2id","m":65536,"t":3,"p":1,"len":32}` |
| `loginKey` | string (Base64) | ja | 32 Byte, aus dem Kontopasswort abgeleitet |

**`201 Created`**

| Feld | Typ | Beschreibung |
|---|---|---|
| `userId` | string | vom Server vergebene UUID |
| `token` | string | Login-Token der neuen Session |

**Fehler:** `400`, `409 already_exists`, `502 upstream_error`

```bash
curl -X POST "https://<host>/api/register" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.de",
    "salt": "GipiGbwf3c3vp5uWK+pfOg==",
    "kdf": { "alg": "argon2id", "m": 65536, "t": 3, "p": 1, "len": 32 },
    "loginKey": "1HjBfYUqJNHybcPBWQoyiesQtOFj0GU0EndmZ6JJU3c="
  }'
```

```json
{
  "userId": "3a0b4d3e-5c1f-4e2a-9b7d-1f2e3c4d5a6b",
  "token": "q3Jx8Vn0bW6tZ2yK9mP4sL1dR7hF5gC0aE8uN3iO6wQ="
}
```

---

## `POST /login`

Meldet den Nutzer mit E-Mail und `loginKey` an. Der Tresor bleibt dabei gesperrt.
Jeder Login legt eine neue Session an. Bestehende Sessions des Nutzers bleiben gültig.

**Header**

| Name | Pflicht | Wert |
|---|---|---|
| `Content-Type` | ja | `application/json` |

**Body**

| Feld | Typ | Pflicht | Regel |
|---|---|---|---|
| `email` | string | ja | gültige E-Mail |
| `loginKey` | string (Base64) | ja | 32 Byte |

**`200 OK`**

| Feld | Typ | Beschreibung |
|---|---|---|
| `token` | string | Login-Token |
| `userId` | string | Nutzer-ID |

**Fehler**

| Status | Wann |
|---|---|
| `400` | Format falsch |
| `401` | E-Mail unbekannt oder `loginKey` falsch. Beide Fälle liefern denselben Body und brauchen gleich lange |
| `429` | 5 fehlgeschlagene Logins für diese E-Mail innerhalb von 60 s. `Retry-After` sagt, wie lange gesperrt ist. Gilt auch, wenn die Daten jetzt stimmen |
| `502` | Key-Service nicht erreichbar |

Der Zähler für `429` gilt pro E-Mail, auch für unbekannte. Ein erfolgreicher Login setzt ihn zurück.
Er liegt nur im Speicher, ein Neustart des Service löscht ihn.

```bash
curl -X POST "https://<host>/api/login" \
  -H "Content-Type: application/json" \
  -d '{ "email": "user@example.de", "loginKey": "1HjBfYUqJNHybcPBWQoyiesQtOFj0GU0EndmZ6JJU3c=" }'
```

```json
{
  "token": "Zt7wP2kQ9xL4mN8vB1cR6yH3sJ0fD5gA2eU7iO4nK1M=",
  "userId": "3a0b4d3e-5c1f-4e2a-9b7d-1f2e3c4d5a6b"
}
```

---

## `POST /logout`

Beendet die Session dieses Tokens. Der Login-Token ist danach wertlos. Andere Sessions des Nutzers bleiben bestehen.

**Header**

| Name | Pflicht | Wert |
|---|---|---|
| `Authorization` | ja | `Bearer <login-token>` |

**Body:** keiner

**`204 No Content`**, ohne Body.

**Fehler:** `401`, wenn der Token fehlt, unbekannt oder abgelaufen ist.
Ein zweiter Logout mit demselben Token ergibt deshalb `401`. Der Client kann das ignorieren und trotzdem lokal aufräumen.

```bash
curl -X POST "https://<host>/api/logout" \
  -H "Authorization: Bearer Zt7wP2kQ9xL4mN8vB1cR6yH3sJ0fD5gA2eU7iO4nK1M="
```

---

## `POST /internal/sessions/verify`

Prüft einen Login-Token und liefert, wem er gehört. Das Gateway ruft das bei jeder Anfrage mit `Authorization`-Header auf.
Der Endpoint ist nur im internen Netz erreichbar. Er braucht genau eine Datenbankabfrage und ruft keinen anderen Service auf.

Ein Token ist gültig, wenn seine Session existiert und nicht abgelaufen ist.

**Header**

| Name | Pflicht | Wert |
|---|---|---|
| `X-Service-Token` | ja | Service-Token des Gateways (`SERVICE_TOKEN_GATEWAY`) |
| `Content-Type` | ja | `application/json` |

Der Service-Token wird vor dem Body geprüft. Ohne gültigen Token gibt es immer `403`, auch bei kaputtem Body.

**Body**

| Feld | Typ | Pflicht | Regel |
|---|---|---|---|
| `token` | string | ja | Login-Token aus `Authorization: Bearer …`, höchstens 512 Zeichen |

**`200 OK`**

| Feld | Typ | Beschreibung |
|---|---|---|
| `userId` | string | Nutzer der Session |
| `sessionId` | string | Session-ID. Der Vault bindet seinen Tresor-Token daran |
| `expiresAt` | integer | Ablauf in Unix-Sekunden |

**Fehler**

| Status | Wann |
|---|---|
| `400` | Format falsch |
| `401` | Token unbekannt oder abgelaufen |
| `403` | `X-Service-Token` fehlt oder ist falsch |

```bash
curl -X POST "http://auth-service:8080/internal/sessions/verify" \
  -H "X-Service-Token: <service-token-gateway>" \
  -H "Content-Type: application/json" \
  -d '{ "token": "Zt7wP2kQ9xL4mN8vB1cR6yH3sJ0fD5gA2eU7iO4nK1M=" }'
```

```json
{
  "userId": "3a0b4d3e-5c1f-4e2a-9b7d-1f2e3c4d5a6b",
  "sessionId": "9c1f7a2b-3d4e-4f5a-8b6c-7d8e9f0a1b2c",
  "expiresAt": 1791500000
}
```

---

## `GET /health`

Sagt, ob der Service läuft und seine Datenbank erreicht. Gedacht für Healthchecks von Docker und Gateway.
Braucht keinen Token und erscheint nicht im Request-Log.

**`200 OK`**

```json
{ "status": "ok" }
```

**`503 Service Unavailable`**, wenn die Datenbank nicht antwortet.

```json
{ "status": "unavailable" }
```
