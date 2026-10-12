package com.cyberyichen.bloub;
import android.graphics.*;
final class OverlayText {
 static void label(Bitmap image,float[] box,String text,int color){Canvas c=new Canvas(image);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);float scale=image.getWidth()/640f,height=27*scale;p.setTextSize(18*scale);float width=Math.min(image.getWidth(),p.measureText(text)+12*scale),x=Math.max(0,Math.min(box[0]*image.getWidth(),image.getWidth()-width)),y=box[1]*image.getHeight();y=y>height?y-height:Math.min(image.getHeight()-height,y);p.setColor(color);c.drawRect(x,y,x+width,y+height,p);p.setColor(Color.WHITE);c.drawText(text,x+6*scale,y+20*scale,p);}
}
