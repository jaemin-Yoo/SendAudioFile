package com.example.recordingsdk30;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainTag";

    // Layout
    Button btn_wait;
    Button btn_test;

    // 통화 상태
    TelephonyManager telephonyManager;
    boolean isCalling = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkPermission();

        isExternalStorageWritable();
        isExternalStorageReadable();

        btn_wait = findViewById(R.id.btn_wait);
        btn_wait.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
                telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
            }
        });

        btn_test = findViewById(R.id.btn_test);
        btn_test.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "TEST");
            }
        });
    }

    PhoneStateListener phoneStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String phoneNumber) {
            switch (state) {
                case TelephonyManager.CALL_STATE_IDLE:
                    if (isCalling == true){
                        Log.d(TAG, "통화 종료");
                        if (Foreground.serviceIntent != null){
                            Intent serviceIntent = new Intent(MainActivity.this, Foreground.class);
                            stopService(serviceIntent);
                            Toast.makeText(MainActivity.this, "서비스 종료", Toast.LENGTH_SHORT).show();
                        } else{
                            Log.d(TAG, "실행 중인 서비스가 없습니다.");
                            Toast.makeText(MainActivity.this, "실행 중인 서비스가 없습니다.", Toast.LENGTH_SHORT).show();
                        }
                    }
                    isCalling = false;
                    break;
                case TelephonyManager.CALL_STATE_OFFHOOK:
                    Log.d(TAG, "통화 발신 중");
                    if (Foreground.serviceIntent == null){
                        Intent serviceIntent = new Intent(MainActivity.this, Foreground.class);
                        startService(serviceIntent);
                        Toast.makeText(MainActivity.this, "서비스 실행", Toast.LENGTH_SHORT).show();
                        Toast.makeText(MainActivity.this, "스팸 판단을 위해 최소 1분~1분30초 간 녹음을 진행해주세요.", Toast.LENGTH_SHORT).show();
                    } else{
                        Log.d(TAG, "이미 실행 중 입니다.");
                        Toast.makeText(MainActivity.this, "이미 실행 중 입니다.", Toast.LENGTH_SHORT).show();
                    }
                    isCalling = true;
                    break;
                case TelephonyManager.CALL_STATE_RINGING:
                    Log.d(TAG, "통화 수신 중");
                    if (Foreground.serviceIntent == null){
                        Intent serviceIntent = new Intent(MainActivity.this, Foreground.class);
                        startService(serviceIntent);
                        Toast.makeText(MainActivity.this, "서비스 실행", Toast.LENGTH_SHORT).show();
                        Toast.makeText(MainActivity.this, "스팸 판단을 위해 최소 1분~1분30초 간 녹음을 진행해주세요.", Toast.LENGTH_SHORT).show();
                    } else{
                        Log.d(TAG, "이미 실행 중 입니다.");
                        Toast.makeText(MainActivity.this, "이미 실행 중 입니다.", Toast.LENGTH_SHORT).show();
                    }
                    isCalling = true;
                    break;
            }
        }
    };

    public void checkPermission(){
        String temp = "";

        //파일 읽기 권한 확인
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            temp += Manifest.permission.READ_EXTERNAL_STORAGE + " ";
        }

        //파일 쓰기 권한 확인
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            temp += Manifest.permission.WRITE_EXTERNAL_STORAGE + " ";
        }

        if (TextUtils.isEmpty(temp) == false) {
            // 권한 요청
            ActivityCompat.requestPermissions(this, temp.trim().split(" "),1);
        }else {
            // 모두 허용 상태
            Toast.makeText(this, "권한을 모두 허용", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        //권한을 허용 했을 경우
        if (requestCode == 1) {
            int length = permissions.length;
            for (int i = 0; i < length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    // 동의
                    Log.d("MainActivity", "권한 허용 : " + permissions[i]);
                }
            }
        }
    }

    /* 외부 저장소가 현재 read와 write를 할 수 있는 상태인지 확인한다 */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            Log.d(TAG, "write 가능");
            return true;
        }
        return false;
    }



    /* 외부 저장소가 현재 read만이라도 할 수 있는 상태인지 확인한다 */
    public boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            Log.d(TAG, "read 가능");
            return true;
        }
        return false;
    }
}