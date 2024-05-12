# V0.4 01.05.2024
- multivendor implementation. Now different vendors can login and use their IDs. The IDs and PW are saved in users.json - done
- landscape mode crashed on later android versions. No error to be seen. Maybe an emulator problem. - done
- Need to get categories of menu. I need to get only the first category, as it is an array. The print should be ordered by categories - done
- disable button is user is not logged in
- servicetask need to be switched to new method https://stackoverflow.com/questions/58767733/the-asynctask-api-is-deprecated-in-android-11-what-are-the-alternatives
- https://stackoverflow.com/questions/12575068/how-to-get-the-result-of-onpostexecute-to-main-activity-because-asynctask-is-a - hold
- deactivate all buttons if not logged in
- get menu categories /api/menus/:menu_id?include=categories - done

# V0.2 15.03.2024
UI updated, Popup for print reset added.
Some description text.

Next Updates:
- Change delivery time in the app. For this I would need an in app kitchen sink with orders POST API
- If clicked on OK in the pupup all initial invoices should be printed.
At the moment the ID counter is just resetted
- Foreground/Background processes. The app should run also if not visible on screen.
In the moment I am using a background process coded in the main activity.
This is not a best practise. So mobile phones could also kill the background process. - done
- Better looking UI
- PHP send data to the app, to switch from periodic job to listeners - will not be done

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