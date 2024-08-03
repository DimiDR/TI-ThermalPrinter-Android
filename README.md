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

# Users File
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
      "kitchen_view": "https://dimitrir14.sg-host.com/admin/thoughtco/kitchendisplay/summary/view/1"
    },
```