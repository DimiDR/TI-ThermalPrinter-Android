# Bestell-Hook (Ton im Laden)

Die App fragt den Shop **nicht** jede Minute nach neuen Bestellungen. Stattdessen sendet TastyIgniter ein Signal, sobald eine Bestellung angelegt oder geändert wird. Das Tablet spielt dann den Ton und lädt die Bestellung nur in diesem Moment.

Es wird **kein eigener Extra-Server** benötigt.

## Wo liegt der ntfy-Server?

Wenn kein eigener Server betrieben wird, nutzt die App den öffentlichen Dienst:

| | |
|---|---|
| Adresse | https://ntfy.sh |
| Betreiber | ntfy-Projekt (unabhängig von Jandiweb und TastyIgniter) |
| Doku | https://docs.ntfy.sh |
| Konto | nicht nötig |

Die Webhook-URL eines Tablets sieht so aus:

```
https://ntfy.sh/jwprinter_<32 Hex-Zeichen>
```

Beispiel: `https://ntfy.sh/jwprinter_a1b2c3d4e5f60718293a4b5c6d7e8f90`

`ntfy.sh` ist ein Vermittler in der Cloud. Der TastyIgniter-Shop liegt meist im Internet, das Tablet im WLAN hinter einem Router. Der Shop kann das Tablet deshalb nicht direkt anpingen. ntfy nimmt das Signal vom Shop entgegen und schiebt es an die App, vergleichbar damit, wie WhatsApp Nachrichten über einen Dienst zustellt und nicht Handy-zu-Handy.

Die Basis-URL ist in der App fest verdrahtet:

```
app/src/main/java/com/dantsu/thermalprinter/helpClasses/OrderHookConfig.java
NTFY_BASE = "https://ntfy.sh"
```

## Ablauf

```
Kunde bestellt
    → TastyIgniter (Webhooks-Erweiterung)
    → POST https://ntfy.sh/jwprinter_…
    → ntfy.sh
    → Tablet (Dauerverbindung /json)
    → Ton im Laden
    → App lädt /api/orders (einmal)
    → optional Auto-Druck
```

Über ntfy geht nur das **Wecksignal**. Bestelldaten (Name, Adresse, Gerichte) laufen nicht über ntfy. Die App holt die Bestellung danach direkt vom eigenen Shop per TastyIgniter-API.

## Einrichtung im Shop

1. In der App anmelden, **Einstellungen** öffnen.
2. Unter **Bestell-Signal** die Webhook-URL kopieren.
3. In TastyIgniter die Erweiterung [Webhooks](https://tastyigniter.com/marketplace/item/igniterlabs-webhook) installieren.
4. Einen Webhook anlegen:
   - Ereignis: **Orders** (Bestellung erstellt, geändert oder gelöscht)
   - Payload-URL: die kopierte `https://ntfy.sh/jwprinter_…`-Adresse
   - Methode: **POST**
5. In der App **Testsignal senden** tippen oder eine Testbestellung aufgeben. Der Ton muss kommen.
6. **Auto Druck** einschalten, wenn Bons automatisch gedruckt werden sollen.

Ohne diesen Webhook im Shop kommt kein Signal. Die App wartet dann still; sie pollt nicht mehr.

## Topic und Sicherheit

Jedes Tablet erzeugt einmalig ein zufälliges Topic `jwprinter_` + 32 Hex-Zeichen und speichert es lokal (`MyPrefs` / `order_hook_topic`).

- Wer die volle URL kennt, kann ein Signal an genau dieses Tablet schicken.
- Die URL nicht öffentlich posten (kein Screenshot ins Internet).
- Es werden keine Kundendaten an ntfy geschickt, nur ein kurzer Text wie `new_order`.
- ntfy.sh ist ein öffentlicher Drittanbieter. Verfügbarkeit und Datenschutz liegen bei diesem Dienst.

## Optional: direkter Hook ins Laden-WLAN

Die App öffnet zusätzlich Port **8765** auf dem Tablet:

```
http://<TABLET-IP>:8765/hook
```

Das funktioniert nur, wenn der TastyIgniter-Server das Tablet erreichen kann (gleiches Netz oder Portweiterleitung). Für einen Shop in der Cloud ist das in der Regel **nicht** der Fall. Der ntfy-Weg bleibt der Standard.

Test vom PC im selben WLAN:

```
curl -d "new_order" http://192.168.1.50:8765/hook
```

## Optional: eigener ntfy-Server

Nur nötig, wenn der öffentliche Dienst `ntfy.sh` nicht genutzt werden soll. Dann ntfy selbst hosten ([Self-hosting](https://docs.ntfy.sh/install/)) und in `OrderHookConfig.NTFY_BASE` die eigene HTTPS-URL eintragen, z. B. `https://ntfy.mein-laden.de`.

Ohne diese Code-Änderung bleibt die App auf `https://ntfy.sh`.

## Verhalten in der App

- Nach dem Login läuft ein Vordergrunddienst und hält die Verbindung zu ntfy.
- Ein Hook löst Ton aus. Auto-Druck druckt nur, wenn er eingeschaltet ist.
- Kitchen Display und Dashboard aktualisieren sich nach dem Hook, nicht im 60-Sekunden-Takt.
- Zum Nachladen per Hand bleibt Wischen (Pull-to-refresh).

## Code

| Klasse | Rolle |
|---|---|
| `OrderHookConfig` | Topic erzeugen, Webhook-URL `https://ntfy.sh/…` |
| `NtfySubscriber` | Dauerhafte GET-Verbindung auf `/json`, nur `event=message` zählt |
| `LocalHookServer` | Optionaler Empfang auf Port 8765 |
| `PrintMonitorService` | Hook empfangen → Ton, Bestellungen laden, ggf. drucken |

## Störungssuche

| Symptom | Prüfen |
|---|---|
| Kein Ton bei echter Bestellung | Webhook in TastyIgniter aktiv? URL 1:1 wie in der App? Lieferhistorie der Erweiterung |
| Testsignal spielt Ton, Shop nicht | Shop-Webhook zeigt Fehler? HTTPS zu ntfy.sh vom Server aus möglich? |
| Weder Test noch Bestellung | App eingeloggt? Benachrichtigung „Warte auf Bestellungen“ sichtbar? Internet auf dem Tablet? |
| Ton kommt, Druck nicht | Auto Druck an? Drucker in den Einstellungen gewählt? |
