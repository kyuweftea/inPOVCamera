package com.iframelabs.poduncan.e_labvideoapp;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.CharBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Chronometer;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import android.location.Location;
import android.location.LocationManager;
import android.location.LocationListener;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.MetadataChangeSet;

import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends Activity implements SensorEventListener,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private Camera mCamera;
    private CameraPreview mPreview;
    private MediaRecorder mediaRecorder;
    private ImageButton capture, vid;
    private Context myContext;
    private FrameLayout cameraPreview;
    private Chronometer chrono;
    private TextView tv;
    private TextView txt;

    int quality = 0;
    int rate = 20;
    String timeStampFile;
    int clickFlag = 0;
    Timer timer;
    int VideoFrameRate = 24;

    LocationListener locationListener;
    LocationManager LM;

    String filePathCSVLocal;
    String filePathVIDLocal;

    GoogleApiClient mGoogleApiClient;
    DriveId mFolderDriveId;
    String timestampFileGD;

    private static final String TAG = "MainActivity";

    protected static final int REQUEST_CODE_RESOLUTION = 1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        myContext = this;
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        head = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        rot = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        cameraPreview = (FrameLayout) findViewById(R.id.camera_preview);

        mPreview = new CameraPreview(myContext, mCamera);
        cameraPreview.addView(mPreview);

        capture = (ImageButton) findViewById(R.id.button_capture);
        capture.setOnClickListener(captureListener);

        chrono = (Chronometer) findViewById(R.id.chronometer);
        txt = (TextView) findViewById(R.id.txt1);
        txt.setTextColor(-16711936);

        vid = (ImageButton) findViewById(R.id.imageButton);
        vid.setVisibility(View.GONE);

        tv = (TextView) findViewById(R.id.textViewHeading);
        String setTextText = "Heading: " + heading + " Speed: " + speed;
        tv.setText(setTextText);

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(MainActivity.this)
                    .addApi(Drive.API)
                    .addScope(Drive.SCOPE_FILE)
                    .addScope(Drive.SCOPE_APPFOLDER) // required for App Folder sample
                    .addConnectionCallbacks(MainActivity.this)
                    .addOnConnectionFailedListener(MainActivity.this)
                    .build();
        }
    }


    private int findBackFacingCamera() {
        int cameraId = -1;
        // Search for the back facing camera
        // get the number of cameras
        int numberOfCameras = Camera.getNumberOfCameras();
        // for every camera check
        for (int i = 0; i < numberOfCameras; i++) {
            CameraInfo info = new CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == CameraInfo.CAMERA_FACING_BACK) {
                cameraId = i;
                break;
            }
        }
        return cameraId;
    }

    public void onResume() {
        super.onResume();
        if (!checkCameraHardware(myContext)) {
            Toast toast = Toast.makeText(myContext, "Phone doesn't have a camera!", Toast.LENGTH_LONG);
            toast.show();
            finish();
        }
        if (mCamera == null) {
            mCamera = Camera.open(findBackFacingCamera());
            mPreview.refreshCamera(mCamera);
        }
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, head, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, gyro, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, rot, SensorManager.SENSOR_DELAY_GAME);



        locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                // Called when a new location is found by the network location provider.

                latitude  = location.getLatitude();
                longitude = location.getLongitude();

                if(location.hasSpeed()) {
                    speed = location.getSpeed();
                }
                location.distanceBetween(latitude_original, longitude_original, latitude, longitude, dist);
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {}

            public void onProviderEnabled(String provider) {}

            public void onProviderDisabled(String provider) {}
        };

        // Acquire a reference to the system Location Manager
        LM = (LocationManager)getSystemService(Context.LOCATION_SERVICE);

        // Register the listener with the Location Manager to receive location updates
        LM.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
    }

    @Override
    protected void onPause() {
        if (mGoogleApiClient != null) {
            mGoogleApiClient.disconnect();
        }
        super.onPause();
        // when on Pause, release camera in order to be used from other
        // applications
        releaseCamera();
        sensorManager.unregisterListener(this);


    }

    private boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
            // this device has a camera
            return true;
        } else {
            // no camera on this device
            return false;
        }
    }


    boolean recording = false;
    OnClickListener captureListener = new OnClickListener() {
        @Override
        public void onClick(View v) {

            if (recording) {
                // stop recording and release camera
                mediaRecorder.stop(); // stop the recording
                releaseMediaRecorder(); // release the MediaRecorder object
                Toast.makeText(MainActivity.this, "Video captured!", Toast.LENGTH_LONG).show();
                recording = false;
                //d.exportData();
                chrono.stop();
                chrono.setBase(SystemClock.elapsedRealtime());

                chrono.start();
                chrono.stop();
                txt.setTextColor(-16711936);
                //chrono.setBackgroundColor(0);
                enddata();
/*
                if(clickFlag == 1){
                    clickFlag = 0;
                    capture.performClick();
                }
*/
                timestampFileGD = timeStampFile;

                mGoogleApiClient.connect();

            } else {
                timeStampFile = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                File wallpaperDirectory = new File(Environment.getExternalStorageDirectory().getPath()+"/elab/");
                wallpaperDirectory.mkdirs();

                File wallpaperDirectory1 = new File(Environment.getExternalStorageDirectory().getPath()+"/elab/"+timeStampFile);
                wallpaperDirectory1.mkdirs();
                if (!prepareMediaRecorder()) {
                    Toast.makeText(MainActivity.this, "Fail in prepareMediaRecorder()!\n - Ended -", Toast.LENGTH_LONG).show();
                    finish();
                }

                // work on UiThread for better performance
                runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            mediaRecorder.start();
                        } catch (final Exception ex) {
                        }
                    }
                });
                Toast.makeText(MainActivity.this, "Recording...", Toast.LENGTH_LONG).show();

                Camera.Parameters params = mCamera.getParameters();
                params.setPreviewFpsRange( 30000, 30000 ); // 30 fps
                if ( params.isAutoExposureLockSupported() )
                    params.setAutoExposureLock( true );

                params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                mCamera.setParameters(params);
                //d.beginData();
                storeData();
                chrono.setBase(SystemClock.elapsedRealtime());

                chrono.start();
                //chrono.setBackgroundColor(-65536);
                txt.setTextColor(-65536);
                recording = true;

            }
        }
    };

    private void releaseMediaRecorder() {
        if (mediaRecorder != null) {
            mediaRecorder.reset(); // clear recorder configuration
            mediaRecorder.release(); // release the recorder object
            mediaRecorder = null;
            mCamera.lock(); // lock camera for later use
        }
    }

    private boolean prepareMediaRecorder() {

        mediaRecorder = new MediaRecorder();

        mCamera.unlock();
        mediaRecorder.setCamera(mCamera);

        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        if(quality == 0)
            mediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_1080P));
        else if(quality == 1)
            mediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_720P));
        else if(quality == 2)
            mediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_480P));

        //String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());

        filePathVIDLocal = Environment.getExternalStorageDirectory().getPath()+"/elab/" + timeStampFile + "/" + timeStampFile  + ".mp4";

        mediaRecorder.setOutputFile(filePathVIDLocal);
        mediaRecorder.setVideoFrameRate(VideoFrameRate);
        //mediaRecorder.setMaxDuration(5000);

        try {
            mediaRecorder.prepare();
        } catch (IllegalStateException e) {
            releaseMediaRecorder();
            return false;
        } catch (IOException e) {
            releaseMediaRecorder();
            return false;
        }
        return true;

    }

    private void releaseCamera() {
        // stop and release camera
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
    }

    /* --------------------- Data Section ----------------------------*/

    Location location;
    LocationManager lm;
    double latitude = 0;
    double longitude = 0;

    double latitude_original = 0;
    double longitude_original = 0;
    //float distance = 0;
    float speed = 0;
    float dist[] = {0,0,0};
    PrintWriter writer = null;
    long timechecker = 5000;

    class SayHello extends TimerTask {
        public void run() {
            lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            //lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100, 0, locationListener );
            //longitude = location.getLongitude();
            //latitude = location.getLatitude();
            //if(location.hasSpeed()) {
              //  speed = location.getSpeed();
            //}
            //dist[0] = (float) 0.0;
            /*
            long elapsedMillis = SystemClock.elapsedRealtime() - chrono.getBase();
            if(elapsedMillis >= timechecker){
                clickFlag = 1;
                timechecker = timechecker + 5000;
                timer.cancel();
                timer.purge();
            }*/

            if(latitude != 0.0) {
                String timeStamp = new SimpleDateFormat("HH-mm-ss-SSS").format(new Date());
                writer.println(longitude + "," + latitude + "," + speed + "," + dist[0] + "," + timeStamp + "," + linear_acc_x + "," + linear_acc_y + "," + linear_acc_z + "," +
                        heading + "," + gyro_x + "," + gyro_y + "," + gyro_z + "," + rot_x + "," + rot_y + "," + rot_z + "," + rot_t);
            }
            else{
                dist[0] = (float) 0.0;
                String timeStamp = new SimpleDateFormat("HH-mm-ss-SSS").format(new Date());
                writer.println(longitude_original + "," + latitude_original + "," + speed + "," + dist[0] + "," + timeStamp + "," + linear_acc_x + "," + linear_acc_y + "," + linear_acc_z + "," +
                        heading + "," + gyro_x + "," + gyro_y + "," + gyro_z + "," + rot_x + "," + rot_y + "," + rot_z + "," + rot_t);
            }



        }
    }

    public void storeData() {

        String filePath = Environment.getExternalStorageDirectory().getPath()+"/elab/" + timeStampFile + "/" + timeStampFile  +  ".csv";
        filePathCSVLocal = filePath;
        try {
            writer = new PrintWriter(filePath);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        writer.println("Longitude" + "," + "Latitude" + "," + "Speed" + "," + "Distance" + "," + "Time" + "," + "Acc X" + "," + "Acc Y" + "," + "Acc Z" + "," + "Heading"
                + "," + "gyro_x" + "," + "gyro_y" + "," + "gyro_z" + "," + "rot_x" + "," + "rot_y" + "," + "rot_z" + "," + "rot_t");
        LocationManager original = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Location original_location = original.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if(original.getLastKnownLocation(LocationManager.GPS_PROVIDER) != null){
            latitude_original = original_location.getLatitude();
            longitude_original = original_location.getLongitude();
        }
        String setTextText = "Heading: " + heading + " Speed: " + speed;
        tv.setText(setTextText);
        timer = new Timer();
        timer.schedule(new SayHello(), 0, rate);
        /*if(clickFlag == 1) {
            capture.performClick();
        }
        */
    }

    public void enddata() {

        writer.close();
    }


    /* ---------------------- Sensor data ------------------- */

    private SensorManager sensorManager;

    private Sensor accelerometer;
    private Sensor head;
    private Sensor gyro;
    private Sensor rot;
    float linear_acc_x = 0;
    float linear_acc_y = 0;
    float linear_acc_z = 0;

    float heading = 0;

    float gyro_x = 0;
    float gyro_y = 0;
    float gyro_z = 0;

    float rot_x = 0;
    float rot_y = 0;
    float rot_z = 0;
    float rot_t = 0;

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        if(event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            linear_acc_x = event.values[0];
            linear_acc_y = event.values[1];
            linear_acc_z = event.values[2];
        }
        else if (event.sensor.getType() == Sensor.TYPE_ORIENTATION) {
            heading = Math.round(event.values[0]);
            if(heading >= 270){
                heading = heading + 90;
                heading = heading - 360;
            }
            else{
                heading = heading + 90;
            }
        }
        else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE){
            gyro_x = event.values[0];
            gyro_y = event.values[1];
            gyro_z = event.values[2];
        }
        else if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            rot_x = event.values[0];
            rot_y = event.values[1];
            rot_z = event.values[2];
            rot_t = event.values[3];
        }
        String setTextText = "Heading: " + heading + " Speed: " + speed;
        tv.setText(setTextText);
    }
    String[] options = {"1080p","720p","480p"};
    String[] options1 = {"15 Hz","10 Hz"};
    String[] options2 = {"10 fps","20 fps","30 fps"};


    public void addQuality(View view){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        String setting = new String();
        if(quality == 0) {
            setting = "1080p";
        }
        else if(quality == 1){
            setting = "720p";
        }
        else if(quality == 2){
            setting = "480p";
        }
        builder.setTitle("Pick Quality, Current setting: " + setting)
                .setItems(options, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // The 'which' argument contains the index position
                        // of the selected item
                        if(which == 0){
                            quality = 0;
                        }
                        else if (which == 1){
                            quality = 1;
                        }
                        else if (which == 2){
                            quality = 2;
                        }
                    }
                });
        builder.show();
    }
    public void addRate(View view)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        String setting = new String();
        if(rate == 100) {
            setting = "10 Hz";
        }
        else if(rate == 67){
            setting = "15 Hz";
        }
        builder.setTitle("Pick Data Save Rate, Current setting: " + setting)
                .setItems(options1, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // The 'which' argument contains the index position
                        // of the selected item
                        if(which == 0){
                            rate = 67 ;
                        }
                        else if (which == 1){
                            rate = 100;
                        }
                    }
                });
        builder.show();
    }
    public void addFrameRate(View view)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        String setting = new String();
        if(VideoFrameRate == 10) {
            setting = "10 fps";
        }
        else if(VideoFrameRate == 20){
            setting = "20 fps";
        }
        else if(VideoFrameRate == 30){
            setting = "30 fps";
        }
        builder.setTitle("Pick Video fps, Current setting: " + setting)
                .setItems(options2, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // The 'which' argument contains the index position
                        // of the selected item
                        if(which == 0){
                            VideoFrameRate = 10 ;
                        }
                        else if (which == 1){
                            VideoFrameRate = 20;
                        }
                        else if (which == 2){
                            VideoFrameRate = 30;
                        }
                    }
                });
        builder.show();
    }


    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private GoogleApiClient getGoogleApiClient() {
        return mGoogleApiClient;
    }



    /**
     * Handles resolution callbacks.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_RESOLUTION && resultCode == RESULT_OK) {
            mGoogleApiClient.connect();
        }
    }


    /**
     * Called when {@code mGoogleApiClient} is disconnected.
     */
    @Override
    public void onConnectionSuspended(int cause) {
        Log.i(TAG, "GoogleApiClient connection suspended");
    }

    /**
     * Called when {@code mGoogleApiClient} is trying to connect but failed.
     * Handle {@code result.getResolution()} if there is a resolution is
     * available.
     */
    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Log.i(TAG, "GoogleApiClient connection failed: " + result.toString());
        if (!result.hasResolution()) {
            // show the localized error dialog.
            GoogleApiAvailability.getInstance().getErrorDialog(this, result.getErrorCode(), 0).show();
            return;
        }
        try {
            result.startResolutionForResult(this, REQUEST_CODE_RESOLUTION);
        } catch (IntentSender.SendIntentException e) {
            Log.e(TAG, "Exception while starting resolution activity", e);
        }
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Log.i(TAG, "GoogleApiClient connected");

        // folder
        MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                .setTitle(timeStampFile).build();/////////////////////////////////////// TODO: folder name
        Drive.DriveApi.getRootFolder(getGoogleApiClient()).createFolder(
                getGoogleApiClient(), changeSet).setResultCallback(callback);
    }

    final ResultCallback<DriveFolder.DriveFolderResult> callback = new ResultCallback<DriveFolder.DriveFolderResult>() {

        @Override
        public void onResult(DriveFolder.DriveFolderResult result) {
            if (!result.getStatus().isSuccess()) {
                showMessage("Error while trying to create the folder");
                return;
            }

            DriveId folderId = result.getDriveFolder().getDriveId();

            showMessage("Created a folder: " + folderId);

            // files
            Drive.DriveApi.fetchDriveId(getGoogleApiClient(), result.getDriveFolder().getDriveId().getResourceId())
                    .setResultCallback(idCallback);
        }
    };

    final private ResultCallback<DriveApi.DriveIdResult> idCallback = new ResultCallback<DriveApi.DriveIdResult>() {
        @Override
        public void onResult(DriveApi.DriveIdResult result) {
            if (!result.getStatus().isSuccess()) {
                showMessage("Cannot find DriveId. Are you authorized to view this folder?");
                return;
            }
            mFolderDriveId = result.getDriveId();
            Drive.DriveApi.newDriveContents(getGoogleApiClient())
                    .setResultCallback(driveContentsCallback);
        }
    };

    final private ResultCallback<DriveApi.DriveContentsResult> driveContentsCallback =
            new ResultCallback<DriveApi.DriveContentsResult>() {
                @Override
                public void onResult(DriveApi.DriveContentsResult result) {
                    if (!result.getStatus().isSuccess()) {
                        showMessage("Error while trying to create new file contents");
                        return;
                    }

                    final DriveContents driveContents = result.getDriveContents();
                    final DriveFolder folder = mFolderDriveId.asDriveFolder();
                    final DriveApi.DriveContentsResult resultFinal = result;

                    // Perform I/O off the UI thread.
                    new Thread() {
                        @Override
                        public void run() {
                            // write content to DriveContents
                            OutputStream outputStream = driveContents.getOutputStream();
                            Writer writer = new OutputStreamWriter(outputStream);

                            StringBuffer text = new StringBuffer();
                            try {
                                File file = new File(filePathCSVLocal);
                                BufferedReader br = new BufferedReader(new FileReader(file));
                                String line;
                                while ((line = br.readLine()) != null) {
                                    text.append(line);
                                    text.append('\n');
                                }
                                br.close() ;

                                writer.write(text.toString()); ////////////////////////////////TODO write csv
                                writer.close();
                            } catch (IOException e) {
                                Log.e(TAG, e.getMessage());
                            }

                            MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                                    .setTitle(timeStampFile + ".csv")////////////////////////////////////TODO data file name
                                    .setMimeType("text/csv")
                                    .setStarred(true).build();

                            folder.createFile(getGoogleApiClient(), changeSet, resultFinal.getDriveContents())
                                    .setResultCallback(fileCallback);
                        }
                    }.start();


                    // Perform I/O off the UI thread.
                    new Thread() {

                        public void pump(InputStream in, OutputStream out, int size) throws IOException {
                            byte[] buffer = new byte[4096]; // Or whatever constant you feel like using
                            int done = 0;
                            while (done < size) {
                                int read = in.read(buffer);
                                if (read == -1) {
                                    throw new IOException("Something went horribly wrong");
                                }
                                out.write(buffer, 0, read);
                                done += read;
                            }
                            // Maybe put cleanup code in here if you like, e.g. in.close, out.flush, out.close
                        }

                        @Override
                        public void run() {
                            // write content to DriveContents
                            OutputStream outputStream = driveContents.getOutputStream();
                            try {

                                File file = new File(filePathVIDLocal);
                                FileInputStream fileInputStream = new FileInputStream(file);

                                pump(fileInputStream, outputStream, (int) file.length());

                            } catch (IOException e) {
                                Log.e(TAG, e.getMessage());
                            }

                            MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                                    .setTitle(timeStampFile + ".mp4") //////////////////////////////TODO video file name
                                    .setMimeType("video/h264")
                                    .setStarred(true).build();

                            folder.createFile(getGoogleApiClient(), changeSet, resultFinal.getDriveContents())
                                    .setResultCallback(fileCallback);
                        }
                    }.start();
                }
            };

    final private ResultCallback<DriveFolder.DriveFileResult> fileCallback =
            new ResultCallback<DriveFolder.DriveFileResult>() {
                @Override
                public void onResult(DriveFolder.DriveFileResult result) {
                    if (!result.getStatus().isSuccess()) {
                        showMessage("Error while trying to create the file");
                        return;
                    }
                    showMessage("Created a file: " + result.getDriveFile().getDriveId());
                }
            };


}