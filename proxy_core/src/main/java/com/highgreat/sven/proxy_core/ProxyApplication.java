package com.highgreat.sven.proxy_core;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class ProxyApplication extends Application {

    private String app_name;
    private String app_version;

    /**
     * ActivityThread创建Application之后调用的第一个方法
     * 可以在这个代理APPlication中进行解密dex，
     * 然后再把解密后的dex交给原来的APPlication去加载
     * @param base
     */
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        //获取用户填入的metadata
        getMetaData();
        //得到当前加密了的APK文件
        File apkFile = new File(getApplicationInfo().sourceDir);
        //把apk解压   app_name+"_"+app_version目录中的内容需要boot权限才能用
        File versionDir = getDir(app_name + "_" + app_version, MODE_PRIVATE);
        File appDir = new File(versionDir, "app");
        File dexDir = new File(appDir, "dexDir");

        //得到我们需要加载的Dex文件
        List<File> dexFiles = new ArrayList<>();
        //进行解密（最好做MD5文件校验）
        if (!dexDir.exists() || dexDir.list().length == 0) {
            //把apk解压到appDir
            Zip.unZip(apkFile, appDir);
            //获取目录下所有的文件
            File[] files = appDir.listFiles();
            for (File file : files) {
                String name = file.getName();
                if (name.endsWith(".dex") && !TextUtils.equals(name, "classes.dex")) {
                    try {
                        AES.init(AES.DEFAULT_PWD);
                        //读取文件内容
                        byte[] bytes = Utils.getBytes(file);
                        //解密
                        byte[] decrypt = AES.decrypt(bytes);
                        //写到指定的目录
                        FileOutputStream fos = new FileOutputStream(file);
                        fos.write(decrypt);
                        fos.flush();
                        fos.close();
                        dexFiles.add(file);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        } else {
            for (File file : dexDir.listFiles()) {
                dexFiles.add(file);
            }
        }

        //
        try {
            //把解密后的文件加载到系统
            loadDex(dexFiles, versionDir);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadDex(List<File> dexFiles, File versionDir) throws Exception {
        //getClassLoader()  获取的是PathClassLoader对象
        //pathListField 是指PathClassLoader的父类BaseDexClassLoader的pathList字段
        Field pathListField = Utils.findField(getClassLoader(), "pathList");
        //DexPathList类对象
        Object pathList = pathListField.get(getClassLoader());
        //获取到DexPathList类的dexElements字段
        Field dexElementsField = Utils.findField(pathList, "dexElements");
        Object[] dexElements = (Object[]) dexElementsField.get(pathList);
        //反射得到初始化dexElements的方法
        Method makeDexElements = Utils.findMethod(pathList, "makePathElements", List.class, File.class, List.class);

        ArrayList<IOException> suppressedExceptions = new ArrayList<IOException>();
        Object[] addElements = (Object[]) makeDexElements.invoke(pathList, dexFiles, versionDir, suppressedExceptions);

        //合并数组
        Object[] newElements = (Object[]) Array.newInstance(dexElements.getClass().getComponentType(), dexElements.length + addElements.length);
        System.arraycopy(dexElements, 0, newElements, 0, dexElements.length);
        System.arraycopy(addElements, 0, newElements, dexElements.length, addElements.length);

        //替换classloader中的element数组
        dexElementsField.set(pathList, newElements);
    }

    private void getMetaData() {
        try {
            ApplicationInfo applicationInfo = getPackageManager().getApplicationInfo(
                    getPackageName(), PackageManager.GET_META_DATA);
            Bundle metaData = applicationInfo.metaData;
            if (null != metaData) {
                if (metaData.containsKey("app_name")) {
                    app_name = metaData.getString("app_name");
                }
                if (metaData.containsKey("app_version")) {
                    app_version = metaData.getString("app_version");
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * 开始替换APPlication，加载真正应用的Application
     */
    @Override
    public void onCreate() {
        super.onCreate();
        try {
            bindRealApplicatin();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getPackageName() {
        if (!TextUtils.isEmpty(app_name)) {
            return "";
        }
        return super.getPackageName();
    }

    /**
     * 这个方法主要作用是创建其他程序的Context，
     * 通过这个Context可以访问该软件包的资源，甚至可以执行其他软件包的代码。
     * 这个代码不写的话，主项目中ContentProvider使用的context就没办法换回来，还是用的ProxyApplication
     * @param packageName
     * @param flags
     * @return
     * @throws PackageManager.NameNotFoundException
     */
    @Override
    public Context createPackageContext(String packageName, int flags) throws PackageManager.NameNotFoundException {
        if (TextUtils.isEmpty(app_name)) {
            return super.createPackageContext(packageName, flags);
        }
        try {
            bindRealApplicatin();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return application;
    }

    boolean isBindReal;
    private Application application;

    private void bindRealApplicatin() throws Exception {
        if (isBindReal) {
            return;
        }
        if (TextUtils.isEmpty(app_name)) {
            return;
        }

        //创建用户真实的application（MyApplication）
        Class<?> delegateClass = Class.forName(app_name);
        application = (Application) delegateClass.newInstance();
        //调用Application.attach(context)方法
        Method declaredMethod = Application.class.getDeclaredMethod("attach", Context.class);
        //设置可用
        declaredMethod.setAccessible(true);
        //得到Application.attach(Context context)传入的context对象
        Context baseContext = getBaseContext();
        declaredMethod.invoke(application, baseContext);

        //第一处： String appClass = mApplicationInfo.className;
        Class<?> loadedApkClass = Class.forName("android.app.LoadedApk");
        Field mApplicationInfoField = loadedApkClass.getDeclaredField("mApplicationInfo");
        mApplicationInfoField.setAccessible(true);
        //LoadedApk实体类对象从通过反射ContextImpl.mPackageInfo得到
        Class<?> contextImplClass = Class.forName("android.app.ContextImpl");
        Field mPackageInfoField = contextImplClass.getDeclaredField("mPackageInfo");
        mPackageInfoField.setAccessible(true);
        Object mPackageInfo = mPackageInfoField.get(baseContext);
        ApplicationInfo mApplicationInfo = (ApplicationInfo) mApplicationInfoField.get(mPackageInfo);
        mApplicationInfo.className = app_name;

        //第二处：appContext.setOuterContext(app);
        Field mOuterContext = contextImplClass.getDeclaredField("mOuterContext");
        mOuterContext.setAccessible(true);
        mOuterContext.set(baseContext, application);

        //第三处：mActivityThread.mAllApplications.add(app);
        // ActivityThread类对象通过反射ContextImpl.mMainThread得到
        Field mMainThreadField = contextImplClass.getDeclaredField("mMainThread");
        mMainThreadField.setAccessible(true);
        //得到的是ActivityThread类对象
        Object mMainThread = mMainThreadField.get(baseContext);
        Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
        Field mAllApplicationsField = activityThreadClass.getDeclaredField("mAllApplications");
        mAllApplicationsField.setAccessible(true);
        ArrayList<Application> mAllApplications = (ArrayList<Application>) mAllApplicationsField.get(mMainThread);
//        mAllApplications.remove(this);
        mAllApplications.add(application);

        //第四处：mApplication = app;
        Field mApplicationField = loadedApkClass.getDeclaredField("mApplication");
        mApplicationField.setAccessible(true);
        mApplicationField.set(mPackageInfo, application);

        //第五处：mInitialApplication = app;
        Field mInitialApplicationField = activityThreadClass.getDeclaredField("mInitialApplication");
        mInitialApplicationField.setAccessible(true);
        mInitialApplicationField.set(mMainThread, application);

        //调用Application的oncreat方法
        application.onCreate();
        isBindReal = true;
    }
}
