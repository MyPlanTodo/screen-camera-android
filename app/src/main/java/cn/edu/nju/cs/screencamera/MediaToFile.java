package cn.edu.nju.cs.screencamera;

import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.widget.TextView;

import cn.edu.nju.cs.screencamera.ReedSolomon.GenericGF;
import cn.edu.nju.cs.screencamera.ReedSolomon.ReedSolomonDecoder;
import cn.edu.nju.cs.screencamera.ReedSolomon.ReedSolomonException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedList;
import java.util.List;

/**
 * 从相机,视频,图片识别二维码的基础类
 * 包含通用的UI通信显示方法,和图像处理方法等
 */
public class MediaToFile extends FileToImg {
    private static final String TAG = "MediaToFile";//log tag
    private static final boolean VERBOSE = false;//是否记录详细log
    private TextView debugView;//输出处理信息的TextView
    private TextView infoView;//输出全局信息的TextView
    protected Handler handler;//与UI进程通信
    int barCodeWidth;
    int barCodeHeight;
    ReedSolomonDecoder decoder;
    public List<BitSet> realBitSetList;
    protected CRC8 crcCheck;
    /**
     * 构造函数,获取必须的参数
     *
     * @param debugView 实例
     * @param infoView  实例
     * @param handler   实例
     */
    public MediaToFile(TextView debugView, TextView infoView, Handler handler) {
        this.debugView = debugView;
        this.infoView = infoView;
        this.handler = handler;
        barCodeWidth = (frameBlackLength + frameVaryLength+frameVaryTwoLength) * 2 + contentLength;
        barCodeHeight=2*frameBlackLength + contentLength;
        crcCheck=new CRC8();
        checkBitSet(null, null);
    }
    /**
     * 更新处理信息,即将此线程的信息输出到UI
     *
     * @param index            帧编号
     * @param lastSuccessIndex 最后识别成功的帧编号
     * @param frameAmount      二维码帧总数
     * @param count            已处理的帧个数
     */
    protected void updateDebug(int index, int lastSuccessIndex, int frameAmount, int count) {
        //final String text = "当前:" + index + "已识别:" + lastSuccessIndex + "帧总数:" + frameAmount + "已处理:" + count;
        final String text = "已处理:" + count;
        handler.post(new Runnable() {
            @Override
            public void run() {
                debugView.setText(text);
            }
        });
    }
    /**
     * 更新全局信息,即将此线程的信息输出到UI
     *
     * @param msg 信息
     */
    protected void updateInfo(String msg) {
        final String text = msg;
        handler.post(new Runnable() {
            @Override
            public void run() {
                infoView.setText(text);
            }
        });
    }
    public int getFileByteNum(Matrix matrix) throws CRCCheckException{
        BitSet head=matrix.getHead(barCodeWidth, barCodeHeight);
        System.out.println(head.toString());
        int intLength=32;
        int byteLength=8;
        int index=0;
        for(int i=0;i<intLength;i++){
            if(head.get(i)){
                index|=1<<(intLength-i-1);
            }
        }
        int crcLength=8;
        int crc=0;
        for(int i=0;i<byteLength;i++){
            if(head.get(intLength+i)){
                crc|=1<<(crcLength-i-1);
            }
        }
        crcCheck.reset();
        crcCheck.update(index);
        int truth=(int)crcCheck.getValue();
        Log.d(TAG, "CRC check: index:" + index + " CRC:" + crc + " truth:" + truth);
        if(crc!=truth||index<0){
            throw CRCCheckException.getNotFoundInstance();
        }
        return index;
    }
    public int[] getRawContent(Matrix matrix){
        final boolean check=false;
        int[] firstColorX={frameBlackLength,frameBlackLength+frameVaryLength+frameVaryTwoLength+contentLength};
        int[] secondColorX={frameBlackLength+frameVaryLength,frameBlackLength+frameVaryLength+frameVaryTwoLength+contentLength+frameVaryLength};
        BitSet content=matrix.getContent(barCodeWidth, barCodeHeight,firstColorX,secondColorX,frameBlackLength,frameBlackLength+contentLength);
        if(check){
            boolean status=checkBitSet(content,matrix);
            Log.d(TAG,"check:"+status);
        }
        int[] con=new int[bitsPerBlock*contentLength*contentLength/ecLength];
        for(int i=0;i<con.length*ecLength;i++){
            if(content.get(i)){
                con[i/ecLength]|=1<<(i%ecLength);
            }
        }
        return con;
    }
    public int[] decode(int[] raw,int ecNum) throws ReedSolomonException {
        if(decoder==null){
            decoder = new ReedSolomonDecoder(GenericGF.AZTEC_DATA_10);
            //decoder = new ReedSolomonDecoder(GenericGF.DATA_MATRIX_FIELD_256);
        }
        decoder.decode(raw, ecNum);
        return raw;
    }
    public byte[] getContent(Matrix matrix) throws ReedSolomonException{
        int[] rawContent=getRawContent(matrix);
        int[] decodedContent=decode(rawContent,ecNum);
        int realByteNum=bitsPerBlock*contentLength*contentLength/8-ecNum*ecLength/8;
        byte[] res=new byte[realByteNum];
        for(int i=0;i<res.length*8;i++){
            if((decodedContent[i/ecLength]&(1<<(i%ecLength)))>0){
                res[i/8]|=1<<(i%8);
            }
        }
        return res;
    }
    public boolean checkBitSet(BitSet con,Matrix matrix){
        if(realBitSetList==null){
            String filePath=Environment.getExternalStorageDirectory() + "/bitsets.txt";
            realBitSetList= (LinkedList<BitSet>)readCheckFile(filePath);
            if(VERBOSE){Log.d(TAG,"load check file success:"+filePath);}
            return false;
        }
        //Log.d(TAG,"check content bit size:"+con.length());
        BitSet least=null;
        int leastCount=10000;
        for(BitSet current:realBitSetList){
            BitSet clone=(BitSet)con.clone();
            //Log.d(TAG,"current check bit size:"+clone.length());
            clone.xor(current);
            if(clone.cardinality()<leastCount){
                least=clone;
                leastCount=clone.cardinality();
            }
            if(clone.cardinality()==0){
                break;
            }
        }
        if(leastCount!=0){
            Log.d(TAG, "check least count:" + leastCount);
            printContentBitSet(least);
            List<Integer> test=new ArrayList<>();
            for(int i=least.nextSetBit(0);i>=0;i=least.nextSetBit(i+1)){
                int real=i/2;
                if(!test.contains(real)) {
                    test.add(real);
                }
            }
            //matrix.check(test);
            return false;
        }
        return true;
    }
    public void printContentBitSet(BitSet content){
        int index=0;
        System.out.println("the wrong bits graph:");
        for(int y=0;y<contentLength;y++){
            for(int x=0;x<contentLength;x++){
                boolean flag=false;
                for(int i=0;i<bitsPerBlock;i++){
                    if(content.get(index+i)){
                        flag=true;
                        break;
                    }
                }
                if(flag){
                    System.out.print("x");
                }
                else{
                    System.out.print(" ");
                }
                index+=bitsPerBlock;
            }
            System.out.println();
        }


    }

