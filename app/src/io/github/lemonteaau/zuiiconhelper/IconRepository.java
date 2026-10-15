package io.github.lemonteaau.zuiiconhelper;

import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Installed apps plus every icon the user can choose from: imported packs, "my images" and each app's own icon. */
final class IconRepository {
    final Context context;
    final CustomIcons custom;
    final ThemeEngine engine;
    final List<String> packs = new ArrayList<>();
    final Map<String, JSONObject> catalogs = new HashMap<>();
    final Map<String, Icon> icons = Collections.synchronizedMap(new LinkedHashMap<>());
    final Map<String, ZipFile> zips = new HashMap<>();
    final List<App> apps = new ArrayList<>();
    static class App { String pkg, label; ActivityInfo info; }
    static class Icon { String choice, pack, id, name, path, search; }

    IconRepository(Context c, ThemeEngine e) throws Exception {
        context = c; engine = e; custom = new CustomIcons(c);
        File[] dirs = new File(c.getFilesDir(), "packs").listFiles();
        if (dirs != null) for (File dir : dirs) {
            File json = new File(dir, "catalog.json"); if (!dir.isDirectory() || dir.getName().endsWith(".tmp") || !json.isFile()) continue;
            JSONObject catalog = new JSONObject(new String(ThemeEngine.read(new FileInputStream(json)), "UTF-8"));
            String pack = dir.getName(); packs.add(pack); catalogs.put(pack, catalog);
            JSONArray list = catalog.getJSONArray("icons");
            for (int i = 0; i < list.length(); i++) {
                JSONObject j = list.getJSONObject(i); Icon icon = new Icon();
                icon.pack = pack; icon.id = j.getString("id"); icon.choice = pack + "/" + icon.id; icon.name = readable(j.getString("name")); icon.path = j.getString("path");
                icon.search = (icon.name + " " + icon.id).toLowerCase(Locale.ROOT); icons.put(icon.choice, icon);
            }
            JSONObject defaults = catalog.getJSONObject("default"); Iterator<String> keys = defaults.keys();
            while (keys.hasNext()) { String pkg = keys.next(); Icon icon = icons.get(pack + "/" + defaults.getString(pkg)); if (icon != null) icon.search += " " + pkg; }
        }
        Collections.sort(packs, (a, b) -> label(a).compareTo(label(b)));
        reloadCustom();
        PackageManager pm = c.getPackageManager(); Set<String> seen = new HashSet<>();
        for (ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
            ActivityInfo ai = r.activityInfo; if (ai.packageName.equals(c.getPackageName()) || !seen.add(ai.packageName)) continue;
            App a = new App(); a.pkg = ai.packageName; a.label = r.loadLabel(pm).toString(); a.info = ai; apps.add(a);
            for (String pack : packs) for (String choice : variants(a.pkg, pack)) { Icon ic = icons.get(choice); if (ic != null) { ic.search += " " + a.label.toLowerCase(Locale.ROOT); if (ic.name.equals(readable(ic.id))) ic.name = a.label; } }
        }
        final java.text.Collator collator = java.text.Collator.getInstance(Locale.CHINA);
        Collections.sort(apps, (a, b) -> collator.compare(a.label, b.label));
    }
    /** Icon packs name files like "icon_weather" or "ic_weather"; show "weather". */
    static String readable(String id) { return id.replace('_', ' ').replaceFirst("^(?i)(icon|ic) ", ""); }
    /** Default-pack choices shown to the user: every imported pack, then the theme's original icons. */
    List<String> bases() { List<String> l = new ArrayList<>(packs); l.add("original"); return l; }
    boolean hasPack(String pack) { return "original".equals(pack) || catalogs.containsKey(pack); }
    String label(String pack) {
        if ("custom".equals(pack)) return "我的图片"; if ("original".equals(pack)) return "系统原始";
        JSONObject c = catalogs.get(pack); return c == null ? "已删除的图标包" : c.optString("label");
    }
    String defaultChoice(String pkg, String pack) {
        JSONObject c = catalogs.get(pack); if (c == null) return "native";
        String id = c.optJSONObject("default").optString(pkg.toLowerCase(Locale.ROOT), "");
        return id.isEmpty() ? "native" : pack + "/" + id;
    }
    List<String> variants(String pkg, String pack) {
        List<String> result = new ArrayList<>(); JSONObject c = catalogs.get(pack); if (c == null) return result;
        JSONArray list = c.optJSONObject("variants").optJSONArray(pkg.toLowerCase(Locale.ROOT));
        if (list != null) for (int i = 0; i < list.length(); i++) { String choice = pack + "/" + list.optString(i); if (icons.containsKey(choice)) result.add(choice); }
        return result;
    }
    synchronized ZipFile zip(String pack) throws Exception {
        ZipFile z = zips.get(pack); if (z == null) { z = new ZipFile(engine.preparedAsset(pack)); zips.put(pack, z); } return z;
    }
    synchronized void close() { for (ZipFile z : zips.values()) try { z.close(); } catch (IOException ignored) {} zips.clear(); }
    byte[] bytes(String choice, App app) throws Exception {
        if (choice.startsWith("custom/")) return ThemeEngine.read(new FileInputStream(custom.resolve(choice)));
        if ("native".equals(choice)) {
            if (app == null) throw new IOException("应用已卸载，请重新打开助手后再试");
            android.content.res.Resources res = context.getPackageManager().getResourcesForApplication(app.pkg);
            Drawable drawable = res.getDrawable(app.info.getIconResource(), null);
            Bitmap b = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888); Canvas canvas = new Canvas(b);
            drawable.setBounds(0, 0, 256, 256); drawable.draw(canvas);
            ByteArrayOutputStream out = new ByteArrayOutputStream(); b.compress(Bitmap.CompressFormat.PNG, 100, out); b.recycle(); return out.toByteArray();
        }
        Icon icon = icons.get(choice); if (icon == null) throw new IOException("找不到所选图标，可能所在的图标包已被删除：" + choice);
        ZipFile z = zip(icon.pack); ZipEntry entry = z.getEntry(icon.path); if (entry == null) throw new IOException("图标文件缺失");
        return ThemeEngine.read(z.getInputStream(entry));
    }
    Bitmap bitmap(String choice, App app) throws Exception {
        byte[] data = bytes(choice, app); BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true; BitmapFactory.decodeByteArray(data, 0, data.length, o);
        o.inSampleSize = 1; while (o.outWidth / o.inSampleSize > 256 || o.outHeight / o.inSampleSize > 256) o.inSampleSize *= 2;
        o.inJustDecodeBounds = false; Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length, o); if (b == null) throw new IOException("图标图片无法读取"); return b;
    }
    void reloadCustom() throws Exception {
        JSONArray entries = custom.entries();
        for (int i = 0; i < entries.length(); i++) {
            JSONObject j = entries.getJSONObject(i); Icon icon = new Icon(); icon.id = j.getString("id"); icon.pack = "custom"; icon.choice = "custom/" + icon.id; icon.name = j.getString("name"); icon.search = icon.name.toLowerCase(Locale.ROOT); icons.put(icon.choice, icon);
        }
    }
    App findApp(String pkg) { for (App a : apps) if (a.pkg.equals(pkg)) return a; return null; }
}
