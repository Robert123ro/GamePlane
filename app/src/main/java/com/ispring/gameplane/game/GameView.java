package com.ispring.gameplane.game;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.ispring.gameplane.R;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class GameView extends View {

    private Paint paint;
    private Paint textPaint;
    private CombatAircraft combatAircraft = null;
    private List<Sprite> sprites = new ArrayList<Sprite>();
    private List<Sprite> spritesNeedAdded = new ArrayList<Sprite>();

    private List<Bitmap> bitmaps = new ArrayList<Bitmap>();
    private float density = getResources().getDisplayMetrics().density;//Screen density
    public static final int STATUS_GAME_STARTED = 1;//The game starts now
    public static final int STATUS_GAME_PAUSED = 2;//Game pause
    public static final int STATUS_GAME_OVER = 3;//game over
    public static final int STATUS_GAME_DESTROYED = 4;//Game destruction
    private int status = STATUS_GAME_DESTROYED;//Initially destroyed
    private long frame = 0;//Total number of frames drawn
    private long score = 0;//Total Score
    private float fontSize = 12;//The default font size used to draw the text in the upper left corner
    private float fontSize2 = 20;//Used to draw text in Dialog when Game Over
    private float borderSize = 2;//The border of the Game Over Dialog
    private Rect continueRect = new Rect();//Rect of "Continue" and "Restart" buttons

    //Variables related to touch events
    private static final int TOUCH_MOVE = 1;//Touch
    private static final int TOUCH_SINGLE_CLICK = 2;//Single Click
    private static final int TOUCH_DOUBLE_CLICK = 3;//Double click
    //A single click event is composed of DOWN and UP events. Assuming that the interval from down to up is less than 200 milliseconds, we think that a single click event has occurred
    private static final int singleClickDurationTime = 200;
    //A double-click event is composed of two click events, and the time between two click events is less than 300 milliseconds, we think that a double-click event has occurred
    private static final int doubleClickDurationTime = 300;
    private long lastSingleClickTime = -1;//The time of the last click
    private long touchDownTime = -1;//The moment when the contact is pressed
    private long touchUpTime = -1;//The moment the contact bounces
    private float touchX = -1;//The x coordinate of the touch point
    private float touchY = -1;//The y coordinate of the touch point

    public GameView(Context context) {
        super(context);
        init(null, 0);
    }

    public GameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public GameView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    private void init(AttributeSet attrs, int defStyle) {
        final TypedArray a = getContext().obtainStyledAttributes(
                attrs, R.styleable.GameView, defStyle, 0);
        a.recycle();
        //Initialize paint
        paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        //Set textPaint, set to anti-aliasing, and bold
        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.FAKE_BOLD_TEXT_FLAG);
        textPaint.setColor(0xff000000);
        fontSize = textPaint.getTextSize();
        fontSize *= density;
        fontSize2 *= density;
        textPaint.setTextSize(fontSize);
        borderSize *= density;
    }

    public void start(int[] bitmapIds){
        destroy();
        for(int bitmapId : bitmapIds){
            Bitmap bitmap = BitmapFactory.decodeResource(getResources(), bitmapId);
            bitmaps.add(bitmap);
        }
        startWhenBitmapsReady();
    }
    
    private void startWhenBitmapsReady(){
        combatAircraft = new CombatAircraft(bitmaps.get(0));
        //Set the game to start
        status = STATUS_GAME_STARTED;
        postInvalidate();
    }
    
    private void restart(){
        destroyNotRecyleBitmaps();
        startWhenBitmapsReady();
    }

    public void pause(){
        //Set the game to pause
        status = STATUS_GAME_PAUSED;
    }

    private void resume(){
        //Set the game to run
        status = STATUS_GAME_STARTED;
        postInvalidate();
    }

    private long getScore(){
        //Get game score
        return score;
    }

    /*-------------------------------draw-------------------------------------*/

    @Override
    protected void onDraw(Canvas canvas) {
        //We check in every frame whether the condition for delay triggering the click event is met
        if(isSingleClick()){
            onSingleClick(touchX, touchY);
        }

        super.onDraw(canvas);

        if(status == STATUS_GAME_STARTED){
            drawGameStarted(canvas);
        }else if(status == STATUS_GAME_PAUSED){
            drawGamePaused(canvas);
        }else if(status == STATUS_GAME_OVER){
            drawGameOver(canvas);
        }
    }

    //Draw the running game
    private void drawGameStarted(Canvas canvas){

        drawScoreAndBombs(canvas);

        //When drawing for the first time, move the fighter to the bottom of the Canvas, in the center of the horizontal direction
        if(frame == 0){
            float centerX = canvas.getWidth() / 2;
            float centerY = canvas.getHeight() - combatAircraft.getHeight() / 2;
            combatAircraft.centerTo(centerX, centerY);
        }

        //Add spritesNeedAdded to sprites
        if(spritesNeedAdded.size() > 0){
            sprites.addAll(spritesNeedAdded);
            spritesNeedAdded.clear();
        }

        //Check that the fighter is in front of the bullet
        destroyBulletsFrontOfCombatAircraft();

        //Remove the sprite that has been destroyed before drawing
        removeDestroyedSprites();

        //Randomly add Sprite every 30 frames
        if(frame % 30 == 0){
            createRandomSprites(canvas.getWidth());
        }
        frame++;

        //Traverse sprites, draw enemy aircraft, bullets, rewards, and explosion effects
        Iterator<Sprite> iterator = sprites.iterator();
        while (iterator.hasNext()){
            Sprite s = iterator.next();

            if(!s.isDestroyed()){
                //The destroy method may be called in the draw method of Sprite
                s.draw(canvas, paint, this);
            }

            //We are here to determine whether Sprite is destroyed after executing the draw method
            if(s.isDestroyed()){
                //If the Sprite is destroyed, remove it from Sprites
                iterator.remove();
            }
        }

        if(combatAircraft != null){
            //Finally draw the fighter
            combatAircraft.draw(canvas, paint, this);
            if(combatAircraft.isDestroyed()){
                //If the fighter is hit and destroyed, the game is over
                status = STATUS_GAME_OVER;
            }
            //By calling the postInvalidate() method, the View is rendered continuously to achieve dynamic effects
            postInvalidate();
        }
    }

    //Draw the paused game
    private void drawGamePaused(Canvas canvas){
        drawScoreAndBombs(canvas);

        //Call the onDraw method of Sprite instead of the draw method, so that you can render the static Sprite without changing the position of the Sprite
        for(Sprite s : sprites){
            s.onDraw(canvas, paint, this);
        }
        if(combatAircraft != null){
            combatAircraft.onDraw(canvas, paint, this);
        }

        //Draw Dialog, display score
        drawScoreDialog(canvas, "carry on");

        if(lastSingleClickTime > 0){
            postInvalidate();
        }
    }

    //Draw the end state of the game
    private void drawGameOver(Canvas canvas){
        //After Game Over, only draw a pop-up window to display the final score
        drawScoreDialog(canvas, "Restart");

        if(lastSingleClickTime > 0){
            postInvalidate();
        }
    }

    private void drawScoreDialog(Canvas canvas, String operation){
        int canvasWidth = canvas.getWidth();
        int canvasHeight = canvas.getHeight();
        //Store original value
        float originalFontSize = textPaint.getTextSize();
        Paint.Align originalFontAlign = textPaint.getTextAlign();
        int originalColor = paint.getColor();
        Paint.Style originalStyle = paint.getStyle();

        int w1 = (int)(20.0 / 360.0 * canvasWidth);
        int w2 = canvasWidth - 2 * w1;
        int buttonWidth = (int)(140.0 / 360.0 * canvasWidth);

        int h1 = (int)(150.0 / 558.0 * canvasHeight);
        int h2 = (int)(60.0 / 558.0 * canvasHeight);
        int h3 = (int)(124.0 / 558.0 * canvasHeight);
        int h4 = (int)(76.0 / 558.0 * canvasHeight);
        int buttonHeight = (int)(42.0 / 558.0 * canvasHeight);

        canvas.translate(w1, h1);
        //Draw background color
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFD7DDDE);
        Rect rect1 = new Rect(0, 0, w2, canvasHeight - 2 * h1);
        canvas.drawRect(rect1, paint);
        //Draw border
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0xFF515151);
        paint.setStrokeWidth(borderSize);
        //paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        canvas.drawRect(rect1, paint);
        //Draw the text "Aircraft Battle Score"
        textPaint.setTextSize(fontSize2);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("Aircraft war score", w2 / 2, (h2 - fontSize2) / 2 + fontSize2, textPaint);
        //Draw the horizontal line under "Aircraft War Score"
        canvas.translate(0, h2);
        canvas.drawLine(0, 0, w2, 0, paint);
        //Plot actual scores
        String allScore = String.valueOf(getScore());
        canvas.drawText(allScore, w2 / 2, (h3 - fontSize2) / 2 + fontSize2, textPaint);
        //Draw a horizontal line under the score
        canvas.translate(0, h3);
        canvas.drawLine(0, 0, w2, 0, paint);
        //Draw button border
        Rect rect2 = new Rect();
        rect2.left = (w2 - buttonWidth) / 2;
        rect2.right = w2 - rect2.left;
        rect2.top = (h4 - buttonHeight) / 2;
        rect2.bottom = h4 - rect2.top;
        canvas.drawRect(rect2, paint);
        //Draw text "continue" or "start again"
        canvas.translate(0, rect2.top);
        canvas.drawText(operation, w2 / 2, (buttonHeight - fontSize2) / 2 + fontSize2, textPaint);
        continueRect = new Rect(rect2);
        continueRect.left = w1 + rect2.left;
        continueRect.right = continueRect.left + buttonWidth;
        continueRect.top = h1 + h2 + h3 + rect2.top;
        continueRect.bottom = continueRect.top + buttonHeight;

        //Reset
        textPaint.setTextSize(originalFontSize);
        textPaint.setTextAlign(originalFontAlign);
        paint.setColor(originalColor);
        paint.setStyle(originalStyle);
    }

    //绘制左上角的得分和左下角炸弹的数量
    private void drawScoreAndBombs(Canvas canvas){
        //绘制左上角的暂停按钮
        Bitmap pauseBitmap = status == STATUS_GAME_STARTED ? bitmaps.get(9) : bitmaps.get(10);
        RectF pauseBitmapDstRecF = getPauseBitmapDstRecF();
        float pauseLeft = pauseBitmapDstRecF.left;
        float pauseTop = pauseBitmapDstRecF.top;
        canvas.drawBitmap(pauseBitmap, pauseLeft, pauseTop, paint);
        //绘制左上角的总得分数
        float scoreLeft = pauseLeft + pauseBitmap.getWidth() + 20 * density;
        float scoreTop = fontSize + pauseTop + pauseBitmap.getHeight() / 2 - fontSize / 2;
        canvas.drawText(score + "", scoreLeft, scoreTop, textPaint);

        //绘制左下角
        if(combatAircraft != null && !combatAircraft.isDestroyed()){
            int bombCount = combatAircraft.getBombCount();
            if(bombCount > 0){
                //绘制左下角的炸弹
                Bitmap bombBitmap = bitmaps.get(11);
                float bombTop = canvas.getHeight() - bombBitmap.getHeight();
                canvas.drawBitmap(bombBitmap, 0, bombTop, paint);
                //绘制左下角的炸弹数量
                float bombCountLeft = bombBitmap.getWidth() + 10 * density;
                float bombCountTop = fontSize + bombTop + bombBitmap.getHeight() / 2 - fontSize / 2;
                canvas.drawText("X " + bombCount, bombCountLeft, bombCountTop, textPaint);
            }
        }
    }

    //检查战斗机跑到子弹前面的情况
    private void destroyBulletsFrontOfCombatAircraft(){
        if(combatAircraft != null){
            float aircraftY = combatAircraft.getY();
            List<Bullet> aliveBullets = getAliveBullets();
            for(Bullet bullet : aliveBullets){
                //如果战斗机跑到了子弹前面，那么就销毁子弹
                if(aircraftY <= bullet.getY()){
                    bullet.destroy();
                }
            }
        }
    }

    //移除掉已经destroyed的Sprite
    private void removeDestroyedSprites(){
        Iterator<Sprite> iterator = sprites.iterator();
        while (iterator.hasNext()){
            Sprite s = iterator.next();
            if(s.isDestroyed()){
                iterator.remove();
            }
        }
    }

    //生成随机的Sprite
    private void createRandomSprites(int canvasWidth){
        Sprite sprite = null;
        int speed = 2;
        //callTime表示createRandomSprites方法被调用的次数
        int callTime = Math.round(frame / 30);
        if((callTime + 1) % 25 == 0){
            //发送道具奖品
            if((callTime + 1) % 50 == 0){
                //发送炸弹
                sprite = new BombAward(bitmaps.get(7));
            }
            else{
                //发送双子弹
                sprite = new BulletAward(bitmaps.get(8));
            }
        }
        else{
            //发送敌机
            int[] nums = {0,0,0,0,0,1,0,0,1,0,0,0,0,1,1,1,1,1,1,2};
            int index = (int)Math.floor(nums.length*Math.random());
            int type = nums[index];
            if(type == 0){
                //小敌机
                sprite = new SmallEnemyPlane(bitmaps.get(4));
            }
            else if(type == 1){
                //中敌机
                sprite = new MiddleEnemyPlane(bitmaps.get(5));
            }
            else if(type == 2){
                //大敌机
                sprite = new BigEnemyPlane(bitmaps.get(6));
            }
            if(type != 2){
                if(Math.random() < 0.33){
                    speed = 4;
                }
            }
        }

        if(sprite != null){
            float spriteWidth = sprite.getWidth();
            float spriteHeight = sprite.getHeight();
            float x = (float)((canvasWidth - spriteWidth)*Math.random());
            float y = -spriteHeight;
            sprite.setX(x);
            sprite.setY(y);
            if(sprite instanceof AutoSprite){
                AutoSprite autoSprite = (AutoSprite)sprite;
                autoSprite.setSpeed(speed);
            }
            addSprite(sprite);
        }
    }

    /*-------------------------------touch------------------------------------*/

    @Override
    public boolean onTouchEvent(MotionEvent event){
        //通过调用resolveTouchType方法，得到我们想要的事件类型
        //需要注意的是resolveTouchType方法不会返回TOUCH_SINGLE_CLICK类型
        //我们会在onDraw方法每次执行的时候，都会调用isSingleClick方法检测是否触发了单击事件
        int touchType = resolveTouchType(event);
        if(status == STATUS_GAME_STARTED){
            if(touchType == TOUCH_MOVE){
                if(combatAircraft != null){
                    combatAircraft.centerTo(touchX, touchY);
                }
            }else if(touchType == TOUCH_DOUBLE_CLICK){
                if(status == STATUS_GAME_STARTED){
                    if(combatAircraft != null){
                        //双击会使得战斗机使用炸弹
                        combatAircraft.bomb(this);
                    }
                }
            }
        }else if(status == STATUS_GAME_PAUSED){
            if(lastSingleClickTime > 0){
                postInvalidate();
            }
        }else if(status == STATUS_GAME_OVER){
            if(lastSingleClickTime > 0){
                postInvalidate();
            }
        }
        return true;
    }

    //合成我们想要的事件类型
    private int resolveTouchType(MotionEvent event){
        int touchType = -1;
        int action = event.getAction();
        touchX = event.getX();
        touchY = event.getY();
        if(action == MotionEvent.ACTION_MOVE){
            long deltaTime = System.currentTimeMillis() - touchDownTime;
            if(deltaTime > singleClickDurationTime){
                //触点移动
                touchType = TOUCH_MOVE;
            }
        }else if(action == MotionEvent.ACTION_DOWN){
            //触点按下
            touchDownTime = System.currentTimeMillis();
        }else if(action == MotionEvent.ACTION_UP){
            //触点弹起
            touchUpTime = System.currentTimeMillis();
            //计算触点按下到触点弹起之间的时间差
            long downUpDurationTime = touchUpTime - touchDownTime;
            //如果此次触点按下和抬起之间的时间差小于一次单击事件指定的时间差，
            //那么我们就认为发生了一次单击
            if(downUpDurationTime <= singleClickDurationTime){
                //计算这次单击距离上次单击的时间差
                long twoClickDurationTime = touchUpTime - lastSingleClickTime;

                if(twoClickDurationTime <=  doubleClickDurationTime){
                    //如果两次单击的时间差小于一次双击事件执行的时间差，
                    //那么我们就认为发生了一次双击事件
                    touchType = TOUCH_DOUBLE_CLICK;
                    //重置变量
                    lastSingleClickTime = -1;
                    touchDownTime = -1;
                    touchUpTime = -1;
                }else{
                    //如果这次形成了单击事件，但是没有形成双击事件，那么我们暂不触发此次形成的单击事件
                    //我们应该在doubleClickDurationTime毫秒后看一下有没有再次形成第二个单击事件
                    //如果那时形成了第二个单击事件，那么我们就与此次的单击事件合成一次双击事件
                    //否则在doubleClickDurationTime毫秒后触发此次的单击事件
                    lastSingleClickTime = touchUpTime;
                }
            }
        }
        return touchType;
    }

    //在onDraw方法中调用该方法，在每一帧都检查是不是发生了单击事件
    private boolean isSingleClick(){
        boolean singleClick = false;
        //我们检查一下是不是上次的单击事件在经过了doubleClickDurationTime毫秒后满足触发单击事件的条件
        if(lastSingleClickTime > 0){
            //计算当前时刻距离上次发生单击事件的时间差
            long deltaTime = System.currentTimeMillis() - lastSingleClickTime;

            if(deltaTime >= doubleClickDurationTime){
                //如果时间差超过了一次双击事件所需要的时间差，
                //那么就在此刻延迟触发之前本该发生的单击事件
                singleClick = true;
                //重置变量
                lastSingleClickTime = -1;
                touchDownTime = -1;
                touchUpTime = -1;
            }
        }
        return singleClick;
    }

    private void onSingleClick(float x, float y){
        if(status == STATUS_GAME_STARTED){
            if(isClickPause(x, y)){
                //单击了暂停按钮
                pause();
            }
        }else if(status == STATUS_GAME_PAUSED){
            if(isClickContinueButton(x, y)){
                //单击了“继续”按钮
                resume();
            }
        }else if(status == STATUS_GAME_OVER){
            if(isClickRestartButton(x, y)){
                //单击了“重新开始”按钮
                restart();
            }
        }
    }

    //是否单击了左上角的暂停按钮
    private boolean isClickPause(float x, float y){
        RectF pauseRecF = getPauseBitmapDstRecF();
        return pauseRecF.contains(x, y);
    }

    //是否单击了暂停状态下的“继续”那妞
    private boolean isClickContinueButton(float x, float y){
        return continueRect.contains((int)x, (int)y);
    }

    //是否单击了GAME OVER状态下的“重新开始”按钮
    private boolean isClickRestartButton(float x, float y){
        return continueRect.contains((int)x, (int)y);
    }

    private RectF getPauseBitmapDstRecF(){
        Bitmap pauseBitmap = status == STATUS_GAME_STARTED ? bitmaps.get(9) : bitmaps.get(10);
        RectF recF = new RectF();
        recF.left = 15 * density;
        recF.top = 15 * density;
        recF.right = recF.left + pauseBitmap.getWidth();
        recF.bottom = recF.top + pauseBitmap.getHeight();
        return recF;
    }

    /*-------------------------------destroy------------------------------------*/
    
    private void destroyNotRecyleBitmaps(){
        //将游戏设置为销毁状态
        status = STATUS_GAME_DESTROYED;

        //重置frame
        frame = 0;

        //重置得分
        score = 0;

        //销毁战斗机
        if(combatAircraft != null){
            combatAircraft.destroy();
        }
        combatAircraft = null;

        //销毁敌机、子弹、奖励、爆炸
        for(Sprite s : sprites){
            s.destroy();
        }
        sprites.clear();
    }

    public void destroy(){
        destroyNotRecyleBitmaps();

        //释放Bitmap资源
        for(Bitmap bitmap : bitmaps){
            bitmap.recycle();
        }
        bitmaps.clear();
    }

    /*-------------------------------public methods-----------------------------------*/

    //向Sprites中添加Sprite
    public void addSprite(Sprite sprite){
        spritesNeedAdded.add(sprite);
    }

    //添加得分
    public void addScore(int value){
        score += value;
    }

    public int getStatus(){
        return status;
    }

    public float getDensity(){
        return density;
    }

    public Bitmap getYellowBulletBitmap(){
        return bitmaps.get(2);
    }

    public Bitmap getBlueBulletBitmap(){
        return bitmaps.get(3);
    }

    public Bitmap getExplosionBitmap(){
        return bitmaps.get(1);
    }

    //获取处于活动状态的敌机
    public List<EnemyPlane> getAliveEnemyPlanes(){
        List<EnemyPlane> enemyPlanes = new ArrayList<EnemyPlane>();
        for(Sprite s : sprites){
            if(!s.isDestroyed() && s instanceof EnemyPlane){
                EnemyPlane sprite = (EnemyPlane)s;
                enemyPlanes.add(sprite);
            }
        }
        return enemyPlanes;
    }

    //获得处于活动状态的炸弹奖励
    public List<BombAward> getAliveBombAwards(){
        List<BombAward> bombAwards = new ArrayList<BombAward>();
        for(Sprite s : sprites){
            if(!s.isDestroyed() && s instanceof BombAward){
                BombAward bombAward = (BombAward)s;
                bombAwards.add(bombAward);
            }
        }
        return bombAwards;
    }

    //获取处于活动状态的子弹奖励
    public List<BulletAward> getAliveBulletAwards(){
        List<BulletAward> bulletAwards = new ArrayList<BulletAward>();
        for(Sprite s : sprites){
            if(!s.isDestroyed() && s instanceof BulletAward){
                BulletAward bulletAward = (BulletAward)s;
                bulletAwards.add(bulletAward);
            }
        }
        return bulletAwards;
    }

    //获取处于活动状态的子弹
    public List<Bullet> getAliveBullets(){
        List<Bullet> bullets = new ArrayList<Bullet>();
        for(Sprite s : sprites){
            if(!s.isDestroyed() && s instanceof Bullet){
                Bullet bullet = (Bullet)s;
                bullets.add(bullet);
            }
        }
        return bullets;
    }
}