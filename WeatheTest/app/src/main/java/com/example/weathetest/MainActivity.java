package com.example.weathetest;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Query;

public class MainActivity extends AppCompatActivity implements LocationListener, View.OnClickListener {

    LocationManager locationManager;
    double latitude;
    double longitude;

    String temperature, curWeather, city, county, icon;
    String readMessage;
    int intWeatherTemp, intSensorTemp;
    double doubleWeatherTemp, doubleSensorTemp;

    Boolean isGPSEnabled, isNetworkEnabled;

    WeatherRepo weatherRepo;

    TextView tvCity, tvTemperature, tvCurrentTemp, tvDesiredTemp;
    ImageView ivWeather;

    BluetoothAdapter mBluetoothAdapter;
    Set<BluetoothDevice> mPairedDevices;
    List<String> mListPairedDevices;
    Handler mBluetoothHandler;
    ConnectedBluetoothThread mThreadConnectedBluetooth;
    BluetoothDevice mBluetoothDevice;
    BluetoothSocket mBluetoothSocket;

    final static int BT_REQUEST_ENABLE = 1;
    final static int BT_MESSAGE_READ = 2;
    final static int BT_CONNECTING_STATUS = 3;
    final static UUID BT_UUID = UUID.fromString("**** Type your own UUID ****");

