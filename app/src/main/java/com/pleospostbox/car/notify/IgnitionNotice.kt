package com.pleospostbox.car.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.pleospostbox.car.MainActivity
import com.pleospostbox.car.R
import com.pleospostbox.car.data.DummyLetterRepository
import com.pleospostbox.car.data.LetterRepository
import com.pleospostbox.car.data.SharedPrefsStore
import com.pleospostbox.delivery.ignitionTime
import com.pleospostbox.delivery.lettersToReveal
import com.pleospostbox.letter.Letter

/**
 * 시동을 켜면 그동안 온 편지가 몇 통인지 알림(헤드업)으로 알린다.
 * 차량 시스템 UI는 메시지 형식(MessagingStyle + 읽음 표시 버튼) 알림만 화면 위에 띄워 주므로 그 형식을 따른다.
 */
object IgnitionNotice {
    private const val CHANNEL = "new_letters"
    private const val ID = 1

    /** 이번 시동 때 도착하는 편지. 앱 화면이 처음 열릴 때 보여줄 편지와 같다. */
    fun arrivals(repo: LetterRepository, ignitionAt: Long): List<Letter> = lettersToReveal(repo.letters(), ignitionAt)

    fun repository(context: Context): LetterRepository {
        val app = context.applicationContext
        val store = SharedPrefsStore(app.getSharedPreferences("postbox", Context.MODE_PRIVATE))
        return DummyLetterRepository({ app.assets.open("letters.json").bufferedReader().use { it.readText() } }, store)
    }

    /** 지금 시동 기준으로 새 편지가 있으면 알림을 띄운다. 없으면 아무것도 하지 않는다. */
    fun postForCurrentIgnition(context: Context) {
        val at = ignitionTime(System.currentTimeMillis(), SystemClock.elapsedRealtime())
        post(context, arrivals(repository(context), at))
    }

    fun post(context: Context, letters: List<Letter>) {
        if (letters.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.notice_channel), NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val dismiss = PendingIntent.getBroadcast(
            context, 1, Intent(context, DismissReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // 보낸 사람 이름(겹치지 않게, 온 순서대로)이 알림 제목 자리에 나온다. 예: "엄마, 지우"
        val senders = letters.map { it.senderName }.distinct().joinToString(", ")
        val text = context.getString(R.string.notice_title, letters.size)
        val me = Person.Builder().setName(context.getString(R.string.brand)).build()
        val style = NotificationCompat.MessagingStyle(me)
            .addMessage(text, letters.last().createdAt, Person.Builder().setName(senders).build())
        val markRead = NotificationCompat.Action.Builder(0, context.getString(R.string.notice_later), dismiss)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setStyle(style)
            .setContentTitle(senders)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .addInvisibleAction(markRead)
            .setAutoCancel(true)
            .build()
        nm.notify(ID, n)
    }

    /** 앱을 열면 편지를 바로 보여주므로 알림은 지운다. */
    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID)
    }

    /** 알림의 '나중에'(읽음 표시): 알림만 지운다. 편지는 앱에서 화면에 띄워야 읽은 것이 된다. */
    class DismissReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = cancel(context)
    }
}
