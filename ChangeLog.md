# V0.2 15.03.2024
UI updated, Popup for print reset added.
Some description text.

Next Updates:
- Change delivery time in the app. For this I would need an in app kitchen sink with orders POST API
- If clicked on OK in the pupup all initial invoices should be printed.
At the moment the ID counter is just resetted
- Foreground/Background processes. The app should run also if not visible on screen.
In the moment I am using a background process coded in the main activity.
This is not a best practise. So mobile phones could also kill the background process.
- Better looking UI
- PHP send data to the app, to switch from periodic job to listeners

# V0.1 28.02.2024

First version of the App.
Only for Bluetooth.
The old coding from the form was not cleaned up. TI methods were created additionaly.
Left it to reuse in USB & Printing later on.

Next Updates:
- text input field for the shop address
- text field to display some log information
- better UI
- USB / TCP support
- manual "reprint" option on another activity
- GET setting in API is on all. Maybe implement Username / PW for employee authentification
- recolor the print button, so you can see if the service is running