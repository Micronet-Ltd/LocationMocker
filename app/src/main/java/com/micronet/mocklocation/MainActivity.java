package com.micronet.mocklocation;

/*
    Use "adb shell appops set com.micronet.mocklocation android:mock_location allow" to allow this application to mock device location.
 */

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

public class MainActivity extends AppCompatActivity {
    private final static String TAG = "LocationMocker";

    private boolean inTrip = false;
    private int currentlySelectedDrive = -1;

    private Button btnTrip;
    private Spinner spinner;
    private Thread tripThread;
    private CheckBox chBearing;
    private CheckBox chSpeed;
    private SeekBar seekBarSpeed;
    private SeekBar seekBarBearing;

    private HashMap<String, Integer> driveFiles;
    private ArrayList<String> driveNames;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle("Location Mocker v" + BuildConfig.VERSION_NAME);

        initializeUiComponents();
    }

    ////////////////////////////////////
    // UI Functions
    ////////////////////////////////////

    private void initializeUiComponents() {
        // Get UI components
        btnTrip = findViewById(R.id.btnTrip);
        chSpeed = findViewById(R.id.chSpeed);
        chBearing = findViewById(R.id.chBearing);
        seekBarSpeed = findViewById(R.id.seekBarSpeed);
        seekBarBearing = findViewById(R.id.seekBarBearing);
        spinner = findViewById(R.id.spinner);

        // TODO Implement seek bars
        seekBarSpeed.setEnabled(false);
        seekBarBearing.setEnabled(false);

        // Prepare spinner
        populateSpinner();

        // Setup listeners for components.
        btnTrip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (inTrip) {
                    inTrip = false;

                    if (tripThread != null) {
                        tripThread.interrupt();
                        Log.d(TAG, "Trip stopped.");
                    }
                } else {
                    inTrip = true;

                    tripThread = new Thread(locationUpdaterFromNMEAFile);
                    tripThread.start();
                }

                // Update UI for whether or not we are in a trip.
                updateUiForTrip(inTrip);
            }
        });

        seekBarSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                Log.d(TAG, "Speed changed to " + i + ".");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        seekBarBearing.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                Log.d(TAG, "Bearing changed to " + i + ".");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }

    private void updateUiForTrip(boolean startTrip) {
        if (startTrip) {
            btnTrip.setText("Stop Trip");

            spinner.setEnabled(false);
            chSpeed.setEnabled(false);
            chBearing.setEnabled(false);
        } else {
            btnTrip.setText("Start Trip");

            spinner.setEnabled(true);
            chSpeed.setEnabled(true);
            chBearing.setEnabled(true);
        }
    }

    public void populateSpinner(){
        driveNames = new ArrayList<>();
        driveFiles = new HashMap<>();

        // Get all raw resources
        Field[] fields  = R.raw.class.getFields();
        for(int count=0; count < fields.length; count++){
            Log.i("Raw Asset: ", fields[count].getName());

            try {
                driveFiles.put(fields[count].getName(), fields[count].getInt(fields[count]));
                driveNames.add(fields[count].getName());
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        // Populate spinner
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getApplicationContext(),  android.R.layout.simple_spinner_dropdown_item, driveNames);
        adapter.setDropDownViewResource( android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        // Set onclick listener
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                if (i > -1) {
                    Object driveSelected = adapterView.getItemAtPosition(i);
                    Log.d(TAG, "Selected drive: " + driveSelected);
                    currentlySelectedDrive = driveFiles.get(driveSelected);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                // Do Nothing
            }
        });
    }

    private Runnable updateRunnable(final double lat, final double lon, final double speed, final double bearing) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                updateUI(lat, lon, speed, bearing);
            }
        };

        return runnable;
    }

    private void updateUI(double lat, double lon, double speed, double bearing) {
        TextView currentLat = findViewById(R.id.tvCurrentLat);
        TextView currentLon = findViewById(R.id.tvCurrentLon);
        TextView currentSpeed = findViewById(R.id.tvCurrentSpeed);
        TextView currentBearing = findViewById(R.id.tvCurrentBearing);
        DecimalFormat decimalFormat = new DecimalFormat();
        decimalFormat.setMaximumFractionDigits(5);

        currentLat.setText(decimalFormat.format(lat));
        currentLon.setText(decimalFormat.format(lon));

        decimalFormat.setMaximumFractionDigits(2);
        currentSpeed.setText(decimalFormat.format(speed * 2.237) + " MPH");
        currentBearing.setText(decimalFormat.format(bearing));

//        seekBarSpeed.setProgress((int) (speed * 2.237));
//        seekBarBearing.setProgress((int) (bearing));
    }

    ////////////////////////////////////////////
    // Location Mocking Methods
    ////////////////////////////////////////////

    private Runnable locationUpdaterFromNMEAFile = new Runnable() {
        @Override
        public void run() {
            boolean varySpeed = chSpeed.isChecked();
            boolean varyBearing = chBearing.isChecked();
            int i = 0;

            Random random = new Random();
            InputStream is = getResources().openRawResource(currentlySelectedDrive);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));

            try {
                String line;
                while ((line = reader.readLine()) != null && inTrip) {
                    // Example line
                    // $GPRMC,231317.385,A,4051.298,N,11154.461,W,038.9,221.5,090120,000.0,W*6F
                    // Generate routes using https://nmeagen.org/.
                    String[] arr = line.split(",");

                    if (!arr[0].equals("$GPRMC")) {
                        // Lines we don't need to use for now.
                        continue;
                    }

                    int indexOfPeriod = arr[3].indexOf('.');
                    double currentLat = Double.valueOf(arr[3].substring(0, indexOfPeriod-2)) + (Double.valueOf(arr[3].substring(indexOfPeriod-2)) / 60);
                    if (arr[4].equals("S")) currentLat *= -1;

                    indexOfPeriod = arr[5].indexOf('.');
                    double currentLon = Double.valueOf(arr[5].substring(0, indexOfPeriod-2)) + (Double.valueOf(arr[5].substring(indexOfPeriod-2)) / 60);
                    if (arr[6].equals("W")) currentLon *= -1;

                    float currentSpeed = (Float.valueOf(arr[7])/1.944f) + (varySpeed ? (10 * random.nextFloat()) - 5 : 0);
                    float currentBearing = Float.valueOf(arr[8]) + (varyBearing ? (10 * random.nextFloat()) - 5 : 0);

                    mockLocation(currentLat, currentLon, currentSpeed, currentBearing);
                    Log.v(TAG, "Drive step: " + i + "; Current location: " + currentLat + ", " + currentLon + "; Speed: " + currentSpeed*2.237 + " MPH; Bearing: " + currentBearing);
                    i++;
                    Thread.sleep(500);
                }

                reader.close();
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            } catch (InterruptedException ie) {
                // Do Nothing
                return;
            } catch (Exception e) {
                Log.e(TAG, e.toString());

                Snackbar.make(findViewById(R.id.constraintLayout), "Use \"adb shell appops set com.micronet.mocklocation android:mock_location allow\" to allow this application to mock device location.", Snackbar.LENGTH_LONG)
                        .show();
            }

            // Handle trip finish
            Log.d(TAG, "Trip finished.");
            inTrip = false;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateUiForTrip(inTrip);
                }
            });
        }
    };

    private void mockLocation(double latitude, double longitude, float speed, float bearing) {
        // Add a new test provider to location manager.
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationManager.addTestProvider(LocationManager.GPS_PROVIDER,
                false,
                false,
                false,
                false,
                false,
                true,
                true,
                android.location.Criteria.POWER_LOW,
                android.location.Criteria.ACCURACY_FINE);

        // Create new location using lat, long, speed, and bearing.
        Location newLocation = new Location(LocationManager.GPS_PROVIDER);
        newLocation.setLatitude(latitude);
        newLocation.setLongitude(longitude);
        newLocation.setAccuracy(1);
        newLocation.setAltitude(0);
        newLocation.setAccuracy(500);
        newLocation.setTime(System.currentTimeMillis());
        newLocation.setSpeed(speed);
        newLocation.setBearing(bearing);
        newLocation.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());

        // Use new location
        locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);
        locationManager.setTestProviderStatus(LocationManager.GPS_PROVIDER, LocationProvider.AVAILABLE,null, System.currentTimeMillis());
        locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, newLocation);

        // Update UI
        runOnUiThread(updateRunnable(latitude, longitude, speed, bearing));
    }
}
