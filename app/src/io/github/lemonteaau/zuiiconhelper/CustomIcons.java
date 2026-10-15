package io.github.lemonteaau.zuiiconhelper;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import android.os.Build;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.util.*;

/** Private, durable PNG copies; no external-storage permission or persistent URI grant. */
final class CustomIcons {
    static final int OUTPUT_SIZE=512;
    static final long MAX_SOURCE_BYTES=64L*1024*1024;
    final File directory;
    final AtomicFile index;
    CustomIcons(Context context) throws IOException {
        directory=new File(context.getFilesDir(),"custom-icons");
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("无法创建图片目录");
        index=new AtomicFile(new File(directory,"index.json"));
    }
    synchronized JSONArray entries() throws Exception {
        if(!index.getBaseFile().exists())return new JSONArray();
        return new JSONArray(new String(index.readFully(),"UTF-8"));
    }
    File resolve(String choice) throws IOException {
        if(!choice.matches("custom/[a-f0-9]{32}"))throw new IOException("无效的自定义图标");
        File file=new File(directory,choice.substring(7)+".png");
        if(!file.isFile())throw new IOException("自定义图片缺失，请重新导入");
        return file;
    }
    synchronized void removeTestEntry(String choice) throws Exception {
        File file=resolve(choice);JSONArray old=entries(),next=new JSONArray();
        for(int i=0;i<old.length();i++)if(!choice.equals("custom/"+old.getJSONObject(i).getString("id")))next.put(old.getJSONObject(i));
        FileOutputStream output=null;try{output=index.startWrite();output.write(next.toString().getBytes("UTF-8"));index.finishWrite(output);}catch(Exception e){if(output!=null)index.failWrite(output);throw e;}file.delete();
    }
    synchronized String save(Bitmap bitmap,String label) throws Exception {
        String id=UUID.randomUUID().toString().replace("-","");File png=new File(directory,id+".png");
        try(FileOutputStream out=new FileOutputStream(png)) {if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("图片保存失败");out.getFD().sync();}
        JSONArray data=entries();JSONObject item=new JSONObject();item.put("id",id);item.put("name",label+" · 自定义");data.put(item);
        FileOutputStream output=null;
        try {output=index.startWrite();output.write(data.toString().getBytes("UTF-8"));index.finishWrite(output);}
        catch(Exception e){if(output!=null)index.failWrite(output);png.delete();throw e;}
        return "custom/"+id;
    }
    static void copySource(Context c,Uri uri,File target) throws Exception {
        File temp=new File(target.getPath()+".tmp");
        try(InputStream in=c.getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(temp)) {
            if(in==null)throw new IOException("无法打开所选图片");byte[] buf=new byte[65536];long size=0;int n;
            while((n=in.read(buf))!=-1){size+=n;if(size>MAX_SOURCE_BYTES)throw new IOException("图片超过 64 MB，请选择较小的图片");out.write(buf,0,n);}
            if(size==0)throw new IOException("所选文件为空");out.getFD().sync();
        }catch(Exception e){temp.delete();throw e;}
        if(!temp.renameTo(target)){temp.delete();throw new IOException("无法保存导入图片");}
    }
    static Bitmap decode(File source) throws Exception {
        if(source.length()>MAX_SOURCE_BYTES)throw new IOException("图片超过 64 MB");
        if(Build.VERSION.SDK_INT>=28){
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(source),(decoder,info,src)->{
                int w=info.getSize().getWidth(),h=info.getSize().getHeight();
                if(w<=0||h<=0)throw new IllegalArgumentException("图片尺寸无效");
                double ratio=Math.min(1d,1536d/Math.max(w,h));decoder.setTargetSize(Math.max(1,(int)(w*ratio)),Math.max(1,(int)(h*ratio)));decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });
        }
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(source.getPath(),o);
        if(o.outWidth<=0||o.outHeight<=0)throw new IOException("无法识别图片，请选择 PNG、JPEG 或 WebP");
        o.inSampleSize=1;while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>1536)o.inSampleSize*=2;o.inJustDecodeBounds=false;
        Bitmap bitmap=BitmapFactory.decodeFile(source.getPath(),o);if(bitmap==null)throw new IOException("图片解码失败");
        android.media.ExifInterface exif=new android.media.ExifInterface(source.getPath());int orientation=exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);Matrix m=new Matrix();
        switch(orientation){case 2:m.setScale(-1,1);break;case 3:m.setRotate(180);break;case 4:m.setScale(1,-1);break;case 5:m.setRotate(90);m.postScale(-1,1);break;case 6:m.setRotate(90);break;case 7:m.setRotate(270);m.postScale(-1,1);break;case 8:m.setRotate(270);break;}
        if(!m.isIdentity()){Bitmap rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),m,true);if(rotated!=bitmap)bitmap.recycle();bitmap=rotated;}return bitmap;
    }
}
