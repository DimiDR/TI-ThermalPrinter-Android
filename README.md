# Tasty Igniter Printer App
This app is a fork of https://github.com/DantSu/ESCPOS-ThermalPrinter-Android. Thank you for the great implementation.
It was modified to comply with https://tastyigniter.com/ and its Orders API https://tastyigniter.com/marketplace/item/igniter-api.

The app will run in the background. It gets the last 50 requests from your shop.
The printed IDs will be saved in an array and checked to see if they were already printed to prevent double prints.
The job runs every minute.

In general, I think the Bluetooth solution is one of the best for small businesses.
It is portable (printer & Android device not connected with a cable) and is fast to set up.
IP printers require multiple steps to be set up correctly. Logging in to different routers is also not an easy task.
# Restrictions
It's a native Android app, so no IoS support is in place.
I tried the React Native EXPO implementation, but it is not working properly.
Libraries I tried:
"react-native-bluetooth-escpos-printer"
"react-native-thermal-printer"
"react-native-thermal-receipt-printer"
As those are popular in the POS printer space, I'm not sure if any React Native app can work,
or is not outdated after a couple Java patches for the Android app generation.

Another solution would be to connect to the Printing add-on.
https://tastyigniter.com/marketplace/item/thoughtco-printer
However, the web would call the App on the device, and the App would not switch back to Chrome afterward.
The implementation of this kind of solution would be difficult and require a lot of maintenance.

The API Orders implementation provided by Tasty Igniter is fast, but lacks settings.
This is why I'm getting the last 50 Orders. If you know better ways to filter the request, please open an issue.

# CORS Problems
In cases of cross-origin errors, I added the following lines at the top of the file ".htaccess"
Header set Access-Control-Allow-Origin "*"
Header set Access-Control-Allow-Headers "origin, x-requested-with, content-type"
Header set Access-Control-Allow-Methods "GET"