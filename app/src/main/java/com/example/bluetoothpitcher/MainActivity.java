package com.example.bluetoothpitcher;

/*
    Charles Yu
    Basic bluetooth application for a SMART water pitcher
    Reads and writes data over serial communication
 */

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private TextView textViewCurrDevice;
    private TextView textViewCurrTemp;
    private TextView textViewConnection;
    private TextView textViewReady;
    private TextView textViewFull;
    private SeekBar tempBar;
    private BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private  BluetoothDevice selectedDevice;
    private static final String TAG = "MY_APP_DEBUG_TAG";
    private ConnectThread connection = null;
    private ConnectionManager connectionManager = null;
    private Handler handler;
    private int desiredTemp = 0;
    private int currentTemp = 0;
    private int maximumTemp = 100;
    private int waterLevel = 1;
    private boolean isReady = false;
    private boolean connected = false;
    public StringBuilder sb = new StringBuilder();

    //message constants for handler, currently only using for reading
    private interface MessageConstants {
        public static final int MESSAGE_READ = 0;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        createChannel();// creates notification channel

        //layout finding
        final TextView setTemp = (TextView)findViewById(R.id.desired_temperature);

        textViewCurrDevice = (TextView)findViewById(R.id.currentPitcher);
        setCurrDevice(textViewCurrDevice);

        textViewCurrTemp = (TextView)findViewById(R.id.currentTemperature);
        textViewCurrTemp.setText(Integer.toString(desiredTemp));

        textViewConnection = (TextView)findViewById(R.id.connect);

        textViewReady = (TextView)findViewById(R.id.isReady);
        textViewFull = (TextView)findViewById(R.id.isFull);

        //UI thread handler that updates main UI
        handler = new Handler(Looper.getMainLooper()) {
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MessageConstants.MESSAGE_READ: // if message received is for reading data
                        String tmpmsg = (String)msg.obj;
                        sb.append(tmpmsg);
                        Log.e(TAG, "tmpmsgstart");
                        Log.e(TAG,tmpmsg);
                        Log.e(TAG, sb.toString());

                        //Serial data reading with a string builder
                        int endLine = sb.indexOf(">");
                        if(endLine > 0){
                            String dataInPrint = sb.substring(0, endLine);

                            if(sb.charAt(0) == '<'){
                                Log.d(TAG, "appended");
                                Log.e(TAG, dataInPrint);
                                extractData(sb.substring(1,endLine));
                                updateUI();
                            }
                            sb.delete(0, sb.length());
                            dataInPrint = " ";
                        }
                        break;
                }
            };
        };

        //seek bar to set temperature
        tempBar = (SeekBar)findViewById(R.id.tempBar);
        tempBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                desiredTemp = progress; //gets progress of seekbar sets desiredTemp to that value and updates UI
                setTemp.setText(String.valueOf(desiredTemp));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                //writes to outputstream only if connection is valid
                if(connection != null) {
                    connection.write(Integer.toString(desiredTemp).getBytes());
                }
            }
        });
    }

    //function that starts activity that selects device
    public void findDevices(View listOfDevices){
        Intent intent = new Intent(this, ListOfDevices.class);
        startActivityForResult(intent, 1);
    }

    //gets selected device
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode==1){
            if(resultCode==RESULT_OK){
                selectedDevice = data.getExtras().getParcelable("Device");
                setCurrDevice(textViewCurrDevice);
            }
            else if(resultCode==RESULT_CANCELED){
                setCurrDevice(textViewCurrDevice);
            }
        }
    }

    //xml onclick calls
    public void connectDevice(View view){
        if(selectedDevice != null) {
            connection =  new ConnectThread(selectedDevice);
            connection.start();
            Toast.makeText(MainActivity.this, "Connecting", Toast.LENGTH_SHORT).show();
        }
        else Toast.makeText(MainActivity.this, "No Selected Device", Toast.LENGTH_SHORT).show();
    }

    public void setCurrDevice(View view){
        if(selectedDevice == null)
            textViewCurrDevice.setText("No Device Selected");
        else
            textViewCurrDevice.setText(selectedDevice.getName()+ "\n" + selectedDevice.getAddress());
    }


    //bluetooth connection classes derived from Android Development Blog
    private class ConnectThread extends Thread {
        private static final String TAG = "MY_APP_DEBUG_TAG";
        private final BluetoothSocket btSocket;
        private final BluetoothDevice btDevice;

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket
            // because mmSocket is final.
            BluetoothSocket tmp = null;
            btDevice = device;

            try {
                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                // MY_UUID is the app's UUID string, also used in the server code.
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
                connected = true;
            } catch (IOException e) {
                Log.e(TAG, "Socket's create() method failed", e);
                connected = false;
            }
            btSocket = tmp;
        }
        public void run() {
            // Cancel discovery because it otherwise slows down the connection.
            bluetoothAdapter.cancelDiscovery();

            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                btSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                try {
                    btSocket.close();
                    connected = false;
                } catch (IOException closeException) {
                    Log.e(TAG, "Could not close the client socket", closeException);
                    connected = false;
                }
                return;
            }

            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.
            connectionManager = new ConnectionManager(btSocket);
            connectionManager.start();
        }

        public void write(byte[] data){
            if (connectionManager!=null){
                connectionManager.write(data);
            }
        }

        // Closes the client socket and causes the thread to finish.
        public void cancel() {
            try {
                btSocket.close();
                connected = false;
            } catch (IOException e) {
                Log.e(TAG, "Could not close the client socket", e);
            }
        }
    }

    private class ConnectionManager extends Thread {
        private final BluetoothSocket connectionSocket;
        private final InputStream inputStream;
        private final OutputStream outputStream;
        private byte[] buffer;

        public ConnectionManager(BluetoothSocket socket){
            connectionSocket = socket;
            InputStream tmpInput = null;
            OutputStream tmpOutput = null;

            try{
                tmpInput = socket.getInputStream();
            }
            catch(IOException e){
                Log.e(TAG, "Error occured when creating input stream", e);
            }
            try{
                tmpOutput = socket.getOutputStream();
            }
            catch(IOException e){
               Log.e(TAG, "Error occurred when creating output stream", e);
            }

            connected = true;
            inputStream = tmpInput;
            outputStream = tmpOutput;
        }

        public void run(){
            Log.i(TAG, "BEGIN connectedThread");
            buffer = new byte[1024];
            int numBytes;
            while (true) {
                try {
                    // Read from the InputStream of buffer lengths at a time and sends it to main UI handler where data is processed
                    if(inputStream.available()> 0) {
                        numBytes = inputStream.read(buffer);
                        // Send the obtained bytes to the UI activity.
                        Log.d(TAG, "reading");
                        String dataString = new String(buffer, 0, numBytes).trim();
                        //Log.d(TAG, dataString);
                        Message readMsg = handler.obtainMessage(MessageConstants.MESSAGE_READ, dataString);
                        readMsg.sendToTarget();
                    }
                } catch (IOException e) {
                    connected = false;
                    Log.d(TAG, "Input stream was disconnected", e);
                    connected = false;
                    break;
                }
            }
        }

        //writing to output stream with \n as a delimiter
        public void write(byte[] bytes) {
            try {
                outputStream.write(bytes);
                outputStream.write('\n');
            } catch (IOException e) {
                connected = false;
                Log.e(TAG, "Error occurred when sending data", e);
            }
        }

        public void cancel(){
            try {
                connectionSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }
    }


    //data comes in form "currentTemp(int),maxTemp(int), waterLevel(int), isReady(bool), so parses data into usable integers"
    public void extractData(String data) {
        String[] values = data.split(",");

        try {
            // Log.d(TAG, data);
            // Log.d(TAG, "update");
            currentTemp = Integer.parseInt(values[0]);
            // Log.d(TAG,values[1]);
            maximumTemp = Integer.parseInt(values[1]);
            waterLevel = Integer.parseInt(values[2]);
            isReady = Integer.parseInt(values[3]) > 0 ? true : false ;
            Log.e(TAG, Boolean.toString(isReady));
        } catch (NullPointerException e) {
            Log.e(TAG, "null object inputstream", e);
        }
    }

    //updates main UI thread, could probably put more updates here
    public void updateUI(){
        textViewCurrTemp.setText(Integer.toString(currentTemp));

        if(waterLevel==0)
            textViewFull.setText("Not Full");
        else
            textViewFull.setText("Full");

        if(isReady == true || currentTemp > desiredTemp)
            textViewReady.setText("Ready");
        else
            textViewReady.setText("Not Ready");

        if(connected == true)
            textViewConnection.setText("Connected");
        else
            textViewReady.setText("Disconnected");

        tempBar.setMax(maximumTemp);

        if(isReady || currentTemp == desiredTemp){
            addNotification();
        }

    }


    //creates notification and sends it to manager
    private void addNotification(){
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "0")
                .setContentText("Your water is ready")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("SMART Water Pitcher")
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(1, builder.build());
    }

    //creating notification channel
    private void createChannel(){
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){
            CharSequence name = "SMART Water Pitcher";
            String description = "Your water is ready.";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;

            NotificationChannel notificationChannel = new NotificationChannel("0", name, importance);
            notificationChannel.setDescription(description);
            NotificationManager notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(notificationChannel);

        }
    }
}
