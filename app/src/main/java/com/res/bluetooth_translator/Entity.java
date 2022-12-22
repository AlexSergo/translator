package com.res.bluetooth_translator;

import java.io.Serializable;

public class Entity implements Serializable {
    public double lat;
    public double lon;
    public double asim;
    public double alt;
    public double cam_deflect = 0;
    public double cam_angle = 0;
    public boolean calc_target = false;

    public Entity(double lat, double lon, double alt, double asim, double camera_deflection, double camera_angle, boolean calc_target){
        this.lat = lat;
        this.lon = lon;
        this.asim = asim;
        this.alt = alt;
        this.cam_deflect = camera_deflection;
        this.cam_angle = camera_angle;
        this.calc_target = calc_target;
    }


    public Entity(double lat, double lon, double alt, double asim){
        this.lat = lat;
        this.lon = lon;
        this.asim = asim;
        this.alt = alt;
    }
}
