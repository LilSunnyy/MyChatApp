package com.example.chatapp.Activities;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.chatapp.R;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class MyFirebaseMessagingService extends FirebaseMessagingService {
    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);
        String title = message.getData().get("Title");
        String messagex = message.getData().get("Message");

        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        if (!UserActive.isActive || (UserActive.isActive && (!title.equals(UserActive.chattingWith)))) {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), "0")
                    .setSmallIcon(R.drawable.ic_baseline_notifications_24)
                    .setContentTitle(title)
                    .setContentText(messagex)
                    .setContentIntent(pendingIntent)
                    .setPriority(Notification.PRIORITY_HIGH);
            // NotificationManager nm= (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationManagerCompat nm = NotificationManagerCompat.from(getApplicationContext());
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            nm.notify(0, builder.build());
        }
    }

    @Override
    public void onDeletedMessages() {
        super.onDeletedMessages();
    }

    @Override
    public void onMessageSent(@NonNull String msgId) {
        super.onMessageSent(msgId);
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        MainActivity.tkn=token;
//        FirebaseDatabase.getInstance().getReference().child("Token").child(FirebaseAuth.getInstance().getCurrentUser().getUid()).setValue(token);
    }
}
