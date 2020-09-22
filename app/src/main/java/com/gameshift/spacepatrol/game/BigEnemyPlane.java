package com.gameshift.spacepatrol.game;

import android.graphics.Bitmap;
public class BigEnemyPlane extends EnemyPlane {

    public BigEnemyPlane(Bitmap bitmap){
        super(bitmap);
        setPower(10);
        setValue(30000);
    }

}