    final static String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 0);

        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        initView();
        requestLocation();

        mBluetoothHandler = new Handler() {
            public void handleMessage(android.os.Message msg) {
                readMessage = null;
                if (msg.what == BT_MESSAGE_READ) {
                    try {
                        readMessage = new String((byte[]) msg.obj, "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                    if (readMessage != null) {

                    }
                }
            }

        };

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.action_menu, menu);

        MenuItem itemSwitch = menu.findItem(R.id.mySwitch); //GET SWITCH ITEM FROM action_menu.
        itemSwitch.setActionView(R.layout.use_switch);  //USE LAYOUT AND SWITCH AS MENU ITEM.
        final Switch sw = (Switch) menu.findItem(R.id.mySwitch).getActionView().findViewById(R.id.action_switch);   // GET SWITCH INSTANCE
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked) {
                    bluetoothOn();
                } else {
                    bluetoothOff();
                }
            }
        });
        return true;    // Ready to run.

    }

    void bluetoothOn() {
        if (mBluetoothAdapter == null) {
            Toast.makeText(getApplicationContext(), "블루투스를 지원하지 않는 기기입니다.", Toast.LENGTH_LONG).show();
        } else {
            if (mBluetoothAdapter.isEnabled()) {
                listPairedDevices();
            } else {
                Toast.makeText(getApplicationContext(), "블루투스가 비활성화 되어 있습니다.", Toast.LENGTH_LONG).show();
                Intent intentBluetoothEnable = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(intentBluetoothEnable, BT_REQUEST_ENABLE);
            }
        }
    }

    void bluetoothOff() {
        if (mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.disable();
            Toast.makeText(getApplicationContext(), "블루투스가 비활성화 되었습니다.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getApplicationContext(), "블루투스가 이미 비활성화 되어 있습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case BT_REQUEST_ENABLE:
                if (resultCode == RESULT_OK) { // 블루투스 활성화를 확인을 클릭하였다면
                    Toast.makeText(getApplicationContext(), "블루투스가 활성화 되었습니다.", Toast.LENGTH_LONG).show();
                    listPairedDevices();
                } else if (resultCode == RESULT_CANCELED) { // 블루투스 활성화를 취소를 클릭하였다면
                    Toast.makeText(getApplicationContext(), "취소되었습니다.", Toast.LENGTH_LONG).show();
                }
                break;

            case 0:
                requestLocation();
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    void listPairedDevices() {
        if (mBluetoothAdapter.isEnabled()) {
            mPairedDevices = mBluetoothAdapter.getBondedDevices();

            if (mPairedDevices.size() > 0) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("장치 선택");

                mListPairedDevices = new ArrayList<String>();
                for (BluetoothDevice device : mPairedDevices) {
                    mListPairedDevices.add(device.getName());
                }
                final CharSequence[] items = mListPairedDevices.toArray(new CharSequence[mListPairedDevices.size()]);
                mListPairedDevices.toArray(new CharSequence[mListPairedDevices.size()]);

                builder.setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int item) {
                        connectSelectedDevice(items[item].toString());
                    }
                });
                AlertDialog alert = builder.create();
                alert.show();
            } else {
                Toast.makeText(getApplicationContext(), "페어링된 장치가 없습니다.", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(getApplicationContext(), "블루투스가 비활성화 되어 있습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    void connectSelectedDevice(String selectedDeviceName) {
        for (BluetoothDevice tempDevice : mPairedDevices) {
            if (selectedDeviceName.equals(tempDevice.getName())) {
                mBluetoothDevice = tempDevice;
                break;
            }
        }
        try {
            mBluetoothSocket = mBluetoothDevice.createRfcommSocketToServiceRecord(BT_UUID);
            mBluetoothSocket.connect();
            mThreadConnectedBluetooth = new ConnectedBluetoothThread(mBluetoothSocket);
            mThreadConnectedBluetooth.start();
            mBluetoothHandler.obtainMessage(BT_CONNECTING_STATUS, 1, -1).sendToTarget();
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), "블루투스 연결 중 오류가 발생했습니다.", Toast.LENGTH_LONG).show();
        }
    }

    private class ConnectedBluetoothThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedBluetoothThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Toast.makeText(getApplicationContext(), "소켓 연결 중 오류가 발생했습니다.", Toast.LENGTH_LONG).show();
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            while (true) {
                try {
                    bytes = mmInStream.available();
                    if (bytes != 0) {
                        SystemClock.sleep(100);
                        bytes = mmInStream.available();
                        bytes = mmInStream.read(buffer, 0, bytes);
                        mBluetoothHandler.obtainMessage(BT_MESSAGE_READ, bytes, -1, buffer).sendToTarget();
                    }
                } catch (IOException e) {
                    break;
                }
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Toast.makeText(getApplicationContext(), "소켓 해제 중 오류가 발생했습니다.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void initView() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        tvCity = (TextView)findViewById(R.id.city);
        tvTemperature = (TextView)findViewById(R.id.temperature);
        ivWeather = (ImageView)findViewById(R.id.weather);
        tvCurrentTemp = (TextView)findViewById(R.id.currentTemp);
    }

    public interface ApiService{
        //Base URL
        static final String BASEURL = "https://apis.openapi.sk.com/";
        static final String APPKEY = "**** Type your own AppKey ****";
        //get 메소ㄷㅡ를 통한 http rest api 통신
        @GET("weather/current/hourly")
        Call<WeatherRepo> getHourly (@Header("appkey")String appKey, @Query("version")int version, @Query("lat") double lat, @Query("lon") double lon);
    }

    private void getWeather(double latitude, double longitude) {
        Retrofit retrofit = new Retrofit.Builder().addConverterFactory(GsonConverterFactory.create()).baseUrl(ApiService.BASEURL).build();
        ApiService apiService = retrofit.create(ApiService.class);
        Call<WeatherRepo> call = apiService.getHourly(ApiService.APPKEY,1,latitude,longitude);
        call.enqueue(new Callback<WeatherRepo>() {
            @Override
            public void onResponse(Call<WeatherRepo> call, Response<WeatherRepo> response) {
                if(response.isSuccessful()){
                    weatherRepo = response.body();
                    if(weatherRepo.getResult().getCode().equals("9200")){ // 9200 = 성공
                        temperature= weatherRepo.getWeather().getHourly().get(0).getTemperature().getTc();
                        city = weatherRepo.getWeather().getHourly().get(0).getGrid().getCity();
                        county = weatherRepo.getWeather().getHourly().get(0).getGrid().getCounty();
                        curWeather = weatherRepo.getWeather().getHourly().get(0).getSky().getName();
                        icon = weatherRepo.getWeather().getHourly().get(0).getSky().getCode();

                        doubleWeatherTemp = Double.parseDouble(temperature);
                        intWeatherTemp = (int) doubleWeatherTemp;

                        tvCity.setText(city+" "+county);
                        tvTemperature.setText(intWeatherTemp+" ℃");
                        weatherImageChange();
                    }else{
                    }
                }
            }

            @Override
            public void onFailure(Call<WeatherRepo> call, Throwable t) {

            }
        });
    }

    private void requestLocation() {
        //사용자로 부터 위치정보 권한체크
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 0);

            requestLocation();
        } else {

            if(!chkGpsEnabled()) {
                Toast.makeText(this, "위치 설정이 꺼져있습니다.", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivityForResult(intent, 0);
            } else {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 1, this);
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 500, 1, this);
            }
        }
    }

    private boolean chkGpsEnabled() {
        // GPS 프로바이더 사용가능여부
        isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        // 네트워크 프로바이더 사용가능여부
        isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        if(isGPSEnabled){
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        /*현재 위치에서 위도경도 값을 받아온뒤 우리는 지속해서 위도 경도를 읽어올것이 아니니
        날씨 api에 위도경도 값을 넘겨주고 위치 정보 모니터링을 제거한다.*/
        latitude = location.getLatitude();
        longitude = location.getLongitude();
        //날씨 가져오기 통신
        getWeather(latitude, longitude);
        //위치정보 모니터링 제거
        locationManager.removeUpdates(MainActivity.this);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
        }
    }

    private void weatherImageChange() {

        if(icon.equals("SKY_O01")){
            ivWeather.setImageResource(R.drawable.sky_a01);
        } else if(icon.equals("SKY_O02")){
            ivWeather.setImageResource(R.drawable.sky_a02);
        } else if(icon.equals("SKY_O03")){
            ivWeather.setImageResource(R.drawable.sky_a03);
        } else if (icon.equals("SKY_O04")){
            ivWeather.setImageResource(R.drawable.sky_a04);
        } else if (icon.equals("SKY_O05")){
            ivWeather.setImageResource(R.drawable.sky_a05);
        } else if (icon.equals("SKY_O06")){
            ivWeather.setImageResource(R.drawable.sky_a06);
        } else if (icon.equals("SKY_O07")){
            ivWeather.setImageResource(R.drawable.sky_a07);
        } else if (icon.equals("SKY_O08")){
            ivWeather.setImageResource(R.drawable.sky_a08);
        } else if (icon.equals("SKY_O09")){
            ivWeather.setImageResource(R.drawable.sky_a09);
        } else if (icon.equals("SKY_O10")){
            ivWeather.setImageResource(R.drawable.sky_a10);
        } else if (icon.equals("SKY_O11")){
            ivWeather.setImageResource(R.drawable.sky_a11);
        } else if (icon.equals("SKY_O12")){
            ivWeather.setImageResource(R.drawable.sky_a12);
        } else if (icon.equals("SKY_O13")){
            ivWeather.setImageResource(R.drawable.sky_a13);
        } else if (icon.equals("SKY_O14")){
            ivWeather.setImageResource(R.drawable.sky_a14);
        }
    }



}
