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
    static final int PICK_ICON=601;
    android.net.Uri pendingResult;
    boolean busy=true, binding; TextView status, subtitle, appCount, packDetail; Button apply; TextView defaults; AppAdapter adapter; ListView list; LinearLayout root;
    List<IconRepository.App> filtered=new ArrayList<>();
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
        LinearLayout content=column();content.setPadding(dp(16),dp(18),dp(16),dp(12));content.addView(SmartisanStyle.caption(this,"默认方案"));
        LinearLayout group=column();SmartisanStyle.card(group);
        LinearLayout defaultRow=listRow("setting","默认图标包","选择默认图标包",()->chooseDefault());defaults=(TextView)defaultRow.getTag();group.addView(defaultRow,new LinearLayout.LayoutParams(-1,dp(56)));
        View line=SmartisanStyle.divider(this,SmartisanStyle.DIVIDER);((LinearLayout.LayoutParams)line.getLayoutParams()).leftMargin=dp(50);group.addView(line);
        LinearLayout packRow=listRow("search","图标包","导入或管理图标包",()->managePacks());packDetail=(TextView)packRow.getTag();group.addView(packRow,new LinearLayout.LayoutParams(-1,dp(56)));
        content.addView(group,new LinearLayout.LayoutParams(-1,-2));
        subtitle=text("新安装的应用自动用默认包；单独选过的应用不受影响。",12,MUTED);subtitle.setPadding(dp(16),dp(8),dp(8),dp(18));content.addView(subtitle);
        EditText search=new EditText(this);search.setSingleLine(true);search.setHint("搜索应用名称或包名");content.addView(searchField(search),new LinearLayout.LayoutParams(-1,dp(38)));
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){query=s.toString().toLowerCase(Locale.ROOT);if(repo!=null)filter();}public void afterTextChanged(Editable e){}});
        LinearLayout sectionRow=new LinearLayout(this);sectionRow.setPadding(0,dp(20),dp(16),0);sectionRow.addView(SmartisanStyle.caption(this,"应用图标"),new LinearLayout.LayoutParams(0,-2,1));appCount=text("",12,MUTED);sectionRow.addView(appCount);content.addView(sectionRow);
        FrameLayout listCard=new FrameLayout(this);SmartisanStyle.card(listCard);list=new ListView(this);list.setDivider(new android.graphics.drawable.InsetDrawable(new android.graphics.drawable.ColorDrawable(SmartisanStyle.DIVIDER),dp(16),0,0,0));list.setDividerHeight(Math.max(1,dp(.8f)));list.setSelector(android.R.color.transparent);list.setVerticalScrollBarEnabled(false);adapter=new AppAdapter();list.setAdapter(adapter);listCard.addView(list,new FrameLayout.LayoutParams(-1,-1));content.addView(listCard,new LinearLayout.LayoutParams(-1,0,1));
        root.addView(new SmartisanStyle.Centered(this,content,-1),new LinearLayout.LayoutParams(-1,0,1));root.addView(SmartisanStyle.divider(this,SmartisanStyle.BAR_LINE));
        LinearLayout bottom=column();bottom.setPadding(dp(16),dp(10),dp(16),dp(14));status=text("正在整理图标预览…",12,MUTED);status.setGravity(Gravity.CENTER);status.setPadding(0,0,0,dp(10));bottom.addView(status);
        LinearLayout footer=new LinearLayout(this);footer.setBaselineAligned(false);Button undo=button("撤销修改");undo.setOnClickListener(v->{if(busy)return;base=appliedBase;overrides=cloneJson(appliedOverrides);persistDraft();updateDefault();adapter.notifyDataSetChanged();updateState();});footer.addView(undo,new LinearLayout.LayoutParams(0,dp(48),1));
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
    void updateDefault(){defaults.setText(repo==null?"正在读取":repo.label(base));if(packDetail!=null)packDetail.setText(repo==null?"正在读取":repo.packs.isEmpty()?"去导入":repo.packs.size()+" 个");}
    void chooseDefault(){if(busy)return;LinearLayout choices=column();PanelDialog dialog=new PanelDialog.Builder(this).setTitle("默认图标包").setView(choices).setNegativeButton("取消",null).create();for(String pack:repo.bases()){LinearLayout row=new LinearLayout(this);row.setPadding(dp(20),0,dp(20),0);row.setGravity(Gravity.CENTER_VERTICAL);row.addView(text(repo.label(pack),16,INK),new LinearLayout.LayoutParams(0,-2,1));row.addView(SmartisanStyle.icon(this,base.equals(pack)?"selected":"unselected",22));row.setContentDescription(repo.label(pack)+(base.equals(pack)?" · 已选":""));row.setOnClickListener(v->{base=pack;persistDraft();updateDefault();adapter.notifyDataSetChanged();updateState();dialog.dismiss();});choices.addView(row,new LinearLayout.LayoutParams(-1,dp(54)));choices.addView(SmartisanStyle.divider(this,SmartisanStyle.DIVIDER));}dialog.show();}
    void menu(){if(busy)return;new PanelDialog.Builder(this).setTitle("设置").setItems(new String[]{"全部应用重新跟随默认包","恢复系统原始图标","使用说明","关于与开源许可"},(d,n)->{
        if(n==2){new PanelDialog.Builder(this).setTitle("使用说明").setMessage("1. 先导入图标包：点「图标包」，选平板上已经安装的图标包 App。\n\n2. 在「默认图标包」里选一个，所有它收录的应用都会换成这一套。以后新装的应用也会自动匹配。\n\n3. 想给某个应用换别的样子：在它那一行点选一个预览（浅蓝底加蓝色小勾就是选中），或者点「更多图标」从所有图标包里挑，也可以导入自己的图片。\n\n4. 点底部的「应用选择」，桌面图标才会真正改变。\n\n随时可以在这里选「恢复系统原始图标」回到出厂的样子。壁纸和桌面布局都不会被改动。\n\n如果在系统设置里换了主题，请回到助手重新点一次「应用选择」。").setPositiveButton("知道了",null).show();return;}
        if(n==3){new PanelDialog.Builder(this).setTitle("关于").setMessage("ZUI 图标助手 "+versionName()+"\n给联想 ZUI 平板的原厂桌面换图标，不需要 root。\n\n开源地址：github.com/lemonteaau/zui-icon-helper\n\n界面风格参考 Smartisan OS UI Kit (Unofficial)，作者 Paix Chan，CC BY 4.0。\n\n助手本身不带任何第三方图标。导入的图标包只在你的平板上转换使用，版权归原作者所有。").setPositiveButton("知道了",null).show();return;}
        new PanelDialog.Builder(this).setTitle(n==0?"清除所有单独选择？":"恢复系统原始图标？").setMessage(n==0?"所有应用将重新跟随默认包，点「应用选择」后生效。":"将清除单独选择，并恢复主题自带的原始图标。壁纸和布局不变。").setNegativeButton("取消",null).setPositiveButton(n==0?"清除选择":"恢复",(x,w)->{overrides=new JSONObject();if(n==1)base="original";persistDraft();binding=true;updateDefault();binding=false;adapter.notifyDataSetChanged();updateState();if(n==1)applySelection();}).show();
    }).show();}
    String versionName(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception e){return "";}}

    JSONObject cloneJson(JSONObject o){try{return new JSONObject(o.toString());}catch(Exception e){throw new IllegalStateException(e);}}
    String signature(String b,JSONObject o){TreeMap<String,String> m=new TreeMap<>();Iterator<String> keys=o.keys();while(keys.hasNext()){String k=keys.next();m.put(k,o.optString(k));}return b+m.toString();}
    void persistDraft(){prefs.edit().putString("draftBase",base).putString("draftOverrides",overrides.toString()).apply();}
    void updateState(){if(appCount!=null&&repo!=null)appCount.setText(filtered.size()+" 个应用");boolean dirty=!signature(base,overrides).equals(signature(appliedBase,appliedOverrides));status.setText((dirty?"有未应用的修改":"选择已保存")+" · "+overrides.length()+" 个应用单独指定");apply.setEnabled(!busy);apply.setText(dirty?"应用选择":"重新应用");}
    void filter(){filtered.clear();for(IconRepository.App a:repo.apps)if((a.label+" "+a.pkg).toLowerCase(Locale.ROOT).contains(query))filtered.add(a);adapter.notifyDataSetChanged();}
    String effective(IconRepository.App a){return overrides.optString(a.pkg,repo.defaultChoice(a.pkg,base));}
    void select(IconRepository.App a,String choice){if(busy)return;try{overrides.put(a.pkg,choice);}catch(Exception e){throw new IllegalStateException(e);}persistDraft();adapter.notifyDataSetChanged();updateState();}
    String choiceLabel(String c){if("native".equals(c))return "原生";IconRepository.Icon i=repo.icons.get(c);return i==null?"未知":repo.label(i.pack);}
    void load(ImageView view,String choice,IconRepository.App app){
        String key=choice+("native".equals(choice)?":"+app.pkg:"");view.setTag(key);Bitmap b=cache.get(key);if(b!=null){view.setImageBitmap(b);return;}view.setImageDrawable(null);
        images.execute(()->{try{Bitmap bitmap=repo.bitmap(choice,app);cache.put(key,bitmap);runOnUiThread(()->{if(key.equals(view.getTag()))view.setImageBitmap(bitmap);});}catch(Exception e){android.util.Log.w("ZuiIconHelper","preview "+key,e);runOnUiThread(()->{if(key.equals(view.getTag()))view.setContentDescription("预览加载失败");});}});
    }
    View tile(IconRepository.App a,String choice,String label,boolean selected,Runnable click){
        FrameLayout cell=new FrameLayout(this);cell.setBackground(SmartisanStyle.tile(this,selected));cell.setContentDescription(a.label+" · "+label+(selected?" · 已选":""));cell.setOnClickListener(v->click.run());
        LinearLayout inner=column();inner.setGravity(Gravity.CENTER_HORIZONTAL);inner.setPadding(dp(4),dp(9),dp(4),dp(6));ImageView icon=new ImageView(this);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);inner.addView(icon,new LinearLayout.LayoutParams(dp(48),dp(48)));load(icon,choice,a);
        TextView name=text(label,11,selected?GREEN:MUTED);if(selected)name.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);name.setGravity(Gravity.CENTER);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);np.topMargin=dp(7);inner.addView(name,np);cell.addView(inner,new FrameLayout.LayoutParams(-1,-1));
        if(selected){ImageView mark=SmartisanStyle.icon(this,"selected",16);FrameLayout.LayoutParams mp=new FrameLayout.LayoutParams(dp(16),dp(16),Gravity.TOP|Gravity.END);mp.topMargin=dp(5);mp.rightMargin=dp(5);cell.addView(mark,mp);}
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(80),dp(90));cp.rightMargin=dp(4);cell.setLayoutParams(cp);return cell;
    }
    class AppAdapter extends BaseAdapter {
        public int getCount(){return filtered.size();}public Object getItem(int n){return filtered.get(n);}public long getItemId(int n){return n;}
        public View getView(int n,View old,android.view.ViewGroup parent){
            IconRepository.App a=filtered.get(n);String selected=effective(a);boolean own=overrides.has(a.pkg);LinearLayout row=column();row.setPadding(dp(10),dp(12),dp(8),dp(10));
            LinearLayout heading=new LinearLayout(MainActivity.this);heading.setGravity(Gravity.CENTER_VERTICAL);heading.setPadding(dp(6),0,0,0);LinearLayout names=column();TextView name=text(a.label,15,INK);name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);names.addView(name);
            TextView source=text((own?"单独选择 · ":"跟随默认 · ")+choiceLabel(selected),12,own?GREEN:MUTED);source.setPadding(0,dp(5),0,0);source.setSingleLine(true);source.setEllipsize(TextUtils.TruncateAt.END);names.addView(source);heading.addView(names,new LinearLayout.LayoutParams(0,-2,1));
            if(own)heading.addView(textButton("跟随默认","让"+a.label+"跟随默认包",()->{if(busy)return;overrides.remove(a.pkg);persistDraft();notifyDataSetChanged();updateState();}),new LinearLayout.LayoutParams(-2,dp(36)));
            heading.addView(detailButton("更多图标","为"+a.label+"选择更多图标或导入图片",()->{if(!busy)library(a);}),new LinearLayout.LayoutParams(-2,dp(36)));row.addView(heading);
            HorizontalScrollView scroll=new HorizontalScrollView(MainActivity.this);scroll.setHorizontalScrollBarEnabled(false);scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);LinearLayout options=new LinearLayout(MainActivity.this);options.setPadding(0,dp(8),dp(4),0);
            options.addView(tile(a,"native","原生",selected.equals("native"),()->select(a,"native")));Set<String> added=new HashSet<>();added.add("native");
            for(String pack:repo.packs){List<String> choices=repo.variants(a.pkg,pack);for(int i=0;i<choices.size();i++){String c=choices.get(i);if(added.add(c))options.addView(tile(a,c,repo.label(pack)+(choices.size()>1?" "+(i+1):""),selected.equals(c),()->select(a,c)));}}
            if(!added.contains(selected))options.addView(tile(a,selected,choiceLabel(selected),true,()->select(a,selected)));
            scroll.addView(options);row.addView(scroll);return row;
        }
    }
    void library(IconRepository.App app){
        LinearLayout body=column();body.setPadding(dp(16),dp(14),dp(16),dp(8));
        Button importButton=button("＋  从相册或文件导入图片");importButton.setContentDescription("为"+app.label+"导入自定义图片");body.addView(importButton,new LinearLayout.LayoutParams(-1,dp(46)));
        final List<String> packs=new ArrayList<>();packs.add("all");packs.addAll(repo.packs);packs.add("custom");final int[] pack={0};String[] packLabels=new String[packs.size()];for(int i=0;i<packs.size();i++)packLabels[i]=i==0?"全部":repo.label(packs.get(i));List<IconRepository.Icon> found=new ArrayList<>();Runnable[] refresh=new Runnable[1];
        LinearLayout segments=SmartisanStyle.segmented(this,packLabels,0,n->{pack[0]=n;refresh[0].run();});LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(38));sp.topMargin=dp(14);body.addView(segments,sp);
        EditText find=new EditText(this);find.setSingleLine(true);find.setHint("搜索图标名称、英文名或包名");LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,dp(38));fp.topMargin=dp(12);body.addView(searchField(find),fp);
        TextView count=text("",12,MUTED);count.setPadding(dp(4),dp(12),0,dp(8));body.addView(count);
        GridView grid=new GridView(this);grid.setNumColumns(GridView.AUTO_FIT);grid.setColumnWidth(dp(88));grid.setVerticalSpacing(dp(4));grid.setHorizontalSpacing(dp(4));grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);grid.setSelector(android.R.color.transparent);grid.setVerticalScrollBarEnabled(false);body.addView(grid,new LinearLayout.LayoutParams(-1,Math.min(dp(400),getResources().getDisplayMetrics().heightPixels*42/100)));
        PanelDialog dialog=new PanelDialog.Builder(this).setTitle("为「"+app.label+"」挑选图标").setView(body).setNegativeButton("取消",null).create();
        BaseAdapter libraryAdapter=new BaseAdapter(){public int getCount(){return found.size();}public Object getItem(int n){return found.get(n);}public long getItemId(int n){return n;}
            public View getView(int n,View old,android.view.ViewGroup parent){IconRepository.Icon ic=found.get(n);boolean chosen=ic.choice.equals(effective(app));FrameLayout cell=new FrameLayout(MainActivity.this);cell.setBackground(SmartisanStyle.tile(MainActivity.this,chosen));
                LinearLayout inner=column();inner.setGravity(Gravity.CENTER_HORIZONTAL);inner.setPadding(dp(4),dp(9),dp(4),dp(4));ImageView image=new ImageView(MainActivity.this);inner.addView(image,new LinearLayout.LayoutParams(dp(50),dp(50)));load(image,ic.choice,app);
                TextView name=text(ic.name,11,chosen?GREEN:INK);name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);name.setGravity(Gravity.CENTER);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);np.topMargin=dp(6);inner.addView(name,np);TextView source=text(repo.label(ic.pack),10,MUTED);source.setGravity(Gravity.CENTER);source.setPadding(0,dp(3),0,0);inner.addView(source,new LinearLayout.LayoutParams(-1,-2));cell.addView(inner);
                if(chosen){ImageView mark=SmartisanStyle.icon(MainActivity.this,"selected",16);FrameLayout.LayoutParams mp=new FrameLayout.LayoutParams(dp(16),dp(16),Gravity.TOP|Gravity.END);mp.topMargin=dp(5);mp.rightMargin=dp(5);cell.addView(mark,mp);}
                cell.setContentDescription(ic.name+" · "+repo.label(ic.pack)+(chosen?" · 已选":""));cell.setOnClickListener(v->{select(app,ic.choice);dialog.dismiss();});cell.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1,dp(106)));return cell;}};
        importButton.setOnClickListener(v->{dialog.dismiss();pickImage(app);});grid.setAdapter(libraryAdapter);
        refresh[0]=()->{String q=find.getText().toString().trim().toLowerCase(Locale.ROOT);found.clear();for(IconRepository.Icon ic:repo.icons.values())if((pack[0]==0||packs.get(pack[0]).equals(ic.pack))&&ic.search.contains(q))found.add(ic);count.setText(found.isEmpty()&&"custom".equals(packs.get(pack[0]))?"还没有导入过图片，点上面的按钮导入。":found.size()+" 个图标 · 点选后回到应用列表");libraryAdapter.notifyDataSetChanged();grid.setSelection(0);};
        find.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){refresh[0].run();}public void afterTextChanged(Editable e){}});
        refresh[0].run();dialog.show();dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(32),dp(720)),-2);new Handler().postDelayed(()->captureWindow(dialog.getWindow(),"dialog-preview.png"),1500);
    }

    void applySelection(){
        if(busy||!CHANGING.compareAndSet(false,true))return;busy=true;apply.setEnabled(false);status.setText("正在准备所选图标，请稍等…");final String selectedBase=base;final JSONObject chosen=cloneJson(overrides);
        new Thread(()->{String error=null;try{
            File candidate=engine.compose(selectedBase,chosen,repo);runOnUiThread(()->status.setText("正在应用并核对桌面图标…"));
            engine.apply(candidate,engine.configuration(chosen),selectedBase+" "+chosen.toString());
            String hash=engine.liveHash();prefs.edit().putString("appliedBase",selectedBase).putString("appliedOverrides",chosen.toString()).putString("appliedHash",hash).commit();
            verifySelections();
        }catch(Exception e){error=e.getMessage();android.util.Log.e("ZuiIconHelper","apply",e);}finally{engine.saveReport();CHANGING.set(false);}
        final String failure=error;runOnUiThread(()->{busy=false;if(failure==null){appliedBase=selectedBase;appliedOverrides=chosen;updateState();status.setText("已应用并校验 · "+repo.label(base)+" 默认 · "+chosen.length()+" 个应用单独指定");}else{updateState();status.setText("未完成："+failure);}new Handler().postDelayed(()->savePreview(),1500);});
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
                final String saved=choice,failure=error;runOnUiThread(()->{busy=false;if(saved!=null){try{repo.reloadCustom();select(app,saved);clearImport();status.setText("图片已选中，点击「应用选择」后生效。");}catch(Exception e){status.setText("图片已保存，请重新打开助手选择。");}}else{updateState();status.setText("保存失败："+failure);}});
            },"save-icon").start();
        });
    }
}
