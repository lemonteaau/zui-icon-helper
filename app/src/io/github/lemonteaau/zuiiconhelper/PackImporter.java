package io.github.lemonteaau.zuiiconhelper;

import android.content.*;
import android.content.pm.*;
import android.content.res.Resources;
import android.graphics.*;
import android.graphics.drawable.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.json.*;
import org.xmlpull.v1.XmlPullParser;

/**
 * Imports an icon pack the user has installed (the common Nova / ADW / Apex / GO launcher format):
 * reads its appfilter.xml, renders each icon to PNG, and builds a theme zip on top of the
 * current theme's original icons. Nothing from the pack leaves the device.
 */
final class PackImporter {
    interface Progress { void update(int done, int total); }
    static final class Source { String pkg, label, version; boolean imported; }

    static final String[] PACK_ACTIONS = {
        "org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.teslacoilsw.launcher.THEME", "com.anddoes.launcher.THEME",
        "com.gau.go.launcherex.theme", "org.adw.launcher.icons.ACTION_PICK_ICON", "com.dlto.atom.launcher.THEME", "ch.deletescape.lawnchair.ICONPACK"};
    static final int SIZE = 256;

    final Context context; final ThemeEngine engine;
    PackImporter(Context c, ThemeEngine e) { context = c; engine = e; }

    static String packId(String pkg) { return "p_" + pkg.replaceAll("[^A-Za-z0-9]", "_"); }

    /** Installed apps that declare themselves as launcher icon packs. */
    List<Source> discover(Set<String> imported) {
        PackageManager pm = context.getPackageManager(); Map<String, Source> found = new TreeMap<>();
        for (String action : PACK_ACTIONS) for (ResolveInfo r : pm.queryIntentActivities(new Intent(action), PackageManager.GET_META_DATA)) {
            String pkg = r.activityInfo.packageName; if (pkg.equals(context.getPackageName()) || found.containsKey(pkg)) continue;
            Source s = new Source(); s.pkg = pkg; s.label = r.activityInfo.applicationInfo.loadLabel(pm).toString();
            try { s.version = pm.getPackageInfo(pkg, 0).versionName; } catch (Exception e) { s.version = ""; }
            s.imported = imported.contains(packId(pkg)); found.put(pkg, s);
        }
        List<Source> list = new ArrayList<>(found.values());
        final java.text.Collator collator = java.text.Collator.getInstance(Locale.CHINA); Collections.sort(list, (a, b) -> collator.compare(a.label, b.label));
        return list;
    }

