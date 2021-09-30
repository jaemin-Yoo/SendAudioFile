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
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.BufferedInputStream;
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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainTag";
    private String img_path;

    // Layout
    Button btn_send;
    Button btn_test;
    Button btn_convert;

    // Socket
    private static String SERVER_IP = "192.168.0.169";
    private static int SERVER_PORT = 50000;
    private static String folderName = "Call";

    // Handler
    Handler handler = new Handler(); // 토스트를 띄우기 위해 메인스레드 핸들러 객체 생성


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkPermission();

        isExternalStorageWritable();
        isExternalStorageReadable();

        btn_send = findViewById(R.id.btn_send);
        btn_send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String sendData = findData();
                SocketThread thread = new SocketThread(SERVER_IP, SERVER_PORT, sendData);
                thread.start();
            }
        });

        btn_convert = findViewById(R.id.btn_convert);
        btn_convert.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "CONVERT");
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

    class SocketThread extends Thread{
        String host;
        int port;
        String data;

        public SocketThread(String host, int port, String data){
            this.host = host;
            this.data = data;
            this.port = port;
        }

        @Override
        public void run() {
            try{
                File filepath = Environment.getExternalStorageDirectory();
                String path = filepath.getPath(); // /storage/emulated/0

                Socket socket = new Socket(host, port);

                InputStream is = socket.getInputStream();
                OutputStream os = socket.getOutputStream();

                // 파일명 전송
                byte[] byteArr = null;
                byteArr = data.getBytes("UTF-8");
                os.write(byteArr);
                os.flush();

                // 파일 전송
                DataInputStream dis = new DataInputStream(new FileInputStream(new File(path+"/"+folderName+"/"+data)));
                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

                int read;
                byte[] buf = new byte[1024];
                while((read = dis.read(buf)) > 0) {
                    dos.write(buf, 0, read);
                    dos.flush();
                }

                Log.d(TAG, "Data Transmitted OK!");

                is.close();
                os.close();
                dis.close();
                dos.close();
                socket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private String findData(){
        List<String> fileList = FileList(folderName);

        List<String> tempList = new ArrayList<>();
        for (String s : fileList){
            String fileDate = (s.substring(s.length()-17, s.length()-4)); // 날짜만 추출
            tempList.add(fileDate);
        }
        Collections.sort(tempList, Collections.reverseOrder()); // 날짜 정렬
        String tempData = tempList.get(0); // 가장 최근 날짜 추출
        Log.d(TAG, "tempData : "+tempData);

        String sendData = "";
        for (String s : fileList){
            if (s.contains(tempData)){
                sendData = s; // 가장 최근 날짜 파일 추출
                Log.d(TAG, "sendData : "+sendData);
            }
        }

        return sendData;
    }

    private List<String> FileList(String strFolderName){
        File filepath = Environment.getExternalStorageDirectory();
        String path = filepath.getPath(); // /storage/emulated/0

        File directory = new File(path+"/"+strFolderName);
        File[] files = directory.listFiles();

        List<String> filesNameList = new ArrayList<>();

        for (int i=0; i<files.length; i++){
            filesNameList.add(files[i].getName());
        }

        Log.d(TAG, strFolderName+" 파일 리스트 :"+filesNameList.toString());
        return filesNameList;
    }

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