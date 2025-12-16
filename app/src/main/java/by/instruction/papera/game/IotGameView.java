package by.instruction.papera.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * SurfaceView-based mini-game: a worker avoids falling bricks.
 */
public class IotGameView extends SurfaceView implements SurfaceHolder.Callback {

    public interface GameListener {
        void onTick(long elapsedMs);

        void onShieldChanged(int hits);

        void onGameOver(long durationMs, boolean endedByPlate);
    }

    private static class FallingObject {
        float x;
        float y;
        float width;
        float height;
        float speed;
        boolean helmet;
        boolean plate;
    }

    private final Paint backgroundPaint = new Paint();
    private final Paint playerPaint = new Paint();
    private final Paint brickPaint = new Paint();
    private final Paint helmetPaint = new Paint();
    private final Paint platePaint = new Paint();
    private final Paint tilePaint = new Paint();
    private final Paint gridPaint = new Paint();
    private final Paint platformPaint = new Paint();
    private final Paint bubbleBgPaint = new Paint();
    private final Paint bubbleStrokePaint = new Paint();
    private final Paint textPaint = new Paint();
    private final Random random = new Random();
    private final List<FallingObject> objects = new ArrayList<>();

    private float playerX;
    private float playerY;
    private float playerSize;
    private int shieldHits;
    private boolean helmetOnHead;
    private boolean hadHelmet;
    private boolean deadPose;
    private boolean dropHelmetOnGround;
    private float dropHelmetX;
    private float dropHelmetY;
    private boolean message60Shown;
    private boolean message120Shown;
    private String bubbleText;
    private long bubbleUntilMs;

    private GameListener listener;
    private GameThread gameThread;
    private volatile boolean running = false;
    private boolean started = false;
    private boolean gameOver = false;
    private boolean plateSpawned = false;
    private float platformHeight;

    private long startTimeMs;
    private long lastSpawnMs;
    private long lastHelmetSpawnMs;
    private long lastTickMs;
    private long pauseStartedMs;

    public IotGameView(Context context) {
        super(context);
        init();
    }

