package com.example.helloapp;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.InetAddresses;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.net.wifi.WifiManager;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channel;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.graphics.*;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    Button btnOnOff,btnDiscover,btnSend,chooseFileButton,btnSendFile;
    ListView listView;
    ImageView imageView;
    TextView readMsgBox, connectionStatus;
    EditText writeMsg;
    private byte[] fileBytes;
    String TYPE =".pdf";

    WifiP2pManager  manager;
    WifiP2pManager.Channel channel;
    private static final int PICKFILE_REQUEST_CODE = 1;

    BroadcastReceiver receiver;
    IntentFilter intentFilter;

    List<WifiP2pDevice> peers = new ArrayList<WifiP2pDevice>();
    String[] deviceNameArray;
    WifiP2pDevice[] deviceArray;
    Socket socket;

    ServerClass serverClass;
    ClientClass clientClass;
    FileChooser fileChooser = new FileChooser(MainActivity.this);

    boolean isHost;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initialWork();
        exqListener();
    }

    private void exqListener(){
        btnOnOff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                 Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
                 startActivityForResult(intent,1);
            }
        });
        btnDiscover.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                         connectionStatus.setText("Discovery Started");
                    }

                    @Override
                    public void onFailure(int i) {
                        connectionStatus.setText("Discovery not Started");
                    }
                });
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final WifiP2pDevice device = deviceArray[position];
                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = device.deviceAddress;;
                manager.connect(channel, config, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        connectionStatus.setText("Connected: " + device.deviceAddress);
                    }

                    @Override
                    public void onFailure(int i) {
                        connectionStatus.setText("Not connected");
                    }
                });
            }
        });

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                String msg = writeMsg.getText().toString();
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        if(msg.length() != 0 && isHost){
                            serverClass.write(msg.getBytes());
                        }
                        else if(msg.length() != 0  && !isHost ){
                            clientClass.write(msg.getBytes());
                        }
                    }
                });
            }
        });

        chooseFileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fileChooser.chooseFile();
            }
        });
        btnSendFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        if( isHost){
                            serverClass.write(fileBytes);
                        }
                        else if(!isHost ){
                            clientClass.write(fileBytes);
                        }
                    }
                });

            }
        });
    }
    private void initialWork(){
        btnOnOff = (Button) findViewById(R.id.onOff);
        btnDiscover = (Button) findViewById(R.id.discover);
        btnSend = (Button) findViewById(R.id.sendButton);
        listView = (ListView) findViewById(R.id.peerListView);
        readMsgBox = (TextView) findViewById(R.id.readMsg);
        connectionStatus = (TextView) findViewById(R.id.connectionStatus);
        writeMsg = (EditText)findViewById(R.id.writeMsg);
        chooseFileButton = (Button)findViewById(R.id.choose_file_button);
        btnSendFile = (Button)findViewById(R.id.sendFile);


        manager = (WifiP2pManager)getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this,getMainLooper(),null);
        receiver = new WiFiDirectBroadcastReceiver( manager,channel,this);

        intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);

    }

    WifiP2pManager.PeerListListener peerListListener = new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList wifiP2pDeviceList) {
            if (!wifiP2pDeviceList.equals(peers)){

                peers.clear();
                peers.addAll(wifiP2pDeviceList.getDeviceList());

                deviceNameArray = new String[wifiP2pDeviceList.getDeviceList().size()];
                deviceArray = new WifiP2pDevice[10];

                int index = 0;
                for (WifiP2pDevice device:wifiP2pDeviceList.getDeviceList()){
                    deviceNameArray[index] = device.deviceName;
                    deviceArray[index] = device;
                    index++;
                }

                ArrayAdapter<String> adapter = new ArrayAdapter<>(getApplicationContext(), android.R.layout.simple_list_item_1,deviceNameArray);
                listView.setAdapter(adapter);

                if(peers.size() == 0){
                    connectionStatus.setText("No Device Found");
                    return;
                }
            }
        }
    };

    WifiP2pManager.ConnectionInfoListener connectionInfoListener = new WifiP2pManager.ConnectionInfoListener() {
        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {
                final InetAddress groupOwnerAdress = wifiP2pInfo.groupOwnerAddress;
                if(wifiP2pInfo.groupFormed && wifiP2pInfo.isGroupOwner){
                    connectionStatus.setText("Host");
                    isHost = true;
                    serverClass = new ServerClass();
                    serverClass.start();
                }else if(wifiP2pInfo.groupFormed){
                    connectionStatus.setText("Client");
                    isHost = false;
                    clientClass = new ClientClass(groupOwnerAdress);
                    clientClass.start();
                }
        }
    };
    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(receiver,intentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }
    public class ServerClass extends Thread{
        ServerSocket serverSocket;
        private InputStream inputStream;
        private OutputStream outputStream;

        public void write(byte[] bytes){
            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                return;
            }
        }

        @Override
        public void run(){
            try {
                serverSocket = new ServerSocket(8888);
                socket  = serverSocket.accept();
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            ExecutorService executor = Executors.newSingleThreadExecutor();
            Handler handler = new Handler(Looper.getMainLooper());

            executor.execute(new Runnable() {
                @Override
                public void run() {
                    byte[] buffer = new byte[1024];
                    int bytes;

                    while(socket != null){
                        try {
                            bytes = inputStream.read(buffer);
                            if (bytes > 0){
                                int finalBytes = bytes;
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (readMsgBox.getText().toString().contains("type")){
                                                saveToFile(buffer,finalBytes,"type");
                                        }else {
                                            String tempMSG = new String(buffer, 0, finalBytes);
                                            readMsgBox.setText(tempMSG);
                                        }
                                    }
                                });
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        }
        private void saveToFile(byte[] Bytes,int length,String type) {
            try {
                byte[] Data = Arrays.copyOf(Bytes, length);
                String fileName = "example"+type; // Название файла, под которым сохранить
                File file = new File(Environment.getExternalStorageDirectory(), fileName); // Создание файла в папке внешнего хранилища

                FileOutputStream fos = new FileOutputStream(file);
                fos.write(Data); // Запись массива байт в файл
                fos.close();

            } catch (IOException e) {
                e.printStackTrace();

            }
        }
    }

    public class ClientClass extends Thread {
        String hostAdd;
        private InputStream inputStream;
        private OutputStream outputStream;

        public ClientClass(InetAddress hostAdress){
            hostAdd = hostAdress.getHostAddress();
            socket = new Socket();
        }

        public void write(byte[] bytes){
            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                return;
            }
        }

        @Override
        public void run(){
            try{
                socket.connect(new InetSocketAddress(hostAdd,8888),500);
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();

            } catch (IOException e){
                e.printStackTrace();
            }

            ExecutorService executor  = Executors.newSingleThreadExecutor();
            Handler handler = new Handler(Looper.getMainLooper());

            executor.execute(new Runnable() {
                @Override
                public void run() {
                    byte[] buffer = new byte[1024];
                    int bytes;

                    while(socket!=null){
                        try{
                            bytes = inputStream.read(buffer);
                            if(bytes > 0){
                                int finalBytes = bytes;
                                handler.post(new Runnable() {

                                    @Override
                                    public void run() {
                                        if (readMsgBox.getText().toString().contains(TYPE)){
                                            saveToFile(buffer,finalBytes,TYPE);
                                        }else {
                                            String tempMSG = new String(buffer, 0, finalBytes);
                                            readMsgBox.setText(tempMSG);
                                        }
                                    }
                                });
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        }

        private void saveToFile(byte[] Bytes,int length,String type) {
            try {
                byte[] Data = Arrays.copyOf(Bytes, length);
                String fileName = "example"+type;
                File file = new File(Environment.getExternalStorageDirectory(), fileName);

                FileOutputStream fos = new FileOutputStream(file);
                fos.write(Data); // Запись массива байт в файл
                fos.close();

            } catch (IOException e) {
                e.printStackTrace();

            }
        }

    }


    public class FileChooser {



        private final Context context;

        public FileChooser(Context context) {
            this.context = context;
        }

        public void chooseFile() {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);

            Intent chooserIntent = Intent.createChooser(intent, "Select File");
            startActivityForResult(chooserIntent, PICKFILE_REQUEST_CODE);
        }

        private void startActivityForResult(Intent intent, int requestCode) {
            if (context instanceof Activity) {
                ((Activity) context).startActivityForResult(intent, requestCode);
            }
        }




        public byte[] getFileBytes(Uri uri) throws IOException {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                byteStream.write(buffer, 0, bytesRead);
            }
            return byteStream.toByteArray();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICKFILE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            Uri uri = data.getData();
            Log.d("FileChooser", "Selected file URI: " + uri);
            try {
                fileBytes = fileChooser.getFileBytes(uri);

                Log.d("FileChooser", "Selected file URI: " + fileBytes);
            } catch (IOException e) {
                //Handle exception
                Log.d("FileChooser", "Ban"+fileBytes);
                return;
            }
        }
    }

}