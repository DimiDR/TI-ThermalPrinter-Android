# Tasty Igniter Printer App
This app is a fork of https://github.com/DantSu/ESCPOS-ThermalPrinter-Android. Thank you for the great implementation. 
It was modified to comply with https://tastyigniter.com/ and its Orders API https://tastyigniter.com/marketplace/item/igniter-api.

The app will run in the background. It gets the last 50 requests from your shop.
The printed IDs will be saved in an array and checked, if they were already printed to prevent double prints.
The job runs every 1 minute.

In general I think the bluetooth solution is one of the best for small businesses,
as it is portable (printer & Android device not connected with a cable) and is fast to setup.
IP printers require multiple steps to setup correctly. Logging in into different routers is also not an easy task.
# Restrictions
Its a native Android app, so no IoS support in place.
React native EXPO implementation I tried, but they are not working properly.
Librieries I tried:
"react-native-bluetooth-escpos-printer"
"react-native-thermal-printer"
"react-native-thermal-receipt-printer"
As thouse are popular in the POS pronter space, I'm not sure if any react native app can work, 
or is not outdated after couple Java patches for the Android app generation.

Another solution would be to connect to the Printing addon 
https://tastyigniter.com/marketplace/item/thoughtco-printer
However, the web would call the App on the device and the App will not switch back to Chrome afterwards.
The implemantation for this kind of solution would be difficult and require alot of maintenance.

API Orders implementation provided by Tasty Igniter is fast, but lacks on settings.
This is why im getting the last 50 Orders. If you know better ways to filter the request please open an issue.

# CORS Problems
In case of Cross Origin errors. Added the following lines on the top of the file ".htaccess"
Header set Access-Control-Allow-Origin "*"
Header set Access-Control-Allow-Headers "origin, x-requested-with, content-type"
Header set Access-Control-Allow-Methods "GET"