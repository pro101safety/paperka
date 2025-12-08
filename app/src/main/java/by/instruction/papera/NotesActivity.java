package by.instruction.papera;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NotesActivity extends AppCompatActivity {
    private static final long MIN_FREE_SPACE_BYTES = 5L * 1024 * 1024; // 5 MB запас

    private RecyclerView recyclerView;
    private NoteGridAdapter adapter;
    private List<Note> notes = new ArrayList<>();
    private List<Note> filteredNotes = new ArrayList<>();
    private ActivityResultLauncher<Uri> takePictureLauncher;
    private ActivityResultLauncher<String> cameraPermissionLauncher;
    private Uri pendingPhotoUri;
    private String pendingPhotoPath;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes);

        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), true);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.getMenu().clear();
            toolbar.inflateMenu(R.menu.notes_menu);
            toolbar.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.action_search) {
                    showSearchDialog();
                    return true;
                }
                return false;
            });
        }

        initActivityResultLaunchers();

        FloatingActionButton fab = findViewById(R.id.fab_add_note);
        if (fab != null) {
            fab.setVisibility(View.VISIBLE);
            fab.setOnClickListener(v -> showAddTypeDialog());
        }

        recyclerView = findViewById(R.id.recyclerView);
        if (recyclerView != null) {
            int spanCount = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE ? 3 : 2;
            recyclerView.setLayoutManager(new GridLayoutManager(this, spanCount));
            recyclerView.addItemDecoration(new SpacingItemDecoration(getResources().getDimensionPixelSize(R.dimen.note_tile_spacing)));
            loadNotes();
            adapter = new NoteGridAdapter();
            recyclerView.setAdapter(adapter);
        }
    }

    private void initActivityResultLaunchers() {
        takePictureLauncher = registerForActivityResult(new ActivityResultContracts.TakePicture(), result -> {
            if (Boolean.TRUE.equals(result) && pendingPhotoPath != null) {
                showPhotoNoteDialog(pendingPhotoPath);
            } else {
                deleteFileSilently(pendingPhotoPath);
                pendingPhotoPath = null;
                pendingPhotoUri = null;
            }
        });

        cameraPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (Boolean.TRUE.equals(isGranted)) {
                launchCamera();
            } else {
                Toast.makeText(this, "Доступ к камере отклонён", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadNotes() {
        notes.clear();
        notes.addAll(NoteStore.getAllNotes(this));
        // Сортируем по времени: новые сверху
        Collections.sort(notes, (a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        filteredNotes.clear();
        filteredNotes.addAll(notes);
    }

    private void applyFilter(String query) {
        String searchText = (query == null) ? "" : query.trim().toLowerCase();
        filteredNotes.clear();
        
        if (searchText.isEmpty()) {
            filteredNotes.addAll(notes);
        } else {
            for (Note note : notes) {
                String title = note.getTitle() == null ? "" : note.getTitle().toLowerCase();
                String content = note.getContent() == null ? "" : note.getContent().toLowerCase();
                if (title.contains(searchText) || content.contains(searchText)) {
                    filteredNotes.add(note);
                }
            }
        }
        
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }
    
    // Диалог поиска
    private void showSearchDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Поиск по заметкам");
        
        // Создаем EditText для ввода поискового запроса
        final EditText input = new EditText(this);
        input.setHint("Введите текст для поиска");
        input.setSingleLine(true);
        
        // Устанавливаем размеры EditText
        android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(50, 20, 50, 20);
        input.setLayoutParams(params);
        
        builder.setView(input);
        
        builder.setPositiveButton("Поиск", (dialog, which) -> {
            String query = input.getText().toString().trim();
            if (!query.isEmpty()) {
                applyFilter(query);
            }
        });
        
        builder.setNegativeButton("Отмена", (dialog, which) -> dialog.cancel());
        
        // Кнопка "Очистить"
        builder.setNeutralButton("Очистить", (dialog, which) -> {
            // Очищаем результаты поиска
            filteredNotes.clear();
            filteredNotes.addAll(notes);
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        });
        
        AlertDialog dialog = builder.create();
        dialog.show();
        
        // Фокус на поле ввода
        input.requestFocus();
        input.selectAll();
    }

    private void showAddTypeDialog() {
        String[] actions = new String[]{"Текстовая заметка", "Фото заметка"};
        new AlertDialog.Builder(this)
                .setTitle("Создать")
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        showNoteDialog(null);
                    } else {
                        startPhotoNoteFlow();
                    }
                })
                .show();
    }

    private void startPhotoNoteFlow() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchCamera() {
        if (!hasEnoughStorageForPhoto()) {
            Toast.makeText(this, "Недостаточно памяти для сохранения фото", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File imageFile = createImageFile();
            pendingPhotoPath = imageFile.getAbsolutePath();
            pendingPhotoUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    imageFile
            );
            takePictureLauncher.launch(pendingPhotoUri);
        } catch (IOException e) {
            Toast.makeText(this, "Не удалось открыть камеру", Toast.LENGTH_SHORT).show();
            pendingPhotoPath = null;
            pendingPhotoUri = null;
        }
    }

    private File createImageFile() throws IOException {
        File storageDir = new File(getFilesDir(), "photos");
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }
        String fileName = "note_" + System.currentTimeMillis();
        return File.createTempFile(fileName, ".jpg", storageDir);
    }

    private boolean hasEnoughStorageForPhoto() {
        File dir = getFilesDir();
        return dir != null && dir.getUsableSpace() > MIN_FREE_SPACE_BYTES;
    }

    private void showNoteDialog(Note note) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(note == null ? "Новая заметка" : "Редактировать заметку");

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_note_edit, null);
        EditText titleEdit = dialogView.findViewById(R.id.edit_title);
        EditText contentEdit = dialogView.findViewById(R.id.edit_content);

        if (note != null) {
            titleEdit.setText(note.getTitle());
            contentEdit.setText(note.getContent());
        }

        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        
        dialog.setButton(DialogInterface.BUTTON_POSITIVE, "Сохранить", (d, which) -> {
            // Этот обработчик будет вызван, но мы обработаем валидацию внутри
        });
        
        dialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Отмена", (d, which) -> {
            dialog.dismiss();
        });

        dialog.show();
        
        // Переопределяем обработчик кнопки "Сохранить" после показа диалога
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = titleEdit.getText().toString().trim();
            String content = contentEdit.getText().toString().trim();

            if (title.isEmpty()) {
                Toast.makeText(this, "Введите название заметки", Toast.LENGTH_SHORT).show();
                return;
            }

            if (note == null) {
                // Создаём новую заметку
                Note newNote = new Note(
                    NoteStore.generateId(),
                    title,
                    content,
                    System.currentTimeMillis(),
                    Note.Type.TEXT,
                    null
                );
                NoteStore.saveNote(this, newNote);
            } else {
                // Обновляем существующую заметку
                note.setTitle(title);
                note.setContent(content);
                note.setTimestamp(System.currentTimeMillis()); // Обновляем время при редактировании
                if (note.getType() == null) {
                    note.setType(Note.Type.TEXT);
                }
                NoteStore.saveNote(this, note);
            }

            loadNotes();
            adapter.notifyDataSetChanged();
            dialog.dismiss();
        });
    }

    private void showPhotoNoteDialog(String imagePath) {
        if (imagePath == null) {
            return;
        }

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_photo_note, null, false);
        ImageView preview = dialogView.findViewById(R.id.photo_preview);
        EditText titleEdit = dialogView.findViewById(R.id.edit_title);
        EditText captionEdit = dialogView.findViewById(R.id.edit_caption);

        Bitmap bitmap = decodeSampledBitmap(imagePath, 600, 600);
        if (bitmap != null) {
            preview.setImageBitmap(bitmap);
        } else {
            preview.setImageResource(R.drawable.ic_photo_placeholder);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Подпись к фото")
                .setView(dialogView)
                .setPositiveButton("Сохранить", null)
                .setNegativeButton("Отмена", (d, which) -> {
                    deleteFileSilently(imagePath);
                    pendingPhotoPath = null;
                    pendingPhotoUri = null;
                })
                .create();

        dialog.show();

        dialog.setOnCancelListener(d -> {
            deleteFileSilently(imagePath);
            pendingPhotoPath = null;
            pendingPhotoUri = null;
        });

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = titleEdit.getText().toString().trim();
            String caption = captionEdit.getText().toString().trim();

            if (title.isEmpty()) {
                Toast.makeText(this, "Введите название заметки", Toast.LENGTH_SHORT).show();
                return;
            }

            Note photoNote = new Note(
                    NoteStore.generateId(),
                    title,
                    caption,
                    System.currentTimeMillis(),
                    Note.Type.PHOTO,
                    imagePath
            );
            NoteStore.saveNote(this, photoNote);
            pendingPhotoPath = null;
            pendingPhotoUri = null;
            loadNotes();
            adapter.notifyDataSetChanged();
            dialog.dismiss();
        });
    }

    private void showDeleteDialog(Note note) {
        new AlertDialog.Builder(this)
            .setTitle("Удалить заметку?")
            .setMessage(note.getTitle())
            .setPositiveButton("Удалить", (dialog, which) -> {
                NoteStore.deleteNote(this, note.getId());
                if (note.hasPhoto()) {
                    deleteFileSilently(note.getImagePath());
                }
                loadNotes();
                adapter.notifyDataSetChanged();
                Toast.makeText(this, "Заметка удалена", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return false;
    }

    private class NoteGridAdapter extends RecyclerView.Adapter<NoteGridAdapter.NoteViewHolder> {

        @NonNull
        @Override
        public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_note_card, parent, false);
            return new NoteViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
            holder.bind(filteredNotes.get(position));
        }

        @Override
        public int getItemCount() {
            return filteredNotes.size();
        }

        class NoteViewHolder extends RecyclerView.ViewHolder {
            private final TextView title;
            private final TextView content;
            private final TextView dateTime;
            private final ImageView photo;

            NoteViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.title);
                content = itemView.findViewById(R.id.content);
                dateTime = itemView.findViewById(R.id.date_time);
                photo = itemView.findViewById(R.id.photo);

                itemView.setOnClickListener(v -> {
                    int position = getBindingAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        Note note = filteredNotes.get(position);
                        if (note.getType() == Note.Type.PHOTO) {
                            showPhotoDetails(note);
                        } else {
                            showNoteDialog(note);
                        }
                    }
                });

                itemView.setOnLongClickListener(v -> {
                    int position = getBindingAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        Note note = filteredNotes.get(position);
                        showDeleteDialog(note);
                    }
                    return true;
                });
            }

            void bind(Note note) {
                title.setText(note.getTitle());

                String noteContent = note.getContent();
                if (noteContent != null && !noteContent.isEmpty()) {
                    if (noteContent.length() > 100) {
                        content.setText(noteContent.substring(0, 100) + "...");
                    } else {
                        content.setText(noteContent);
                    }
                    content.setVisibility(View.VISIBLE);
                } else {
                    content.setVisibility(View.GONE);
                }

                dateTime.setText(note.getFormattedDateTime());

                if (note.hasPhoto()) {
                    photo.setVisibility(View.VISIBLE);
                    Bitmap bitmap = decodeSampledBitmap(note.getImagePath(), 600, 400);
                    if (bitmap != null) {
                        photo.setImageBitmap(bitmap);
                    } else {
                        photo.setImageResource(R.drawable.ic_photo_placeholder);
                    }
                } else {
                    photo.setImageDrawable(null);
                    photo.setVisibility(View.GONE);
                }
            }
        }
    }

    private void showPhotoDetails(Note note) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_photo_note, null, false);
        ImageView preview = view.findViewById(R.id.photo_preview);
        EditText titleEdit = view.findViewById(R.id.edit_title);
        EditText captionEdit = view.findViewById(R.id.edit_caption);

        titleEdit.setText(note.getTitle());
        captionEdit.setText(note.getContent());
        Bitmap bitmap = decodeSampledBitmap(note.getImagePath(), 800, 800);
        if (bitmap != null) {
            preview.setImageBitmap(bitmap);
        } else {
            preview.setImageResource(R.drawable.ic_photo_placeholder);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Фото заметка")
                .setView(view)
                .setPositiveButton("Сохранить", null)
                .setNegativeButton("Отмена", null)
                .create();

        dialog.show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = titleEdit.getText().toString().trim();
            String caption = captionEdit.getText().toString().trim();

            if (title.isEmpty()) {
                Toast.makeText(this, "Введите название заметки", Toast.LENGTH_SHORT).show();
                return;
            }

            note.setTitle(title);
            note.setContent(caption);
            note.setTimestamp(System.currentTimeMillis());
            note.setType(Note.Type.PHOTO);
            NoteStore.saveNote(this, note);
            loadNotes();
            adapter.notifyDataSetChanged();
            dialog.dismiss();
        });
    }

    private Bitmap decodeSampledBitmap(String path, int reqWidth, int reqHeight) {
        if (path == null) {
            return null;
        }
        File file = new File(path);
        if (!file.exists()) {
            return null;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(path, options);
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return Math.max(1, inSampleSize);
    }

    private void deleteFileSilently(String path) {
        if (path == null) {
            return;
        }
        try {
            File file = new File(path);
            if (file.exists()) {
                file.delete();
            }
        } catch (Exception ignored) { }
    }

    private static class SpacingItemDecoration extends RecyclerView.ItemDecoration {
        private final int spacing;

        SpacingItemDecoration(int spacing) {
            this.spacing = spacing;
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            RecyclerView.LayoutManager layoutManager = parent.getLayoutManager();
            int spanCount = 1;
            if (layoutManager instanceof GridLayoutManager) {
                spanCount = ((GridLayoutManager) layoutManager).getSpanCount();
            }
            int position = parent.getChildAdapterPosition(view);
            outRect.left = spacing;
            outRect.right = spacing;
            outRect.bottom = spacing;
            if (position == RecyclerView.NO_POSITION || position < spanCount) {
                outRect.top = spacing;
            } else {
                outRect.top = spacing / 2;
            }
        }
    }
}

