package com.dar.nclientv2.async;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.util.JsonReader;

import com.dar.nclientv2.api.components.Tag;
import com.dar.nclientv2.api.enums.TagStatus;
import com.dar.nclientv2.api.enums.TagType;
import com.dar.nclientv2.async.database.Queries;
import com.dar.nclientv2.settings.Database;
import com.dar.nclientv2.settings.Global;

import java.io.IOException;
import java.util.Date;

import androidx.annotation.Nullable;
import okhttp3.Request;
import okhttp3.Response;

public class ScrapeTags extends IntentService {
    /*It is updated once a month*/
    private static final String URL="https://violable-hats.000webhostapp.com/tags.json";
    
    private static final String ALTERNATIVE_URL="https://raw.githubusercontent.com/Dar9586/NClientV2/master/data/tags.json";
    //check every 30day
    private static final long DIFFERENCE_TIME=30L*24*60*60;
    public ScrapeTags() {
        super("Scrape Tag");
    }


    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        try {
            if(new Date().getTime()-getApplicationContext().getSharedPreferences("Settings",0).getLong("lastSync",new Date().getTime())<DIFFERENCE_TIME)return;
            Response x=Global.client.newCall(new Request.Builder().url(URL).build()).execute();
            if(x.code()!=200)x=Global.client.newCall(new Request.Builder().url(ALTERNATIVE_URL).build()).execute();
            if(x.code()!=200)return;
            JsonReader reader=new JsonReader(x.body().charStream());
            reader.beginArray();
            int id=reader.nextInt();
            String name=reader.nextString();
            int count=reader.nextInt();
            TagType type=TagType.values()[reader.nextInt()];
            while (reader.hasNext()) Queries.TagTable.insert(Database.getDatabase(),new Tag(name,count,id,type, TagStatus.DEFAULT));
            reader.close();
            getApplicationContext().getSharedPreferences("Settings",0).edit().putLong("lastSync",new Date().getTime()).apply();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
