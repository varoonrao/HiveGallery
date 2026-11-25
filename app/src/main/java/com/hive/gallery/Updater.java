package com.hive.gallery;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.*;

/** Updates are downloaded only after approval and verified before the system installer opens. */
final class Updater {
    private final Activity activity;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile boolean busy=false,closed=false;
    private File pending;
    private static final int MAX_JSON=64*1024;
    private static final long MAX_APK=200L*1024*1024;
    Updater(Activity a){activity=a;}
    private void ui(Runnable r){activity.runOnUiThread(()->{if(!closed&&!activity.isFinishing()&&!activity.isDestroyed())r.run();});}
    private String base(){return "https://github.com/"+BuildConfig.UPDATE_REPO+"/releases/latest/download/";}
    void check(boolean interactive){
        if(BuildConfig.UPDATE_REPO.isEmpty()){if(interactive)message("Updates unavailable","Build with -PhiveRepo=OWNER/REPO or publish via the provided GitHub workflow.");return;}
        if(busy||closed)return;busy=true;
        if(interactive)android.widget.Toast.makeText(activity,"Checking for updates…",android.widget.Toast.LENGTH_SHORT).show();
        worker.execute(()->{try{
            JSONObject j=new JSONObject(new String(readLimited(base()+"update.json",MAX_JSON),java.nio.charset.StandardCharsets.UTF_8));
            long code=j.getLong("versionCode");String version=j.getString("versionName"),sha=j.getString("sha256").toLowerCase(Locale.US),tag=j.getString("tag"),pkg=j.getString("packageName");long size=j.getLong("size");
            if(!pkg.equals(activity.getPackageName())||!sha.matches("[0-9a-f]{64}")||!tag.matches("v[0-9]+")||!tag.equals("v"+code)||size<=0||size>MAX_APK||!version.matches("[A-Za-z0-9.\\-]+"))throw new IOException("Invalid update manifest");
            if(code<=BuildConfig.VERSION_CODE){if(interactive)ui(()->message("You're up to date","Hive "+BuildConfig.VERSION_NAME+" is installed."));return;}
            ui(()->new AlertDialog.Builder(activity).setTitle("Hive "+version+" is available").setMessage("Download the signed update ("+String.format(Locale.US,"%.1f",size/1048576d)+" MB)? Your photos and app settings will remain intact.").setNegativeButton("Later",null).setPositiveButton("Download",(d,w)->download(code,tag,sha,size)).show());
        }catch(Exception e){if(interactive)ui(()->message("Could not check for updates",safe(e)));}finally{busy=false;}});
    }
    private void download(long code,String tag,String sha,long size){
        if(busy||closed)return;busy=true;
        ProgressDialog progress=new ProgressDialog(activity);progress.setTitle("Updating Hive");progress.setMessage("Downloading and verifying…");progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);progress.setMax(100);progress.setCancelable(false);progress.show();
        worker.execute(()->{File tmp=null;try{
            File dir=new File(activity.getCacheDir(),"updates");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Cannot create update directory");tmp=new File(dir,"hive-part.apk");
            MessageDigest md=MessageDigest.getInstance("SHA-256");String u="https://github.com/"+BuildConfig.UPDATE_REPO+"/releases/download/"+tag+"/Hive.apk";HttpURLConnection conn=connect(u);long total=0;int last=-1;
            try(InputStream in=conn.getInputStream();FileOutputStream out=new FileOutputStream(tmp)){byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1){if(closed||Thread.currentThread().isInterrupted())throw new IOException("Cancelled");total+=n;if(total>size||total>MAX_APK)throw new IOException("APK exceeds expected size");md.update(buf,0,n);out.write(buf,0,n);int percent=(int)(total*100/size);if(percent!=last){last=percent;ui(()->progress.setProgress(percent));}}}finally{conn.disconnect();}
            if(total!=size||!hex(md.digest()).equals(sha))throw new IOException("APK checksum or size mismatch");
            verifyPackage(tmp,code);File file=new File(dir,"Hive.apk");if(file.exists()&&!file.delete())throw new IOException("Cannot replace old update");if(!tmp.renameTo(file))throw new IOException("Cannot finalize APK");tmp=null;
            ui(()->{progress.dismiss();pending=file;activity.getSharedPreferences("hive",0).edit().putString("pendingUpdate",file.getName()).apply();install();});
        }catch(Exception e){if(tmp!=null)tmp.delete();ui(()->{progress.dismiss();message("Update failed",safe(e));});}finally{busy=false;}});
    }
    private void verifyPackage(File apk,long code)throws Exception{
        PackageManager pm=activity.getPackageManager();PackageInfo installed=pm.getPackageInfo(activity.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);PackageInfo incoming=pm.getPackageArchiveInfo(apk.getAbsolutePath(),PackageManager.GET_SIGNING_CERTIFICATES);
        if(incoming==null||!activity.getPackageName().equals(incoming.packageName)||incoming.getLongVersionCode()!=code||code<=installed.getLongVersionCode()||incoming.signingInfo==null||installed.signingInfo==null)throw new IOException("APK package or version is invalid");
        Signature[] a=installed.signingInfo.getApkContentsSigners(),b=incoming.signingInfo.getApkContentsSigners();if(a==null||b==null||a.length!=b.length||!new java.util.HashSet<>(Arrays.asList(a)).equals(new java.util.HashSet<>(Arrays.asList(b))))throw new IOException("Signing certificate does not match installed Hive");
    }
    private void install(){
        if(pending==null||!pending.isFile())return;
        if(!activity.getPackageManager().canRequestPackageInstalls()){
            new AlertDialog.Builder(activity).setTitle("Allow Hive to install updates").setMessage("Android requires you to allow installation from this app. After enabling it, return to Hive and confirm the update.").setNegativeButton("Later",null).setPositiveButton("Open settings",(d,w)->{try{activity.startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())),MainActivity.INSTALL_PERMISSION);}catch(Exception e){message("Cannot open settings",safe(e));}}).show();return;
        }
        try{Uri uri=FileProvider.getUriForFile(activity,activity.getPackageName()+".updates",pending);Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);activity.startActivity(intent);}catch(Exception e){message("Cannot open installer",safe(e));}
    }
    void resumeInstall(){if(pending!=null)install();}
    void installCached(){File file=new File(new File(activity.getCacheDir(),"updates"),"Hive.apk");if(!file.isFile()){check(true);return;}try{PackageInfo p=activity.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(),0);if(p==null)throw new IOException("Invalid APK");verifyPackage(file,p.getLongVersionCode());pending=file;install();}catch(Exception e){file.delete();check(true);}}
    private void message(String title,String text){new AlertDialog.Builder(activity).setTitle(title).setMessage(text).setPositiveButton("OK",null).show();}
    private static String safe(Exception e){String s=e.getMessage();return s==null?"Please try again.":s;}
    private static String hex(byte[] bytes){StringBuilder b=new StringBuilder();for(byte v:bytes)b.append(String.format(Locale.US,"%02x",v&255));return b.toString();}
    private static byte[] readLimited(String url,int max)throws Exception{HttpURLConnection c=connect(url);try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>max)throw new IOException("Manifest too large");out.write(b,0,n);}return out.toByteArray();}finally{c.disconnect();}}
    private static HttpURLConnection connect(String address)throws Exception{
        URL url=new URL(address);for(int i=0;i<6;i++){
            String host=url.getHost().toLowerCase(Locale.US);if(!"https".equals(url.getProtocol())||!(host.equals("github.com")||host.equals("githubusercontent.com")||host.endsWith(".githubusercontent.com")))throw new IOException("Unexpected download host");
            HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setRequestProperty("User-Agent","HiveGallery/"+BuildConfig.VERSION_NAME);int code=c.getResponseCode();
            if(code>=300&&code<400){String next=c.getHeaderField("Location");c.disconnect();if(next==null)throw new IOException("Missing redirect");url=new URL(url,next);continue;}
            if(code!=200){c.disconnect();throw new IOException("GitHub returned HTTP "+code+". Ensure the release is public and contains both assets.");}return c;
        }throw new IOException("Too many redirects");
    }
    void close(){closed=true;worker.shutdownNow();}
}
