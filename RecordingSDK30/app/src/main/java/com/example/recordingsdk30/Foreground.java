package com.example.recordingsdk30;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class Foreground extends Service {
    private static final String TAG = "ForegroundTag";
    private String connTime;
    private String prevSize = "";

    public static Intent serviceIntent;

    // Notification
    private static final String CHANNEL_ID = "FGS1";
    private static final int NOTI_ID = 0;
    private NotificationManager mNotificationManager;

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
        SimpleDateFormat sdf = new SimpleDateFormat("yyMMdd_HHmmss");
        connTime = sdf.format(mDate);
        Log.d(TAG, "connTime: "+connTime);

        sThread.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        serviceIntent = intent;

        initializeNotification();
        return START_STICKY;
    }

    private Thread sThread = new Thread("Socket thread"){
        @Override
        public void run() {
            while(true){
                try{
                    socket = new Socket(SERVER_IP, SERVER_PORT);
                    dos = new DataOutputStream(socket.getOutputStream());
                    writer = new PrintWriter(socket.getOutputStream(), true);
                    br = new BufferedReader(new InputStreamReader(socket.getInputStream(),"UTF-8"));
                    Log.d(TAG, "Socket : "+socket);
                    while(true){
                        String sendData = findData(); // 통화 중 저장된 녹음파일 가져오기
                        if (sendData != "Not yet"){
                            while(true){
                                Log.d(TAG, "Recording..");
                                // 파일 경로 가져오기
                                File filepath = Environment.getExternalStorageDirectory();
                                String path = filepath.getPath(); // /storage/emulated/0

                                // 파일 사이즈 추출
                                File mFile = new File(path+"/"+folderName+"/"+sendData);
                                long fileSize = mFile.length();
                                String strFileSize = Long.toString(fileSize);
                                Log.d(TAG, "fileSize : "+strFileSize);
                                Log.d(TAG, "prevSize : "+prevSize);

                                // 통화 녹음이 완료되기 전 파일을 전송하는 것을 방지
                                if (prevSize.equals(strFileSize) == false) {
                                    prevSize = strFileSize;
                                    sleep(3000);
                                    continue;
                                }
                                Log.d(TAG, "Ready");

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
                                connTime = sendData.substring(sendData.length()-17, sendData.length()-4); // connTime 재정의

                                String recvData = br.readLine(); // 한 줄씩 받기 때문에 개행문자(\n)를 받아야 대기상태에 안머무름
                                Log.d(TAG, "recvData : "+recvData);

                                if (recvData == null){
                                    socket = null;
                                    break;
                                }
                                warningNotification(recvData); // 푸쉬 알림

                                // Service 에서 Toast를 사용하기 위한 Handler
                                Handler handler = new Handler(Looper.getMainLooper());
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(Foreground.this, recvData, Toast.LENGTH_SHORT).show();
                                    }
                                });

                                break;
                            }
                        } else{
                            Log.d(TAG, "No recording");
                            sleep(5000);
                        }
                    }
                } catch (IOException | InterruptedException e) {
                    if (sThread == null){
                        Log.d(TAG, "sThread Exit");
                        break; // 스레드를 종료해도 while문이 작동하는 현상 해결
                    } else{
                        try {
                            Log.d(TAG, "Socket Connection Wait..");
                            socket = null;
                            sleep(5000); // 서버와 연결이 안되면, 주기적으로 서버와 연결을 요청함
                        } catch (InterruptedException interruptedException) {
                            Log.d(TAG, "sThread Error");
                            interruptedException.printStackTrace();
                        }
                    }
                }
            }
        }
    };

    private String findData(){
        List<String> fileList = FileList(folderName); // 통화 목록에 있는 파일 리스트 가져오기

        List<String> tempList = new ArrayList<>();
        for (String s : fileList){
            String fileDate = s.substring(s.length()-17, s.length()-4); // 날짜만 추출
            tempList.add(fileDate);
        }
        tempList.add(connTime);
        Collections.sort(tempList, Collections.reverseOrder()); // 날짜 정렬
        String tempData = tempList.get(0); // 가장 최근 날짜 추출

        // 통화 중 저장된 파일 확인
        if (tempData.equals(connTime) == true){
            return "Not yet";
        }

        String sendFileName = "";
        for (String s : fileList){
            if (s.contains(tempData)){
                sendFileName = s; // 가장 최근 날짜 파일 추출
                Log.d(TAG, "sendFileName : "+sendFileName);
                break;
            }
        }

        return sendFileName;
    }

    public List<String> FileList(String strFolderName){
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

    // Notification Builder를 만드는 메소드
    private NotificationCompat.Builder getNotificationBuilder(String msg) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID);
        builder.setContentText(msg);
        builder.setContentTitle("피싱 판단");
        builder.setSmallIcon(R.drawable.ic_launcher_foreground);
        builder.setAutoCancel(true);
        builder.setVibrate(new long[]{1000,1000});
        builder.setWhen(0);
        builder.setShowWhen(true);
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        builder.setContentIntent(pendingIntent);
        return builder;
    }

    // Notification을 보내는 메소드
    public void warningNotification(String msg){
        // Builder 생성
        NotificationCompat.Builder notifyBuilder = getNotificationBuilder(msg);
        // Manager를 통해 notification 디바이스로 전달
        mNotificationManager.notify(NOTI_ID,notifyBuilder.build());
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
        startForeground(1, notification);
    }

    public void createNotificationChannel() {

        //notification manager 생성
        mNotificationManager = (NotificationManager)
                getSystemService(NOTIFICATION_SERVICE);
        // 기기(device)의 SDK 버전 확인 ( SDK 26 버전 이상인지 - VERSION_CODES.O = 26)
        if(android.os.Build.VERSION.SDK_INT
                >= android.os.Build.VERSION_CODES.O){
            //Channel 정의 생성자( construct 이용 )
            NotificationChannel notificationChannel = new NotificationChannel(CHANNEL_ID
                    ,"Test Notification",mNotificationManager.IMPORTANCE_HIGH);
            //Channel에 대한 기본 설정
            notificationChannel.enableLights(true);
            notificationChannel.setLightColor(Color.RED);
            notificationChannel.enableVibration(true);
            notificationChannel.setDescription("Notification from Mascot");
            // Manager을 이용하여 Channel 생성
            mNotificationManager.createNotificationChannel(notificationChannel);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        try {
            if (serviceIntent != null){
                serviceIntent = null;
            }

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
                socket = null;
            }

            if (sThread != null){
                sThread.interrupt();
                sThread = null;
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
