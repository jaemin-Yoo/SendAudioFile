package com.example.sendaudiofile;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainTag";

    //Button
    Button btn_send;

    //TextView
    TextView tv_state;

    // Socket
    private Socket socket;
    private static String SERVER_IP = "192.168.0.169";
    private static int SERVER_PORT = 50000;
    private static String folderName = "Music";
    private static String fileName = "Jeremy Zucker - comethru (Official Video).m4a";
    private DataInputStream dis;
    private DataOutputStream dos;
    private PrintWriter writer;
    private BufferedReader br;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestPermission();

        tv_state = findViewById(R.id.tv_state);
        btn_send = findViewById(R.id.btn_send);
        btn_send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                tv_state.setText("서버 연결 중");
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

                tv_state.setText("전송 중");

                // 파일 경로 가져오기
                File filepath = Environment.getExternalStorageDirectory();
                String path = filepath.getPath(); // /storage/emulated/0

                // 파일 사이즈 추출
                File mFile = new File(path+"/"+folderName+"/"+fileName);
                long fileSize = mFile.length();
                String strFileSize = Long.toString(fileSize);

                // 파일명, 파일 사이즈 전송
                String fileInfo = fileName+"/"+strFileSize;
                writer.printf(fileInfo);
                Log.d(TAG, "fileInfo: "+fileInfo);

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

                tv_state.setText(recvData);

                dis.close();
                dos.close();
                writer.close();
                socket.close();
            } catch (IOException e) {
                Log.d(TAG, "No Connect");
                tv_state.setText("서버 연결 실패");
                e.printStackTrace();
            }
        }
    };

    private void requestPermission(){
        if (Build.VERSION.SDK_INT >= 23 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){

            ActivityCompat.requestPermissions(
                    this,
                    new String[] {Manifest.permission.READ_EXTERNAL_STORAGE}, 0
            );
        }
    }
}