    public Object readCheckFile(String filePath){
        ObjectInputStream inputStream;
        Object d=null;
        try {
            inputStream = new ObjectInputStream(new FileInputStream(filePath));
            d = inputStream.readObject();
        }catch (ClassNotFoundException ec){
            ec.printStackTrace();
        }catch (IOException e){
            e.printStackTrace();
        }



        return d;
    }
    public boolean bytesToFile(byte[] bytes,String fileName){
        if(fileName.isEmpty()){
            Log.i(TAG, "file name is empty");
            updateInfo("文件名为空！ ");
            return false;
        }
        File file = new File(Environment.getExternalStorageDirectory() + "/Download/" + fileName);
        OutputStream os;
        try {
            os = new FileOutputStream(file);
            os.write(bytes);
            os.close();
        } catch (FileNotFoundException e) {
            Log.i(TAG, "file path error, cannot create file:" + e.toString());
            return false;
        }catch (IOException e){
            Log.i(TAG, "IOException:" + e.toString());
            return false;
        }
        Log.i(TAG,"file created successfully: "+file.getAbsolutePath());
        updateInfo("文件写入成功！");
        return true;
    }
    public int[] smallBorder(int[] origBorder){
        int[] border=new int[origBorder.length];
        int horizonSub=origBorder[2]-origBorder[0];
        int verticalSub=origBorder[3]-origBorder[1];
        border[0]=origBorder[0]+horizonSub/10;
        border[1]=origBorder[1]+verticalSub/10;
        border[2]=origBorder[2]-horizonSub/10;
        border[3]=origBorder[3]-verticalSub/10;
        return border;
    }
}