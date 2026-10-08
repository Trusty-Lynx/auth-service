# auth-service · API

Der auth-service verwaltet Konten und Login-Sessions: registrieren, anmelden, abmelden.
Das Gateway prüft über ihn jeden Login-Token. Mit dem Tresor hat dieser Service nichts zu tun.

| Methode | Pfad | Zweck | Aufrufer |
|---|---|---|---|
| `GET` | [`/prelogin`](#get-prelogin) | Salt und Argon2-Parameter für das Kontopasswort | Client |
| `POST` | [`/register`](#post-register) | Konto und erste Session anlegen | Client |
| `POST` | [`/login`](#post-login) | Anmelden, liefert einen Login-Token | Client |
| `POST` | [`/logout`](#post-logout) | Session dieses Geräts beenden | Client |
| `POST` | [`/internal/sessions/verify`](#post-internalsessionsverify) | Login-Token prüfen | nur Gateway |
| `GET` | [`/health`](#get-health) | Läuft der Service? | Docker, Gateway |

## Konventionen

- **Pfade:** Der Client ruft `https://<host>/api/…` auf, das Gateway leitet ohne `/api` weiter. Aus `/api/login` wird hier `/login`.
- **Format:** JSON in UTF-8. Binärdaten als Base64 (Standard-Alphabet, mit Padding), Zeitpunkte in Unix-Sekunden.
- **Gerät:** Jeder Client-Aufruf außer `/prelogin` schickt `X-Device-Id`. Erlaubt sind 1–128 Zeichen aus `A–Z a–z 0–9 . _ -`.
- **Geräte-Signatur:** `X-Timestamp` und `X-Device-Sig` prüft das Gateway. Der auth-service liest sie nicht.
- **E-Mail:** Wird überall auf `trim` + `lowercase` normalisiert. ` Alice@Example.com ` und `alice@example.com` sind dasselbe Konto.
- **Request-ID:** Optionaler Header `X-Request-Id` (1–128 Zeichen aus `A–Z a–z 0–9 . _ -`). Fehlt er oder ist er ungültig, vergibt der Service eine UUID.
  Die ID kommt in jeder Antwort als `X-Request-Id` zurück, steht im Log und geht an den Key-Service weiter.

### Login-Token

- **Aufbau:** 32 Zufallsbytes als Base64, also 44 Zeichen. Der Token ist undurchsichtig, der Client wertet ihn nicht aus.
- **Speicherung:** Der Server speichert nur den SHA-256-Hash.
- **Laufzeit:** 12 Stunden ab Ausstellung, einstellbar über `LOGIN_TOKEN_TTL_SECONDS`. Es gibt keinen Refresh.
- **Eine Session pro Gerät:** Pro Nutzer und Gerät gibt es höchstens eine Session. Ein neuer Login auf demselben Gerät macht den alten Token sofort ungültig.
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
| `malformed request body` | JSON kaputt oder Pflichtfeld fehlt |
| `missing email` | Query-Parameter `email` fehlt (`/prelogin`) |
| `invalid email` | keine gültige E-Mail oder länger als 254 Zeichen |
| `invalid base64 length, expected N bytes` | Base64-Feld hat die falsche Länge |
| `invalid base64 encoding` | Base64-Feld lässt sich nicht dekodieren |
| `decoded value must be N bytes` | dekodiert, aber falsche Byte-Anzahl |
| `unsupported kdf parameters` | `kdf` weicht von den Standardwerten ab |
| `missing or invalid device id` | `X-Device-Id` bzw. `deviceId` fehlt oder hat ein falsches Format |
| `device id does not match X-Device-Id header` | `device.id` ≠ `X-Device-Id` (`/register`) |
| `device name must be 1-100 characters` | Gerätename leer oder zu lang (nach `trim`) |
| `public key must be an uncompressed P-256 point` | `publicKey` beginnt nicht mit `0x04` |
| `public key is not a valid P-256 point` | Punkt liegt nicht auf der Kurve P-256 |
| `invalid token` | `token` leer oder länger als 512 Zeichen (`/internal/sessions/verify`) |

---

## `GET /prelogin`

Erster Schritt jedes Logins. Der Client braucht Salt und Argon2-Parameter, um aus dem Kontopasswort den `loginKey` abzuleiten.
Der Endpoint braucht keinen Token und keine Signatur und ändert nichts.

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

Legt das Konto an und meldet das Gerät direkt an. Den Tresor legt der Client danach beim Vault-Service an.

Ablauf im Service:

1. Prüfen, ob die E-Mail frei ist.
2. `userId` vergeben und den Login-Verifier berechnen. Dafür holt der Service `h = HMAC(pepper_login, loginKey)` vom Key-Service und speichert `Argon2id(h, salt_s_login)`. Der `loginKey` selbst wird nicht gespeichert.
3. Beim Key-Service den Serverschlüssel für den Nutzer anlegen.
4. Konto und Session in einer Transaktion speichern und den Login-Token zurückgeben.

Schlägt ein Schritt fehl, wird kein Konto gespeichert.

**Header**

| Name | Pflicht | Wert |
|---|---|---|
| `X-Device-Id` | ja | deviceId, gleich `device.id` |
| `Content-Type` | ja | `application/json` |

**Body**

| Feld | Typ | Pflicht | Regel |
|---|---|---|---|
| `email` | string | ja | gültige E-Mail, höchstens 254 Zeichen |
| `salt` | string (Base64) | ja | 16 Byte, `salt_login`, vom Client gewürfelt |
| `kdf` | object | ja | genau `{"alg":"argon2id","m":65536,"t":3,"p":1,"len":32}` |
| `loginKey` | string (Base64) | ja | 32 Byte, aus dem Kontopasswort abgeleitet |
| `device.id` | string | ja | gleich `X-Device-Id` |
| `device.name` | string | ja | 1–100 Zeichen, z. B. `MacBook` |
| `device.publicKey` | string (Base64) | ja | 65 Byte, unkomprimierter P-256-Punkt (`04 ‖ x ‖ y`) |

**`201 Created`**

| Feld | Typ | Beschreibung |
|---|---|---|
| `userId` | string | vom Server vergebene UUID |
| `token` | string | Login-Token für dieses Gerät |

**Fehler:** `400`, `409 already_exists`, `502 upstream_error`

```bash
curl -X POST "https://<host>/api/register" \
  -H "X-Device-Id: dev-mac-01" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.de",
    "salt": "GipiGbwf3c3vp5uWK+pfOg==",
    "kdf": { "alg": "argon2id", "m": 65536, "t": 3, "p": 1, "len": 32 },
    "loginKey": "1HjBfYUqJNHybcPBWQoyiesQtOFj0GU0EndmZ6JJU3c=",
    "device": { "id": "dev-mac-01", "name": "MacBook", "publicKey": "BBq7vVj9PxQxP8x1aP4QS42HbtSIpFuzPeDQloJIpEbaPwnhnEq7sj2hIg1rOLGp6j/CT/ZWAXB4CLrxuu+H5eU=" }
  }'
```

```json
{
  "userId": "3a0b4d3e-5c1f-4e2a-9b7d-1f2e3c4d5a6b",
  "token": "q3Jx8Vn0bW6tZ2yK9mP4sL1dR7hF5gC0aE8uN3iO6wQ="
}
```

> **Noch offen:** Das Gerät wird noch nicht beim Guard angelegt. Bis der Guard angebunden ist, gilt jedes Gerät als `trusted`.

---

## `POST /login`

Meldet den Nutzer mit E-Mail und `loginKey` an. Der Tresor bleibt dabei gesperrt.
Gibt es auf diesem Gerät schon eine Session, wird sie ersetzt und der alte Token ist ungültig.

**Header**

| Name | Pflicht | Wert |
|---|---|---|
| `X-Device-Id` | ja | deviceId |
| `Content-Type` | ja | `application/json` |

**Body**

| Feld | Typ | Pflicht | Regel |
|---|---|---|---|
| `email` | string | ja | gültige E-Mail |
| `loginKey` | string (Base64) | ja | 32 Byte |
| `device` | object | nein | nur beim ersten Login auf diesem Gerät |
| `device.name` | string | mit `device` | 1–100 Zeichen |
| `device.publicKey` | string (Base64) | mit `device` | 65 Byte, unkomprimierter P-256-Punkt |

**`200 OK`**

| Feld | Typ | Beschreibung |
|---|---|---|
| `token` | string | Login-Token |
| `userId` | string | Nutzer-ID |
| `deviceStatus` | string | `trusted`, `unknown` oder `blocked`. Solange der Guard nicht angebunden ist, immer `trusted` |

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
  -H "X-Device-Id: dev-mac-01" \
  -H "Content-Type: application/json" \
  -d '{ "email": "user@example.de", "loginKey": "1HjBfYUqJNHybcPBWQoyiesQtOFj0GU0EndmZ6JJU3c=" }'
```

```json
{
  "token": "Zt7wP2kQ9xL4mN8vB1cR6yH3sJ0fD5gA2eU7iO4nK1M=",
  "userId": "3a0b4d3e-5c1f-4e2a-9b7d-1f2e3c4d5a6b",
  "deviceStatus": "trusted"
}
```

---

## `POST /logout`

Beendet die Session dieses Geräts. Der Login-Token ist danach wertlos. Andere Geräte bleiben angemeldet.

**Header**

| Name | Pflicht | Wert |
|---|---|---|
| `Authorization` | ja | `Bearer <login-token>` |
| `X-Device-Id` | ja | Gerät, für das der Token ausgestellt wurde |

**Body:** keiner

**`204 No Content`**, ohne Body.

**Fehler:** `401`, wenn Token oder `X-Device-Id` fehlen oder der Token unbekannt ist. Dasselbe gilt, wenn er abgelaufen ist oder zu einem anderen Gerät gehört.
Ein zweiter Logout mit demselben Token ergibt deshalb `401`. Der Client kann das ignorieren und trotzdem lokal aufräumen.

```bash
curl -X POST "https://<host>/api/logout" \
  -H "Authorization: Bearer Zt7wP2kQ9xL4mN8vB1cR6yH3sJ0fD5gA2eU7iO4nK1M=" \
  -H "X-Device-Id: dev-mac-01"
```

---

## `POST /internal/sessions/verify`

Prüft einen Login-Token und liefert, wem er gehört. Das Gateway ruft das bei jeder Anfrage mit `Authorization`-Header auf.
Der Endpoint ist nur im internen Netz erreichbar. Er braucht genau eine Datenbankabfrage und ruft keinen anderen Service auf.

Ein Token ist gültig, wenn seine Session existiert, nicht abgelaufen ist und zu `deviceId` gehört.

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
| `deviceId` | string | ja | `X-Device-Id` der Client-Anfrage, vom Gateway per Signatur geprüft |

**`200 OK`**

| Feld | Typ | Beschreibung |
|---|---|---|
| `userId` | string | Nutzer der Session |
| `deviceId` | string | Gerät der Session |
| `sessionId` | string | Session-ID. Der Vault bindet seinen Tresor-Token daran |
| `expiresAt` | integer | Ablauf in Unix-Sekunden |

**Fehler**

| Status | Wann |
|---|---|
| `400` | Format falsch |
| `401` | Token unbekannt, abgelaufen oder für ein anderes Gerät ausgestellt |
| `403` | `X-Service-Token` fehlt oder ist falsch |

```bash
curl -X POST "http://auth-service:8080/internal/sessions/verify" \
  -H "X-Service-Token: <service-token-gateway>" \
  -H "Content-Type: application/json" \
  -d '{ "token": "Zt7wP2kQ9xL4mN8vB1cR6yH3sJ0fD5gA2eU7iO4nK1M=", "deviceId": "dev-mac-01" }'
```

```json
{
  "userId": "3a0b4d3e-5c1f-4e2a-9b7d-1f2e3c4d5a6b",
  "deviceId": "dev-mac-01",
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
