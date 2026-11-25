package com.hive.gallery;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import java.util.*;
import java.text.SimpleDateFormat;

final class Media {
    final Uri uri; final String name, album, day, month, year, mime; final long date, duration; final boolean video;
    Media(Uri uri,String name,String album,long date,String mime,long duration,boolean video){
        this.uri=uri;this.name=name==null?"Untitled":name;this.album=album==null||album.isEmpty()?"Other":album;this.date=date;this.mime=mime;this.duration=duration;this.video=video;
        Date d=new Date(date); day=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(d); month=day.substring(0,7);year=day.substring(0,4);
    }
    String displayDate(String format){return new SimpleDateFormat(format,Locale.getDefault()).format(new Date(date));}
    static List<Media> query(Context ctx){
        ArrayList<Media> out=new ArrayList<>();
        read(ctx,MediaStore.Images.Media.EXTERNAL_CONTENT_URI,false,out);
        read(ctx,MediaStore.Video.Media.EXTERNAL_CONTENT_URI,true,out);
        out.sort(Comparator.comparingLong((Media m)->m.date).reversed().thenComparing(m->m.uri.toString())); return out;
    }
    private static void read(Context ctx,Uri base,boolean video,List<Media> out){
        String[] cols=video?new String[]{"_id","_display_name","bucket_display_name","datetaken","date_added","mime_type","duration"}:new String[]{"_id","_display_name","bucket_display_name","datetaken","date_added","mime_type"};
        try(Cursor c=ctx.getContentResolver().query(base,cols,null,null,"date_added DESC")){
            if(c==null)return; while(c.moveToNext()){
                long date=c.getLong(3);if(date<=0)date=c.getLong(4)*1000L;
                out.add(new Media(ContentUris.withAppendedId(base,c.getLong(0)),c.getString(1),c.getString(2),date,c.getString(5),video?c.getLong(6):0,video));
            }
        }catch(SecurityException ignored){}
    }
}
