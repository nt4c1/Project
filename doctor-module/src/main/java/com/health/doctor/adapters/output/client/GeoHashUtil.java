package com.health.doctor.adapters.output.client;

import ch.hsr.geohash.GeoHash;

public class GeoHashUtil {
    private GeoHashUtil() {}

    public static String generate(double lat, double lng,int precision){
        return GeoHash.geoHashStringWithCharacterPrecision(lat, lng, precision);
    }

    public static String getPrefix(String geohash,int prefixLength){
        return geohash.substring(0,prefixLength);
    }
}
