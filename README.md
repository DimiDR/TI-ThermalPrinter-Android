# Tasty Igniter Printer App
This app is used by restaurant owners to monitor and print food orders.
It is connected to a thermal printing device like this one https://www.amazon.de/Epson-TM-M30III-152-Bluetooth-Modell-schwarz/dp/B0BXH1QM42.
There is a job running every 60 seconds checking the API if new order came in.
If it is there, it will be sent to the printer device.

The app will run in the background. It gets the last 50 requests from your shop.
The printed IDs will be saved in an array and checked to see if they were already printed to prevent double prints.
The job runs every minute.

This app is a fork of https://github.com/DantSu/ESCPOS-ThermalPrinter-Android. Thank you for the great implementation.
It was modified to comply with https://tastyigniter.com/ shop software and its Orders API https://tastyigniter.com/marketplace/item/igniter-api.

# Restrictions
It's a native Android app, so no IoS support is in place.
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
This is why I'm getting the last 50 Orders. If you know better ways to filter the request, please open an issue.

Promissing Library: https://github.com/tr3v3r/react-native-esc-pos-printer

# CORS Problems
In cases of cross-origin errors, I added the following lines at the top of the file ".htaccess"

Header set Access-Control-Allow-Origin "*"
Header set Access-Control-Allow-Headers "origin, x-requested-with, content-type"
Header set Access-Control-Allow-Methods "GET"

Also set the API for Order in admin dashboard API/Orders - All Resources Get to all and not employee.

# Test Shop Credentials
- URL: https://dimitrir14.sg-host.com
- User: admin
- PW: XXX

# Steps to make the app operational 
## 1. Open API communication
The app is getting information from 3 APIs. Please install the API addon for TI and set the following 3 API to ALL for "List all resources (GET)"
- /api/orders
- /api/menus
- /api/categories

## 1. Users File
The file app\src\main\assets\users.json has the user information. It is part of gitignore to not expose the user information.
Here is the structure of the file

```
"users": [
    {
      "shop_id": 1,
      "username": "ts",
      "password": "pw2",
      "shop_name": "testshop",
      "domain_shop": "https://dimitrir14.sg-host.com",
      "domain_website": "https://dimitrir14.sg-host.com",
      "kitchen_view": "https://dimitrir14.sg-host.com/admin/thoughtco/kitchendisplay/summary/view/1",
      "location_id": "10"
    }, ... ]
```

- shop_id - id for the shop. Only app interany. Should be unique in the users file.
- username - Login name. Should be unique in users file. 
- password - password to login to the app
- shop_name - Display name in the app
- domain_shop - URL of the shop
- domain_website - URL of the restaurant website
- kitchen_view - URL of kitchen view
- location_id - location id of the shop from TI.



## 2. Version Updater (optional)
The app has an integrated update function outsite the playstore. With this updates can come much faster.
There is hardcoded link to version updates in "Constants.java" file for APP_DETAILS_URL. Where creating a new APK change the variable "currentAppVersion" to a higher number.

This is the structure for the update JSON file. 
The app will compare the version in JSON and in the coding and download the latest version from the link storen in JSON for "apk_file".

Structure of the JSON:
```
{
    "app_version": "1.0.6",
    "update_title": "Jandiweb Installer",
    "update_description": "Improvement in Bluetooth communication",
    "date": "2027-27-27T18:25:43.511Z",
    "apk_file": "https://[PAGE URL]/jandiweb_printer_1.0.6.apk"
}
```

Put the JSON into the location URL of APP_DETAILS_URL and the file location for "apk_file". Then the app will notify the owner that there was an update.

## 3. Create APK
Download Android Studio and install it. In "Build" --> "Build App Bundle(s)/APK(s)" create the APK.