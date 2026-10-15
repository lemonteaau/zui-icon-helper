package io.github.lemonteaau.zuiiconhelper;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.text.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    static final int INK=SmartisanStyle.TEXT, MUTED=SmartisanStyle.SECONDARY, GREEN=SmartisanStyle.BLUE, BG=SmartisanStyle.BACKGROUND;
    static final java.util.concurrent.atomic.AtomicBoolean CHANGING=new java.util.concurrent.atomic.AtomicBoolean();
    final ExecutorService images=Executors.newFixedThreadPool(2);
    final android.util.LruCache<String,Bitmap> cache=new android.util.LruCache<String,Bitmap>(12*1024*1024) { protected int sizeOf(String k,Bitmap b) {return b.getByteCount();} };
    ThemeEngine engine; IconRepository repo; android.content.SharedPreferences prefs;
    JSONObject overrides=new JSONObject(), appliedOverrides=new JSONObject(); String base="original",appliedBase="original",query="";
    int iconScale=100,appliedScale=100;
    static final int PICK_ICON=601;
    android.net.Uri pendingResult;
    boolean busy=true, binding; TextView status, subtitle, appCount, packDetail, scaleDetail; Button apply; TextView defaults; AppAdapter adapter; ListView list; LinearLayout root;
    List<IconRepository.App> filtered=new ArrayList<>();final List<Object> rows=new ArrayList<>();static final Object FOOTER=new Object();
    int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    TextView text(String s,int size,int color){return SmartisanStyle.text(this,s,size,color);}
    GradientDrawable shape(int color,int stroke){return SmartisanStyle.surface(this,color,10,stroke);}
    LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    Button button(String title){Button b=new Button(this);b.setText(title);b.setAllCaps(false);SmartisanStyle.type(b,14,0xff606060,true);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(10),0,dp(10),0);b.setBackground(SmartisanStyle.button(this,false));b.setStateListAnimator(null);b.setElevation(0);return b;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.WHITE);getWindow().setNavigationBarColor(Color.WHITE);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        engine=new ThemeEngine(this);prefs=getSharedPreferences("selection",0);buildUI();
        new Thread(()->{
            String current="unknown",error=null;
            try{repo=new IconRepository(this,engine);engine.original();current=engine.identify(repo.packs);engine.probe();}catch(Exception e){error=e.getMessage();android.util.Log.e("ZuiIconHelper","initialize",e);}finally{engine.saveReport();}
            final String detected=current, failure=error;
            runOnUiThread(()->{
                if(failure!=null){status.setText("读取失败："+failure+"。请关闭后重试。");return;}
                try{
                    appliedBase=prefs.getString("appliedBase", "custom".equals(detected)||"unknown".equals(detected)?"original":detected);if(!repo.hasPack(appliedBase))appliedBase="original";
                    appliedOverrides=new JSONObject(prefs.getString("appliedOverrides","{}"));
                    if(!"custom".equals(detected)&&!"unknown".equals(detected)&&!detected.equals(appliedBase)){appliedBase=detected;appliedOverrides=new JSONObject();}
                    base=prefs.getString("draftBase",appliedBase);if(!repo.hasPack(base))base="original";overrides=new JSONObject(prefs.getString("draftOverrides",appliedOverrides.toString()));
                    // Remove stale native overrides for apps uninstalled since the previous session.
                    List<String> removed=new ArrayList<>();Iterator<String> it=overrides.keys();while(it.hasNext()){String p=it.next();String c=overrides.optString(p);if(("native".equals(c)&&repo.findApp(p)==null)||(!"native".equals(c)&&!repo.icons.containsKey(c)))removed.add(p);}for(String p:removed)overrides.remove(p);
                    appliedScale=IconScale.valid(prefs.getInt("appliedScale",100));iconScale=IconScale.valid(prefs.getInt("draftScale",appliedScale));
                    busy=false;binding=true;updateDefault();binding=false;filter();updateState();
                    if("unknown".equals(detected))status.setText("当前桌面图标不是由助手设置的；选好后点「应用选择」即可接管。");
                    if(repo.packs.isEmpty())status.setText("还没有导入图标包：点上面的「图标包」开始。");
                    resumeImport();
                }catch(Exception e){status.setText("读取选择记录失败："+e.getMessage());}
            });
        },"load-icons").start();
    }
    void buildUI(){
        root=column();root.setBackgroundColor(BG);
        FrameLayout bar=new FrameLayout(this);bar.setBackgroundColor(Color.WHITE);TextView title=text("图标助手",17,SmartisanStyle.TITLE);title.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));title.setGravity(Gravity.CENTER);bar.addView(title,new FrameLayout.LayoutParams(-1,dp(48)));
        FrameLayout.LayoutParams rp=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.RIGHT|Gravity.CENTER_VERTICAL);rp.rightMargin=dp(6);bar.addView(barAction("setting",20,"设置与帮助",()->menu()),rp);root.addView(bar);root.addView(SmartisanStyle.divider(this,SmartisanStyle.BAR_LINE));
        LinearLayout content=column();content.setPadding(dp(16),0,dp(16),0);LinearLayout header=column();header.setPadding(0,dp(18),0,0);header.addView(SmartisanStyle.caption(this,"默认方案"));
        LinearLayout group=column();SmartisanStyle.card(group);
        LinearLayout defaultRow=listRow("setting","默认图标包","选择默认图标包",()->chooseDefault());defaults=(TextView)defaultRow.getTag();group.addView(defaultRow,new LinearLayout.LayoutParams(-1,dp(56)));
        View line=SmartisanStyle.divider(this,SmartisanStyle.DIVIDER);((LinearLayout.LayoutParams)line.getLayoutParams()).leftMargin=dp(50);group.addView(line);
        LinearLayout packRow=listRow("search","图标包","导入或管理图标包",()->managePacks());packDetail=(TextView)packRow.getTag();group.addView(packRow,new LinearLayout.LayoutParams(-1,dp(56)));
        View line2=SmartisanStyle.divider(this,SmartisanStyle.DIVIDER);((LinearLayout.LayoutParams)line2.getLayoutParams()).leftMargin=dp(50);group.addView(line2);
        LinearLayout scaleRow=listRow("resize","图标大小","统一调整替换后图标的大小",()->chooseScale());scaleDetail=(TextView)scaleRow.getTag();group.addView(scaleRow,new LinearLayout.LayoutParams(-1,dp(56)));
        header.addView(group,new LinearLayout.LayoutParams(-1,-2));
        subtitle=text("点右边的图标换上，点左边的用回原生；点进一行可以挑别的图标或用自己的图片。",12,MUTED);subtitle.setLineSpacing(dp(3),1);subtitle.setPadding(dp(16),dp(8),dp(8),dp(18));header.addView(subtitle);
        EditText search=new EditText(this);search.setSingleLine(true);search.setHint("搜索应用名称或包名");header.addView(searchField(search),new LinearLayout.LayoutParams(-1,dp(38)));
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){query=s.toString().toLowerCase(Locale.ROOT);if(repo!=null)filter();}public void afterTextChanged(Editable e){}});
        list=new ListView(this);list.setDivider(null);list.setSelector(android.R.color.transparent);list.setVerticalScrollBarEnabled(false);list.setClipToPadding(false);list.setPadding(0,0,0,dp(16));list.setItemsCanFocus(true);list.addHeaderView(header,null,false);adapter=new AppAdapter();list.setAdapter(adapter);content.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        root.addView(new SmartisanStyle.Centered(this,content,-1),new LinearLayout.LayoutParams(-1,0,1));root.addView(SmartisanStyle.divider(this,SmartisanStyle.BAR_LINE));
        LinearLayout bottom=column();bottom.setPadding(dp(16),dp(10),dp(16),dp(14));status=text("正在整理图标预览…",12,MUTED);status.setGravity(Gravity.CENTER);status.setPadding(0,0,0,dp(10));bottom.addView(status);
        LinearLayout footer=new LinearLayout(this);footer.setBaselineAligned(false);Button undo=button("撤销修改");undo.setOnClickListener(v->{if(busy)return;base=appliedBase;iconScale=appliedScale;overrides=cloneJson(appliedOverrides);persistDraft();updateDefault();refresh();updateState();});footer.addView(undo,new LinearLayout.LayoutParams(0,dp(48),1));
        apply=button("应用选择");SmartisanStyle.type(apply,16,Color.WHITE,true);apply.setBackground(SmartisanStyle.button(this,true));apply.setStateListAnimator(null);apply.setOnClickListener(v->applySelection());LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,dp(48),2);ap.leftMargin=dp(12);footer.addView(apply,ap);bottom.addView(footer);
        FrameLayout bottomBar=new SmartisanStyle.Centered(this,bottom,-2);bottomBar.setBackgroundColor(Color.WHITE);root.addView(bottomBar,new LinearLayout.LayoutParams(-1,-2));
        setContentView(root);apply.setEnabled(false);
    }
    View barAction(String name,int size,String description,Runnable click){FrameLayout hit=new FrameLayout(this);ImageView icon=SmartisanStyle.icon(this,name,size);hit.addView(icon,new FrameLayout.LayoutParams(dp(size),dp(size),Gravity.CENTER));hit.setContentDescription(description);hit.setBackground(SmartisanStyle.surface(this,Color.TRANSPARENT,24,0));hit.setForeground(SmartisanStyle.row(this));hit.setOnClickListener(v->click.run());return hit;}
    View searchField(EditText input){LinearLayout field=new LinearLayout(this);field.setGravity(Gravity.CENTER_VERTICAL);field.setPadding(dp(14),0,dp(4),0);field.setBackground(SmartisanStyle.surface(this,Color.WHITE,100,SmartisanStyle.BORDER));field.addView(SmartisanStyle.icon(this,"search",16));SmartisanStyle.type(input,14,INK,false);input.setHintTextColor(0xffaaaaaa);input.setBackgroundColor(Color.TRANSPARENT);input.setPadding(dp(10),0,dp(4),0);field.addView(input,new LinearLayout.LayoutParams(0,-1,1));View clear=barAction("close",16,"清除搜索",()->input.setText(""));field.addView(clear,new LinearLayout.LayoutParams(dp(36),-1));return field;}
    TextView textButton(String label,String description,Runnable click){TextView t=text(label,13,GREEN);t.setGravity(Gravity.CENTER);t.setPadding(dp(10),0,dp(10),0);t.setBackground(SmartisanStyle.surface(this,Color.TRANSPARENT,6,0));t.setForeground(SmartisanStyle.row(this));t.setContentDescription(description);t.setOnClickListener(v->click.run());return t;}
    View detailButton(String label,String description,Runnable click){LinearLayout d=new LinearLayout(this);d.setGravity(Gravity.CENTER_VERTICAL);d.setPadding(dp(10),0,dp(2),0);d.addView(text(label,13,MUTED));d.addView(SmartisanStyle.icon(this,"right",20));d.setForeground(SmartisanStyle.row(this));d.setContentDescription(description);d.setOnClickListener(v->click.run());return d;}
    /** A Smartisan list tile: icon, title, grey detail and chevron. The detail TextView is returned as the row's tag. */
    LinearLayout listRow(String icon,String title,String description,Runnable click){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(16),0,dp(10),0);row.setForeground(SmartisanStyle.row(this));
        row.addView(SmartisanStyle.icon(this,icon,20));TextView label=text(title,15,INK);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.leftMargin=dp(14);row.addView(label,lp);
        TextView detail=text("正在读取",14,MUTED);row.addView(detail);row.addView(SmartisanStyle.icon(this,"right",22));row.setTag(detail);row.setContentDescription(description);row.setOnClickListener(v->click.run());return row;
    }
    void managePacks(){
        if(busy)return;LinearLayout body=column();body.setPadding(0,dp(6),0,dp(6));final PanelDialog[] dialog=new PanelDialog[1];
        List<PackImporter.Source> sources=new PackImporter(this,engine).discover(new HashSet<>(repo.packs));Set<String> installed=new HashSet<>();for(PackImporter.Source src:sources)installed.add(src.pkg);
        body.addView(sectionLabel("已导入"));if(repo.packs.isEmpty())body.addView(note("还没有导入任何图标包。"));
        for(String pack:repo.packs){JSONObject c=repo.catalogs.get(pack);String sourcePkg=c.optString("source"),name=repo.label(pack);
            LinearLayout row=packRow(name,c.optJSONArray("icons").length()+" 个图标 · 版本 "+c.optString("version"));
            if(installed.contains(sourcePkg))row.addView(textButton("更新","重新导入"+name,()->{dialog[0].dismiss();runImport(sourcePkg,name);}),new LinearLayout.LayoutParams(-2,dp(36)));
            row.addView(textButton("删除","删除"+name,()->{dialog[0].dismiss();confirmDelete(pack);}),new LinearLayout.LayoutParams(-2,dp(36)));body.addView(row);}
        body.addView(sectionLabel("平板上可导入的图标包"));int available=0;
        for(PackImporter.Source src:sources){if(src.imported)continue;available++;LinearLayout row=packRow(src.label,src.pkg);row.addView(textButton("导入","导入"+src.label,()->{dialog[0].dismiss();runImport(src.pkg,src.label);}),new LinearLayout.LayoutParams(-2,dp(36)));body.addView(row);}
        if(available==0){body.addView(note("没有找到新的图标包。先安装图标包 App（应用商店里买的、或第三方的都行，支持 Nova / ADW 等启动器通用的图标包），再回到这里导入。\n\n还没有图标包的话，可以先试试免费的 CraftEcho。"));
            Button get=button("下载 CraftEcho 图标包");get.setOnClickListener(v->{try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://github.com/gaodeng/CraftEcho-Icons/releases/latest")));}catch(Exception e){status.setText("没有可以打开网页的浏览器");}});LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-1,dp(44));gp.setMargins(dp(20),dp(4),dp(20),dp(10));body.addView(get,gp);}
        ScrollView scroll=new ScrollView(this);scroll.addView(body);dialog[0]=new PanelDialog.Builder(this).setTitle("图标包").setView(scroll).setNegativeButton("关闭",null).create();dialog[0].show();
        dialog[0].getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(32),dp(560)),-2);
    }
    TextView sectionLabel(String s){TextView t=text(s,13,MUTED);t.setPadding(dp(20),dp(14),dp(20),dp(6));return t;}
    TextView note(String s){TextView t=text(s,13,MUTED);t.setLineSpacing(dp(4),1);t.setPadding(dp(20),dp(4),dp(20),dp(10));return t;}
    LinearLayout packRow(String title,String detail){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(20),dp(8),dp(10),dp(8));LinearLayout names=column();TextView t=text(title,15,INK);t.setSingleLine(true);t.setEllipsize(TextUtils.TruncateAt.END);names.addView(t);TextView d=text(detail,12,MUTED);d.setPadding(0,dp(4),0,0);d.setSingleLine(true);d.setEllipsize(TextUtils.TruncateAt.MIDDLE);names.addView(d);row.addView(names,new LinearLayout.LayoutParams(0,-2,1));return row;}
    void confirmDelete(String pack){new PanelDialog.Builder(this).setTitle("删除「"+repo.label(pack)+"」？").setMessage("只删除助手里的这份导入，不会卸载图标包 App。用到这个图标包的单独选择会被清除；桌面上现有的图标不会马上变化，下次「应用选择」时才更新。").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{
        Iterator<String> it=overrides.keys();List<String> drop=new ArrayList<>();while(it.hasNext()){String k=it.next();if(overrides.optString(k).startsWith(pack+"/"))drop.add(k);}for(String k:drop)overrides.remove(k);if(pack.equals(base))base="original";persistDraft();
        repo.close();PackImporter.deleteTree(engine.packDir(pack));recreate();}).show();}
    void runImport(String pkg,String label){
        if(busy||!CHANGING.compareAndSet(false,true))return;busy=true;apply.setEnabled(false);
        LinearLayout body=column();body.setPadding(dp(24),dp(20),dp(24),dp(20));TextView progress=text("正在读取图标包…",14,INK);body.addView(progress);TextView hint=text("图标较多时需要一两分钟，请不要离开助手。",12,MUTED);hint.setPadding(0,dp(10),0,0);body.addView(hint);
        PanelDialog dialog=new PanelDialog.Builder(this).setTitle("导入「"+label+"」").setView(body).create();dialog.setCancelable(false);dialog.show();
        new Thread(()->{String error=null;try{repo.close();new PackImporter(this,engine).importPack(pkg,(done,total)->runOnUiThread(()->progress.setText("正在转换图标 "+done+" / "+total)));}catch(Exception e){error=e.getMessage();android.util.Log.e("ZuiIconHelper","import pack",e);}finally{CHANGING.set(false);}
            final String failure=error;runOnUiThread(()->{dialog.dismiss();busy=false;if(failure==null){if(repo.packs.isEmpty())prefs.edit().putString("draftBase",PackImporter.packId(pkg)).commit();recreate();}else{updateState();new PanelDialog.Builder(this).setTitle("导入失败").setMessage(failure==null?"未知错误":failure).setPositiveButton("知道了",null).show();}});
        },"import-pack").start();
    }
    void updateDefault(){if(scaleDetail!=null)scaleDetail.setText(iconScale+"%");defaults.setText(repo==null?"正在读取":repo.label(base));if(packDetail!=null)packDetail.setText(repo==null?"正在读取":repo.packs.isEmpty()?"去导入":repo.packs.size()+" 个");}
    void chooseDefault(){if(busy)return;LinearLayout choices=column();PanelDialog dialog=new PanelDialog.Builder(this).setTitle("默认图标包").setView(choices).setNegativeButton("取消",null).create();for(String pack:repo.bases()){LinearLayout row=new LinearLayout(this);row.setPadding(dp(20),0,dp(20),0);row.setGravity(Gravity.CENTER_VERTICAL);row.addView(text(repo.label(pack),16,INK),new LinearLayout.LayoutParams(0,-2,1));row.addView(SmartisanStyle.icon(this,base.equals(pack)?"selected":"unselected",22));row.setContentDescription(repo.label(pack)+(base.equals(pack)?" · 已选":""));row.setOnClickListener(v->{base=pack;persistDraft();updateDefault();refresh();updateState();dialog.dismiss();});choices.addView(row,new LinearLayout.LayoutParams(-1,dp(54)));choices.addView(SmartisanStyle.divider(this,SmartisanStyle.DIVIDER));}dialog.show();}
    void menu(){if(busy)return;new PanelDialog.Builder(this).setTitle("设置").setItems(new String[]{"全部应用重新跟随默认包","恢复系统原始图标","使用说明","关于与开源许可"},(d,n)->{
        if(n==2){new PanelDialog.Builder(this).setTitle("使用说明").setMessage("1. 先导入图标包：点「图标包」，选平板上已经安装的图标包 App。\n\n2. 在「默认图标包」里选一个，所有它收录的应用都会换成这一套。以后新装的应用也会自动匹配。\n\n3. 每个应用一行：左边是原生图标，右边是替换图标，点哪个就用哪个（红色小勾就是选中）。点进那一行，可以从所有图标包里挑别的，也可以导入自己的图片。\n\n4. 点底部的「应用选择」，桌面图标才会真正改变。\n\n随时可以在这里选「恢复系统原始图标」回到出厂的样子。壁纸和桌面布局都不会被改动。\n\n如果在系统设置里换了主题，请回到助手重新点一次「应用选择」。").setPositiveButton("知道了",null).show();return;}
        if(n==3){new PanelDialog.Builder(this).setTitle("关于").setMessage("ZUI 图标助手 "+versionName()+"\n给联想 ZUI 平板的原厂桌面换图标，不需要 root。\n\n开源地址：github.com/lemonteaau/zui-icon-helper\n\n界面风格参考 Smartisan OS UI Kit (Unofficial)，作者 Paix Chan，CC BY 4.0。\n\n助手本身不带任何第三方图标。导入的图标包只在你的平板上转换使用，版权归原作者所有。").setPositiveButton("知道了",null).show();return;}
        new PanelDialog.Builder(this).setTitle(n==0?"清除所有单独选择？":"恢复系统原始图标？").setMessage(n==0?"所有应用将重新跟随默认包，点「应用选择」后生效。":"将清除单独选择，并恢复主题自带的原始图标。壁纸和布局不变。").setNegativeButton("取消",null).setPositiveButton(n==0?"清除选择":"恢复",(x,w)->{overrides=new JSONObject();if(n==1){base="original";iconScale=100;}persistDraft();binding=true;updateDefault();binding=false;refresh();updateState();if(n==1)applySelection();}).show();
    }).show();}
    String versionName(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception e){return "";}}

    JSONObject cloneJson(JSONObject o){try{return new JSONObject(o.toString());}catch(Exception e){throw new IllegalStateException(e);}}
    String signature(String b,JSONObject o){TreeMap<String,String> m=new TreeMap<>();Iterator<String> keys=o.keys();while(keys.hasNext()){String k=keys.next();m.put(k,o.optString(k));}return b+m.toString();}
    void persistDraft(){prefs.edit().putString("draftBase",base).putString("draftOverrides",overrides.toString()).putInt("draftScale",iconScale).apply();}
    void updateState(){if(appCount!=null&&repo!=null)appCount.setText(filtered.size()+" 个应用");if(scaleDetail!=null)scaleDetail.setText(iconScale+"%");boolean dirty=iconScale!=appliedScale||!signature(base,overrides).equals(signature(appliedBase,appliedOverrides));status.setText((dirty?"有未应用的修改":"选择已保存")+" · "+overrides.length()+" 个应用单独指定 · "+iconScale+"%");apply.setEnabled(!busy);apply.setText(dirty?"应用选择":"重新应用");}
    void refresh(){if(repo!=null)filter();}
    /** Smartisan groups apps by whether the icon set covers them; here that is the default pack. */
    void filter(){filtered.clear();rows.clear();for(IconRepository.App a:repo.apps)if((a.label+" "+a.pkg).toLowerCase(Locale.ROOT).contains(query))filtered.add(a);
        List<IconRepository.App> covered=new ArrayList<>(),missing=new ArrayList<>();for(IconRepository.App a:filtered)("native".equals(repo.defaultChoice(a.pkg,base))?missing:covered).add(a);
        if("original".equals(base)){if(!filtered.isEmpty()){rows.add("应用图标 · "+filtered.size());rows.addAll(filtered);}}
        else{String name="「"+repo.label(base)+"」";if(!covered.isEmpty()){rows.add(name+"已收录 · "+covered.size());rows.addAll(covered);}if(!missing.isEmpty()){rows.add(name+"未收录 · "+missing.size());rows.addAll(missing);}}
        if(!filtered.isEmpty())rows.add(FOOTER);else rows.add("没有找到应用");adapter.notifyDataSetChanged();}
    String effective(IconRepository.App a){return overrides.optString(a.pkg,repo.defaultChoice(a.pkg,base));}
    void select(IconRepository.App a,String choice){if(busy)return;if(choice.equals(repo.defaultChoice(a.pkg,base)))overrides.remove(a.pkg);else try{overrides.put(a.pkg,choice);}catch(Exception e){throw new IllegalStateException(e);}persistDraft();refresh();updateState();if(selectionListener!=null)selectionListener.run();}
    /** The icon in a row's right-hand tile: the chosen one, else the default pack's, else any pack's; null when nothing covers the app. */
    String alternative(IconRepository.App a){String eff=effective(a);if(!"native".equals(eff))return eff;String d=repo.defaultChoice(a.pkg,base);if(!"native".equals(d))return d;for(String p:repo.packs){List<String> v=repo.variants(a.pkg,p);if(!v.isEmpty())return v.get(0);}return null;}
    String statusFor(IconRepository.App a){String eff=effective(a);if("native".equals(eff))return alternative(a)==null?"可替换图标":"使用原生图标";return choiceLabel(eff)+(overrides.has(a.pkg)?" · 已替换图标":" · 跟随默认图标包");}
    String choiceLabel(String c){if("native".equals(c))return "原生";IconRepository.Icon i=repo.icons.get(c);return i==null?"未知":repo.label(i.pack);}
    void load(ImageView view,String choice,IconRepository.App app){load(view,choice,app,true);}
    void load(ImageView view,String choice,IconRepository.App app,boolean scaled){
        final int previewScale=scaled?iconScale:100;String key=choice+("native".equals(choice)?":"+app.pkg:"")+":"+previewScale;view.setTag(key);Bitmap b=cache.get(key);if(b!=null){view.setImageBitmap(b);return;}view.setImageDrawable(null);
        images.execute(()->{try{Bitmap source=repo.bitmap(choice,app);Bitmap bitmap=IconScale.bitmap(source,previewScale);if(bitmap!=source)source.recycle();cache.put(key,bitmap);runOnUiThread(()->{if(key.equals(view.getTag()))view.setImageBitmap(bitmap);});}catch(Exception e){android.util.Log.w("ZuiIconHelper","preview "+key,e);runOnUiThread(()->{if(key.equals(view.getTag()))view.setContentDescription("预览加载失败");});}});
    }
    /** A Smartisan icon tile: white square with the icon, the red check when chosen and an optional caption. A null choice is the grey "+" tile. The check is the cell's tag. */
    View iconCell(IconRepository.App a,String choice,String caption,boolean chosen,int size,Runnable click){
        LinearLayout cell=column();cell.setGravity(Gravity.CENTER_HORIZONTAL);int over=dp(7),badge=dp(22);FrameLayout holder=new FrameLayout(this);
        FrameLayout tile=new FrameLayout(this);tile.setBackground(SmartisanStyle.iconTile(this,choice==null));tile.setOnClickListener(v->click.run());tile.setContentDescription(a.label+" · "+(choice==null?"选择替换图标":caption==null?choiceLabel(choice):caption)+(chosen?" · 已选":""));
        ImageView icon=new ImageView(this);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);int inner=choice==null?size*45/100:size*76/100;tile.addView(icon,new FrameLayout.LayoutParams(inner,inner,Gravity.CENTER));
        if(choice==null)icon.setImageResource(getResources().getIdentifier("sm_plus","drawable",getPackageName()));else load(icon,choice,a);
        FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(size,size);tp.setMargins(over,over,over,over/2);holder.addView(tile,tp);
        ImageView mark=SmartisanStyle.icon(this,"badge",22);holder.addView(mark,new FrameLayout.LayoutParams(badge,badge,Gravity.TOP|Gravity.END));mark.setVisibility(chosen?View.VISIBLE:View.INVISIBLE);cell.addView(holder,new LinearLayout.LayoutParams(-2,-2));cell.setTag(mark);
        if(caption!=null){TextView t=text(caption,11,MUTED);t.setSingleLine(true);t.setEllipsize(TextUtils.TruncateAt.END);t.setGravity(Gravity.CENTER);t.setPadding(dp(2),dp(4),dp(2),dp(10));cell.addView(t,new LinearLayout.LayoutParams(size+over*2,-2));}
        return cell;
    }
    class AppAdapter extends BaseAdapter {
        public int getCount(){return rows.size();}public Object getItem(int n){return rows.get(n);}public long getItemId(int n){return n;}
        public int getViewTypeCount(){return 3;}public int getItemViewType(int n){Object o=rows.get(n);return o instanceof IconRepository.App?0:o==FOOTER?2:1;}
        public boolean isEnabled(int n){return false;}
        public View getView(int n,View old,android.view.ViewGroup parent){
            Object item=rows.get(n);
            if(item==FOOTER){TextView t=text("默认图标包没有收录的应用，可以点「+」或点进那一行，从其他图标包里挑一个，也可以用自己的图片。",12,MUTED);t.setLineSpacing(dp(3),1);t.setPadding(dp(16),dp(10),dp(16),dp(8));return t;}
            if(item instanceof String){TextView t=SmartisanStyle.caption(MainActivity.this,(String)item);t.setPadding(dp(16),dp(22),0,dp(8));return t;}
            IconRepository.App a=(IconRepository.App)item;boolean first=!(rows.get(n-1) instanceof IconRepository.App),last=n==rows.size()-1||!(rows.get(n+1) instanceof IconRepository.App);
            String eff=effective(a),alt=alternative(a);boolean nat="native".equals(eff);
            LinearLayout row=new LinearLayout(MainActivity.this);row.setGravity(Gravity.CENTER_VERTICAL);row.setBaselineAligned(false);row.setBackground(SmartisanStyle.segment(MainActivity.this,first,last));row.setPadding(dp(14),dp(8),dp(12),dp(8));
            row.addView(iconCell(a,"native",null,nat,dp(58),()->select(a,"native")));
            row.addView(iconCell(a,alt,null,!nat,dp(58),alt==null?()->detail(a):()->select(a,alt)));
            LinearLayout names=column();TextView name=text(a.label,16,INK);name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);names.addView(name);
            TextView status=text(statusFor(a),13,MUTED);status.setPadding(0,dp(7),0,0);status.setSingleLine(true);status.setEllipsize(TextUtils.TruncateAt.END);names.addView(status);
            LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,-2,1);np.leftMargin=dp(14);row.addView(names,np);row.addView(SmartisanStyle.icon(MainActivity.this,"right",22));
            row.setContentDescription(a.label+" · "+statusFor(a)+" · 打开详情");row.setOnClickListener(v->detail(a));
            AbsListView.LayoutParams lp=new AbsListView.LayoutParams(-1,-2);row.setLayoutParams(lp);return row;
        }
    }
    /** Smartisan's 「替换图标」 page: the app on top, then every icon it can use, in grouped grids. */
    void detail(IconRepository.App app){
        if(busy)return;Dialog page=new Dialog(this,android.R.style.Theme_Material_Light_NoActionBar);Window w=page.getWindow();w.setStatusBarColor(Color.WHITE);w.setNavigationBarColor(Color.WHITE);w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        LinearLayout frame=column();frame.setBackgroundColor(BG);
        FrameLayout bar=new FrameLayout(this);bar.setBackgroundColor(Color.WHITE);TextView title=text("替换图标",17,SmartisanStyle.TITLE);title.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));title.setGravity(Gravity.CENTER);bar.addView(title,new FrameLayout.LayoutParams(-1,dp(48)));
        FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.LEFT|Gravity.CENTER_VERTICAL);bp.leftMargin=dp(6);bar.addView(barAction("back",20,"返回",page::dismiss),bp);frame.addView(bar);frame.addView(SmartisanStyle.divider(this,SmartisanStyle.BAR_LINE));
        LinearLayout body=column();body.setPadding(dp(16),dp(16),dp(16),dp(28));ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);scroll.addView(new SmartisanStyle.Centered(this,body,-2));frame.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        List<Object[]> marks=new ArrayList<>(),found=new ArrayList<>();
        LinearLayout head=column();SmartisanStyle.card(head);head.setGravity(Gravity.CENTER_HORIZONTAL);head.setPadding(dp(20),dp(26),dp(20),dp(22));
        ImageView big=new ImageView(this);big.setScaleType(ImageView.ScaleType.FIT_CENTER);head.addView(big,new LinearLayout.LayoutParams(dp(60),dp(60)));load(big,"native",app,false);
        TextView name=text(app.label,18,INK);name.setGravity(Gravity.CENTER);name.setPadding(0,dp(14),0,0);head.addView(name);TextView version=text(versionOf(app.pkg),13,MUTED);version.setGravity(Gravity.CENTER);version.setPadding(0,dp(9),0,0);head.addView(version);
        TextView state=text(statusFor(app),13,MUTED);state.setGravity(Gravity.CENTER);state.setPadding(0,dp(9),0,0);head.addView(state);body.addView(head,new LinearLayout.LayoutParams(-1,-2));
        Runnable repaint=()->{String eff=effective(app);for(List<Object[]> l:Arrays.asList(marks,found))for(Object[] m:l)((View)m[1]).setVisibility(eff.equals(m[0])?View.VISIBLE:View.INVISIBLE);state.setText(statusFor(app));};
        java.util.function.BiFunction<String,String,View> cell=(choice,caption)->{View v=iconCell(app,choice,caption,choice.equals(effective(app)),dp(64),()->{select(app,choice);});return v;};
        SmartisanStyle.Grid own=new SmartisanStyle.Grid(this,104);View nativeCell=cell.apply("native","原生");marks.add(new Object[]{"native",nativeCell.getTag()});own.addView(nativeCell);
        for(String pack:repo.packs){List<String> v=repo.variants(app.pkg,pack);for(int i=0;i<v.size();i++){String c=v.get(i);View x=cell.apply(c,repo.label(pack)+(v.size()>1?" "+(i+1):""));marks.add(new Object[]{c,x.getTag()});own.addView(x);}}
        String eff=effective(app);boolean listed=false;for(Object[] m:marks)if(eff.equals(m[0]))listed=true;if(!listed&&!eff.startsWith("custom/")){View x=cell.apply(eff,choiceLabel(eff));marks.add(new Object[]{eff,x.getTag()});own.addView(x);}
        body.addView(group("本应用的图标",own),groupParams());
        SmartisanStyle.Grid mine=new SmartisanStyle.Grid(this,104);for(IconRepository.Icon ic:new ArrayList<>(repo.icons.values()))if("custom".equals(ic.pack)){View x=cell.apply(ic.choice,ic.name);marks.add(new Object[]{ic.choice,x.getTag()});mine.addView(x);}
        mine.addView(iconCell(app,null,"导入图片",false,dp(64),()->{page.dismiss();pickImage(app);}));body.addView(group("我的图片",mine),groupParams());
        LinearLayout searchBox=column();EditText find=new EditText(this);find.setSingleLine(true);find.setHint("搜索全部图标包，例如 camera、相机");LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,dp(38));fp.setMargins(dp(16),0,dp(16),0);searchBox.addView(searchField(find),fp);
        TextView count=text("",12,MUTED);count.setGravity(Gravity.CENTER);count.setPadding(dp(16),dp(12),dp(16),dp(4));searchBox.addView(count);SmartisanStyle.Grid results=new SmartisanStyle.Grid(this,104);searchBox.addView(results);
        TextView more=textButton("显示更多","显示更多搜索结果",()->{});LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-2,dp(36));mp.gravity=Gravity.CENTER_HORIZONTAL;mp.bottomMargin=dp(6);searchBox.addView(more,mp);body.addView(group("在全部图标包中搜索",searchBox),groupParams());
        int[] limit={60};Runnable search=()->{String q=find.getText().toString().trim().toLowerCase(Locale.ROOT);results.removeAllViews();found.clear();int total=0;
            if(!q.isEmpty())for(IconRepository.Icon ic:new ArrayList<>(repo.icons.values())){if("custom".equals(ic.pack)||!ic.search.contains(q))continue;if(++total>limit[0])continue;View x=cell.apply(ic.choice,ic.name.matches("(?i)(icon|ic)?[ _]?[0-9a-f]{6,}")?repo.label(ic.pack):ic.name);found.add(new Object[]{ic.choice,x.getTag()});results.addView(x);}
            count.setText(q.isEmpty()?"按名称搜索，可以用中文、英文或包名":total==0?"没有找到图标":total>limit[0]?"找到 "+total+" 个，先显示前 "+limit[0]+" 个":"找到 "+total+" 个图标");more.setVisibility(total>limit[0]?View.VISIBLE:View.GONE);};
        more.setOnClickListener(v->{limit[0]+=60;search.run();});
        find.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){limit[0]=60;search.run();}public void afterTextChanged(Editable e){}});search.run();
        selectionListener=repaint;page.setOnDismissListener(d->{selectionListener=null;refresh();updateState();});page.setContentView(frame);page.show();
    }
    Runnable selectionListener;
    LinearLayout group(String title,View content){LinearLayout g=column();SmartisanStyle.card(g);TextView t=text(title,16,INK);t.setGravity(Gravity.CENTER);t.setPadding(0,dp(20),0,dp(10));g.addView(t,new LinearLayout.LayoutParams(-1,-2));g.addView(content,new LinearLayout.LayoutParams(-1,-2));g.setPadding(0,0,0,dp(8));return g;}
    LinearLayout.LayoutParams groupParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(14);return p;}
    String versionOf(String pkg){try{String v=getPackageManager().getPackageInfo(pkg,0).versionName;return v==null?pkg:v;}catch(Exception e){return pkg;}}

    void applySelection(){
        if(busy||!CHANGING.compareAndSet(false,true))return;busy=true;apply.setEnabled(false);status.setText("正在准备所选图标，请稍等…");final String selectedBase=base;final int selectedScale=iconScale;final JSONObject chosen=cloneJson(overrides);
        new Thread(()->{String error=null;try{
            File candidate=engine.compose(selectedBase,chosen,repo,selectedScale);runOnUiThread(()->status.setText("正在应用并核对桌面图标…"));
            engine.apply(candidate,engine.configuration(chosen),selectedBase+" "+chosen.toString());
            String hash=engine.liveHash();prefs.edit().putString("appliedBase",selectedBase).putString("appliedOverrides",chosen.toString()).putString("appliedHash",hash).putInt("appliedScale",selectedScale).commit();
            verifySelections();
        }catch(Exception e){error=e.getMessage();android.util.Log.e("ZuiIconHelper","apply",e);}finally{engine.saveReport();CHANGING.set(false);}
        final String failure=error;runOnUiThread(()->{busy=false;if(failure==null){appliedBase=selectedBase;appliedScale=selectedScale;appliedOverrides=chosen;updateState();status.setText("已应用并校验 · "+repo.label(base)+" 默认 · "+chosen.length()+" 个应用单独指定");}else{updateState();status.setText("未完成："+failure);}new Handler().postDelayed(()->savePreview(),1500);});
        },"apply-icons").start();
    }
    void verifySelections() throws Exception {
        JSONObject state=new JSONObject();state.put("base",prefs.getString("appliedBase",""));state.put("overrides",new JSONObject(prefs.getString("appliedOverrides","{}")));state.put("systemHash",engine.liveHash());
        try(FileOutputStream out=openFileOutput("selection-state.json",0)){out.write(state.toString(2).getBytes("UTF-8"));}
    }
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused&&root!=null)new Handler().postDelayed(()->savePreview(),1600);}
    void savePreview(){captureWindow(getWindow(),"editor-preview.png");}
    void captureWindow(Window window,String name){try{View view=window.getDecorView();if(view.getWidth()==0||view.getHeight()==0)return;Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);PixelCopy.request(window,bitmap,result->{if(result==PixelCopy.SUCCESS){new Thread(()->{try(FileOutputStream out=openFileOutput(name,0)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}catch(Exception e){android.util.Log.w("ZuiIconHelper","capture",e);}finally{bitmap.recycle();}}).start();}else bitmap.recycle();},new Handler(Looper.getMainLooper()));}catch(Exception e){android.util.Log.w("ZuiIconHelper","capture",e);}}

    File importSource(){return new File(getFilesDir(),"import-source");}
    void clearImport(){prefs.edit().remove("importPackage").commit();importSource().delete();}
    void pickImage(IconRepository.App app){
        if(busy)return;prefs.edit().putString("importPackage",app.pkg).commit();
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{startActivityForResult(intent,PICK_ICON);}catch(ActivityNotFoundException e){clearImport();new PanelDialog.Builder(this).setMessage("未找到系统文件选择器，请先启用文件应用。").setPositiveButton("知道了",null).show();}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=PICK_ICON)return;
        if(result!=RESULT_OK||data==null||data.getData()==null){clearImport();return;}
        pendingResult=data.getData();if(repo!=null&&!busy)resumeImport();
    }
    void resumeImport(){
        String pkg=prefs.getString("importPackage","");IconRepository.App app=repo.findApp(pkg);
        if(pkg.isEmpty())return;if(app==null){clearImport();status.setText("原应用已卸载，请为其他应用重新选择图片。");return;}
        final android.net.Uri uri=pendingResult;pendingResult=null;if(uri==null&&!importSource().exists())return;
        busy=true;apply.setEnabled(false);status.setText("正在读取图片…");
        new Thread(()->{Bitmap bitmap=null;String error=null;
            try{if(uri!=null)CustomIcons.copySource(this,uri,importSource());bitmap=CustomIcons.decode(importSource());}
            catch(Exception e){error="无法读取这张图片，请选择有效的 PNG、JPEG 或 WebP。";android.util.Log.w("ZuiIconHelper","image import",e);clearImport();}
            final Bitmap decoded=bitmap;final String failure=error;runOnUiThread(()->{busy=false;updateState();if(isFinishing()||isDestroyed()){if(decoded!=null)decoded.recycle();return;}if(failure!=null){new PanelDialog.Builder(this).setTitle("图片未导入").setMessage(failure).setPositiveButton("知道了",null).show();}else showCrop(app,decoded);});
        },"import-image").start();
    }
    void showCrop(IconRepository.App app,Bitmap bitmap){
        LinearLayout body=column();body.setPadding(dp(20),dp(16),dp(20),dp(12));
        TextView help=text("拖动调整位置；棋盘格是透明区域，不会出现在图标里。",13,MUTED);help.setGravity(Gravity.CENTER);body.addView(help,new LinearLayout.LayoutParams(-1,-2));
        IconCropView crop=new IconCropView(this,bitmap);FrameLayout frame=new FrameLayout(this);int side=Math.min(dp(300),getResources().getDisplayMetrics().heightPixels/3);FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(side,side,Gravity.CENTER);frame.addView(crop,cp);LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,side);fp.topMargin=dp(14);body.addView(frame,fp);
        SeekBar zoom=new SmartisanSeekBar(this);zoom.setMax(250);zoom.setProgress(50);zoom.setContentDescription("图标缩放比例");
        LinearLayout modes=new LinearLayout(this);modes.setBaselineAligned(false);modes.setGravity(Gravity.CENTER_VERTICAL);LinearLayout segments=SmartisanStyle.segmented(this,new String[]{"适应","填满"},0,n->{crop.reset(n==1);zoom.setProgress(50);});modes.addView(segments,new LinearLayout.LayoutParams(0,dp(38),2));
        Button rotate=button("旋转 90°");rotate.setTextSize(13);rotate.setOnClickListener(v->crop.rotate());LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,dp(38),1);rp.leftMargin=dp(12);modes.addView(rotate,rp);LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,-2);mp.topMargin=dp(16);body.addView(modes,mp);
        LinearLayout zoomRow=new LinearLayout(this);zoomRow.setGravity(Gravity.CENTER_VERTICAL);zoomRow.addView(text("缩放",13,MUTED));LinearLayout.LayoutParams zp=new LinearLayout.LayoutParams(0,dp(44),1);zp.leftMargin=dp(6);zoomRow.addView(zoom,zp);LinearLayout.LayoutParams zr=new LinearLayout.LayoutParams(-1,-2);zr.topMargin=dp(8);body.addView(zoomRow,zr);
        zoom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int p,boolean from){crop.zoom(.5f+p/100f);}});
        TextView note=text("保存后会出现在「我的图片」里；点「应用选择」后才会换到桌面。",12,MUTED);note.setGravity(Gravity.CENTER);note.setPadding(0,dp(8),0,0);body.addView(note,new LinearLayout.LayoutParams(-1,-2));
        ScrollView scroller=new ScrollView(this);scroller.addView(body);
        PanelDialog dialog=new PanelDialog.Builder(this).setTitle("为「"+app.label+"」导入图标").setView(scroller).setNegativeButton("取消",(d,w)->clearImport()).setPositiveButton("使用图片",null).create();
        dialog.setOnCancelListener(d->clearImport());dialog.setOnDismissListener(d->bitmap.recycle());dialog.show();new Handler().postDelayed(()->captureWindow(dialog.getWindow(),"dialog-preview.png"),1500);dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(32),dp(520)),-2);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            Bitmap output=crop.export();busy=true;apply.setEnabled(false);status.setText("正在保存自定义图标…");dialog.dismiss();
            new Thread(()->{String choice=null,error=null;try{choice=repo.custom.save(output,app.label);}catch(Exception e){error=e.getMessage();}finally{output.recycle();}
                final String saved=choice,failure=error;runOnUiThread(()->{busy=false;if(saved!=null){try{repo.reloadCustom();select(app,saved);clearImport();status.setText("图片已选中，点击「应用选择」后生效。");detail(app);}catch(Exception e){status.setText("图片已保存，请重新打开助手选择。");}}else{updateState();status.setText("保存失败："+failure);}});
            },"save-icon").start();
        });
    }

    void chooseScale(){
        if(busy)return;final int[] chosen={iconScale};LinearLayout body=column();body.setPadding(dp(20),dp(18),dp(20),dp(16));
        // Live preview: a few of the user's own icons inside launcher-like cells.
        LinearLayout cells=new LinearLayout(this);cells.setGravity(Gravity.CENTER);List<ImageView> views=new ArrayList<>();List<Bitmap> sources=new ArrayList<>();
        for(int i=0;i<5;i++){FrameLayout cell=new FrameLayout(this);android.graphics.drawable.GradientDrawable frame=SmartisanStyle.surface(this,Color.TRANSPARENT,10,0);frame.setStroke(Math.max(1,dp(1)),0xffcfd8ee,dp(4),dp(3));cell.setBackground(frame);ImageView v=new ImageView(this);v.setScaleType(ImageView.ScaleType.FIT_CENTER);cell.addView(v,new FrameLayout.LayoutParams(dp(58),dp(58),Gravity.CENTER));views.add(v);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(66),dp(66));cp.setMargins(dp(5),0,dp(5),0);cells.addView(cell,cp);}
        body.addView(cells,new LinearLayout.LayoutParams(-1,-2));
        TextView value=text(chosen[0]+"%",22,INK);value.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));value.setGravity(Gravity.CENTER);value.setPadding(0,dp(16),0,dp(4));body.addView(value,new LinearLayout.LayoutParams(-1,-2));
        Runnable paint=()->{value.setText(chosen[0]+"%");for(int i=0;i<views.size()&&i<sources.size();i++)views.get(i).setImageBitmap(IconScale.bitmap(sources.get(i),chosen[0]));};
        SmartisanSeekBar slider=new SmartisanSeekBar(this);slider.setMax((IconScale.MAX-IconScale.MIN)/5);slider.setProgress((chosen[0]-IconScale.MIN)/5);slider.setContentDescription("图标大小");
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int progress,boolean user){chosen[0]=IconScale.MIN+progress*5;paint.run();}public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}});
        LinearLayout sliderRow=new LinearLayout(this);sliderRow.setGravity(Gravity.CENTER_VERTICAL);sliderRow.setBaselineAligned(false);sliderRow.addView(text(IconScale.MIN+"%",12,MUTED));sliderRow.addView(slider,new LinearLayout.LayoutParams(0,dp(44),1));sliderRow.addView(text(IconScale.MAX+"%",12,MUTED));body.addView(sliderRow,new LinearLayout.LayoutParams(-1,-2));
        TextView reset=textButton("恢复 100%","把图标大小恢复为 100%",()->slider.setProgress((100-IconScale.MIN)/5));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-2,dp(34));rp.gravity=Gravity.CENTER_HORIZONTAL;body.addView(reset,rp);
        TextView hint=text("只改变替换后图标的画面大小，桌面格子和文字不变。放大时超出边框的部分会被裁掉。",12,MUTED);hint.setGravity(Gravity.CENTER);hint.setLineSpacing(dp(3),1);hint.setPadding(0,dp(10),0,0);body.addView(hint,new LinearLayout.LayoutParams(-1,-2));
        new PanelDialog.Builder(this).setTitle("图标大小").setView(body).setNegativeButton("取消",null).setPositiveButton("确定",(d,w)->{iconScale=chosen[0];persistDraft();refresh();updateState();if(iconScale!=appliedScale)status.setText("图标大小改为 "+iconScale+"% · 点「应用选择」后生效");}).show();
        new Thread(()->{List<Bitmap> loaded=new ArrayList<>();for(IconRepository.App a:repo.apps){if(loaded.size()>=5)break;try{loaded.add(repo.bitmap(effective(a),a));}catch(Exception ignored){}}runOnUiThread(()->{sources.addAll(loaded);paint.run();});},"scale-preview").start();
    }
}
