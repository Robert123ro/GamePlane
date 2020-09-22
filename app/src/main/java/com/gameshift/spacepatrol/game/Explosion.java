package com.gameshift.spacepatrol.game;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

public class Explosion extends Sprite {

    private int segment = 14;
    private int level = 0;
    private int explodeFrequency = 2;

    public Explosion(Bitmap bitmap){
        super(bitmap);
    }

    @Override
    public float getWidth() {
        Bitmap bitmap = getBitmap();
        if(bitmap != null){
            return bitmap.getWidth() / segment;
        }
        return 0;
    }

    @Override
    public Rect getBitmapSrcRec() {
        Rect rect = super.getBitmapSrcRec();
        int left = (int)(level * getWidth());
        rect.offsetTo(left, 0);
        return rect;
    }

    @Override
    protected void afterDraw(Canvas canvas, Paint paint, GameView gameView) {
        if(!isDestroyed()){
            if(getFrame() % explodeFrequency == 0){
                level++;
                if(level >= segment){
                    destroy();
                }
            }
        }
    }
	
    public int getExplodeDurationFrame(){
        return segment * explodeFrequency;
    }
}