    /** package (lower case) → drawable names, in appfilter order. */
    private LinkedHashMap<String, List<String>> appfilter(Context pack, Resources res, String pkg) throws Exception {
        XmlPullParser p; InputStream assets = null;
        int id = res.getIdentifier("appfilter", "xml", pkg);
        if (id != 0) p = res.getXml(id);
        else {
            try { assets = pack.getAssets().open("appfilter.xml"); } catch (IOException e) { throw new IOException("这个图标包没有 appfilter 映射表，无法导入"); }
            p = android.util.Xml.newPullParser(); p.setInput(assets, "UTF-8");
        }
        LinkedHashMap<String, List<String>> groups = new LinkedHashMap<>();
        try {
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                if (p.getEventType() != XmlPullParser.START_TAG || !"item".equals(p.getName())) continue;
                String component = p.getAttributeValue(null, "component"), drawable = p.getAttributeValue(null, "drawable");
                if (component == null || drawable == null) continue;
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("ComponentInfo\\{([^/{}]+)/[^{}]+\\}").matcher(component);
                if (!m.matches()) continue;
                String key = m.group(1).toLowerCase(Locale.ROOT); List<String> names = groups.get(key);
                if (names == null) { names = new ArrayList<>(); groups.put(key, names); }
                if (!names.contains(drawable)) names.add(drawable);
            }
        } finally { if (assets != null) assets.close(); }
        if (groups.isEmpty()) throw new IOException("图标包的映射表是空的");
        return groups;
    }

    private static byte[] render(Drawable d) throws IOException {
        Bitmap b;
        if (d instanceof BitmapDrawable && ((BitmapDrawable) d).getBitmap() != null) {
            Bitmap src = ((BitmapDrawable) d).getBitmap(); int w = src.getWidth(), h = src.getHeight();
            if (Math.max(w, h) > SIZE) { float s = SIZE / (float) Math.max(w, h); b = Bitmap.createScaledBitmap(src, Math.max(1, Math.round(w * s)), Math.max(1, Math.round(h * s)), true); }
            else b = src;
        } else {
            b = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888); d.setBounds(0, 0, SIZE, SIZE); d.draw(new Canvas(b));
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(); if (!b.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new IOException("图标转换失败");
        return out.toByteArray();
    }

    /** Builds packs/<id>/res.zip and catalog.json. Returns the pack id. */
    String importPack(String pkg, Progress progress) throws Exception {
        PackageManager pm = context.getPackageManager();
        Context pack = context.createPackageContext(pkg, 0); Resources res = pack.getResources();
        String label = pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString(), version = pm.getPackageInfo(pkg, 0).versionName;
        LinkedHashMap<String, List<String>> groups = appfilter(pack, res, pkg);

        // Render every drawable the map refers to; drop names the pack doesn't actually ship.
        String id = packId(pkg); File dir = engine.packDir(id), temp = new File(dir.getParentFile(), id + ".tmp"), rendered = new File(temp, "img");
        deleteTree(temp); if (!rendered.mkdirs()) throw new IOException("无法创建图标包目录");
        LinkedHashSet<String> names = new LinkedHashSet<>(); for (List<String> l : groups.values()) names.addAll(l);
        Map<String, File> images = new LinkedHashMap<>(); int done = 0, total = names.size();
        for (String name : names) {
            int rid = res.getIdentifier(name, "drawable", pkg); if (rid == 0) rid = res.getIdentifier(name, "mipmap", pkg);
            if (rid != 0 && name.matches("[A-Za-z0-9_]+")) try {
                File f = new File(rendered, name + ".png"); try (FileOutputStream out = new FileOutputStream(f)) { out.write(render(res.getDrawableForDensity(rid, android.util.DisplayMetrics.DENSITY_XXXHIGH, null))); } images.put(name, f);
            } catch (Exception | OutOfMemoryError e) { android.util.Log.w("ZuiIconHelper", "skip " + name, e); }
            if (++done % 20 == 0 || done == total) progress.update(done, total);
        }
        LinkedHashMap<String, String> defaults = new LinkedHashMap<>(); JSONObject variants = new JSONObject();
        for (Map.Entry<String, List<String>> e : groups.entrySet()) {
            JSONArray list = new JSONArray(); for (String n : e.getValue()) if (images.containsKey(n)) list.put(n);
            if (list.length() == 0) continue; defaults.put(e.getKey(), list.getString(0)); variants.put(e.getKey(), list);
        }
        if (defaults.isEmpty()) throw new IOException("没能从这个图标包里读出任何图标");

        File zipFile = new File(temp, "res.zip"); build(engine.original(), engine.forcedAliases(), defaults, images, zipFile);

        JSONObject catalog = new JSONObject(); catalog.put("label", label); catalog.put("source", pkg); catalog.put("version", version == null ? "" : version);
        catalog.put("sha256", ThemeEngine.hashFile(zipFile)); catalog.put("theme", engine.theme()); catalog.put("default", new JSONObject(defaults)); catalog.put("variants", variants);
        JSONArray icons = new JSONArray(); for (String n : images.keySet()) { JSONObject j = new JSONObject(); j.put("id", n); j.put("name", n.replace('_', ' ')); j.put("path", "res/drawable-xxhdpi/ipk_" + n + ".png"); icons.put(j); }
        catalog.put("icons", icons);
        try (FileOutputStream out = new FileOutputStream(new File(temp, "catalog.json"))) { out.write(catalog.toString().getBytes("UTF-8")); }
        deleteTree(rendered); deleteTree(dir); if (!temp.renameTo(dir)) throw new IOException("无法保存图标包");
        return id;
    }

    /** Original theme zip, with every mapped package pointed at the pack's icon. */
    static void build(File original, JSONObject forced, Map<String, String> defaults, Map<String, File> images, File output) throws Exception {
        Map<String, String> replaceFiles = new HashMap<>(); // file in original zip → drawable name
        StringWriter writer = new StringWriter(); org.xmlpull.v1.XmlSerializer xml = android.util.Xml.newSerializer(); xml.setOutput(writer); xml.startDocument("UTF-8", true); xml.startTag(null, "resources");
        try (ZipFile zip = new ZipFile(original)) {
            XmlPullParser p = android.util.Xml.newPullParser(); p.setInput(zip.getInputStream(zip.getEntry("res/icons.xml")), "UTF-8");
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                if (p.getEventType() != XmlPullParser.START_TAG || !"drawable".equals(p.getName())) continue;
                String name = p.getAttributeValue(null, "name"), value = p.nextText(); String key = name.contains("__") ? name.substring(0, name.indexOf("__")) : name;
                String pkg = key.replace('_', '.');
                if (defaults.containsKey(pkg)) { if (value.startsWith("@drawable/")) replaceFiles.put("res/drawable-xxhdpi/" + value.substring(10) + ".png", defaults.get(pkg)); continue; }
                xml.startTag(null, "drawable"); xml.attribute(null, "name", name); xml.text(value); xml.endTag(null, "drawable");
            }
            for (Map.Entry<String, String> e : defaults.entrySet()) {
                JSONArray aliases = forced.optJSONArray(e.getKey()); if (aliases != null) for (int i = 0; i < aliases.length(); i++) replaceFiles.put(aliases.getString(i), e.getValue());
                String key = e.getKey().replace('.', '_');
                String[] suffixes = "com.android.dialer".equals(e.getKey()) ? new String[]{"ic_launcher_phone", "ic_launcher", "ic_launcher_contacts", "dialer_launcher"} : new String[]{"icon"};
                for (String s : suffixes) { xml.startTag(null, "drawable"); xml.attribute(null, "name", key + "__" + s); xml.text("@drawable/ipk_" + e.getValue()); xml.endTag(null, "drawable"); }
            }
            xml.endTag(null, "resources"); xml.endDocument();
            try (ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(output)))) {
                out.setLevel(1); byte[] buffer = new byte[65536]; Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement(); if ("res/icons.xml".equals(entry.getName())) continue;
                    out.putNextEntry(new ZipEntry(entry.getName()));
                    String replacement = replaceFiles.get(entry.getName());
                    if (replacement != null && images.containsKey(replacement)) out.write(ThemeEngine.read(new FileInputStream(images.get(replacement))));
                    else try (InputStream in = zip.getInputStream(entry)) { int n; while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n); }
                    out.closeEntry();
                }
                for (Map.Entry<String, File> e : images.entrySet()) { out.putNextEntry(new ZipEntry("res/drawable-xxhdpi/ipk_" + e.getKey() + ".png")); ThemeEngine.copyNoClose(new FileInputStream(e.getValue()), out); out.closeEntry(); }
                out.putNextEntry(new ZipEntry("res/icons.xml")); out.write(writer.toString().getBytes("UTF-8")); out.closeEntry();
            }
        }
    }

    static void deleteTree(File f) { if (f == null || !f.exists()) return; File[] kids = f.listFiles(); if (kids != null) for (File k : kids) deleteTree(k); f.delete(); }
}
