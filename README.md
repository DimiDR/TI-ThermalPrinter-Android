# Tasty Igniter Printer App
This app is used by restaurant owners to monitor and print food orders.
It is connected to a thermal printing device like this one https://www.amazon.de/Epson-TM-M30III-152-Bluetooth-Modell-schwarz/dp/B0BXH1QM42.
New orders are detected via a shop webhook (ntfy or a local HTTP hook) and then printed. The printed IDs are stored so the same order is not printed twice.

This app is a fork of https://github.com/DantSu/ESCPOS-ThermalPrinter-Android. Thank you for the great implementation.
It was modified to comply with https://tastyigniter.com/ shop software and its Orders API https://tastyigniter.com/marketplace/item/igniter-api.

# Restrictions
It's a native Android app, so no iOS support is in place.
I tried the React Native EXPO implementation, but it is not working properly.

Libraries I tried:
- "react-native-bluetooth-escpos-printer"
- "react-native-thermal-printer"
- "react-native-thermal-receipt-printer"

As those are popular in the POS printer space, I'm not sure if any React Native app can work.
Most of them are outdated and probably will break after a couple Java patches for the Android
app generation. So better to stick with native.

Another solution would be to connect to the Printing add-on.
https://tastyigniter.com/marketplace/item/thoughtco-printer
However, the web would call the App on the device, and the App would not switch back to Chrome afterward.
The implementation of this kind of solution would be difficult and require a lot of maintenance.

The API Orders implementation provided by Tasty Igniter is fast, but lacks extensive filter settings.
This is why the app loads the last 50 orders. If you know better ways to filter the request, please open an issue.

Promising Library: https://github.com/tr3v3r/react-native-esc-pos-printer

# API access
Install the TastyIgniter API addon and allow **List all resources (GET)** for:
- `/api/orders`
- `/api/menus`
- `/api/categories`
- `/api/status` (used to map status names to IDs)

Do **not** open CORS with `Access-Control-Allow-Origin: *`. This is a native Android app; it talks to the shop API directly with a bearer token. A wildcard CORS header is unnecessary and unsafe.

Set the Orders API in the admin dashboard to the staff/employee scope that matches your shop policy. Do not publish test shop passwords in this repository.

# Order webhook (sound in the store)
The tablet does not poll the shop every minute. TastyIgniter sends a hook when an order is created; the app then plays a sound and loads the order.

No extra server of your own is required. The public relay is **https://ntfy.sh** (see [docs/order-webhook.md](docs/order-webhook.md) for setup, LAN hook, and self-hosting).

Short setup:

1. In the app, open **Settings** and copy the webhook URL (`https://ntfy.sh/jwprinter_…`).
2. In TastyIgniter, install [Webhooks](https://tastyigniter.com/marketplace/item/igniterlabs-webhook) and create an **Orders** webhook with that URL (POST).
3. Tap **Send test signal** or place a test order. A sound should play.
4. Turn on **Auto Print** if tickets should print automatically.


# Shop catalog
Shop display names can come from two places:

1. **Platform directory** — `GET {platform_url}/api/tenancy/shops` (Tasty Multitenant). The login screen lists every active tenant. New restaurants appear without a new APK.
2. **Bundled fallback** — `app/src/main/assets/users.json` for local/emulator shops and for installs that are not on the platform yet.

`users.json` **must not contain passwords**. Staff log in with their TastyIgniter username and password against **that shop’s URL**; the app exchanges them for a token and never stores the password.

```
{
  "platform_url": "https://foodninja.app",
  "shops": [
    {
      "shop_id": 1,
      "shop_name": "Example Shop",
      "domain_shop": "https://shop.example.com",
      "domain_website": "https://www.example.com",
      "kitchen_view": "https://shop.example.com/admin/thoughtco/kitchendisplay/summary/view/1",
      "location_id": 1
    }
  ]
}
```

- `platform_url` – host used to fetch `/api/tenancy/shops` (platform or any tenant on the same install)
- `shop_id` – unique ID used only inside the app
- `shop_name` – display name
- `domain_shop` – TastyIgniter shop URL (login and API)
- `domain_website` – public restaurant website
- `kitchen_view` – kitchen display URL
- `location_id` – fallback TastyIgniter location id; after login the app prefers `GET /api/locations`

This is **not** a Lieferando-style marketplace. Each tenant still has its own domain. The web storefront shows one shop; the printer app shows all hosted tenants so you do not need a separate APK per restaurant.

# Version updater (optional)
The app can install sideloaded updates from a JSON file at `APP_DETAILS_URL` in `Constants.java`. Bump `currentAppVersion` when you ship a new APK.

Updates are offered only when all of the following are true:
- `app_version` is newer than the installed version
- `apk_file` is HTTPS on `app-version.jandiweb.de` or `jandiweb.de`
- `apk_sha256` is present (64 hex characters)

The APK is downloaded to app cache, hashed with SHA-256, and installed via FileProvider only if the hash matches. If `apk_sha256` is missing, the update is ignored.

```
{
    "app_version": "1.1.1",
    "update_title": "Jandiweb Installer",
    "update_description": "Improvement in Bluetooth communication",
    "date": "2027-27-27T18:25:43.511Z",
    "apk_file": "https://app-version.jandiweb.de/printer-app/jandiweb_printer_1.1.1.apk",
    "apk_sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
}
```

Generate the hash with:

```
certutil -hashfile jandiweb_printer_1.1.1.apk SHA256
```

# Create APK
Download Android Studio and install it. In "Build" --> "Build App Bundle(s)/APK(s)" create the APK.
