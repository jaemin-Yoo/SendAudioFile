package com.example.spamdetection

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.*
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.AdapterView.OnItemClickListener
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.*
import java.net.Socket
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {

    private val TAG : String = "MainTag"

    // Call State
    private lateinit var telephonyManager : TelephonyManager
    var isCalling : Boolean = false

    // List
    lateinit var dataArrayList: ArrayList<SampleData>
    private var myAdapter: MyAdapter? = null

    // Socket
    private var socket: Socket? = null
    private val SERVER_IP = "192.168.0.169"
    private val SERVER_PORT = 50000
    private var dis: DataInputStream? = null
    private var dos: DataOutputStream? = null
    private var writer: PrintWriter? = null
    private var br: BufferedReader? = null
    private val folderName = "Call"

    // MediaPlayer
    private var mediaPlayer: MediaPlayer? = null
    private var stateMediaPlayer = 0
    private val STATE_NOTSTARTER = 0
    private val STATE_PLAYING = 1
    private val STATE_PAUSING = 2
    private val STATEMP_ERROR = 3

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkPermission()

        val btn_wait : Button = findViewById(R.id.btn_wait)
        btn_wait.setOnClickListener {
            Log.d(TAG, "Wait..")
            telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
            Toast.makeText(this@MainActivity,"통화 대기 상태",Toast.LENGTH_SHORT).show()
        }

        val btn_list : Button = findViewById(R.id.btn_list)
        btn_list.setOnClickListener {
            Log.d(TAG, "Load..")
            Toast.makeText(this@MainActivity,"통화 녹음목록 불러오는 중",Toast.LENGTH_SHORT).show()

            dataArrayList = ArrayList<SampleData>()
            dataArrayList!!.add(SampleData("서버 연결상태 확인 중..", ""))
            val listView = findViewById<ListView>(R.id.listView)
            myAdapter = MyAdapter(this@MainActivity, dataArrayList)
            listView.adapter = myAdapter
            listView.onItemClickListener =
                OnItemClickListener { adapterView, view, i, l ->
                    val fileName: String = myAdapter!!.getItem(i).record_name
                    initMediaPlayer(fileName)
                    when (stateMediaPlayer) {
                        STATE_NOTSTARTER -> {
                            mediaPlayer!!.start()
                            stateMediaPlayer = STATE_PLAYING
                            Toast.makeText(this@MainActivity, "재생", Toast.LENGTH_SHORT).show()
                        }
                        STATE_PLAYING -> {
                            mediaPlayer!!.pause()
                            stateMediaPlayer = STATE_PAUSING
                        }
                        STATE_PAUSING -> {
                            mediaPlayer!!.start()
                            stateMediaPlayer = STATE_PLAYING
                        }
                    }
                }
            sThread.start()
        }

        val btn_stop : Button = findViewById<Button>(R.id.btn_stop)
        btn_stop.setOnClickListener(View.OnClickListener {
            mediaPlayer!!.stop()
            mediaPlayer!!.release()
            Toast.makeText(this@MainActivity, "중지", Toast.LENGTH_SHORT).show()
        })

        val btn_test : Button = findViewById(R.id.btn_test)
        btn_test.setOnClickListener {
            Log.d(TAG, "TEST")
            Toast.makeText(this@MainActivity, "테스트", Toast.LENGTH_SHORT).show()
        }

    }

    private val sThread: Thread = object : Thread("Socket Thread") {
        override fun run() {
            try {
                socket = Socket(SERVER_IP, SERVER_PORT)
                dataArrayList!!.clear()
                dataArrayList!!.add(SampleData("불러오는 중..", ""))
                var handler = Handler(Looper.getMainLooper())
                handler.post { myAdapter!!.notifyDataSetChanged() }
                dos = DataOutputStream(socket!!.getOutputStream())
                writer = PrintWriter(socket!!.getOutputStream(), true)
                br = BufferedReader(InputStreamReader(socket!!.getInputStream(), "UTF-8"))
                Log.d(TAG, "Socket : $socket")

                val fileList = getRecordList()

                if (fileList!!.size != 0){
                    for (i in 0..4) {
                        val sendData = fileList!![i]

                        // 파일 경로 가져오기
                        val filepath = Environment.getExternalStorageDirectory()
                        val path = filepath.path // /storage/emulated/0

                        // 파일 사이즈 추출
                        val mFile = File(path + "/" + folderName + "/" + sendData)
                        val fileSize = mFile.length()
                        val strFileSize = java.lang.Long.toString(fileSize)
                        Log.d(TAG, "fileSize : $strFileSize")
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
                        val recvData = br!!.readLine() // 한 줄씩 받기 때문에 개행문자(\n)를 받아야 대기상태에 안머무름
                        Log.d(TAG, "recvData : $recvData")
                        dataArrayList!!.add(SampleData(sendData, "   ->   $recvData"))
                        handler = Handler(Looper.getMainLooper())
                        handler.post { myAdapter!!.notifyDataSetChanged() }

                    }
                    dataArrayList!!.removeAt(0)
                    dataArrayList!!.add(0, SampleData("완료", ""))
                } else{
                    dataArrayList!!.removeAt(0)
                    dataArrayList!!.add(0, SampleData("완료 (녹음파일이 없습니다.)", ""))
                }

                handler = Handler(Looper.getMainLooper())
                handler.post { myAdapter!!.notifyDataSetChanged() }
                dis!!.close()
                dos!!.close()
                writer!!.close()
                socket!!.close()
            } catch (e: IOException) {
                Log.d(TAG, "No Connect")
                dataArrayList!!.clear()
                dataArrayList!!.add(SampleData("서버 연결 안됨", ""))
                val handler = Handler(Looper.getMainLooper())
                handler.post { myAdapter!!.notifyDataSetChanged() }
                e.printStackTrace()
            }
        }
    }

    private fun initMediaPlayer(fileName: String) {
        val filepath = Environment.getExternalStorageDirectory()
        val path = filepath.path // /storage/emulated/0
        mediaPlayer = MediaPlayer()
        try {
            mediaPlayer!!.setDataSource(path + "/" + folderName + "/" + fileName)
            mediaPlayer!!.prepare()
            stateMediaPlayer = STATE_NOTSTARTER
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun getRecordList(): List<String>? {
        val filepath = Environment.getExternalStorageDirectory()
        val path = filepath.path // /storage/emulated/0
        val directory = File(path + "/" + folderName)
        val files = directory.listFiles()
        val filesNameList: MutableList<String> = java.util.ArrayList()
        for (i in files.indices) {
            filesNameList.add(files[i].name)
        }
        val tempList: MutableList<String> = java.util.ArrayList()
        for (s in filesNameList) {
            val fileDate = s.substring(s.length - 17, s.length - 4) // 날짜만 추출
            tempList.add(fileDate)
        }
        Collections.sort(tempList, Collections.reverseOrder()) // 날짜 정렬
        val sortList: MutableList<String> = java.util.ArrayList()
        for (t in tempList) {
            for (s in filesNameList) {
                if (s.contains(t)) {
                    sortList.add(s)
                }
            }
        }
        return sortList
    }



    var phoneStateListener: PhoneStateListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String) {
            when (state) {
                TelephonyManager.CALL_STATE_IDLE -> {
                    if (isCalling == true) {
                        Log.d(TAG, "통화 종료")
                        if (Foreground.serviceIntent != null) {
                            val serviceIntent = Intent(this@MainActivity, Foreground::class.java)
                            stopService(serviceIntent)
                            Toast.makeText(this@MainActivity, "서비스 종료", Toast.LENGTH_SHORT).show()
                        } else {
                            Log.d(TAG, "실행 중인 서비스가 없습니다.")
                            Toast.makeText(
                                this@MainActivity,
                                "실행 중인 서비스가 없습니다.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    isCalling = false
                }
                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    Log.d(TAG, "통화 발신 중")
                    if (Foreground.serviceIntent == null) {
                        val serviceIntent = Intent(this@MainActivity, Foreground::class.java)
                        startService(serviceIntent)
                        Toast.makeText(this@MainActivity, "서비스 실행", Toast.LENGTH_SHORT).show()
                        Toast.makeText(
                            this@MainActivity,
                            "스팸 판단을 위해 최소 1분~1분30초 간 녹음을 진행해주세요.",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Log.d(TAG, "이미 실행 중 입니다.")
                        Toast.makeText(this@MainActivity, "이미 실행 중 입니다.", Toast.LENGTH_SHORT).show()
                    }
                    isCalling = true
                }
                TelephonyManager.CALL_STATE_RINGING -> {
                    Log.d(TAG, "통화 수신 중")
                    if (Foreground.serviceIntent == null) {
                        val serviceIntent = Intent(this@MainActivity, Foreground::class.java)
                        startService(serviceIntent)
                        Toast.makeText(this@MainActivity, "서비스 실행", Toast.LENGTH_SHORT).show()
                        Toast.makeText(
                            this@MainActivity,
                            "스팸 판단을 위해 최소 1분~1분30초 간 녹음을 진행해주세요.",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Log.d(TAG, "이미 실행 중 입니다.")
                        Toast.makeText(this@MainActivity, "이미 실행 중 입니다.", Toast.LENGTH_SHORT).show()
                    }
                    isCalling = true
                }
            }
        }
    }

    fun checkPermission() {
        var temp = ""

        //파일 읽기 권한 확인
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            temp += Manifest.permission.READ_EXTERNAL_STORAGE + " "
        }

        //파일 쓰기 권한 확인
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            temp += Manifest.permission.WRITE_EXTERNAL_STORAGE + " "
        }
        if (TextUtils.isEmpty(temp) == false) {
            // 권한 요청
            ActivityCompat.requestPermissions(
                this,
                temp.trim { it <= ' ' }.split(" ").toTypedArray(),
                1
            )
        } else {
            // 모두 허용 상태
            Toast.makeText(this, "권한을 모두 허용", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        //권한을 허용 했을 경우
        if (requestCode == 1) {
            val length = permissions.size
            for (i in 0 until length) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    // 동의
                    Log.d("MainActivity", "권한 허용 : " + permissions[i])
                }
            }
        }
    }
}