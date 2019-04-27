package edu.temple.mapchatv2;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, AdapterView.OnItemSelectedListener, NfcAdapter.CreateNdefMessageCallback {

    //Map bits
    LocationManager lm;
    LocationListener ll;
    LatLng latLng;
    String lat;
    String lng;
    GoogleMap gMap;
    Location mLocation;
    HashMap<String, Marker> mMarkers = new HashMap<>();

    //Key service bits
    KeyService keyService;
    boolean kBound=false;

    //UI Bits
    EditText userBox;
    String userName;
    Button setUser;
    ListView partnerList;
    MapView mapView;
    ArrayList<String> partnerNames;
    ArrayAdapter<String> partnerAdapter;

    //NFC bits
    NfcAdapter nfcAdapter;
    private PendingIntent pendingIntent;

    //Extra bits
    public static final String PARTNER_EXTRA="PARTNER_EXTRA";
    public static final String USER_EXTRA="USER_EXTRA";
    public static final String CHANNEL_ID = "CHANNEL_ID";
    String toeken;
    ArrayList<Partner> partners;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //link to UI
        setUser=findViewById(R.id.setUserBtn);
        userBox=findViewById(R.id.userNameBox);

        //everything for partners
        partnerList=findViewById(R.id.partnerListField);
        partners=new ArrayList<>();
        partnerNames=new ArrayList<>();
        partnerAdapter=new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, partnerNames);
        partnerList.setAdapter(partnerAdapter);
        partnerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                onItemSelected(parent, view, position, id);
            }
        });

        //Map stuff
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        SupportMapFragment mapFragment = new SupportMapFragment();
        transaction.replace(R.id.mapView, mapFragment).commit();
        mapFragment.getMapAsync(MainActivity.this);
        lm = getSystemService(LocationManager.class);
        ll=new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                lat=String.valueOf(location.getLatitude());
                lng=String.valueOf(location.getLongitude());
                mLocation=location;
                latLng = new LatLng(location.getLatitude(), location.getLongitude());
                //unfortunately this constantly moves the camera to your current location if you try to scroll away. I just wanted the scroll to happen on startup, but couldn't get that to work.
                animateCamera(latLng);
            }
            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {

            }
            @Override
            public void onProviderEnabled(String provider) {

            }
            @Override
            public void onProviderDisabled(String provider) {

            }
        };

        //sets user's name and upload's info to both destinations
        setUser.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                userName=userBox.getText().toString();
                keyService.genKeyPair();
                uploadUserLoc(userName);
                postUserFCM();
            }
        });

        //Beam stuff
        Intent nfcIntent = new Intent(this, MainActivity.class);
        pendingIntent = PendingIntent.getActivity(this, 0, nfcIntent, 0);
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        nfcAdapter.setNdefPushMessageCallback(this, this);

        //update map and partner list every 30 seconds
        final Handler handler = new Handler();
        final int delay = 30000; //milliseconds
        handler.postDelayed(new Runnable(){
            public void run(){
                getPartners();
                handler.postDelayed(this, delay);
            }
        }, delay);

        //firebase info
        FirebaseInstanceId.getInstance().getInstanceId()
                .addOnCompleteListener(new OnCompleteListener<InstanceIdResult>() {
                    @Override
                    public void onComplete(@NonNull Task<InstanceIdResult> task) {
                        if (!task.isSuccessful()) {
                            Log.w("tokentrack", "getInstanceId failed", task.getException());
                            return;
                        }
                        // Get new Instance ID token
                        String token = task.getResult().getToken();
                        // Log and toast
                        Log.d("tokentrack", token);
                        toeken = token;
                    }
                });
    }

    //move camerea to current loction
    private void animateCamera(LatLng latLng) {
        CameraUpdate cameraUpdate=CameraUpdateFactory.newLatLngZoom(latLng, 16);
        gMap.animateCamera(cameraUpdate);
    }

    //populate the partner list to show in listview
    public void getPartners(){
        partners=new ArrayList<>();
        final String url = "https://kamorris.com/lab/get_locations.php";
        RequestQueue queue2 = Volley.newRequestQueue(this);

        JsonArrayRequest jsonArrayRequest = new JsonArrayRequest(Request.Method.GET, url,null, new Response.Listener<JSONArray>() {
            @Override
            public void onResponse(JSONArray response) {

                try{

                    for(int i=0;i<response.length();i++){
                        JSONObject partner = response.getJSONObject(i);
                        String userName2 = partner.getString("username");
                        String lat = partner.getString("latitude");
                        String lng = partner.getString("longitude");
                        Partner p=new Partner(userName2,lat,lng);
                        partners.add(p);
                    }
                }catch (JSONException e){
                    e.printStackTrace();
                }
                updatePartners();
                updateMarkers();
            }
        },
                new Response.ErrorListener(){
                    @Override
                    public void onErrorResponse(VolleyError error){
                        Toast.makeText(getApplication(),"Failure", Toast.LENGTH_LONG).show();
                    }
                }

        );
        queue2.add(jsonArrayRequest);
    }

    //update partnerlist on main screen
    public void updatePartners(){
        partnerNames.clear();
        for(int i=0;i<partners.size();i++)
            partnerNames.add(partners.get(i).getUser());
        partnerAdapter.notifyDataSetChanged();
    }

    //update all the markers on the main screen
    public void updateMarkers(){
        String name;
        for(int i=0;i<partners.size();i++){
            name=partners.get(i).getUser();
            Marker m=mMarkers.get(name);
            if(m==null){
                m = gMap.addMarker(new MarkerOptions().title(name).position(partners.get(i).getLatLng()));mMarkers.put(name, m);
            }
            else{
                m.remove();
                m = gMap.addMarker(new MarkerOptions().title(name).position(partners.get(i).getLatLng()));mMarkers.put(name, m);
            }
        }
    }

    //upload User location and name
    public void uploadUserLoc(String user2){

        String url="https://kamorris.com/lab/register_location.php";
        final String u2=user2;
        RequestQueue queue = Volley.newRequestQueue(getApplicationContext());
        StringRequest postRequest = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>()
                {
                    @Override
                    public void onResponse(String response) {
                        // response
                        Log.d("Response", response);
                    }
                },
                new Response.ErrorListener()
                {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        // error
                        //Log.d("Error.Response", error);
                    }
                }
        ) {
            @Override
            protected Map<String, String> getParams()
            {
                Map<String, String>  params = new HashMap<String, String>();
                params.put("user", u2);
                params.put("latitude", lat);
                params.put("longitude", lng);

                return params;
            }
        };
        queue.add(postRequest);
        getPartners();
    }

    @Override
    public void onMapReady(GoogleMap map) {
        gMap=map;
        map.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        getPartners();
    }


    public NdefMessage createNdefMessage(NfcEvent event) {
        String payload;
        payload = sendKey();

        NdefRecord record = NdefRecord.createTextRecord(null, payload);
        NdefMessage msg = new NdefMessage(new NdefRecord[]{record});

        return msg;
    }

    //converts public key to proper format for BEAM payload
    private String sendKey(){
        String pubKey = keyService.getPublicKey();
        return "{\"user\":\""+ userName +"\",\"key\":\""+ pubKey +"\"}";
    }

    //process incoming intent for partner's key
    void processIntent(Intent intent) {
        String payload = new String(
                ((NdefMessage) intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)[0]).getRecords()[0].getPayload());

        String jsonString = payload.substring(3);
        try {
            JSONObject json = new JSONObject(jsonString);
            newPartner(json);

        } catch (JSONException e) {}

    }

    //takes payload info and adds a new partner and key
    private void newPartner(JSONObject json) {
        String partner = "Flying Spaghetti Monster";

        try {
            String owner = json.getString("user");
            String pemKey = json.getString("key");
            partner = owner;
            //Toast.makeText(this,pemKey,Toast.LENGTH_LONG).show();

            if(kBound)
                keyService.storePartnerKey(owner, pemKey);
        } catch (Exception e) {

        }
    }


    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, KeyService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(mConnection);
        kBound= false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) {
            processIntent(getIntent());
        }
        nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);

        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) {
            registerForLocationUpdates();
        }else{
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},1);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        nfcAdapter.disableForegroundDispatch(this);
        lm.removeUpdates(ll);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
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

    //checks location permissions still exist
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(grantResults[0]==PackageManager.PERMISSION_GRANTED) {
            registerForLocationUpdates();
        }else{
            Toast.makeText(this,"Cannot do the thing", Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("MissingPermission")
    private void registerForLocationUpdates(){
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0,0,ll);
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {

    }

    //acts when the user selects a name in the partner list, warns if no key has been exchanged
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

        Intent intent=new Intent(parent.getContext(), ChatActivity.class);
        String partnerName=parent.getItemAtPosition(position).toString();
        //Toast.makeText(this,"here", Toast.LENGTH_LONG).show();

        try {
            //No key exchange warning
            if(keyService.getPartnerPublicKey(partnerName)==null){
                Toast.makeText(this,"You need that person's key first!", Toast.LENGTH_LONG).show();

            }
            else{
                //goes to chatting with partner
                intent.putExtra(PARTNER_EXTRA, partnerName);
                intent.putExtra(USER_EXTRA, userName);
                startActivity(intent);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {}

    //post user's info to FCM server
    private void postUserFCM(){
        if(userName == null || toeken == null)
            return;

        StringRequest stringRequest = new StringRequest(Request.Method.POST, "https://kamorris.com/lab/fcm_register.php", new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                Log.d("regtrack", "Volley Response: " + response);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                error.printStackTrace(); //log the error resulting from the request for diagnosis/debugging
                Log.d("regtrack", "Volley Error: " + error);
            }
        }){

            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                Map<String, String> postMap = new HashMap<>();
                postMap.put("user", userName);
                postMap.put("token", ""+ toeken);
                return postMap;
            }
        };
        Volley.newRequestQueue(this).add(stringRequest);
        Log.d("regtrack", "added the request to the queue");
    }
}