    public IotGameView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public IotGameView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        getHolder().addCallback(this);
        setFocusable(true);
        backgroundPaint.setColor(Color.parseColor("#F2F2F2"));
        playerPaint.setColor(Color.BLACK);
        brickPaint.setColor(Color.BLACK);
        helmetPaint.setColor(Color.parseColor("#FFD600"));
        platePaint.setColor(Color.BLACK);
        tilePaint.setColor(Color.parseColor("#E6E6E6"));
        gridPaint.setColor(Color.parseColor("#DDDDDD"));
        gridPaint.setStrokeWidth(dp(1));
        gridPaint.setAntiAlias(false);
        platformPaint.setColor(Color.parseColor("#CCCCCC"));
        bubbleBgPaint.setColor(Color.parseColor("#CCFFFFFF"));
        bubbleBgPaint.setAntiAlias(true);
        bubbleStrokePaint.setColor(Color.parseColor("#66000000"));
        bubbleStrokePaint.setStyle(Paint.Style.STROKE);
        bubbleStrokePaint.setStrokeWidth(dp(1));
        bubbleStrokePaint.setAntiAlias(true);
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(dp(12));
        textPaint.setAntiAlias(true);
        playerSize = dp(32);
        platformHeight = dp(48);
    }

    public void setListener(GameListener listener) {
        this.listener = listener;
    }

    public void startNewSession() {
        started = true;
        resetState();
        startLoop();
    }

    public void pauseGame() {
        if (!started) return;
        pauseStartedMs = SystemClock.elapsedRealtime();
        stopLoop();
    }

    public void resumeGame() {
        if (!started || gameOver) return;
        if (pauseStartedMs > 0) {
            long pausedFor = SystemClock.elapsedRealtime() - pauseStartedMs;
            startTimeMs += pausedFor;
            pauseStartedMs = 0;
        }
        if (!gameOver) {
            startLoop();
        }
    }

    public void stopGame() {
        stopLoop();
    }

    private void startLoop() {
        if (running) return;
        SurfaceHolder holder = getHolder();
        if (holder == null || holder.getSurface() == null || !holder.getSurface().isValid()) {
            return;
        }
        stopLoop();
        running = true;
        gameThread = new GameThread();
        gameThread.start();
    }

    private void stopLoop() {
        running = false;
        if (gameThread != null && gameThread != Thread.currentThread()) {
            try {
                gameThread.join(300);
            } catch (InterruptedException ignored) {
            }
            gameThread = null;
        }
    }

    private void resetState() {
        objects.clear();
        gameOver = false;
        plateSpawned = false;
        shieldHits = 0;
        helmetOnHead = false;
        hadHelmet = false;
        deadPose = false;
        dropHelmetOnGround = false;
        message60Shown = false;
        message120Shown = false;
        bubbleText = null;
        bubbleUntilMs = 0;
        notifyShieldChanged();
        long now = SystemClock.elapsedRealtime();
        startTimeMs = now;
        lastSpawnMs = now;
        lastHelmetSpawnMs = now;
        lastTickMs = 0;
        if (getWidth() > 0 && getHeight() > 0) {
            playerX = getWidth() / 2f;
            playerY = getHeight() - platformHeight - playerSize * 1.0f;
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (started && !running) {
            startLoop();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        playerY = height - platformHeight - playerSize * 1.0f;
        if (playerX == 0) {
            playerX = width / 2f;
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stopLoop();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_MOVE || event.getAction() == MotionEvent.ACTION_DOWN) {
            playerX = Math.max(playerSize / 2f, Math.min(event.getX(), getWidth() - playerSize / 2f));
        }
        return true;
    }

    private void updateGame(float deltaSeconds) {
        if (gameOver) return;

        long now = SystemClock.elapsedRealtime();
        long elapsedMs = now - startTimeMs;

        // notify timer throttled to reduce UI chatter
        if (listener != null && (lastTickMs == 0 || now - lastTickMs > 100)) {
            lastTickMs = now;
            listener.onTick(elapsedMs);
        }

        // Faster ramp: by ~60s already fast, total session ~3 min target
        float difficulty = 1f + Math.min(elapsedMs / 1000f / 40f, 7f);
        // Denser rain of bricks (higher frequency)
        float spawnInterval = Math.max(110f, 600f - (elapsedMs / 650f));

        if (now - lastSpawnMs > spawnInterval) {
            spawnBrick(elapsedMs, difficulty);
            lastSpawnMs = now;
        }

        if (!plateSpawned && elapsedMs >= 180_000) {
            spawnPlate();
            plateSpawned = true;
        }

        if (now - lastHelmetSpawnMs > 9000 && random.nextFloat() < 0.12f) {
            spawnHelmet(elapsedMs);
            lastHelmetSpawnMs = now;
        }

        if (!message60Shown && elapsedMs >= 60_000) {
            bubbleText = "Бахнуть бы кофейку";
            bubbleUntilMs = now + 2000;
            message60Shown = true;
        } else if (!message120Shown && elapsedMs >= 120_000) {
            bubbleText = "Лучше бы бухгалтером стал";
            bubbleUntilMs = now + 2000;
            message120Shown = true;
        }

        Iterator<FallingObject> iterator = objects.iterator();
        float playerHalf = playerSize / 2f;
        RectF playerRect = new RectF(
                playerX - playerHalf,
                playerY - playerSize,
                playerX + playerHalf,
                playerY + playerSize
        );

        while (iterator.hasNext()) {
            FallingObject obj = iterator.next();
            obj.y += obj.speed * deltaSeconds;

            if (obj.y - obj.height > getHeight()) {
                iterator.remove();
                continue;
            }

            RectF objRect = new RectF(obj.x, obj.y, obj.x + obj.width, obj.y + obj.height);
            if (RectF.intersects(playerRect, objRect)) {
                if (obj.helmet) {
                    shieldHits = 2; // two safe collisions
                    helmetOnHead = true;
                    hadHelmet = true;
                    notifyShieldChanged();
                    iterator.remove();
                    continue;
                }
                if (obj.plate) {
                    deadPose = true;
                    if (helmetOnHead || hadHelmet) {
                        dropHelmetOnGround = true;
                        dropHelmetX = playerX;
                        dropHelmetY = playerY + playerSize * 0.2f;
                        helmetOnHead = false;
                        hadHelmet = false;
                        notifyShieldChanged();
                    }
                    triggerGameOver(true);
                    return;
                }
                if (shieldHits > 0) {
                    shieldHits--;
                    notifyShieldChanged();
                    iterator.remove();
                    // helmet stays visible; drop on next lethal hit
                } else {
                    deadPose = true;
                    if (helmetOnHead || hadHelmet) {
                        dropHelmetOnGround = true;
                        dropHelmetX = playerX;
                        dropHelmetY = playerY + playerSize * 0.2f;
                        helmetOnHead = false;
                        hadHelmet = false;
                        notifyShieldChanged();
                    }
                    triggerGameOver(false);
                    return;
                }
            }
        }
    }

    private void triggerGameOver(boolean endedByPlate) {
        if (gameOver) return;
        gameOver = true;
        long durationMs = SystemClock.elapsedRealtime() - startTimeMs;
        // Render final pose before stopping
        drawFrame();
        stopLoop();
        if (listener != null) {
            listener.onGameOver(durationMs, endedByPlate);
        }
    }

    private void spawnBrick(long elapsedMs, float difficulty) {
        if (getWidth() == 0) return;
        FallingObject brick = new FallingObject();
        brick.width = dp(28);
        brick.height = dp(18);
        brick.x = random.nextFloat() * Math.max(1, getWidth() - brick.width);
        brick.y = -brick.height;
        float baseSpeed = dp(210);
        brick.speed = baseSpeed * difficulty + (elapsedMs / 1000f) * dp(3.5f);
        brick.helmet = false;
        brick.plate = false;
        objects.add(brick);
    }

    private void spawnHelmet(long elapsedMs) {
        if (getWidth() == 0) return;
        FallingObject helmet = new FallingObject();
        helmet.width = dp(20);
        helmet.height = dp(14);
        helmet.x = random.nextFloat() * Math.max(1, getWidth() - helmet.width);
        helmet.y = -helmet.height;
        float baseSpeed = dp(140);
        helmet.speed = baseSpeed + (elapsedMs / 1000f) * dp(1);
        helmet.helmet = true;
        helmet.plate = false;
        objects.add(helmet);
    }

    private void spawnPlate() {
        FallingObject plate = new FallingObject();
        plate.width = getWidth();
        plate.height = dp(80);
        plate.x = 0;
        plate.y = -plate.height;
        plate.speed = dp(480);
        plate.helmet = false;
        plate.plate = true;
        objects.add(plate);
    }

    private void drawFrame() {
        SurfaceHolder holder = getHolder();
        Canvas canvas = holder.lockCanvas();
        if (canvas == null) return;
        try {
            canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);
            drawBackgroundTiles(canvas);
            // platform
            float ph = platformHeight;
            canvas.drawRect(0, getHeight() - ph, getWidth(), getHeight(), platformPaint);

            // Player as simple stick figure
            if (deadPose) {
                drawPlayerHorizontal(canvas);
            } else {
                drawPlayerStanding(canvas);
            }

            for (FallingObject obj : objects) {
                if (obj.helmet) {
                    drawHelmet(canvas, obj);
                } else {
                    if (obj.plate) {
                        drawPlate(canvas, obj);
                    } else {
                        canvas.drawRect(obj.x, obj.y, obj.x + obj.width, obj.y + obj.height, brickPaint);
                    }
                }
            }

            if (dropHelmetOnGround) {
                drawHelmetAt(canvas, dropHelmetX - dp(10), dropHelmetY + dp(12), dp(28), dp(18));
            }
            drawBubbleIfNeeded(canvas);
        } finally {
            holder.unlockCanvasAndPost(canvas);
        }
    }

    private void drawBackgroundTiles(Canvas canvas) {
        float step = dp(24);
        float tileW = step * 0.5f;
        float tileH = step * 0.45f;
        int height = getHeight();
        int width = getWidth();
        for (float y = 0; y < height; y += step) {
            float startX = (((int) (y / step)) % 2 == 0) ? 0 : step / 2f;
            for (float x = startX; x < width; x += step) {
                canvas.drawRect(x, y, x + tileW, y + tileH, tilePaint);
            }
        }
        // sparse vertical lines to hint scaffolding
        for (float x = 0; x < width; x += step * 2f) {
            canvas.drawLine(x, 0, x, height, gridPaint);
        }
    }

    private void drawHelmet(Canvas canvas, FallingObject obj) {
        float radius = Math.min(obj.width, obj.height) * 0.4f;
        float cx = obj.x + obj.width / 2f;
        float cy = obj.y + obj.height * 0.55f;
        // Dome
        canvas.drawRoundRect(obj.x, obj.y, obj.x + obj.width, obj.y + obj.height * 0.8f, radius, radius, helmetPaint);
        // Brim
        float brimY = obj.y + obj.height * 0.8f;
        float brimPad = obj.width * 0.1f;
        canvas.drawRect(obj.x - brimPad, brimY, obj.x + obj.width + brimPad, brimY + obj.height * 0.2f, helmetPaint);
        // Ridge line
        canvas.drawLine(cx, obj.y, cx, brimY, helmetPaint);
    }

    private void drawHelmetAt(Canvas canvas, float x, float y, float width, float height) {
        float radius = Math.min(width, height) * 0.4f;
        float cx = x + width / 2f;
        float domeHeight = height * 0.8f;
        canvas.drawRoundRect(x, y, x + width, y + domeHeight, radius, radius, helmetPaint);
        float brimY = y + domeHeight;
        float brimPad = width * 0.1f;
        canvas.drawRect(x - brimPad, brimY, x + width + brimPad, brimY + height * 0.2f, helmetPaint);
        canvas.drawLine(cx, y, cx, brimY, helmetPaint);
    }

    private void drawPlate(Canvas canvas, FallingObject obj) {
        canvas.drawRect(obj.x, obj.y, obj.x + obj.width, obj.y + obj.height, platePaint);
        // Text on plate
        Paint.Align oldAlign = textPaint.getTextAlign();
        float oldSize = textPaint.getTextSize();
        int oldColor = textPaint.getColor();
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(dp(16));
        textPaint.setColor(Color.WHITE);
        float textX = obj.x + obj.width / 2f;
        float textY = obj.y + obj.height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f;
        canvas.drawText("Memento mori", textX, textY, textPaint);
        textPaint.setTextAlign(oldAlign);
        textPaint.setTextSize(oldSize);
        textPaint.setColor(oldColor);
    }

    private void drawPlayerStanding(Canvas canvas) {
        float headRadius = playerSize * 0.35f;
        float bodyHeight = playerSize * 0.9f;
        float centerX = playerX;
        float headCenterY = playerY - bodyHeight;
        float bodyTopY = headCenterY + headRadius;
        float bodyBottomY = playerY + playerSize * 0.2f;
        canvas.drawCircle(centerX, headCenterY, headRadius, playerPaint); // head
        canvas.drawLine(centerX, bodyTopY, centerX, bodyBottomY, playerPaint); // torso
        float armY = bodyTopY + bodyHeight * 0.25f;
        float armSpan = playerSize * 0.6f;
        canvas.drawLine(centerX - armSpan / 2f, armY, centerX + armSpan / 2f, armY, playerPaint); // arms
        float legSpan = playerSize * 0.4f;
        canvas.drawLine(centerX, bodyBottomY, centerX - legSpan, bodyBottomY + playerSize * 0.6f, playerPaint); // left leg
        canvas.drawLine(centerX, bodyBottomY, centerX + legSpan, bodyBottomY + playerSize * 0.6f, playerPaint); // right leg
        if (helmetOnHead) {
            float headDiameter = headRadius * 2f;
            drawHelmetAt(canvas, centerX - headRadius, headCenterY - headRadius * 1.2f, headDiameter, headRadius * 1.6f);
        }
    }

    private void drawPlayerHorizontal(Canvas canvas) {
        float headRadius = playerSize * 0.35f;
        float bodyLength = playerSize * 1.1f;
        float centerX = playerX;
        float centerY = playerY + playerSize * 0.5f;
        float headCenterX = centerX - bodyLength * 0.3f;
        float headCenterY = centerY;
        float bodyLeftX = headCenterX + headRadius;
        float bodyRightX = bodyLeftX + bodyLength;
        canvas.drawCircle(headCenterX, headCenterY, headRadius, playerPaint); // head
        canvas.drawLine(bodyLeftX, centerY, bodyRightX, centerY, playerPaint); // torso
        float armX = bodyLeftX + bodyLength * 0.3f;
        float armSpan = playerSize * 0.6f;
        canvas.drawLine(armX, centerY, armX, centerY - armSpan / 2f, playerPaint);
        canvas.drawLine(armX, centerY, armX, centerY + armSpan / 2f, playerPaint);
        float legX = bodyRightX;
        canvas.drawLine(legX, centerY, legX + playerSize * 0.6f, centerY - playerSize * 0.2f, playerPaint);
        canvas.drawLine(legX, centerY, legX + playerSize * 0.6f, centerY + playerSize * 0.2f, playerPaint);
    }

    private void drawBubbleIfNeeded(Canvas canvas) {
        if (bubbleText == null || bubbleUntilMs == 0) return;
        long now = SystemClock.elapsedRealtime();
        if (now > bubbleUntilMs) return;
        float textSizeOld = textPaint.getTextSize();
        Paint.Align oldAlign = textPaint.getTextAlign();
        textPaint.setTextSize(dp(14));
        textPaint.setTextAlign(Paint.Align.LEFT);
        float padding = dp(8);
        float textWidth = textPaint.measureText(bubbleText);
        float textHeight = textPaint.descent() - textPaint.ascent();
        float boxW = textWidth + padding * 2;
        float boxH = textHeight + padding * 2;
        float boxX = playerX - boxW / 2f;
        float boxY = playerY - playerSize * 1.6f - boxH;
        if (boxX < dp(4)) boxX = dp(4);
        if (boxX + boxW > getWidth() - dp(4)) boxX = getWidth() - dp(4) - boxW;
        if (boxY < dp(8)) boxY = dp(8);
        canvas.drawRoundRect(boxX, boxY, boxX + boxW, boxY + boxH, dp(10), dp(10), bubbleBgPaint);
        canvas.drawRoundRect(boxX, boxY, boxX + boxW, boxY + boxH, dp(10), dp(10), bubbleStrokePaint);
        canvas.drawText(bubbleText, boxX + padding, boxY + padding - textPaint.ascent(), textPaint);
        textPaint.setTextSize(textSizeOld);
        textPaint.setTextAlign(oldAlign);
    }

    private void notifyShieldChanged() {
        if (listener != null) {
            listener.onShieldChanged(shieldHits);
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private class GameThread extends Thread {
        @Override
        public void run() {
            long previous = SystemClock.elapsedRealtime();
            while (running) {
                long now = SystemClock.elapsedRealtime();
                float delta = (now - previous) / 1000f;
                if (delta > 0.05f) delta = 0.05f; // clamp to avoid jumps
                updateGame(delta);
                drawFrame();
                previous = now;
                try {
                    Thread.sleep(16);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }
}

