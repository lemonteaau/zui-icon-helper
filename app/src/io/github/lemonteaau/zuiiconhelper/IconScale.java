package io.github.lemonteaau.zuiiconhelper;

import android.graphics.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Centered scaling within the existing transparent icon canvas, never the launcher grid. */
final class IconScale {
    static final int MIN=70,MAX=120;
    static int valid(int percent) throws IOException {if(percent<MIN||percent>MAX)throw new IOException("图标大小必须在 70%–120% 之间");return percent;}
    static Bitmap bitmap(Bitmap source,int percent) {
        if(percent==100)return source;
        int w=source.getWidth(),h=source.getHeight();Bitmap output=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        float scale=percent/100f;Canvas canvas=new Canvas(output);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(source,null,new RectF(w*(1-scale)/2,h*(1-scale)/2,w*(1+scale)/2,h*(1+scale)/2),paint);return output;
    }
    static byte[] png(byte[] bytes,int percent)throws Exception {
        valid(percent);if(percent==100)return bytes;
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
        if(options.outWidth<1||options.outHeight<1||options.outWidth>2048||options.outHeight>2048)throw new IOException("图标图片尺寸无效或过大");
        Bitmap source=BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(source==null)throw new IOException("图标无法解码");Bitmap output=null;
        try{output=bitmap(source,percent);ByteArrayOutputStream out=new ByteArrayOutputStream();if(!output.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("图标缩放保存失败");return out.toByteArray();}
        finally{if(output!=null&&output!=source)output.recycle();source.recycle();}
    }
    static File archive(File source,File target,int percent,JSONObject forced)throws Exception {
        valid(percent);if(percent==100)return source;
        Set<String> resources=new HashSet<>(),paths=new HashSet<>();
        Iterator<String> keys=forced.keys();while(keys.hasNext()){JSONArray list=forced.getJSONArray(keys.next());for(int i=0;i<list.length();i++)paths.add(list.getString(i));}
        File temp=new File(target.getPath()+".tmp");
        try(ZipFile zip=new ZipFile(source);ZipOutputStream out=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(temp)))) {
            org.xmlpull.v1.XmlPullParser parser=android.util.Xml.newPullParser();
            try(InputStream in=zip.getInputStream(zip.getEntry("res/icons.xml"))){parser.setInput(in,"UTF-8");while(parser.next()!=org.xmlpull.v1.XmlPullParser.END_DOCUMENT){if(parser.getEventType()==org.xmlpull.v1.XmlPullParser.START_TAG&&"drawable".equals(parser.getName())){String value=parser.nextText().trim();if(value.startsWith("@drawable/"))resources.add(value.substring(10));}}}
            out.setLevel(1);Enumeration<? extends ZipEntry> entries=zip.entries();byte[] buffer=new byte[65536];
            while(entries.hasMoreElements()){ZipEntry entry=entries.nextElement();String path=entry.getName();out.putNextEntry(new ZipEntry(path));
                String name=path.substring(path.lastIndexOf('/')+1);boolean icon=path.startsWith("res/drawable")&&path.endsWith(".png")&&(paths.contains(path)||resources.contains(name.substring(0,name.length()-4)));
                try(InputStream in=zip.getInputStream(entry)){if(icon)out.write(png(ThemeEngine.read(in),percent));else{int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}}out.closeEntry();
            }
        }catch(Exception e){temp.delete();throw e;}
        if(!temp.renameTo(target)){temp.delete();throw new IOException("无法保存缩放后的图标");}return target;
    }
}
