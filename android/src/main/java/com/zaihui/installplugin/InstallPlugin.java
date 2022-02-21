package com.zaihui.installplugin;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import java.io.File;
import java.io.FileNotFoundException;

/**
 * 1 获取Registrar 这个接口可以获取 context
 * 2 添加自身所需依赖
 * @property registrar Registrar
 * @constructor
 */
public class InstallPlugin implements FlutterPlugin,MethodCallHandler {
    private MethodChannel channel;
    private static int installRequestCode = 1234;
    private static File apkFile = null;
    private static String appId = null;
    Registrar registrar;

    public InstallPlugin(Registrar _registrar){
        this.registrar = _registrar;
    }

    public static void registerWith(final Registrar registrar){
        MethodChannel channel = new MethodChannel(registrar.messenger(), "install_plugin");
        final InstallPlugin installPlugin = new InstallPlugin(registrar);
        channel.setMethodCallHandler(installPlugin);
        registrar.addActivityResultListener(new PluginRegistry.ActivityResultListener() {
            @Override
            public boolean onActivityResult(int requestCode, int resultCode, Intent intent) {
                Log.d(
                        "ActivityResultListener",
                        String.format("requestCode=%s, resultCode = %s, intent = %s",requestCode,resultCode,intent.toString())
                );
                if(resultCode == Activity.RESULT_OK && requestCode == installRequestCode){
                    installPlugin.install24(registrar.context(), apkFile, appId);
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public void onMethodCall(@NonNull MethodCall methodCall, @NonNull Result result) {
        Log.d("onMethodCall",String.format("methodCall.method:%s",methodCall.method));
        switch(methodCall.method){
            case "getPlatformVersion":
                result.success("Android ${android.os.Build.VERSION.RELEASE}");
            break;
            case "installApk":{
                String filePath = methodCall.argument("filePath");
                String appId = methodCall.argument("appId");
                Log.d("android plugin", String.format("installApk %s %s",filePath,appId));
                try{
                    installApk(filePath, appId);
                    result.success("Success");
                }catch (Exception e){
                    result.error(e.getClass().getSimpleName(), e.getMessage(), null);
                }
            }break;
            default:
                result.notImplemented();
            break;
        }
    }

    private void installApk(String filePath,String currentAppId) throws FileNotFoundException {
        if (filePath == null) throw new NullPointerException("fillPath is null!");

        Activity activity = registrar.activity();
        if(activity == null)throw new NullPointerException("context is null!");

        File file = new File(filePath);
        if(!file.exists())throw new FileNotFoundException("$filePath is not exist! or check permission");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (canRequestPackageInstalls(activity))install24(activity, file, currentAppId);
            else {
                showSettingPackageInstall(activity);
                apkFile = file;
                appId = currentAppId;
            }
        } else {
            installBelow24(activity, file);
        }
    }

    private static void showSettingPackageInstall(Activity activity) { // todo to test with android 26
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d("SettingPackageInstall", ">= Build.VERSION_CODES.O");
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivityForResult(intent, installRequestCode);
        } else {
            throw new RuntimeException("VERSION.SDK_INT < O");
        }

    }

    private boolean canRequestPackageInstalls(Activity activity) {
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.O || activity.getPackageManager().canRequestPackageInstalls();
    }

    private void installBelow24(Context context,File file) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Uri uri = Uri.fromFile(file);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        context.startActivity(intent);
    }

    /**
     * android24及以上安装需要通过 ContentProvider 获取文件Uri，
     * 需在应用中的AndroidManifest.xml 文件添加 provider 标签，
     * 并新增文件路径配置文件 res/xml/provider_path.xml
     * 在android 6.0 以上如果没有动态申请文件读写权限，会导致文件读取失败，你将会收到一个异常。
     * 插件中不封装申请权限逻辑，是为了使模块功能单一，调用者可以引入独立的权限申请插件
     */
    private void install24(Context context,File file,String appId) {
        if (context == null) throw new NullPointerException("context is null!");
        if (file == null) throw new NullPointerException("file is null!");
        if (appId == null) throw new NullPointerException("appId is null!");
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Uri uri = FileProvider.getUriForFile(context, "$appId.fileProvider.install", file);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        context.startActivity(intent);
    }

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        channel = new MethodChannel(registrar.messenger(), "install_plugin");
        final InstallPlugin installPlugin = new InstallPlugin(registrar);
        channel.setMethodCallHandler(installPlugin);
        registrar.addActivityResultListener(new PluginRegistry.ActivityResultListener() {
            @Override
            public boolean onActivityResult(int requestCode, int resultCode, Intent intent) {
                Log.d(
                        "ActivityResultListener",
                        String.format("requestCode=%s, resultCode = %s, intent = %s",requestCode,resultCode,intent.toString())
                );
                if(resultCode == Activity.RESULT_OK && requestCode == installRequestCode){
                    installPlugin.install24(registrar.context(), apkFile, appId);
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        channel.setMethodCallHandler(null);
    }
}
