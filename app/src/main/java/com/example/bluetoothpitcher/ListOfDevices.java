package com.example.bluetoothpitcher;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import java.util.ArrayList;
import java.util.Set;

public class ListOfDevices extends AppCompatActivity {

    private BluetoothAdapter bluetoothAdapter;
    private ArrayList<String> pairedDeviceNamesList = new ArrayList<String>();
    private ArrayList<BluetoothDevice> pairedDeviceList = new ArrayList<BluetoothDevice>();
    private ArrayList<String> discoveredDeviceNamesList = new ArrayList<String>();
    private ArrayList<BluetoothDevice> discoveredDeviceList = new ArrayList<BluetoothDevice>();
    private ListView pairedListView;
    private ListView discoveredListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list_of_devices);

        int REQUEST_ENABLE_BT = 1; //enable bluetooth constant
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        pairedListView = (ListView)findViewById(R.id.pairedListView);
        discoveredListView = (ListView)findViewById(R.id.nearbyBT);


        //what to do when device doesn't support
        if(bluetoothAdapter == null)
            finish();
        //Asks to turn on bluetooth if not enabled
        if(!bluetoothAdapter.isEnabled()){
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        //add discovered devices into list
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                pairedDeviceList.add(device);
                pairedDeviceNamesList.add(device.getName() + "\n" + device.getAddress());
                pairedListView.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, pairedDeviceNamesList));
            }
        }

        //creates on click listener for already paired devices that
        // when device is clicked, sends device parameters back to main activity
        pairedListView.setOnItemClickListener(new AdapterView.OnItemClickListener(){

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id){
                Intent sendDevice = new Intent();
                sendDevice.putExtra("Device",pairedDeviceList.get(position));
                setResult(RESULT_OK, sendDevice);
                finish();
            }
        });

        //on click listener for discovered devices
        discoveredListView.setOnItemClickListener(new AdapterView.OnItemClickListener(){

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id){
                Intent sendDevice = new Intent();
                sendDevice.putExtra("Device",discoveredDeviceList.get(position));
                setResult(RESULT_OK, sendDevice);
                finish();
            }
        });
    }



    //onclick listener or discover btn
    public void discover(View view){
        DiscoverDevices(bluetoothAdapter, receiver);
    }

    //discovering devices goes to receiver
    private void DiscoverDevices(BluetoothAdapter bluetoothAdapter, BroadcastReceiver receiver){

        discoveredDeviceList.clear();
        discoveredDeviceNamesList.clear();

        if(bluetoothAdapter.isDiscovering())
            bluetoothAdapter.cancelDiscovery();
        bluetoothAdapter.startDiscovery();

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(receiver, filter);
    }

    //anonymous class for broadcast receiver to put discovered devices into arrays and creates listviews
    private final BroadcastReceiver receiver = new BroadcastReceiver(){
        public void onReceive(Context context, Intent intent){
            String action = intent.getAction();
            if(BluetoothDevice.ACTION_FOUND.equals(action)){
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                discoveredDeviceList.add(device);
                discoveredDeviceNamesList.add(device.getName() + "\n" + device.getAddress());
                discoveredListView.setAdapter(new ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, discoveredDeviceNamesList));
            }
        }
    };


    @Override
    protected void onDestroy() {
        try {

            unregisterReceiver(receiver);

        } catch(IllegalArgumentException e) {

            e.printStackTrace();
        }
        super.onDestroy();
    }

}
