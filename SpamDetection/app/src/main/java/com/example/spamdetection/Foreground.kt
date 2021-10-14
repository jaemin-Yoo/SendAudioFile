package com.example.spamdetection

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.os.*
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.io.*
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*


class Foreground : Service() {
    private var connTime: String? = null
    private var prevSize = ""
    private var mNotificationManager: NotificationManager? = null

    // Socket
    private var socket: Socket? = null
    private var dis: DataInputStream? = null
    private var dos: DataOutputStream? = null
    private var writer: PrintWriter? = null
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // if 통화 중 상태라면
        val now = System.currentTimeMillis()
        val mDate = Date(now)
        val sdf = SimpleDateFormat("yyMMdd_HHmmss")
        connTime = sdf.format(mDate)
        Log.d(TAG, "connTime: $connTime")
        sThread!!.start()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        serviceIntent = intent
        initializeNotification()
        return START_STICKY
    }

    private var sThread: Thread? = object : Thread("Socket thread") {
        override fun run() {
            while (true) {
                try {
                    socket = Socket(SERVER_IP, SERVER_PORT)
                    dos = DataOutputStream(socket!!.getOutputStream())
                    writer = PrintWriter(socket!!.getOutputStream(), true)
                    val br = BufferedReader(
                        InputStreamReader(
                            socket!!.getInputStream(), "UTF-8"
                        )
                    )
                    Log.d(TAG, "Socket : $socket")
                    while (true) {
                        val sendData = findData() // 통화 중 저장된 녹음파일 가져오기
                        if (sendData !== "Not yet") {
                            while (true) {
                                Log.d(TAG, "Recording..")
                                // 파일 경로 가져오기
                                val filepath = Environment.getExternalStorageDirectory()
                                val path = filepath.path // /storage/emulated/0

                                // 파일 사이즈 추출
                                val mFile = File(path + "/" + folderName + "/" + sendData)
                                val fileSize = mFile.length()
                                val strFileSize = java.lang.Long.toString(fileSize)
                                Log.d(
                                    TAG,
                                    "fileSize : $strFileSize"
                                )
                                Log.d(TAG, "prevSize : $prevSize")

                                // 통화 녹음이 완료되기 전 파일을 전송하는 것을 방지
                                if (prevSize == strFileSize == false) {
                                    prevSize = strFileSize
                                    sleep(3000)
                                    continue
                                }
                                Log.d(TAG, "Ready")
                                val fileInfo = "$sendData/$strFileSize"
                                writer!!.printf(fileInfo)

                                // 파일 전송
                                dis = DataInputStream(FileInputStream(mFile))
                                var read: Int
                                val buf = ByteArray(1024)
                                while (dis!!.read(buf).also { read = it } > 0) {
                                    dos!!.write(buf, 0, read)
                                    dos!!.flush()
                                }
                                Log.d(TAG, "Data Transmitted OK!")
                                connTime = sendData.substring(
                                    sendData.length - 17,
                                    sendData.length - 4
                                ) // connTime 재정의
                                val recvData = br.readLine() // 한 줄씩 받기 때문에 개행문자(\n)를 받아야 대기상태에 안머무름
                                Log.d(TAG, "recvData : $recvData")
                                if (recvData == null) {
                                    socket = null
                                    break
                                }
                                warningNotification(recvData) // 푸쉬 알림

                                // Service 에서 Toast를 사용하기 위한 Handler
                                val handler = Handler(Looper.getMainLooper())
                                handler.post {
                                    Toast.makeText(
                                        this@Foreground,
                                        recvData,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                break
                            }
                        } else {
                            Log.d(TAG, "No recording")
                            sleep(5000)
                        }
                    }
                } catch (e: InterruptedException) {
                    if (socket == null) {
                        Log.d(TAG, "sThread Exit")
                        break
                    } else {
                        try {
                            Log.d(TAG, "Socket Connection Wait..")
                            socket = null
                            sleep(5000)
                        } catch (interruptedException: InterruptedException) {
                            Log.d(TAG, "sThread Error")
                            interruptedException.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    private fun findData(): String {
        val fileList = FileList(folderName) // 통화 목록에 있는 파일 리스트 가져오기
        val tempList: MutableList<String?> = ArrayList()
        for (s in fileList) {
            val fileDate = s.substring(s.length - 17, s.length - 4) // 날짜만 추출
            tempList.add(fileDate)
        }
        tempList.add(connTime)
        Collections.sort(tempList, Collections.reverseOrder()) // 날짜 정렬
        val tempData = tempList[0] // 가장 최근 날짜 추출

        // 통화 중 저장된 파일 확인
        if (tempData == connTime == true) {
            return "Not yet"
        }
        var sendFileName = ""
        for (s in fileList) {
            if (s.contains(tempData!!)) {
                sendFileName = s // 가장 최근 날짜 파일 추출
                Log.d(TAG, "sendFileName : $sendFileName")
                break
            }
        }
        return sendFileName
    }

    fun FileList(strFolderName: String): List<String> {
        val filepath = Environment.getExternalStorageDirectory()
        val path = filepath.path // /storage/emulated/0
        val directory = File("$path/$strFolderName")
        val files = directory.listFiles()
        val filesNameList: MutableList<String> = ArrayList()
        for (i in files.indices) {
            filesNameList.add(files[i].name)
        }
        return filesNameList
    }

    // Notification Builder를 만드는 메소드
    private fun getNotificationBuilder(msg: String): NotificationCompat.Builder {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
        builder.setContentText(msg)
        builder.setContentTitle("피싱 판단")
        builder.setSmallIcon(R.drawable.ic_launcher_foreground)
        builder.setAutoCancel(true)
        builder.setVibrate(longArrayOf(1000, 1000))
        builder.setWhen(0)
        builder.setShowWhen(true)
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)
        builder.setContentIntent(pendingIntent)
        return builder
    }

    // Notification을 보내는 메소드
    fun warningNotification(msg: String) {
        // Builder 생성
        val notifyBuilder = getNotificationBuilder(msg)
        // Manager를 통해 notification 디바이스로 전달
        mNotificationManager!!.notify(NOTI_ID, notifyBuilder.build())
    }

    fun initializeNotification() {
        val builder = NotificationCompat.Builder(this, "1")
        val style = NotificationCompat.BigTextStyle()
        style.bigText("설정을 보려면 누르세요.")
        style.setBigContentTitle(null)
        style.setSummaryText("피싱 판단 중")
        builder.setContentText("피싱 판단 중")
        builder.setContentTitle("Mosk")
        builder.setOngoing(true)
        builder.setStyle(style)
        builder.setWhen(0)
        builder.setShowWhen(false)
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)
        builder.setContentIntent(pendingIntent)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    "1",
                    "undead_service",
                    NotificationManager.IMPORTANCE_NONE
                )
            )
        }
        val notification = builder.build()
        startForeground(1, notification)
    }

    fun createNotificationChannel() {

        //notification manager 생성
        mNotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        // 기기(device)의 SDK 버전 확인 ( SDK 26 버전 이상인지 - VERSION_CODES.O = 26)
        if (Build.VERSION.SDK_INT
            >= Build.VERSION_CODES.O
        ) {
            //Channel 정의 생성자( construct 이용 )
            val notificationChannel = NotificationChannel(
                CHANNEL_ID, "Test Notification", NotificationManager.IMPORTANCE_HIGH
            )
            //Channel에 대한 기본 설정
            notificationChannel.enableLights(true)
            notificationChannel.lightColor = Color.RED
            notificationChannel.enableVibration(true)
            notificationChannel.description = "Notification from Mascot"
            // Manager을 이용하여 Channel 생성
            mNotificationManager!!.createNotificationChannel(notificationChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (serviceIntent != null) {
                serviceIntent = null
            }
            if (dis != null) {
                dis!!.close()
            }
            if (dos != null) {
                dos!!.close()
            }
            if (writer != null) {
                writer!!.close()
            }
            if (socket != null) {
                socket!!.close()
                socket = null
            }

            if (sThread != null) {
                sThread!!.interrupt()
                sThread = null
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    companion object {
        private const val TAG = "ForegroundTag"
        var serviceIntent: Intent? = null

        // Notification
        private const val CHANNEL_ID = "FGS1"
        private const val NOTI_ID = 0
        private const val SERVER_IP = "192.168.0.169"
        private const val SERVER_PORT = 50000
        private const val folderName = "Call"
    }
}
