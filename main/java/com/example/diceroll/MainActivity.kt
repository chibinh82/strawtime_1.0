package com.example.diceroll

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.EventLog
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_main.*
import java.io.*
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.round

class MainActivity : AppCompatActivity() {
    private var historyDate = mutableListOf<String>()
    private var historyPeriod = mutableListOf<Double>()
    lateinit var predictDate: LocalDate
    private var oldPredictDate : String = ""
    private var min : Int = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        //init historyDate
        //var dataFile = File( this.filesDir,"save_file.txt")
        //dataFile.delete()
        readJSONfromFile("save_file.txt")
        predictNextTime()
        //button val
        val plusImg:ImageView = findViewById(R.id.plusImage)
        val calendarButton: ImageView = findViewById(R.id.calBut)
        val analyseButton: ImageView = findViewById(R.id.anaBut)
        val pickDateButton: ImageView = findViewById(R.id.addBut)
        val historyButton : ImageView = findViewById(R.id.strawBasketBut)
        val devButton : ImageView = findViewById(R.id.devImage)
        // view val
        val calendar : CalendarView = findViewById(R.id.calendarView)
        val analyseImage : ImageView = findViewById(R.id.smile)
        val listView = findViewById<ListView>(R.id.historyListView)
        val textViewSTT : TextView = findViewById(R.id.STT)
        val textPeriod : TextView = findViewById(R.id.textPeriod)
        val textDates : TextView = findViewById(R.id.textDates)
        val textNextPeriod : TextView = findViewById(R.id.textNextPeriod)
        val textPredictDate :TextView = findViewById(R.id.datePredict)
        val textDev : TextView = findViewById(R.id.devText)
        // init view as visisble
        selectVisible(
            listOf(
                analyseImage,
                textDates,
                textPeriod,
                textViewSTT,
                textNextPeriod,
                textPredictDate
            )
        )
        pickDateButton.setOnClickListener {
            pickDate()
            writeJSONtoFile("save_file.txt")
        }
        analyseButton.setOnClickListener {
            selectVisible(
                listOf(
                    analyseImage,
                    textDates,
                    textPeriod,
                    textViewSTT,
                    textNextPeriod,
                    textPredictDate
                )
            )
            predictNextTime()
        }
        calendarButton.setOnClickListener {
            selectVisible(listOf(calendar))
        }
        historyButton.setOnClickListener {
            updateHistory()
            selectVisible(listOf(listView, pickDateButton, plusImg))
        }
        devButton.setOnClickListener {
            selectVisible(listOf(devText))
        }
        listView.setOnItemClickListener{ parent, view, position, id ->
            if (position>=0){
                //var oDialogYN = YesNogFragment()
                //oDialogYN.
                //oDialogYN.show(supportFragmentManager, "NoticeDialogFragment")
                val builder = AlertDialog.Builder(this)
                builder.setMessage(R.string.confirm_delete)
                    .setPositiveButton(R.string.yes,
                        DialogInterface.OnClickListener { dialog, id ->
                            // yes
                            historyDate.removeAt(position)
                            updateHistory()
                            writeJSONtoFile("save_file.txt")
                        })
                    .setNegativeButton(R.string.no,
                        DialogInterface.OnClickListener { dialog, id ->
                            // User cancelled the dialog
                        })
                // Create the AlertDialog object and return it
                //builder.create()
                builder.show()
            }
        }
    }
    private fun pickDate() {
            val c = Calendar.getInstance()
            val year = c.get(Calendar.YEAR)
            val month = c.get(Calendar.MONTH)
            val day = c.get(Calendar.DAY_OF_MONTH)
            val myFormat = "yyyy.MM.dd" // mention the format you need
            val sdf = SimpleDateFormat(myFormat, Locale.UK)
            val dpd = DatePickerDialog(
                this,
                DatePickerDialog.OnDateSetListener { view, year, monthOfYear, dayOfMonth ->
                    // Display Selected date in TextView
                    c.set(Calendar.YEAR, year)
                    c.set(Calendar.MONTH, monthOfYear)
                    c.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    if (sdf.format(c.time) in historyDate) {
                        Toast.makeText(this, "dâu này trong giỏ rồi em ơi", Toast.LENGTH_SHORT)
                            .show()
                    } else {
                        if (checkPickDate(sdf.format(c.time))) {
                            Toast.makeText(
                                this, "dâu đã cho vào giỏ", Toast.LENGTH_SHORT
                            ).show()
                            historyDate.add(sdf.format(c.time))
                        } else {
                            Toast.makeText(
                                this,
                                "ngày này chưa đến em ơi, nhập lại nhé ",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    updateHistory()
                    writeJSONtoFile("save_file.txt")
                },
                year,
                month,
                day
            )
            dpd.show()
        }
    private fun updateHistory() {
        val listView = findViewById<ListView>(R.id.historyListView)
        val listItems = arrayOfNulls<String>(historyDate.size)
        historyDate.sortDescending()
        for (i in 0 until historyDate.size) {
            val recipe = historyDate[i]
            val parts = recipe.split(".")
            listItems[i] = parts[2] + "." + parts[1] + "." + parts[0]
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, listItems)
        listView.adapter = adapter
        if (historyDate.size == 0 ) {
            STT.text = "30"
        }
    }
    private fun predictNextTime() {
        updateHistory()
        if (historyDate.size > 0) {
            // create list of period
            var dayCal: Double
            var standardDeviation: Double
            var total: Double = 0.0
            var totalSquare: Double = 0.0
            historyPeriod.clear()
            for (i in 0 until (historyDate.size - 1)) {
                val bigDate = Calendar.getInstance()
                val partBigDate = historyDate[i].split(".")
                bigDate.set(Calendar.YEAR, partBigDate[0].toInt())
                bigDate.set(Calendar.MONTH, partBigDate[1].toInt())
                bigDate.set(Calendar.DATE, partBigDate[2].toInt())
                bigDate.set(Calendar.MILLISECOND, 0)
                val smallDate = Calendar.getInstance()
                val partSmallDate = historyDate[i + 1].split(".")
                smallDate.set(Calendar.YEAR, partSmallDate[0].toInt())
                smallDate.set(Calendar.MONTH, partSmallDate[1].toInt())
                smallDate.set(Calendar.DATE, partSmallDate[2].toInt())
                smallDate.set(Calendar.MILLISECOND, 0)
                dayCal = ((bigDate.timeInMillis - smallDate.timeInMillis) / 86400000).toDouble()
                historyPeriod.add(dayCal)
                total += dayCal
            }
            /// mean value:
            val mean = total / historyPeriod.size
            for (i in 0 until historyPeriod.size) {
                var variation: Double = historyPeriod[i] - mean
                totalSquare += variation * variation
            }
            standardDeviation = kotlin.math.sqrt(totalSquare / historyPeriod.size)
            val predictRange =
                0.85 * (standardDeviation / kotlin.math.sqrt(historyPeriod.size.toDouble()))
            min = round(mean - predictRange).toInt()


            // print out period
            val textViewSTT: TextView = findViewById(R.id.STT)
            textViewSTT.text = min.toString()
            // print out date predict
            val partLatestDate = historyDate[0].split(".")
            val latestDate = LocalDate.of(
                partLatestDate[0].toInt(),
                partLatestDate[1].toInt(),
                partLatestDate[2].toInt()
            )
            val today = LocalDate.now()
            predictDate = latestDate
            var i = 1
            while (today.isAfter(predictDate) or today.isEqual(predictDate)) {
                predictDate = predictDate.plusDays(min.toLong() * i)
            }
            val textDatePredict: TextView = findViewById(R.id.datePredict)
            textDatePredict.text = predictDate.format(DateTimeFormatter.ofPattern("d-M-y"))
            if (oldPredictDate != "" && oldPredictDate != predictDate.toString()) {
                scheduleAlarm()
                oldPredictDate = predictDate.toString()
            }
        } else {
            datePredict.text = "No data"
        }
    }
    private fun writeJSONtoFile(s: String) {
        //Create list to store the all Tags
        var tags = ArrayList<String>()
        // Add the Tag to List
        for (i in 0 until historyDate.size) {
            tags.add(historyDate[i])
        }

        //Create a Object of Post
        //var dataToSave = Data(predictDate.toString(), tags)
        var dataToSave = Data("oo", tags)
        //Create a Object of Gson
        var gson = Gson()
        //Convert the Json object to JsonString
        var jsonString:String = gson.toJson(dataToSave)
        val file = File(this.filesDir, "save_file.txt")
        val fileWriter = FileWriter(file)
        val bufferedWriter = BufferedWriter(fileWriter)
        bufferedWriter.write(jsonString)
        bufferedWriter.close()
    }
    private fun readJSONfromFile(f: String) {
        //Creating a new Gson object to read data
        var gson = Gson()
        //Initialize the String Builder
        var file = File(this.filesDir, "save_file.txt")
        if (Files.exists(file.toPath())) {
            if (f != null && f.trim() != "") {
                var fileInputStream: FileInputStream? = openFileInput(f)
                var inputStreamReader = InputStreamReader(fileInputStream)
                val bufferedReader = BufferedReader(inputStreamReader)
                val stringBuilder: StringBuilder = StringBuilder()
                var text: String? = null
                while ({ text = bufferedReader.readLine(); text }() != null) {
                    stringBuilder.append(text)
                }
                bufferedReader.close()
                //Displaying data on EditText
                if (stringBuilder.toString() != "") {
                    var readData = gson.fromJson(stringBuilder.toString(), Data::class.java)
                    var tempData = readData.allDates
                    var tempPredict = readData.predictDateSave
                    if (tempData != null) {
                        if (tempData.isNotEmpty()) {
                            for (element in tempData) {
                                historyDate.add(element)
                            }
                        }
                    }
                    if (tempPredict != null && oldPredictDate != null) {
                        oldPredictDate = tempPredict
                    }
                }
            }
        }
    }
    private fun selectVisible(visibleView: List<View>) {
        val pickDateButton:ImageView = findViewById(R.id.addBut)
        val plusImg:ImageView = findViewById(R.id.plusImage)
        // view val
        val calendar : CalendarView = findViewById(R.id.calendarView)
        val analyseImage : ImageView = findViewById(R.id.smile)
        val listView = findViewById<ListView>(R.id.historyListView)
        val textViewSTT : TextView = findViewById(R.id.STT)
        val textPeriod : TextView = findViewById(R.id.textPeriod)
        val textDates : TextView = findViewById(R.id.textDates)
        val textNextPeriod : TextView = findViewById(R.id.textNextPeriod)
        val textPredictDate :TextView = findViewById(R.id.datePredict)
        val textDev : TextView = findViewById(R.id.devText)
        val allViews = mutableListOf<View>(
            pickDateButton,
            plusImg,
            calendar,
            calendar,
            analyseImage,
            listView,
            textViewSTT,
            textPeriod,
            textDates,
            textPredictDate,
            textNextPeriod,
            textDev
        )
        for (view in allViews) {
            if (visibleView.indexOf(view) == -1) {
                view.visibility = View.GONE
            } else {
                view.visibility = View.VISIBLE
            }
        }
    }
    private fun checkPickDate(s1: String): Boolean {
        val partLatestDate = s1.split(".")
        val compareDate = LocalDate.of(
            partLatestDate[0].toInt(),
            partLatestDate[1].toInt(),
            partLatestDate[2].toInt()
        )
        val today = LocalDate.now()
        return today.isAfter(compareDate) or today.isEqual(compareDate)
    }
    private fun scheduleAlarm() {
        if (predictDate != null) {
            // Get AlarmManager instance
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val timeSet = Calendar.getInstance()
            val oldTimeSet = Calendar.getInstance()
            timeSet.set(Calendar.YEAR, predictDate.year)
            timeSet.set(Calendar.MONTH, predictDate.monthValue - 1)
            timeSet.set(Calendar.DATE, predictDate.dayOfMonth - 2 )
            timeSet.set(Calendar.HOUR, 8)
            timeSet.set(Calendar.MINUTE, 0)
            timeSet.set(Calendar.MILLISECOND, 0)
            // cancel old alarm
            for (i in 0 until 6 ) {
                val oldIntent = Intent(this, AlarmReceiver::class.java)
                oldIntent.action = "FOO_ACTION"
                oldIntent.putExtra("KEY_FOO_STRING", "Medium AlarmManager Demo")
                val oldPendingIntent = PendingIntent.getBroadcast(this, i, oldIntent, 0)
                // cancel system Alarm Service
                alarmManager.cancel(oldPendingIntent)
            }
            // Intent part
            for (i in 0 until 6 ) {
                val intent = Intent(this, AlarmReceiver::class.java)
                intent.action = "FOO_ACTION"
                intent.putExtra("KEY_FOO_STRING", "Medium AlarmManager Demo")
                val pendingIntent = PendingIntent.getBroadcast(this, i, intent, 0)
                //val ALARM_DELAY_IN_SECOND = 10
                //val alarmTimeAtUTC = System.currentTimeMillis() + ALARM_DELAY_IN_SECOND * 1_000L
                val alarmTimeAtUTC = timeSet.timeInMillis + i * 43200 * 1_000L
                // Set with system Alarm Service
                if (alarmTimeAtUTC > System.currentTimeMillis()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        alarmTimeAtUTC,
                        pendingIntent
                    )
                }
            }
        }
    }
  }
class Data {
    var predictDateSave: String? = null
    var allDates: List<String>? = null
    constructor(Predict: String, AllDates: List<String>) : super() {
        this.predictDateSave = Predict
        this.allDates = AllDates
    }
}
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // TODO: This method is called when the BroadcastReceiver is receiving
        // an Intent broadcast.
        Log.e("TAG", "Alarm received")
        sendNotification(context, intent.getStringExtra("test "))
    }
    companion object {
        fun sendNotification(mcontext: Context, messageBody: String?) {
            val intent = Intent(mcontext, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pendingIntent = PendingIntent.getActivity(
                mcontext, 0 /* Request code */, intent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
            val notificationManager: NotificationManager =
                mcontext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val defaultSoundUri: Uri =
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationChannel = NotificationChannel(
                    mcontext.getString(R.string.default_notification_channel_id),
                    "Rewards Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT
                )

                // Configure the notification channel.
                notificationChannel.description = "App notification channel"
                notificationChannel.enableLights(true)
                notificationChannel.lightColor = Color.GREEN
                notificationChannel.vibrationPattern = longArrayOf(0, 500, 200, 500)
                notificationChannel.enableVibration(true)
                notificationManager.createNotificationChannel(notificationChannel)
            }
            val notificationBuilder = NotificationCompat.Builder(
                mcontext,
                mcontext.getString(R.string.default_notification_channel_id)
            )
            notificationBuilder.setContentTitle("Loa Loa Loa")
            notificationBuilder.setContentText("Be ready, chuẩn bị hái dâu em nhé")
            notificationBuilder.setSmallIcon(R.drawable.pngguru_com)
            notificationBuilder.setAutoCancel(true)
            notificationBuilder.setSound(defaultSoundUri)
            notificationBuilder.setContentIntent(pendingIntent)
            notificationManager.notify(0 /* ID of notification */, notificationBuilder.build())
        }
    }
}


