package com.example.recordingsdk30;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class Foreground extends Service {
    private static final String TAG = "ForegroundTag";
    private String connTime;

    // Notification
    private static final String CHANNEL_ID = "FGS1";
    private static final int NOTI_ID = 1;

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
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();

        // if 통화 중 상태라면
        long now = System.currentTimeMillis();
        Date mDate = new Date(now);
        SimpleDateFormat sdf = new SimpleDateFormat("yyMMdd_hhmmss");
        connTime = sdf.format(mDate);
        Log.d(TAG, "connTime: "+connTime);
        sThread.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        initializeNotification();
        return START_STICKY;
    }

    private Thread sThread = new Thread("Socket thread"){
        @Override
        public void run() {
            try{
                socket = new Socket(SERVER_IP, SERVER_PORT);
                dos = new DataOutputStream(socket.getOutputStream());
                writer = new PrintWriter(socket.getOutputStream(), true);
                br = new BufferedReader(new InputStreamReader(socket.getInputStream(),"UTF-8"));
                while(true){
                    String sendData = findData(); // 통화 중 저장된 통화파일 가져오기
                    if (sendData != "Not yet"){
                        Log.d(TAG, "Ready");
                        File filepath = Environment.getExternalStorageDirectory();
                        String path = filepath.getPath(); // /storage/emulated/0

                        writer.printf(sendData);

                        // 파일 전송
                        dis = new DataInputStream(new FileInputStream(new File(path+"/"+folderName+"/"+sendData)));

                        int read;
                        byte[] buf = new byte[1024];
                        while((read = dis.read(buf)) > 0) {
                            dos.write(buf, 0, read);
                            dos.flush();
                        }
                        Log.d(TAG, "Data Transmitted OK!");

                        dos.writeUTF("Complete");
                        dos.flush();

                        String recvData = br.readLine();

                        Log.d(TAG, "recvData : "+recvData);

                        Handler handler = new Handler(Looper.getMainLooper());
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(Foreground.this, recvData, Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else{
                        Log.d(TAG, "Not yet");
                        sleep(10000);
                    }

                    break;
                }
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    };

    private String findData(){
        List<String> fileList = FileList(folderName); // 통화 목록에 있는 파일 리스트 가져오기

        List<String> tempList = new ArrayList<>();
        for (String s : fileList){
            String fileDate = (s.substring(s.length()-17, s.length()-4)); // 날짜만 추출
            tempList.add(fileDate);
        }
        //tempList.add(connTime);
        Collections.sort(tempList, Collections.reverseOrder()); // 날짜 정렬
        String tempData = tempList.get(0); // 가장 최근 날짜 추출

        // 통화 중 저장된 파일 확인
//        if (tempData == connTime){
//            return "Not yet";
//        } else{
//            connTime = tempData;
//        }

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

        return filesNameList;
    }

    public void initializeNotification(){
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "1");

        NotificationCompat.BigTextStyle style = new NotificationCompat.BigTextStyle();
        style.bigText("설정을 보려면 누르세요.");
        style.setBigContentTitle(null);
        style.setSummaryText("피싱 판단 중");
        builder.setContentText("피싱 판단 중");
        builder.setContentTitle("Mosk");
        builder.setOngoing(true);
        builder.setStyle(style);
        builder.setWhen(0);
        builder.setShowWhen(false);
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        builder.setContentIntent(pendingIntent);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(new NotificationChannel("1", "undead_service", NotificationManager.IMPORTANCE_NONE));
        }
        Notification notification = builder.build();
        startForeground(NOTI_ID, notification);
    }

    public void createNotificationChannel() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            NotificationChannel serviceChannel = new NotificationChannel(CHANNEL_ID, "FOREGROUND", NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager mNotificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            mNotificationManager.createNotificationChannel(serviceChannel);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        try {
            if (dis != null){
                dis.close();
            }
            if (dos != null){
                dos.close();
            }
            if (writer != null){
                writer.close();
            }
            if (socket != null){
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
