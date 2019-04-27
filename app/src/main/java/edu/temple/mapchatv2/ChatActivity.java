package edu.temple.mapchatv2;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Cipher;

public class ChatActivity extends AppCompatActivity {

    //UI bits
    ListView messageView;
    EditText msgToSend;
    Button sendBtn;
    TextView partnerBox;

    //Activity Info
    String userName, partnerName;
    ArrayList<String>messages;
    ArrayAdapter<String>messageAdapter;
    String encryptedMessage;

    //Key bits
    RSAPublicKey partnerPublicKey;
    RSAPrivateKey myPrivateKey;
    KeyService keyService;
    boolean kBound=false;

    //gets FCM payload and acts accordingly, decrypts before showing
    private BroadcastReceiver messageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String to = intent.getStringExtra("to");
            String sender = intent.getStringExtra("partner");
            String content = intent.getStringExtra("message");

            if(sender.equals(partnerName)) {
                //gotta decrypt incoming message before showing it
                String newMsg=decryptMessage(content);
                messages.add(partnerName+": " + newMsg);
                messageAdapter.notifyDataSetChanged();
                messageView.smoothScrollToPosition(messages.size() - 1);
            }

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        //UI linking
        partnerBox=findViewById(R.id.partnerNameBox);
        messageView=findViewById(R.id.chatBox);
        sendBtn=findViewById(R.id.sendMsg);
        msgToSend=findViewById(R.id.messageBox);

        //get partner's info, and your own
        Intent intent=getIntent();
        userName=intent.getStringExtra(MainActivity.USER_EXTRA);
        partnerName=intent.getStringExtra(MainActivity.PARTNER_EXTRA);

        //setup UI
        partnerBox.setText(partnerName);
        messages=new ArrayList<>();
        messageAdapter=new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, messages);
        messageView.setAdapter(messageAdapter);

        //send a message!
        sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //encrypt message before it's sent
                encryptedMessage=encryptMessage(msgToSend.getText().toString());
                sendMessage();
            }
        });
    }

    //sends message, displays what you typed on your screen and sends the encrypted version
    private void sendMessage() {
        //display your message to you
        String message = msgToSend.getText().toString();
        msgToSend.getText().clear();
        messages.add("me: " + message);
        messageAdapter.notifyDataSetChanged();
        messageView.smoothScrollToPosition(messages.size() - 1);

        if(userName == null || partnerName.equals("")|| message == null){
            return;
        }
        StringRequest stringRequest = new StringRequest(Request.Method.POST, "https://kamorris.com/lab/send_message.php", new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {

            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                error.printStackTrace();
            }
        }){
            @Override
            protected Map<String, String> getParams(){
                Map<String, String> postMap = new HashMap<>();
                postMap.put("user", userName);
                postMap.put("partneruser", "" + partnerName);
                //message encrypted already
                postMap.put("message", ""+ encryptedMessage);
                return postMap;
            }
        };
        Volley.newRequestQueue(this).add(stringRequest);
        Log.d("sendtrack", "added the request to the queue");
    }

    //encrypts your message
    public String encryptMessage(String msg){

        String encryptMsg;

        try {
            partnerPublicKey = keyService.getPartnerPublicKey(partnerName);
            String msgToSend = msg;
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.ENCRYPT_MODE, partnerPublicKey);
            byte[] encryptedBytes = cipher.doFinal(msgToSend.getBytes());
            encryptMsg= Base64.encodeToString(encryptedBytes, Base64.DEFAULT);

        }catch (Exception e){
            e.printStackTrace();
            return null;
        }

        return encryptMsg;
    }

    //decrypts incoming messages from FCM
    private String decryptMessage(String message){

        myPrivateKey=keyService.getPrivateKey();
        byte[] encrypted = Base64.decode(message, Base64.DEFAULT);

        try {
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.DECRYPT_MODE, myPrivateKey);
            return new String(cipher.doFinal(encrypted));

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

    }

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            KeyService.LocalBinder binder = (KeyService.LocalBinder) service;
            keyService = binder.getService();
            kBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            kBound = false;
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver,
                new IntentFilter("new_message"));

        Intent intent = new Intent(this, KeyService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        Log.e(" keytrack", "we tried to bind");
    }

    @Override
    protected void onStop() {
        super.onStop();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(messageReceiver);

        unbindService(mConnection);
        kBound = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //String json = logToJson();
        //mPrefs.edit().putString(mSavedChatTag, json).apply();
    }
}
