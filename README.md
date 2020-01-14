# LocationMocker
An Android application created to spoof the location of a device. 

On the first run of the application you must use "adb shell appops set com.micronet.mocklocation android:mock_location allow" to allow the app to mock the GPS location.

This version supports three different trips. Use https://nmeagen.org/ to create the trips and then download the .nmea files to add to the application to add more. Future features could include the ability to read in an .nmea file and also manual alter the speed and bearing using the seekbars in the UI.
