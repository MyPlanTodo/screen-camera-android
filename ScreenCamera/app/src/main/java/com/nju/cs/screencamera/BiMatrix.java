package com.nju.cs.screencamera;

/**
 * Created by zhantong on 15/11/21.
 */
public final class BiMatrix {
    private final int width;
    private final int height;
    private final byte[] pixels;
    private int threshold =0;

    public BiMatrix(int dimension){
        this(dimension,dimension);
    }
    public BiMatrix(int width,int height){
        this.width=width;
        this.height=height;
        this.pixels=new byte[width*height];
    }
    public BiMatrix(byte[] pixels,int width,int height){
        this.pixels=pixels;
        this.width=width;
        this.height=height;
    }
    public int get(int x,int y){
        int offset=y*width+x;
        int gray = pixels[offset]&0xff;
        if(gray<= threshold){
            return 0;
        }
        if(gray> threshold){
            return 1;
        }
        return 0;
    }
    public void setThreshold(int threshold){
        this.threshold = threshold;
    }
    public int getThreshold(){
        return threshold;
    }
    public int get(int location){
        return pixels[location];
    }
    /*
    public void set(int x,int y,int pixel){
        int offset=y*width+x;
        pixels[offset]=pixel;
    }
    public void set(int location,int pixel){
        pixels[location]=pixel;
    }
    */
    public boolean pixelEquals(int x,int y,int pixel){
        int res=get(x,y);
        return res==pixel;
    }
    public boolean pixelEqualsBack(int x,int y,int pixel){
        int offset=y*width+x;
        return pixels[offset]==(byte)pixel;
    }
    public int width(){
        return width;
    }
    public int height(){
        return height;
    }
}
