package edu.temple.mapchatv2;


import android.app.ActivityManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONObject;

import java.util.List;

import static android.content.ContentValues.TAG;
import static edu.temple.mapchatv2.MainActivity.CHANNEL_ID;
import static edu.temple.mapchatv2.MainActivity.PARTNER_EXTRA;
import static edu.temple.mapchatv2.MainActivity.USER_EXTRA;

public class MessageService extends FirebaseMessagingService {

    private LocalBroadcastManager localBroadcastManager;

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        Log.d(TAG, "Refreshed token: " + token);

    }

    //gets the FCM payload
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        localBroadcastManager = LocalBroadcastManager.getInstance(this);

        try {
            JSONObject jsonString = new JSONObject(remoteMessage.getData().get("payload"));
            //deals with the payload
            parseJSON(jsonString);
        } catch (Exception e) {

        }
    }

    //deals with fcm payload
    public void parseJSON(JSONObject json) {
        try {
            String to = json.getString("to");
            String partner = json.getString("from");
            String content = json.getString("message");

            //if app is in the foreground, send the data right to the app
            if(isAppOnForeground(this, "edu.temple.mapchatv2")){
                Intent intent = new Intent("new_message");
                intent.putExtra("to", to);
                intent.putExtra("partner", partner);
                intent.putExtra("message", content);
                localBroadcastManager.sendBroadcast(intent);
            }
            //display notif if app is not in foreground and send data to app
            else{
                Intent newIntent = new Intent(this, ChatActivity.class);

                newIntent.putExtra(USER_EXTRA, to);
                newIntent.putExtra(PARTNER_EXTRA, partner);
                newIntent.putExtra("content", content);
                PendingIntent pi = PendingIntent.getActivity(this,111, newIntent, PendingIntent.FLAG_UPDATE_CURRENT);

                //Build a notification
                NotificationCompat.Builder builder =
                        new NotificationCompat.Builder(this, CHANNEL_ID)
                                .setSmallIcon(R.mipmap.ic_launcher_round)
                                .setContentTitle("You have a new message.")
                                .setContentText(partner + " sent you a message.")
                                .setContentIntent(pi)
                                .setAutoCancel(true)
                                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
                NotificationManagerCompat notificationManager =
                        NotificationManagerCompat.from(this);
                // notificationId is a unique int for each notification that you must define
                notificationManager.notify(1010, builder.build());
            }

        } catch (Exception e) {

        }
    }

    //checks if app is in the foreground
    private boolean isAppOnForeground(Context context, String appPackageName) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> appProcesses = activityManager.getRunningAppProcesses();
        if (appProcesses == null) {
            return false;
        }
        for (ActivityManager.RunningAppProcessInfo appProcess : appProcesses) {
            if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                    && appProcess.processName.equals(appPackageName)) {
                //                Log.e("app",appPackageName);
                return true;
            }
        }
        return false;
    }
}
