package edu.temple.mapchatv2;

import android.support.annotation.NonNull;

import com.google.android.gms.maps.model.LatLng;

import java.io.Serializable;

public class Partner  implements Comparable, Serializable {

    String user,lat,lng;
    LatLng latLng;


    public Partner(String u, String lt, String lg)
    {
        user=u;
        lat=lt;
        lng=lg;
    }

    public String getUser()
    {
        return user;
    }

    public String getLat()
    {
        return lat;
    }

    public String getLng()
    {
        return lng;
    }

    public double latNum(){
        double latN=Double.parseDouble(lat);
        return latN;
    }

    public double lngNum(){
        double lngN=Double.parseDouble(lng);
        return lngN;
    }

    public LatLng getLatLng(){
        double lat2=Double.parseDouble(lat);
        double lng2=Double.parseDouble(lng);
        latLng=new LatLng(lat2,lng2);
        return latLng;
    }

    public String getLatLngString(){
        String lat2 = lat.substring(0, Math.min(lat.length(), 5));
        String lng2 = lng.substring(0, Math.min(lng.length(), 5));
        String returnString=lat2+","+lng2;
        return returnString;
    }

    @Override
    public int compareTo(@NonNull Object o) {
        Partner p=(Partner) o;
        int distance;
        double x1,x2,y1,y2,d;

        x1=this.latNum();
        y1=this.lngNum();

        x2=p.latNum();
        y2=p.lngNum();

        d=Math.sqrt((x2-x1)*(x2-x1)+(y2-y1)*(y2-y1));
        distance=(int) d;



        return distance;
    }

}