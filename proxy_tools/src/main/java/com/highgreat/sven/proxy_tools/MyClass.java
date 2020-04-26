package com.highgreat.sven.proxy_tools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;

public class MyClass {

    public static void main(String[] args) throws Exception {

        File aarFile = new File("proxy_core/build/outputs/aar/proxy_core-debug.aar");
        File aarTemp = new File("proxy_tools/temp");
        Zip.unZip(aarFile,aarTemp);
        File classesJar = new File(aarTemp,"classes.jar");
        File classDex = new File(aarTemp,"classes.dex");

        //dx --dex --output out.dex in.jar      //执行cmd命令。1.windows中需要以cmd /c开头，linux/mac不需要。 2.需要把dx添加环境变量，并重启AS。
        Process process=Runtime.getRuntime().exec("cmd /c dx --dex --output "+classDex.getAbsolutePath()
                +" "+classesJar.getAbsolutePath());
        process.waitFor();
        if(process.exitValue()!=0){
            throw new RuntimeException("dex error");
        }

        /**
         * 加密APK中的所有dex文件
         */
        File apkFile=new File("app/build/outputs/apk/debug/app-debug.apk");
        File apkTemp=new File("app/build/outputs/apk/debug/temp");
        Zip.unZip(apkFile,apkTemp);
        //把dex文件拿出来进行加密
        File[] dexFiles = apkTemp.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".dex");
            }
        });
        //AES加密
        AES.init(AES.DEFAULT_PWD);
        for (File dexFile : dexFiles) {
            byte[] bytes = Utils.getBytes(dexFile);
            byte[] encrypt = AES.encrypt(bytes);
            FileOutputStream fos = new FileOutputStream(new File(apkTemp,"secret-"+dexFile.getName()));
            fos.write(encrypt);
            fos.flush();
            fos.close();
            dexFile.delete();
        }

        /**
         * 把加密后的dex放入apk解压目录，重新压缩成apk文件
         */
        classDex.renameTo(new File(apkTemp,"classes.dex"));
        File unSignedApk = new File("app/build/outputs/apk/debug/app-unsigned.apk");
        Zip.zip(apkTemp,unSignedApk);

        /**
         * 对齐和签名 如果用runtime执行命令需要一直等待，可以使用cmd命令行执行
         */
        File alignedApk = new File("app/build/outputs/apk/debug/app-unsigned-aligned.apk");
        process =Runtime.getRuntime().exec("cmd /c zipalign -v -p 4 "+unSignedApk.getAbsolutePath()
                +" "+alignedApk.getAbsolutePath());
        process.waitFor();
        System.out.println(unSignedApk.getAbsolutePath()+" "+alignedApk.getAbsolutePath());
        if(process.exitValue()!=0){
            throw new RuntimeException("zipalign error");
        }
        File signedApk=new File("app/build/outputs/apk/debug/app-signed-aligned.apk");
        File jks=new File("proxy_tools/reinforceapk.jks");
        process=Runtime.getRuntime().exec("cmd /c apksigner sign --ks "+jks.getAbsolutePath()
                +" --ks-key-alias key0 --ks-pass pass:123456 --key-pass pass:123456 --out "
                +signedApk.getAbsolutePath()+" "+alignedApk.getAbsolutePath());
        process.waitFor();
        if(process.exitValue()!=0){
            throw new RuntimeException("apksigner error");
        }
        System.out.println(jks.getAbsolutePath()+" "+signedApk.getAbsolutePath()+" "+alignedApk.getAbsolutePath());
        System.out.println("SUCCESS");
    }

}
