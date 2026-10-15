package io.github.lemonteaau.zuiiconhelper;

import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import org.json.*;

/**
 * Talks to ZUI's theme service. ZUI keeps the current theme's icon resources in a zip
 * (res.zip) behind a content provider; writing a new zip there and asking the theme service
 * to reload the current theme swaps every launcher icon without root.
 *
 * Nothing device-specific is bundled: the pristine icons of the current theme are read from
 * the theme's own overlay APK on every run, so "restore original icons" always works.
 */
final class ThemeEngine extends ContextWrapper {
    ThemeEngine(Context c) { super(c); }
    private final StringBuilder report = new StringBuilder();
    private static final String BASE = "content://com.zui.themes.provider.themesprovider/";
    private static final String COMMON_ICONS = "/system/etc/themes/common/icons/res.zip";
    private String theme;

    private void log(String s) { report.append(s).append('\n'); android.util.Log.i("ZuiIconHelper", s); }
    static String sha(byte[] b) throws Exception {
        byte[] h = MessageDigest.getInstance("SHA-256").digest(b);
        StringBuilder s = new StringBuilder(); for (byte x : h) s.append(String.format("%02x", x)); return s.toString();
    }
    static byte[] read(InputStream input) throws Exception {
        if (input == null) throw new IOException("无法读取资源");
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192]; int n; while ((n = in.read(b)) != -1) out.write(b, 0, n); return out.toByteArray();
        }
    }
    private void save(String name, byte[] bytes) throws Exception { try (FileOutputStream out = new FileOutputStream(new File(getFilesDir(), name))) { out.write(bytes); } }
    void saveReport() { try { save("report.txt", report.toString().getBytes("UTF-8")); } catch (Exception e) { log(e.toString()); } }
    private byte[] provider(String name) throws Exception { return read(getContentResolver().openInputStream(Uri.parse(BASE + name))); }
    private InputStream providerStream(String name) throws Exception {
        InputStream in = getContentResolver().openInputStream(Uri.parse(BASE + name));
        if (in == null) throw new IOException("无法读取主题资源"); return in;
    }
    static String hashStream(InputStream input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = input) { byte[] buffer = new byte[65536]; int n; while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n); }
        StringBuilder result = new StringBuilder(); for (byte b : digest.digest()) result.append(String.format("%02x", b)); return result.toString();
    }
    static String hashFile(File file) throws Exception { return hashStream(new FileInputStream(file)); }
    static void copyNoClose(InputStream input, OutputStream out) throws Exception {
        try (InputStream in = input) { byte[] buffer = new byte[65536]; int n; while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n); }
    }
    static void copy(InputStream input, OutputStream output) throws Exception {
        try (InputStream in = input; OutputStream out = output) { byte[] buffer = new byte[65536]; int n; while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n); }
    }

    // ---- current theme and its pristine resources ----

    /** Name of the theme ZUI is using right now, e.g. "paintingr". */
    String theme() throws Exception { if (theme == null) theme = current(); return theme; }
    private File systemZip() throws Exception { return new File("/data/system/theme/icons/" + theme() + "/res.zip"); }
    private File themeApk() throws Exception {
        try { return new File(getPackageManager().getApplicationInfo("com.zui.theme.settings.overlay." + theme(), 0).sourceDir); }
        catch (PackageManager.NameNotFoundException e) { throw new IOException("找不到当前主题「" + theme() + "」的原始资源，请在系统设置里切换到一个内置主题后再试。"); }
    }
    /** The current theme's untouched icon zip, extracted from its overlay APK and cached per theme. */
    synchronized File original() throws Exception {
        File dir = new File(getFilesDir(), "original/" + theme()); File zip = new File(dir, "res.zip"), config = new File(dir, "activeicon_config.xml");
        if (zip.isFile() && config.isFile()) return zip;
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("无法保存原始图标备份");
        try (ZipFile apk = new ZipFile(themeApk())) {
            ZipEntry res = apk.getEntry("res/raw/res.zip"), cfg = apk.getEntry("res/raw/activeicon_config.xml");
            if (res == null) throw new IOException("当前主题不含图标资源");
            File temp = new File(dir, "res.zip.tmp"); copy(apk.getInputStream(res), new FileOutputStream(temp));
            byte[] configBytes = cfg != null ? read(apk.getInputStream(cfg)) : "<activeicon/>".getBytes("UTF-8");
            try (FileOutputStream out = new FileOutputStream(config)) { out.write(configBytes); }
            if (!temp.renameTo(zip)) throw new IOException("无法保存原始图标备份");
        }
        log("Original theme resources cached for " + theme()); return zip;
    }
    byte[] originalConfig() throws Exception { original(); return read(new FileInputStream(new File(getFilesDir(), "original/" + theme() + "/activeicon_config.xml"))); }

    /** Package → shared resource files that ZUI's common icon set points several packages at. */
    synchronized JSONObject forcedAliases() throws Exception {
        File cache = new File(getFilesDir(), "forced-aliases.json");
        if (cache.isFile()) return new JSONObject(new String(read(new FileInputStream(cache)), "UTF-8"));
        JSONObject forced = new JSONObject();
        File common = new File(COMMON_ICONS);
        if (common.canRead()) try (ZipFile zip = new ZipFile(common)) {
            ZipEntry update = zip.getEntry("res/update.xml");
            if (update != null) {
                org.xmlpull.v1.XmlPullParser p = android.util.Xml.newPullParser(); p.setInput(zip.getInputStream(update), "UTF-8");
                while (p.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (p.getEventType() != org.xmlpull.v1.XmlPullParser.START_TAG || !"drawable".equals(p.getName())) continue;
                    String name = p.getAttributeValue(null, "name"), value = p.nextText();
                    if (name == null || value == null || !value.startsWith("@drawable/")) continue;
                    String pkg = (name.contains("__") ? name.substring(0, name.indexOf("__")) : name).replace('_', '.');
                    JSONArray list = forced.optJSONArray(pkg); if (list == null) { list = new JSONArray(); forced.put(pkg, list); }
                    list.put("res/drawable-xxhdpi/" + value.substring(10) + ".png");
                }
            }
        }
        save("forced-aliases.json", forced.toString().getBytes("UTF-8")); return forced;
    }

    // ---- packs ----

    File packDir(String pack) { return new File(getFilesDir(), "packs/" + pack); }
    synchronized File preparedAsset(String pack) throws Exception {
        if ("original".equals(pack)) return original();
        File zip = new File(packDir(pack), "res.zip"); if (!zip.isFile()) throw new IOException("图标包文件缺失，请重新导入"); return zip;
    }

    // ---- theme service ----

    private IBinder binder(String name) throws Exception {
        IBinder b = (IBinder) Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, name);
        if (b == null) throw new IOException("这台设备没有 ZUI 主题服务，助手无法使用"); return b;
    }
    String current() throws Exception {
        Parcel d = Parcel.obtain(), r = Parcel.obtain();
        try { d.writeInterfaceToken("zui.themes.IThemeService"); if (!binder("zuithemes").transact(3, d, r, 0)) throw new IOException("主题查询失败"); r.readException(); return r.readString(); }
        finally { d.recycle(); r.recycle(); }
    }
    private void refresh() throws Exception {
        Parcel d = Parcel.obtain(), r = Parcel.obtain();
        try { d.writeInterfaceToken("zui.themes.IThemeService"); d.writeString(theme()); if (!binder("zuithemes").transact(1, d, r, 0)) throw new IOException("主题刷新失败"); r.readException(); }
        finally { d.recycle(); r.recycle(); }
    }
    private long iconTimestamp() throws Exception {
        Parcel d = Parcel.obtain(), r = Parcel.obtain();
        try { d.writeInterfaceToken("zui.icon.IAppIconThemeService"); if (!binder("AppIconThemeServices").transact(3, d, r, 0)) throw new IOException("图标服务查询失败"); r.readException(); return r.readLong(); }
        finally { d.recycle(); r.recycle(); }
    }
    void probe() throws Exception {
        for (String p : new String[]{"READ_THEMES", "WRITE_THEMES", "ACCESS_THEME_MANAGER"}) log(p + "=" + checkSelfPermission("zui.permission." + p));
        log("theme=" + theme()); log("res.zip sha256=" + hashStream(providerStream("res.zip")));
    }
    private void writeProviderFile(String name, File file) throws Exception {
        OutputStream output = getContentResolver().openOutputStream(Uri.parse(BASE + name), "wt");
        if (output == null) throw new IOException("无法写入主题资源");
        copy(new FileInputStream(file), output);
        if (!hashFile(file).equals(hashStream(providerStream(name)))) throw new IOException("写入校验失败：" + name);
    }
    private void writeProvider(String name, byte[] bytes) throws Exception {
        try (OutputStream out = getContentResolver().openOutputStream(Uri.parse(BASE + name), "wt")) { if (out == null) throw new IOException("无法写入主题"); out.write(bytes); }
        if (!sha(bytes).equals(sha(provider(name)))) throw new IOException("写入校验失败：" + name);
    }
    private void awaitLoaded(File expected, long previousTime) throws Exception {
        String hash = hashFile(expected); File live = systemZip(); long deadline = SystemClock.elapsedRealtime() + 45000;
        while (SystemClock.elapsedRealtime() < deadline) {
            Thread.sleep(500);
            if (live.length() == expected.length() && iconTimestamp() != previousTime && hash.equals(hashFile(live))) { log("SYSTEM_RES_SHA256=" + hash); return; }
        }
        throw new IOException("系统加载校验超时");
    }
    /** Writes the new icon zip, reloads the theme, and rolls back to the previous zip if anything fails. */
    void apply(File next, byte[] config, String description) throws Exception {
        if (!theme().equals(current())) throw new IOException("系统主题刚刚被切换过，请关闭助手后重新打开。");
        try (ZipFile zip = new ZipFile(next)) { if (zip.getEntry("res/icons.xml") == null) throw new IOException("图标资源包不完整"); }
        File previous = new File(getFilesDir(), "previous-res.zip"); copy(providerStream("res.zip"), new FileOutputStream(previous));
        byte[] previousConfig = provider("activeicon_config.xml"); long timestamp = iconTimestamp();
        try { writeProviderFile("res.zip", next); writeProvider("activeicon_config.xml", config); refresh(); awaitLoaded(next, timestamp); log("APPLIED=" + description); }
        catch (Exception failure) {
            log("Applying failed; restoring previous resources");
            try { long rollbackTime = iconTimestamp(); writeProviderFile("res.zip", previous); writeProvider("activeicon_config.xml", previousConfig); refresh(); awaitLoaded(previous, rollbackTime); log("ROLLBACK=verified"); }
            catch (Exception rollback) { failure.addSuppressed(rollback); }
            throw failure;
        }
    }
    String liveHash() throws Exception { return hashFile(systemZip()); }
    /** Which pack the system is showing: a pack id, "original", "custom" (our own mix) or "unknown". */
    String identify(List<String> packs) throws Exception {
        String hash = liveHash();
        if (hash.equals(hashFile(original()))) return "original";
        for (String p : packs) if (hash.equals(new JSONObject(new String(read(new FileInputStream(new File(packDir(p), "catalog.json"))), "UTF-8")).optString("sha256"))) return p;
        if (hash.equals(getSharedPreferences("selection", 0).getString("appliedHash", ""))) return "custom";
        return "unknown";
    }
    /** Turns off ZUI's live calendar/clock style icons for apps the user picked an icon for. */
    byte[] configuration(JSONObject overrides) throws Exception {
        String xml = new String(originalConfig(), "UTF-8"); Iterator<String> keys = overrides.keys();
        while (keys.hasNext()) { String pkg = keys.next(); xml = xml.replaceAll("(packageName=\"" + java.util.regex.Pattern.quote(pkg) + "\"\\s+show=\")true", "$1false"); }
        return xml.getBytes("UTF-8");
    }

    /** Builds the zip for a default pack plus per-app overrides. */
    File compose(String pack, JSONObject overrides, IconRepository repo,int scale) throws Exception {
        IconScale.valid(scale);return IconScale.archive(compose(pack,overrides,repo),new File(getFilesDir(),"scaled-next.zip"),scale,forcedAliases());
    }
    File compose(String pack, JSONObject overrides, IconRepository repo) throws Exception {
        File base = preparedAsset(pack);
        if (overrides.length() == 0) return base;
        Map<String, byte[]> replacements = new LinkedHashMap<>(); Map<String, String> mappings = new LinkedHashMap<>(); Set<String> removed = new HashSet<>();
        JSONObject forced = forcedAliases(); Iterator<String> keys = overrides.keys();
        while (keys.hasNext()) {
            String pkg = keys.next(), key = pkg.toLowerCase(Locale.ROOT).replace('.', '_');
            byte[] bytes = repo.bytes(overrides.getString(pkg), repo.findApp(pkg)); String name = "selected_" + key;
            replacements.put("res/drawable-xxhdpi/" + name + ".png", bytes); mappings.put(key + "__icon", "@drawable/" + name); removed.add(key);
            JSONArray aliases = forced.optJSONArray(pkg);
            if (aliases != null) for (int i = 0; i < aliases.length(); i++) {
                String path = aliases.getString(i); byte[] old = replacements.get(path);
                if (old != null && !Arrays.equals(old, bytes)) throw new IOException("系统让两个应用共用了同一张图标，无法分别设置：" + pkg);
                replacements.put(path, bytes);
            }
        }
        File output = new File(getFilesDir(), "custom-next.zip");
        try (ZipFile zip = new ZipFile(base); ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(output)))) {
            out.setLevel(1);
            org.xmlpull.v1.XmlPullParser parser = android.util.Xml.newPullParser();
            parser.setInput(new StringReader(new String(read(zip.getInputStream(zip.getEntry("res/icons.xml"))), "UTF-8")));
            StringWriter writer = new StringWriter(); org.xmlpull.v1.XmlSerializer xml = android.util.Xml.newSerializer(); xml.setOutput(writer); xml.startDocument("UTF-8", true); xml.startTag(null, "resources");
            while (parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() != org.xmlpull.v1.XmlPullParser.START_TAG || !"drawable".equals(parser.getName())) continue;
                String name = parser.getAttributeValue(null, "name"), value = parser.nextText(); String key = name.contains("__") ? name.substring(0, name.indexOf("__")) : name;
                if (removed.contains(key)) { if ("com_android_dialer".equals(key) || "com_zui_launcher".equals(key)) mappings.put(name, mappings.get(key + "__icon")); continue; }
                xml.startTag(null, "drawable"); xml.attribute(null, "name", name); xml.text(value); xml.endTag(null, "drawable");
            }
            for (Map.Entry<String, String> e : mappings.entrySet()) { xml.startTag(null, "drawable"); xml.attribute(null, "name", e.getKey()); xml.text(e.getValue()); xml.endTag(null, "drawable"); }
            xml.endTag(null, "resources"); xml.endDocument(); replacements.put("res/icons.xml", writer.toString().getBytes("UTF-8"));
            Enumeration<? extends ZipEntry> entries = zip.entries(); byte[] buffer = new byte[65536];
            while (entries.hasMoreElements()) { ZipEntry entry = entries.nextElement(); if (replacements.containsKey(entry.getName())) continue;
                out.putNextEntry(new ZipEntry(entry.getName())); try (InputStream in = zip.getInputStream(entry)) { int n; while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n); } out.closeEntry(); }
            for (Map.Entry<String, byte[]> e : replacements.entrySet()) { out.putNextEntry(new ZipEntry(e.getKey())); out.write(e.getValue()); out.closeEntry(); }
        }
        log("COMPOSED base=" + pack + " overrides=" + overrides.length()); return output;
    }
}
