package com.dar.nclientv2.api;

import android.content.Intent;
import android.util.JsonReader;
import android.util.Log;

import com.dar.nclientv2.GalleryActivity;
import com.dar.nclientv2.MainActivity;
import com.dar.nclientv2.adapters.ListAdapter;
import com.dar.nclientv2.api.components.Comment;
import com.dar.nclientv2.api.components.Gallery;
import com.dar.nclientv2.api.components.Tag;
import com.dar.nclientv2.api.enums.ApiRequestType;
import com.dar.nclientv2.api.enums.TagStatus;
import com.dar.nclientv2.async.database.Queries;
import com.dar.nclientv2.components.BaseActivity;
import com.dar.nclientv2.settings.CustomInterceptor;
import com.dar.nclientv2.settings.Database;
import com.dar.nclientv2.settings.Global;
import com.dar.nclientv2.settings.Login;
import com.dar.nclientv2.settings.TagV2;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import androidx.annotation.NonNull;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Inspector {
    public Inspector(MainActivity activity, int page, String query, Set<Tag> tags) {
        this(activity,page,query,ApiRequestType.BYSEARCH,false,tags);
    }
    private boolean byPopular,custom;
    private int page;
    private int pageCount;
    private String query;
    private String url;
    private ApiRequestType requestType;
    private Set<Tag> tags=null;
    private List<Gallery> galleries;
    private static final OkHttpClient client;
    static{
        OkHttpClient.Builder builder=new OkHttpClient.Builder();
        builder.addInterceptor(new CustomInterceptor());
        client=builder.build();
    }

    public boolean isCustom() {
        return custom;
    }

    public String getUrl() {
        return url;
    }
    public String getUsableURL(){
        ApiRequestType requestType=this.requestType;
        StringBuilder builder = new StringBuilder("https://nh-express-git-master.rayriffy.now.sh/");
        String tagQuery=TagV2.getQueryString(query,tags);
        Log.d(Global.LOGTAG,"TAGQUR: "+tagQuery);
        Log.d(Global.LOGTAG,Global.getRemoveIgnoredGalleries()+","+Global.isOnlyTag()+","+requestType);
        if(requestType==ApiRequestType.BYALL&&(tagQuery.length()>0||Global.getOnlyLanguage()!=null))requestType=ApiRequestType.BYSEARCH;
        if(tags!=null&&tagQuery.length()==0)requestType=ApiRequestType.BYALL;
        switch (requestType){
            case BYALL:
                builder.append('?');
                break;
            case BYSEARCH:case BYTAG:
                builder.append("search/?q=").append(query);
                if(tags==null&&Global.getOnlyLanguage()!=null){
                    String lang=appendedLanguage();
                    if(!query.contains(lang)) builder.append('+').append(lang);
                }
                if(requestType == ApiRequestType.BYTAG && !Global.isOnlyTag())builder.append(tagQuery);
                if(requestType == ApiRequestType.BYSEARCH) builder.append(tagQuery);
                if (byPopular) builder.append("&sort=popular");
                break;
            case RELATED: case BYSINGLE:builder.append("g/").append(query);break;

        }
        if(page>1)builder.append("&page=").append(page);
        return builder.toString();
    }
    public Inspector(final BaseActivity activity, final int page, String query, final ApiRequestType requestType) {
        this(activity,page,query,requestType,false,null);
    }
    public Inspector(final BaseActivity activity, final int page, final String query, final ApiRequestType requestType, final boolean update,Set<Tag>tags) {
        Log.d(Global.LOGTAG,"COUNT: "+client.dispatcher().runningCallsCount());
        if(!update)client.dispatcher().cancelAll();
        else if(client.dispatcher().runningCallsCount()>0)return;
        activity.getRefresher().setRefreshing(true);
        custom=tags!=null;
        if(!custom){
            tags=new HashSet<>(Arrays.asList(Queries.TagTable.getAllStatus(Database.getDatabase(), TagStatus.ACCEPTED)));
            if(Global.getRemoveIgnoredGalleries()){
                tags.addAll(Arrays.asList(Queries.TagTable.getAllStatus(Database.getDatabase(), TagStatus.AVOIDED)));
                if(Login.useAccountTag())tags.addAll(Arrays.asList(Queries.TagTable.getAllOnlineFavorite(Database.getDatabase())));
            }
        }
        this.tags=tags;
        this.byPopular = Global.isByPopular();
        this.page=page;
        this.query=query;
        this.requestType=requestType;
        url=getUsableURL();
        Log.d(Global.LOGTAG,"Requesting "+url+" whti tags: "+ tags);
        client.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                activity.runOnUiThread(() -> {
                    galleries=new ArrayList<>(1);
                    if(activity instanceof MainActivity){
                        if(!update||activity.getRecycler().getAdapter()==null)
                            activity.getRecycler().setAdapter(new ListAdapter(activity,galleries,null));
                        ((MainActivity)activity).hidePageSwitcher();
                    }
                    else if(activity instanceof GalleryActivity)activity.getRefresher().setEnabled(false);
                    activity.getRefresher().setRefreshing(false);
                });
            }

            @Override
            public void onResponse(@NonNull Call call,@NonNull Response response) throws IOException {
                Log.d(Global.LOGTAG,"Response of "+url);
                Document d=Jsoup.parse(response.body().byteStream(),"UTF-8","https://nh-express-git-master.rayriffy.now.sh");
                parseGalleries(d.getElementsByTag("script"),d.getElementsByClass("gallery"),requestType==ApiRequestType.BYSINGLE? parseComments(d.getElementById("comments")):null);
                for (Gallery x:galleries)if(x.getId()>Global.getMaxId())Global.updateMaxId(activity,x.getId());
                Elements elements=d.getElementsByClass("last");
                if(elements.size()==1)findTotal(elements.first());
                activity.runOnUiThread(() -> {
                    if(requestType!=ApiRequestType.BYSINGLE){
                        if(update&&activity.getRecycler().getAdapter()!=null)((ListAdapter)activity.getRecycler().getAdapter()).addGalleries(galleries);
                        else activity.getRecycler().setAdapter(new ListAdapter(activity, galleries,Global.getRemoveIgnoredGalleries()?null:query));
                        ((MainActivity)activity).setInspector(Inspector.this);
                        ((MainActivity)activity).showPageSwitcher(Inspector.this.page,Inspector.this.pageCount);
                    }else{
                        Intent intent=new Intent(activity, GalleryActivity.class);
                        intent.putExtra(activity.getPackageName()+".GALLERY",galleries.get(0));
                        intent.putExtra(activity.getPackageName()+".ZOOM",page-1);
                        activity.startActivity(intent);
                        if(activity instanceof GalleryActivity)activity.getRefresher().setEnabled(false);
                        if(page!=-1)activity.finish();
                    }
                    activity.getRefresher().setRefreshing(false);
                });
            }


        });

    }
    private List<Comment> parseComments(Element e) {
        Elements el=e.getElementsByClass("comment");
        List<Comment>comments=new ArrayList<>(el.size());

        for (Element x:el) {
            try{comments.add(new Comment(x.attr("data-state")));}catch (IOException ignore){}
        }
        return comments;
    }
    private void findTotal(Element e){
        String temp=e.attr("href");
        pageCount=Integer.parseInt(temp.substring(temp.lastIndexOf('=')+1));
    }

    public Set<Tag> getTags() {
        return tags;
    }

    @NonNull
    @Override
    public String toString() {
        return "Inspector{" +
                "byPopular=" + byPopular +
                ", page=" + page +
                ", pageCount=" + pageCount +
                ", query='" + query + '\'' +
                ", url='" + url + '\'' +
                ", requestType=" + requestType +
                ", galleries=" + galleries +
                '}';
    }
    public static List<Gallery> parseGalleries(Elements e,ApiRequestType type)throws IOException{
        List<Gallery> galleries=new ArrayList<>(type==ApiRequestType.BYSINGLE?1:e.size());
        if(type!=ApiRequestType.BYSINGLE){
            for(Element el:e)galleries.add(new Gallery(el));
        }else{
            String x=e.last().html();
            int s=x.indexOf("new N.gallery(")+14;
            x=x.substring(s,x.indexOf('\n',s)-2);
            galleries.add(new Gallery(new JsonReader(new StringReader(x)), null, null));
        }
        return galleries;
    }
    private void parseGalleries(Elements scripts, Elements gals, List<Comment> comments)throws IOException{
        List<Gallery>galle=new ArrayList<>(gals.size());
        for(Element el:gals)galle.add(new Gallery(el));
        if(requestType==ApiRequestType.BYSINGLE){
            galleries=new ArrayList<>(1);
            if(scripts.last()==null)return;
            String str=scripts.last().html();
            int s=str.indexOf("new N.gallery(")+14,s1=str.indexOf('\n', s) - 2;
            if(s==13||s1<0)return;
            str = str.substring(s, s1);

            galleries.add(new Gallery(new JsonReader(new StringReader(str)),galle,comments));
        }else galleries=galle;
    }

    private String appendedLanguage(){
        if(Global.getOnlyLanguage()==null)return "";
        switch (Global.getOnlyLanguage()){
            case ENGLISH:return "language:english";
            case CHINESE:return "language:chinese";
            case JAPANESE:return "language:japanese";
            case UNKNOWN:return "-language:japanese+-language:chinese+-language:english";
        }
        return "";
    }

    public boolean isByPopular() {
        return byPopular;
    }

    public int getPage() {
        return page;
    }

    public int getPageCount() {
        return pageCount;
    }

    public String getQuery() {
        return query;
    }

    public ApiRequestType getRequestType() {
        return requestType;
    }

    public List<Gallery> getGalleries() {
        return galleries;
    }
}
