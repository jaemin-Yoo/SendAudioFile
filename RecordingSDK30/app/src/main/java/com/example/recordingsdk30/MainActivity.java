package com.example.recordingsdk30;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
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

    // Call State
    TelephonyManager telephonyManager;
    boolean isCalling = false;

    // Recording
    MediaRecorder recorder;

    ArrayList<SampleData> dataArrayList;
    private MyAdapter myAdapter;

    // Socket
    private Socket socket;
    private static String SERVER_IP = "192.168.0.169";
    private static int SERVER_PORT = 50000;
    private DataInputStream dis;
    private DataOutputStream dos;
    private PrintWriter writer;
    private BufferedReader br;
    private static String folderName = "Call";

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
                Toast.makeText(MainActivity.this, "통화 대기 상태", Toast.LENGTH_SHORT).show();
            }
        });

        btn_test = findViewById(R.id.btn_test);
        btn_test.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "TEST");

                Toast.makeText(MainActivity.this, "통화녹음목록 불러오는 중", Toast.LENGTH_SHORT).show();

                dataArrayList = new ArrayList<SampleData>();

                ListView listView = findViewById(R.id.listView);
                myAdapter = new MyAdapter(MainActivity.this, dataArrayList);

                listView.setAdapter(myAdapter);

                sThread.start();
            }
        });
    }

    private Thread sThread = new Thread("Socket Thread"){
        @Override
        public void run() {
            try {
                socket = new Socket(SERVER_IP, SERVER_PORT);
                dos = new DataOutputStream(socket.getOutputStream());
                writer = new PrintWriter(socket.getOutputStream(), true);
                br = new BufferedReader(new InputStreamReader(socket.getInputStream(),"UTF-8"));
                Log.d(TAG, "Socket : "+socket);

                List<String> fileList = FileList(folderName); // 통화 목록에 있는 파일 리스트 가져오기

                List<String> tempList = new ArrayList<>();
                for (String s : fileList){
                    String fileDate = s.substring(s.length()-17, s.length()-4); // 날짜만 추출
                    tempList.add(fileDate);
                }
                Collections.sort(tempList, Collections.reverseOrder()); // 날짜 정렬



                for (int i = 0; i<5; i++){
                    String tempData = tempList.get(i);

                    String sendData = "";
                    for (String s : fileList){
                        if (s.contains(tempData)){
                            sendData = s; // 가장 최근 날짜 파일 추출
                            Log.d(TAG, "sendData : "+sendData);
                            break;
                        }
                    }

                    // 파일 경로 가져오기
                    File filepath = Environment.getExternalStorageDirectory();
                    String path = filepath.getPath(); // /storage/emulated/0

                    // 파일 사이즈 추출
                    File mFile = new File(path+"/"+folderName+"/"+sendData);
                    long fileSize = mFile.length();
                    String strFileSize = Long.toString(fileSize);
                    Log.d(TAG, "fileSize : "+strFileSize);

                    String fileInfo = sendData+"/"+strFileSize;
                    writer.printf(fileInfo);

                    // 파일 전송
                    dis = new DataInputStream(new FileInputStream(mFile));

                    int read;
                    byte[] buf = new byte[1024];
                    while((read = dis.read(buf)) > 0) {
                        dos.write(buf, 0, read);
                        dos.flush();
                    }
                    Log.d(TAG, "Data Transmitted OK!");

                    String recvData = br.readLine(); // 한 줄씩 받기 때문에 개행문자(\n)를 받아야 대기상태에 안머무름
                    Log.d(TAG, "recvData : "+recvData);
                    dataArrayList.add(new SampleData(sendData+"   ->   ",recvData));

                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            myAdapter.notifyDataSetChanged();
                        }
                    });
                }

                dis.close();
                dos.close();
                writer.close();
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };

    private List<String> FileList(String strFolderName){
        File filepath = Environment.getExternalStorageDirectory();
        String path = filepath.getPath(); // /storage/emulated/0

        File directory = new File(path+"/"+strFolderName);
        File[] files = directory.listFiles();

        List<String> filesNameList = new ArrayList<>();

        for (int i=0; i<files.length; i++){
            filesNameList.add(files[i].getName());
        }

        return filesNameList;
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