package by.instruction.papera.tetris;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.Random;

import by.instruction.papera.R;

public class TetrisActivity extends AppCompatActivity {

    private TetrisView tetrisView;
    private final Handler handler = new Handler();
    private final long TICK_MS = 550;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (tetrisView != null) {
                tetrisView.tick();
                handler.postDelayed(this, TICK_MS);
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tetris);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        tetrisView = findViewById(R.id.tetris_view);

        Button btnLeft = findViewById(R.id.btn_left);
        Button btnRight = findViewById(R.id.btn_right);
        Button btnRotate = findViewById(R.id.btn_rotate);
        Button btnDown = findViewById(R.id.btn_down);

        btnLeft.setOnClickListener(v -> tetrisView.move(-1, 0));
        btnRight.setOnClickListener(v -> tetrisView.move(1, 0));
        btnRotate.setOnClickListener(v -> tetrisView.rotate());
        btnDown.setOnClickListener(v -> tetrisView.softDrop());

        tetrisView.setGameOverListener(() -> runOnUiThread(() ->
                Toast.makeText(this, "Игра окончена", Toast.LENGTH_SHORT).show()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (tetrisView != null) {
            tetrisView.reset();
        }
        handler.postDelayed(tick, TICK_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacksAndMessages(null);
    }

    public static class TetrisView extends View {
        private static final int WIDTH = 10;
        private static final int HEIGHT = 20;

        private final int[][] board = new int[HEIGHT][WIDTH];
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random rnd = new Random();

        private final int[][][] shapes = {
                {{1, 1, 1, 1}},                    // I
                {{1, 1}, {1, 1}},                 // O
                {{0, 1, 0}, {1, 1, 1}},           // T
                {{1, 0, 0}, {1, 1, 1}},           // J
                {{0, 0, 1}, {1, 1, 1}},           // L
                {{1, 1, 0}, {0, 1, 1}},           // S
                {{0, 1, 1}, {1, 1, 0}}            // Z
        };

        private final int[] colors = {
                Color.TRANSPARENT,
                Color.parseColor("#EF5350"),
                Color.parseColor("#AB47BC"),
                Color.parseColor("#42A5F5"),
                Color.parseColor("#26A69A"),
                Color.parseColor("#FFCA28"),
                Color.parseColor("#FFA726"),
                Color.parseColor("#8D6E63")
        };

        private int[][] active;
        private int activeX;
        private int activeY;
        private int activeColor;

        private Runnable gameOverListener;

        public TetrisView(Context context, @Nullable AttributeSet attrs) {
            super(context, attrs);
            paint.setStyle(Paint.Style.FILL);
            reset();
        }

        public TetrisView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);
            paint.setStyle(Paint.Style.FILL);
            reset();
        }

        public void setGameOverListener(Runnable r) {
            this.gameOverListener = r;
        }

        public void reset() {
            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x < WIDTH; x++) {
                    board[y][x] = 0;
                }
            }
            spawn();
            invalidate();
        }

        private void spawn() {
            int idx = rnd.nextInt(shapes.length);
            active = shapes[idx];
            activeColor = idx + 1;
            activeX = WIDTH / 2 - active[0].length / 2;
            activeY = -getTopOffset();
            if (collides(activeX, activeY, active)) {
                // game over
                if (gameOverListener != null) gameOverListener.run();
                reset();
            }
        }

        private int getTopOffset() {
            // how many rows above origin (pieces arrays start at 0)
            return 0;
        }

        public void tick() {
            if (!move(0, 1)) {
                lockPiece();
                clearLines();
                spawn();
            }
            invalidate();
        }

        public boolean move(int dx, int dy) {
            if (collides(activeX + dx, activeY + dy, active)) {
                return false;
            }
            activeX += dx;
            activeY += dy;
            invalidate();
            return true;
        }

        public void rotate() {
            int[][] rotated = rotateMatrix(active);
            if (!collides(activeX, activeY, rotated)) {
                active = rotated;
                invalidate();
            }
        }

        public void softDrop() {
            while (move(0, 1)) {
                // drop until collision
            }
        }

        private int[][] rotateMatrix(int[][] m) {
            int h = m.length;
            int w = m[0].length;
            int[][] res = new int[w][h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    res[x][h - 1 - y] = m[y][x];
                }
            }
            return res;
        }

        private boolean collides(int px, int py, int[][] shape) {
            int h = shape.length;
            int w = shape[0].length;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (shape[y][x] == 0) continue;
                    int bx = px + x;
                    int by = py + y;
                    if (bx < 0 || bx >= WIDTH || by >= HEIGHT) return true;
                    if (by >= 0 && board[by][bx] != 0) return true;
                }
            }
            return false;
        }

        private void lockPiece() {
            int h = active.length;
            int w = active[0].length;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (active[y][x] == 0) continue;
                    int by = activeY + y;
                    int bx = activeX + x;
                    if (by >= 0 && by < HEIGHT && bx >= 0 && bx < WIDTH) {
                        board[by][bx] = activeColor;
                    }
                }
            }
        }

        private void clearLines() {
            int write = HEIGHT - 1;
            for (int y = HEIGHT - 1; y >= 0; y--) {
                boolean full = true;
                for (int x = 0; x < WIDTH; x++) {
                    if (board[y][x] == 0) {
                        full = false;
                        break;
                    }
                }
                if (!full) {
                    if (write != y) {
                        System.arraycopy(board[y], 0, board[write], 0, WIDTH);
                    }
                    write--;
                }
            }
            while (write >= 0) {
                for (int x = 0; x < WIDTH; x++) board[write][x] = 0;
                write--;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int cell = Math.min(getWidth() / WIDTH, getHeight() / HEIGHT);
            int offsetX = (getWidth() - cell * WIDTH) / 2;
            int offsetY = (getHeight() - cell * HEIGHT) / 2;

            // background
            paint.setColor(Color.WHITE);
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);

            // grid
            paint.setColor(Color.parseColor("#EEEEEE"));
            paint.setStrokeWidth(1);
            for (int x = 0; x <= WIDTH; x++) {
                canvas.drawLine(offsetX + x * cell, offsetY,
                        offsetX + x * cell, offsetY + HEIGHT * cell, paint);
            }
            for (int y = 0; y <= HEIGHT; y++) {
                canvas.drawLine(offsetX, offsetY + y * cell,
                        offsetX + WIDTH * cell, offsetY + y * cell, paint);
            }

            // board
            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x < WIDTH; x++) {
                    int colorIdx = board[y][x];
                    if (colorIdx != 0) {
                        drawBlock(canvas, offsetX, offsetY, cell, x, y, colors[colorIdx]);
                    }
                }
            }

            // active piece
            int h = active.length;
            int w = active[0].length;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (active[y][x] == 0) continue;
                    int gx = activeX + x;
                    int gy = activeY + y;
                    if (gy >= 0) {
                        drawBlock(canvas, offsetX, offsetY, cell, gx, gy, colors[activeColor]);
                    }
                }
            }
        }

        private void drawBlock(Canvas canvas, int offsetX, int offsetY, int cell, int gx, int gy, int color) {
            paint.setColor(color);
            int left = offsetX + gx * cell;
            int top = offsetY + gy * cell;
            canvas.drawRect(left, top, left + cell, top + cell, paint);
            paint.setColor(Color.parseColor("#22000000"));
            canvas.drawRect(left, top, left + cell, top + cell, paint);
        }
    }
}


