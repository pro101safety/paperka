package by.instruction.papera;

import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.BodyElementType;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

// Класс для хранения информации о найденном результате поиска
class DocSearchResult {
    final int index;
    final String text;
    final int position;
    final int page;
    
    DocSearchResult(int index, String text, int position, int page) {
        this.index = index;
        this.text = text;
        this.position = position;
        this.page = page;
    }
    
    public int getIndex() {
        return index;
    }
    
    public String getText() {
        return text;
    }
    
    public int getPosition() {
        return position;
    }
    
    public int getPage() {
        return page;
    }
}

public class FullView extends AppCompatActivity {

    private String fileName;
    private String docTitle;
    private int jumpToPage = -1; // Страница для перехода при открытии из закладки
    private WebView webView;
    
    // Кнопки навигации по результатам поиска
    private LinearLayout searchNavigationLayout;
    private ImageButton btnSearchPrev;
    private ImageButton btnSearchNext;

    private DocSearchTask currentTask;
    
    // Индикатор страниц
    private TextView pageIndicator;
    private int totalPages = 1;
    private int currentPage = 1;
    private long lastScrollTime = 0;
    private static final long SCROLL_THROTTLE_MS = 100;
    
    // Настройки поиска
    private boolean caseSensitive = false;
    private boolean wholeWordsOnly = false;
    
    // Навигация по результатам поиска
    private List<DocSearchResult> searchResults = new ArrayList<>();
    private int currentResultIndex = -1;
    private String lastSearchQuery = "";
    private String documentContent = "";
    private String documentHtml = "";
	private String tempHtmlFilePath = null;
    
    // Прогресс загрузки
    private ProgressBar loadingProgress;
    
    // Кэш для оптимизации
    private static final Map<String, String> documentCache = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_full_view);

        webView = findViewById(R.id.webView);
        
        // Инициализация кнопок навигации
        searchNavigationLayout = findViewById(R.id.searchNavigationLayout);
        btnSearchPrev = findViewById(R.id.btnSearchPrev);
        btnSearchNext = findViewById(R.id.btnSearchNext);
        
        // Инициализация индикатора страниц
        pageIndicator = findViewById(R.id.pageIndicator);
        initializePageIndicator();
        
        // Настройка обработчика касаний для индикатора
        setupPageIndicatorTouchListener();
        
        // Инициализация прогресса загрузки
        loadingProgress = findViewById(R.id.loadingProgress);
        
        // Настройка обработчиков для кнопок навигации
        btnSearchPrev.setOnClickListener(v -> navigateToPreviousResult());
        btnSearchNext.setOnClickListener(v -> navigateToNextResult());
        
        // Гарантируем стандартные отступы, чтобы тулбар не уходил под статус-бар
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), true);

        // Подключаем Toolbar как ActionBar, чтобы отрисовать меню (лупу/закладку)
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null && getSupportActionBar() == null) {
            setSupportActionBar(toolbar);
            // Устанавливаем кастомный заголовок с уменьшенным шрифтом
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(getString(R.string.app_name));
                getSupportActionBar().setDisplayShowTitleEnabled(true);
            }
        }

        fileName = getIntent().getStringExtra("fileName");
        docTitle = getIntent().getStringExtra("docTitle");
        jumpToPage = getIntent().getIntExtra("jumpToPage", -1);
        
        android.util.Log.d("BookmarkJump", "Получены параметры - fileName: " + fileName + ", jumpToPage: " + jumpToPage);
        
        if (fileName == null) {
            Toast.makeText(this, "Ошибка: имя файла не передано", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Загружаем DOC файл
        loadDocFile();
    }

    private void loadDocFile() {
        // Проверяем кэш
        if (documentCache.containsKey(fileName)) {
            documentContent = documentCache.get(fileName);
            documentHtml = convertToHtmlOptimized(documentContent);
            displayDocument();
            return;
        }
        
        // Показываем прогресс загрузки
        showLoadingProgress();
        
		new AsyncTask<Void, Void, String>() {
                    @Override
            protected String doInBackground(Void... voids) {
                try {
					InputStream is = getAssets().open(fileName);
                    String text;
                    
                    if (fileName.endsWith(".docx")) {
						// Копируем в временный .docx, чтобы оценить размер и при необходимости включить облегченный парсер
						java.io.File tempDocx = java.io.File.createTempFile("doc_src_", ".docx", getCacheDir());
						java.io.FileOutputStream fosCopy = new java.io.FileOutputStream(tempDocx);
						byte[] bufCopy = new byte[1 << 16];
						int rCopy;
						long totalBytes = 0L;
						while ((rCopy = is.read(bufCopy)) != -1) {
							fosCopy.write(bufCopy, 0, rCopy);
							totalBytes += rCopy;
						}
						fosCopy.flush();
						fosCopy.close();
						is.close();

						final long HUGE_THRESHOLD_BYTES = 15L * 1024L * 1024L; // ~15MB
						boolean useLightweight = totalBytes >= HUGE_THRESHOLD_BYTES;

						if (!useLightweight) {
							// Богатый рендер в HTML (с таблицами) + потоковая запись
							java.io.FileInputStream fis = new java.io.FileInputStream(tempDocx);
							XWPFDocument document = new XWPFDocument(fis);
						java.io.File temp = java.io.File.createTempFile("doc_render_", ".html", getCacheDir());
						java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(new java.io.FileOutputStream(temp), java.nio.charset.StandardCharsets.UTF_8);
						StringBuilder textContent = new StringBuilder();
						final int TEXT_CAP = 500000; // ограничиваем объем текста для поиска, чтобы не расходовать память
						
						// HTML шапка и базовые стили
						writer.write("<html><head><meta charset='UTF-8'>");
						writer.write("<style>");
						writer.write("body { font-family: Arial, sans-serif; margin: 20px; line-height: 1.6; font-size: 34px; }");
						writer.write("p { margin: 0 0 12px 0; }");
						writer.write("table { border-collapse: collapse; width: 100%; margin: 12px 0; }");
						writer.write("td, th { border: 1px solid #ccc; padding: 8px; vertical-align: top; }");
						writer.write("th { background: #f5f5f5; font-weight: bold; }");
                        writer.write(".doc-image { margin: 20px 0; text-align: center; }");
                        writer.write(".doc-image img { max-width: 100%; height: auto; }");
						writer.write("</style></head><body>");

						for (IBodyElement element : document.getBodyElements()) {
							if (element.getElementType() == BodyElementType.PARAGRAPH) {
								XWPFParagraph paragraph = (XWPFParagraph) element;
								String paragraphText = paragraph.getText();
								if (paragraphText != null && !paragraphText.trim().isEmpty() && textContent.length() < TEXT_CAP) {
									int toAppend = Math.min(paragraphText.length(), TEXT_CAP - textContent.length());
									textContent.append(paragraphText, 0, toAppend).append('\n');
								}
								writer.write("<p>");
								for (XWPFRun run : paragraph.getRuns()) {
                                    try {
                                        appendRunToHtml(run, writer);
                                    } catch (IOException e) {
                                        android.util.Log.e("DocRender", "Ошибка записи параграфа", e);
                                    }
								}
								writer.write("</p>");
							} else if (element.getElementType() == BodyElementType.TABLE) {
								XWPFTable table = (XWPFTable) element;
								writer.write("<table>");
								for (XWPFTableRow row : table.getRows()) {
									writer.write("<tr>");
									for (XWPFTableCell cell : row.getTableCells()) {
										writer.write("<td>");
										for (XWPFParagraph p : cell.getParagraphs()) {
											String paraText = p.getText();
											if (paraText != null && !paraText.trim().isEmpty() && textContent.length() < TEXT_CAP) {
												int toAppend = Math.min(paraText.length(), TEXT_CAP - textContent.length());
												textContent.append(paraText, 0, toAppend).append('\t');
											}
											writer.write("<p>");
											for (XWPFRun run : p.getRuns()) {
                                                try {
                                                    appendRunToHtml(run, writer);
                                                } catch (IOException e) {
                                                    android.util.Log.e("DocRender", "Ошибка записи ячейки таблицы", e);
                                                }
											}
											writer.write("</p>");
										}
										writer.write("</td>");
									}
									writer.write("</tr>");
								}
								writer.write("</table>");
							}
						}

						writer.write("</body></html>");
						writer.flush();
						writer.close();
						document.close();
						fis.close();
						// Сохраняем путь к temp-файлу, используем его в displayDocument
						tempHtmlFilePath = temp.getAbsolutePath();
						documentHtml = null; // не используем строковый HTML для больших документов
						text = textContent.toString();
						// Удаляем исходный временный .docx
						try { tempDocx.delete(); } catch (Throwable ignored) {}
						
						} else {
							// Облегченный парсер: извлекаем word/document.xml и строим простой HTML с абзацами
							java.io.FileInputStream fisZip = new java.io.FileInputStream(tempDocx);
							java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(fisZip);
							java.util.zip.ZipEntry entry;
							java.io.File tempHtml = java.io.File.createTempFile("doc_light_", ".html", getCacheDir());
							java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(new java.io.FileOutputStream(tempHtml), java.nio.charset.StandardCharsets.UTF_8);

							StringBuilder textContent = new StringBuilder();
							final int TEXT_CAP = 500000;

							writer.write("<html><head><meta charset='UTF-8'><style>body{font-family:Arial,sans-serif;margin:20px;line-height:1.6;font-size:34px;}p{margin:0 0 12px 0;}.doc-image{margin:20px 0;text-align:center;}.doc-image img{max-width:100%;height:auto;}</style></head><body>");

							try {
								while ((entry = zis.getNextEntry()) != null) {
									if ("word/document.xml".equals(entry.getName())) {
										java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(zis, java.nio.charset.StandardCharsets.UTF_8));
										String line;
										StringBuilder curText = new StringBuilder();
										while ((line = br.readLine()) != null) {
											int idx = 0;
											while (idx < line.length()) {
												int tStart = line.indexOf("<w:t", idx);
												int pEnd = line.indexOf("</w:p>", idx);
												if (pEnd >= 0 && (tStart < 0 || pEnd < tStart)) {
													// Завершение параграфа
													if (curText.length() > 0) {
														String para = escapeHtml(curText.toString().trim());
														if (!para.isEmpty()) {
															writer.write("<p>");
															writer.write(para);
															writer.write("</p>");
															if (textContent.length() < TEXT_CAP) {
																int remain = TEXT_CAP - textContent.length();
																String toAdd = curText.toString();
																if (toAdd.length() > remain) toAdd = toAdd.substring(0, remain);
																textContent.append(toAdd).append('\n');
															}
													}
													curText.setLength(0);
												}
												idx = pEnd + 6;
												continue;
											}
											if (tStart < 0) break;
											int tClose = line.indexOf('>', tStart);
											if (tClose < 0) break;
											int tEnd = line.indexOf("</w:t>", tClose + 1);
											if (tEnd < 0) {
												idx = tClose + 1;
												continue;
											}
											String txt = line.substring(tClose + 1, tEnd);
											// Раскодируем XML сущности
											txt = txt.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'");
											curText.append(txt);
											idx = tEnd + 6;
										}
										}
										br.close();
										// После обработки документа выходим из цикла по Zip-входам
										break;
									}
								}
							} catch (Throwable ignored) {}

							writer.write("</body></html>");
							writer.flush();
							writer.close();
							zis.close();
							fisZip.close();

							tempHtmlFilePath = tempHtml.getAbsolutePath();
							documentHtml = null;
							text = textContent.toString();

							try { tempDocx.delete(); } catch (Throwable ignored) {}
						}
                    } else {
                        // Оптимизированная обработка DOC файлов
                        HWPFDocument document = new HWPFDocument(is);
                        Range range = document.getRange();
                        text = range.text();
                        // Для .doc генерируем HTML из текста без таблиц (ограничение формата/библиотеки в текущем решении)
                        documentHtml = convertToHtmlOptimized(text);
                        document.close();
                    }
                    
                    is.close();
                    return text;
                } catch (IOException e) {
                    return "Ошибка загрузки файла: " + e.getMessage();
                }
            }

            @Override
			protected void onPostExecute(String content) {
                documentContent = content;
				// Сохраняем в кэш для будущих загрузок (только текст), кроме очень больших документов
				if (tempHtmlFilePath == null) {
					documentCache.put(fileName, content);
				}

				// Если HTML не подготовлен (потоковый режим), просто загрузим файл, иначе сгенерируем из текста
				if (tempHtmlFilePath == null) {
					if (documentHtml == null || documentHtml.isEmpty()) {
						documentHtml = convertToHtmlOptimized(content);
					}
				}

                displayDocument();
                hideLoadingProgress();
            }
        }.execute();
    }

    private String convertToHtmlOptimized(String text) {
        // Оптимизированное преобразование текста в HTML
        StringBuilder html = new StringBuilder(text.length() + 1000); // Предварительное выделение памяти
        
        html.append("<html><head><meta charset='UTF-8'>")
            .append("<style>")
            .append("body { font-family: Arial, sans-serif; margin: 20px; line-height: 1.6; font-size: 34px; }")
            .append(".highlight { background-color: #FFEB3B !important; color: #000000 !important; padding: 2px 4px; border-radius: 3px; font-weight: bold; display: inline; }")
            .append(".highlight.active { background-color: #4CAF50 !important; color: #FFFFFF !important; padding: 3px 6px !important; border-radius: 5px !important; font-weight: bold !important; display: inline !important; border: 3px solid #2E7D32 !important; box-shadow: 0 2px 4px rgba(0,0,0,0.3) !important; }")
            .append(".doc-image { margin: 20px 0; text-align: center; }")
            .append(".doc-image img { max-width: 100%; height: auto; }")
            .append("</style></head><body>");
        
        // Оптимизированная обработка параграфов
        String[] paragraphs = text.split("\n");
        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (!trimmed.isEmpty()) {
                html.append("<p>")
                    .append(escapeHtml(trimmed))
                    .append("</p>");
            }
        }
        
        html.append("</body></html>");
        return html.toString();
    }
    
    private String escapeHtml(String text) {
        // Оптимизированное экранирование HTML символов
        StringBuilder result = new StringBuilder(text.length() + text.length() / 4);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&': result.append("&amp;"); break;
                case '<': result.append("&lt;"); break;
                case '>': result.append("&gt;"); break;
                case '"': result.append("&quot;"); break;
                case '\'': result.append("&#39;"); break;
                default: result.append(c); break;
            }
        }
        return result.toString();
    }

    private String convertToHtml(String text) {
        // Простое преобразование текста в HTML (для обратной совместимости)
        String html = "<html><head><meta charset='UTF-8'>" +
                "<style>" +
                "body { font-family: Arial, sans-serif; margin: 20px; line-height: 1.6; font-size: 34px; }" +
                ".highlight { background-color: #FFEB3B !important; color: #000000 !important; padding: 2px 4px; border-radius: 3px; font-weight: bold; display: inline; }" +
                ".highlight.active { background-color: #4CAF50 !important; color: #FFFFFF !important; padding: 3px 6px !important; border-radius: 5px !important; font-weight: bold !important; display: inline !important; border: 3px solid #2E7D32 !important; box-shadow: 0 2px 4px rgba(0,0,0,0.3) !important; }" +
                ".doc-image { margin: 20px 0; text-align: center; }" +
                ".doc-image img { max-width: 100%; height: auto; }" +
                "</style></head><body>";
        
        // Разбиваем текст на параграфы
        String[] paragraphs = text.split("\n");
        for (String paragraph : paragraphs) {
            if (!paragraph.trim().isEmpty()) {
                html += "<p>" + paragraph.replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                        .replace("\"", "&quot;")
                        .replace("'", "&#39;") + "</p>";
            }
        }
        
        html += "</body></html>";
        return html;
    }

    private String convertToHtmlWithHighlight(String text) {
        // Оптимизированное преобразование текста в HTML с сохранением выделения
        StringBuilder html = new StringBuilder(text.length() + 2000); // Предварительное выделение памяти
        
        html.append("<html><head><meta charset='UTF-8'>")
            .append("<style>")
            .append("body { font-family: Arial, sans-serif; margin: 20px; line-height: 1.6; font-size: 34px; }")
            .append(".highlight { background-color: #FFEB3B !important; color: #000000 !important; padding: 2px 4px; border-radius: 3px; font-weight: bold; display: inline; }")
            .append(".highlight.active { background-color: #4CAF50 !important; color: #FFFFFF !important; padding: 3px 6px !important; border-radius: 5px !important; font-weight: bold !important; display: inline !important; border: 3px solid #2E7D32 !important; box-shadow: 0 2px 4px rgba(0,0,0,0.3) !important; }")
            .append(".doc-image { margin: 20px 0; text-align: center; }")
            .append(".doc-image img { max-width: 100%; height: auto; }")
            .append("</style></head><body>");
        
        // Оптимизированная обработка параграфов с выделением
        String[] paragraphs = text.split("\n");
        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (!trimmed.isEmpty()) {
                html.append("<p>");
                
                // Обработка выделения
                if (trimmed.contains("<span class='highlight'>")) {
                // Сначала защищаем теги выделения от экранирования
                    String protectedParagraph = trimmed
                        .replace("<span class='highlight'>", "___HIGHLIGHT_START___")
                        .replace("</span>", "___HIGHLIGHT_END___");
                
                // Экранируем HTML символы
                    String escapedParagraph = escapeHtml(protectedParagraph);
                
                // Восстанавливаем теги выделения
                escapedParagraph = escapedParagraph
                        .replace("___HIGHLIGHT_START___", "<span class='highlight'>")
                        .replace("___HIGHLIGHT_END___", "</span>");
                
                    html.append(escapedParagraph);
                } else {
                    html.append(escapeHtml(trimmed));
                }
                
                html.append("</p>");
            }
        }
        
        html.append("</body></html>");
        return html.toString();
    }

    private void appendRunToHtml(XWPFRun run, java.io.Writer writer) throws IOException {
        if (run == null || writer == null) {
            return;
        }

        appendPictures(run, writer);

        String runText = run.toString();
        if (runText == null || runText.isEmpty()) {
            return;
        }
        String escaped = escapeHtml(runText);
        if (run.isBold()) escaped = "<b>" + escaped + "</b>";
        if (run.isItalic()) escaped = "<i>" + escaped + "</i>";
        writer.write(escaped);
    }

    private void appendPictures(XWPFRun run, java.io.Writer writer) throws IOException {
        List<XWPFPicture> pictures = run.getEmbeddedPictures();
        if (pictures == null || pictures.isEmpty()) {
            return;
        }
        for (XWPFPicture picture : pictures) {
            String dataUri = buildDataUri(picture);
            if (dataUri == null) continue;
            writer.write("<div class='doc-image'><img src='");
            writer.write(dataUri);
            writer.write("' alt='' /></div>");
        }
    }

    private String buildDataUri(XWPFPicture picture) {
        if (picture == null) {
            return null;
        }
        try {
            XWPFPictureData pictureData = picture.getPictureData();
            if (pictureData == null) {
                return null;
            }
            byte[] bytes = pictureData.getData();
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            String mime = guessMimeType(pictureData.suggestFileExtension());
            if (mime == null || mime.trim().isEmpty()) {
                mime = "application/octet-stream";
            }
            String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
            return "data:" + mime + ";base64," + base64;
        } catch (Exception e) {
            android.util.Log.e("DocRender", "Не удалось подготовить изображение", e);
            return null;
        }
    }

    private String guessMimeType(String extension) {
        if (extension == null) {
            return null;
        }
        String ext = extension.toLowerCase(Locale.ROOT);
        switch (ext) {
            case "png":
                return "image/png";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "gif":
                return "image/gif";
            case "bmp":
                return "image/bmp";
            case "webp":
                return "image/webp";
            case "svg":
            case "svgz":
                return "image/svg+xml";
            default:
                return null;
        }
    }

    private void displayDocument() {
		webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);
        webView.getSettings().setSupportZoom(true);
		webView.getSettings().setAllowFileAccess(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                android.util.Log.d("PageIndicator", "Страница загружена");
                
                // Инициализируем индикатор страниц после загрузки
                initializePageIndicator();
                
                // Если нужно перейти к определенной странице (из закладки)
                if (jumpToPage >= 0) {
                    android.util.Log.d("BookmarkJump", "Переход к закладке на странице: " + jumpToPage);
                    jumpToBookmarkPage(jumpToPage);
                }
            }
        });
		if (tempHtmlFilePath != null) {
			webView.loadUrl("file://" + tempHtmlFilePath);
		} else {
			webView.loadDataWithBaseURL(null, documentHtml, "text/html", "UTF-8", null);
		}
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.actionbar_menu, menu);
        
        // Инициализация кнопок навигации
        updateNavigationButtons();
        
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.search) {
            // Показываем диалог поиска
            showSearchDialog();
            return true;
        } else if (id == R.id.add_bookmark) {
            // Получаем текущую позицию прокрутки и небольшой текстовый сниппет из WebView
            webView.post(() -> {
                webView.evaluateJavascript(
                    "(function() {" +
                    "var scrollTop = window.pageYOffset || document.documentElement.scrollTop;" +
                    "var windowHeight = window.innerHeight;" +
                    "var documentHeight = document.body.scrollHeight;" +
                    "var scrollPercent = scrollTop / (documentHeight - windowHeight);" +
                    "var pagesPerScreen = documentHeight / windowHeight;" +
                    "var estimatedPages = Math.max(1, Math.ceil(pagesPerScreen));" +
                    "estimatedPages = Math.min(estimatedPages, 500);" +
                    "var estimatedPage = Math.max(1, Math.round(scrollPercent * estimatedPages) + 1);" +
                    // Сниппет: ищем ближайший видимый параграф и берем первые 80 символов
                    "var snippet = '';" +
                    "var paragraphs = document.querySelectorAll('p');" +
                    "for (var i = 0; i < paragraphs.length; i++) {" +
                    "  var rect = paragraphs[i].getBoundingClientRect();" +
                    "  if (rect.bottom > 0 && rect.top < window.innerHeight) {" +
                    "    snippet = (paragraphs[i].innerText || '').trim();" +
                    "    if (snippet.length > 120) snippet = snippet.substring(0, 120) + '…';" +
                    "    break;" +
                    "  }" +
                    "}" +
                    "return JSON.stringify({page: estimatedPage, snippet: snippet});" +
                    "})()",
                    (result) -> {
                        try {
                            String clean = result == null ? "" : result.trim();
                            if (clean.startsWith("\"")) clean = clean.substring(1, clean.length() - 1);
                            clean = clean.replace("\\\"", "\"");
                            org.json.JSONObject obj = new org.json.JSONObject(clean);
                            int currentPage = obj.optInt("page", 1);
                            String snippet = obj.optString("snippet", "");

                            String displayName = (docTitle != null && !docTitle.isEmpty()) ? docTitle : fileName;
                            String title = displayName + ": документ";
                            BookmarkStore.addBookmark(FullView.this, fileName, currentPage - 1, title, snippet);

                            runOnUiThread(() -> {
                                String toastText = snippet == null || snippet.isEmpty()
                                        ? ("Закладка сохранена на странице " + currentPage)
                                        : ("Закладка сохранена: \"" + (snippet.length() > 40 ? snippet.substring(0, 40) + "…" : snippet) + "\"");
                                Toast.makeText(FullView.this, toastText, Toast.LENGTH_SHORT).show();
                            });
                        } catch (Throwable e) {
                            android.util.Log.e("BookmarkDebug", "Error creating bookmark with snippet: " + result, e);
                            String displayName = (docTitle != null && !docTitle.isEmpty()) ? docTitle : fileName;
                            String title = displayName + ": документ";
                            BookmarkStore.addBookmark(FullView.this, fileName, 0, title);
                            runOnUiThread(() -> Toast.makeText(FullView.this, "Закладка сохранена", Toast.LENGTH_SHORT).show());
                        }
                    }
                );
            });
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        if (currentTask != null) {
            currentTask.cancel(true);
        }
		// Удаляем временный HTML файл, если он был создан
		if (tempHtmlFilePath != null) {
			try { new java.io.File(tempHtmlFilePath).delete(); } catch (Throwable ignored) {}
			tempHtmlFilePath = null;
		}
        super.onDestroy();
    }

    private void runSearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            searchResults.clear();
            currentResultIndex = -1;
            displayDocument(); // Показываем документ без выделений
            updateNavigationButtons();
            updateTitleWithSearchInfo(-1);
            return;
        }
        
        String normalizedQuery = query.trim().replaceAll("\\s+", " ");
        lastSearchQuery = normalizedQuery;
        
        // Выделяем все результаты в документе и создаем список для навигации
        highlightAllSearchResults(normalizedQuery);
    }

    private class DocSearchTask extends AsyncTask<Void, Void, List<DocSearchResult>> {
        private final String query;
        private final boolean caseSensitive;
        private final boolean wholeWordsOnly;
        private Exception error;

        DocSearchTask(String query, boolean caseSensitive, boolean wholeWordsOnly) {
            this.query = query;
            this.caseSensitive = caseSensitive;
            this.wholeWordsOnly = wholeWordsOnly;
        }

        @Override
        protected List<DocSearchResult> doInBackground(Void... voids) {
            List<DocSearchResult> results = new ArrayList<>();
            
            try {
                String searchText = caseSensitive ? documentContent : documentContent.toLowerCase(Locale.getDefault());
                    String searchQuery = caseSensitive ? query : query.toLowerCase(Locale.getDefault());
                
                int from = 0;
                while (true) {
                    int idx = searchText.indexOf(searchQuery, from);
                    if (idx < 0) break;

                    // Проверяем условие поиска по целым словам
                    if (wholeWordsOnly && !isWholeWordMatch(searchText, idx, searchQuery.length())) {
                        from = idx + 1;
                        continue;
                    }

                    // Находим контекст вокруг найденного текста
                    int contextStart = Math.max(0, idx - 50);
                    int contextEnd = Math.min(searchText.length(), idx + searchQuery.length() + 50);
                    String context = searchText.substring(contextStart, contextEnd);
                    
                    results.add(new DocSearchResult(0, searchQuery, idx, 1));
                    from = idx + 1;
                }
            } catch (Exception e) {
                error = e;
            }
            
            return results;
        }

        private boolean isWholeWordMatch(String text, int start, int length) {
            // Проверяем символ перед найденным текстом
            if (start > 0 && Character.isLetterOrDigit(text.charAt(start - 1))) {
                return false;
            }
            
            // Проверяем символ после найденного текста
            int end = start + length;
            if (end < text.length() && Character.isLetterOrDigit(text.charAt(end))) {
                return false;
            }
            
            return true;
        }

        @Override
        protected void onPostExecute(List<DocSearchResult> results) {
            if (error != null) {
                Toast.makeText(FullView.this, "Ошибка поиска: " + error.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }
            
            // Обновляем результаты поиска
            searchResults.clear();
            searchResults.addAll(results);
            
            updateNavigationButtons();

            if (!searchResults.isEmpty()) {
                currentResultIndex = 0;
                // Не вызываем navigateToResult, так как все результаты уже выделены
            } else {
                currentResultIndex = -1;
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Обработка клавиш для навигации по результатам поиска
        if (!searchResults.isEmpty()) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_PAGE_UP:
                    navigateToPreviousResult();
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_PAGE_DOWN:
                    navigateToNextResult();
                    return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }


    // Методы для навигации по результатам поиска
    private void updateNavigationButtons() {
        if (searchNavigationLayout == null || btnSearchPrev == null || btnSearchNext == null) {
            return;
        }
        
        boolean hasResults = !searchResults.isEmpty();
        boolean canGoPrev = hasResults && currentResultIndex > 0;
        boolean canGoNext = hasResults && currentResultIndex < searchResults.size() - 1;
        
        // Показываем/скрываем панель навигации
        searchNavigationLayout.setVisibility(hasResults ? View.VISIBLE : View.GONE);
        
        // Активируем/деактивируем кнопки
        btnSearchPrev.setEnabled(canGoPrev);
        btnSearchNext.setEnabled(canGoNext);
        
        // Изменяем прозрачность для визуального отображения состояния
        btnSearchPrev.setAlpha(canGoPrev ? 1.0f : 0.3f);
        btnSearchNext.setAlpha(canGoNext ? 1.0f : 0.3f);
    }

    private void navigateToPreviousResult() {
        if (searchResults.isEmpty()) {
            return;
        }
        
        if (currentResultIndex <= 0) {
            return;
        }
        
        currentResultIndex--;
        navigateToResult(currentResultIndex);
    }

    private void navigateToNextResult() {
        if (searchResults.isEmpty()) {
            return;
        }
        
        if (currentResultIndex >= searchResults.size() - 1) {
            return;
        }
        
        currentResultIndex++;
        navigateToResult(currentResultIndex);
    }

    private void navigateToResult(int index) {
        if (index < 0 || index >= searchResults.size()) {
            return;
        }
        
        DocSearchResult result = searchResults.get(index);
        
        // Устанавливаем активный класс на текущий результат
        setActiveResult(index);
        
        // Автоматически прокручиваем к найденному результату
        scrollToSearchResult(result);
        
        // Обновляем заголовок
        updateTitleWithSearchInfo(index);
        
        // Обновляем состояние кнопок навигации
        updateNavigationButtons();
    }

    private void highlightAllSearchResults(String searchText) {
        // Проверяем валидность поискового запроса
        if (searchText == null || searchText.trim().isEmpty()) {
            displayDocument(); // Показываем документ без выделений
            searchResults.clear();
            return;
        }
        
        // Выделяем все найденные результаты в документе одновременно
        String content = documentContent;
        
        // Экранируем специальные символы для регулярных выражений
        String escapedSearchText = searchText.replaceAll("([\\[\\](){}.*+?^$|\\\\])", "\\\\$1");
        
        // Создаем регулярное выражение для поиска
        String regex;
        if (wholeWordsOnly) {
            regex = "\\b" + escapedSearchText + "\\b";
        } else {
            regex = escapedSearchText;
        }
        
        try {
            // Применяем поиск с учетом регистра
            java.util.regex.Pattern pattern;
            if (caseSensitive) {
                pattern = java.util.regex.Pattern.compile(regex);
            } else {
                pattern = java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.CASE_INSENSITIVE);
            }
            
            // Создаем список результатов поиска
            searchResults.clear();
            java.util.regex.Matcher matcher = pattern.matcher(content);
            int resultIndex = 0;
            
            while (matcher.find()) {
                // Вычисляем примерную страницу для каждого результата
                int charPosition = matcher.start();
                int estimatedPage = Math.max(1, (int) Math.ceil((double) charPosition / content.length() * 10));
                
                DocSearchResult result = new DocSearchResult(
                    resultIndex,
                    matcher.group(),
                    charPosition,
                    estimatedPage
                );
                searchResults.add(result);
                resultIndex++;
            }
            
            // Заменяем все найденные вхождения на выделенные
            String highlightedContent = pattern.matcher(content).replaceAll(
                "<span class='highlight'>$0</span>");
            
            // Преобразуем в HTML с правильным экранированием
            String highlightedHtml = convertToHtmlWithHighlight(highlightedContent);
            
            webView.loadDataWithBaseURL(null, highlightedHtml, "text/html", "UTF-8", null);
            
            // Обновляем состояние навигации
            if (!searchResults.isEmpty()) {
                currentResultIndex = 0; // Устанавливаем на первый результат
                updateTitleWithSearchInfo(0);
                
                // Автоматически прокручиваем к первому результату и устанавливаем активный класс
                webView.postDelayed(() -> {
                    setActiveResult(0);
                    scrollToSearchResult(searchResults.get(0));
                }, 500);
            } else {
                currentResultIndex = -1;
                updateTitleWithSearchInfo(-1);
                // Если ничего не найдено, показываем toast
                android.widget.Toast.makeText(this, "Ничего не найдено", android.widget.Toast.LENGTH_SHORT).show();
            }
            updateNavigationButtons();
            
        } catch (Exception e) {
            // В случае ошибки показываем документ без выделений
            displayDocument();
            searchResults.clear();
        }
    }

    private void highlightSearchResult(DocSearchResult result) {
        // Этот метод теперь просто вызывает highlightAllSearchResults
        highlightAllSearchResults(lastSearchQuery);
    }

    private void updateTitleWithSearchInfo(int currentIndex) {
        if (searchResults.isEmpty()) {
            // Возвращаем обычный заголовок
            String displayName = (docTitle != null && !docTitle.isEmpty()) ? docTitle : fileName;
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(displayName);
            }
        } else {
            // Показываем информацию о поиске в заголовке
            String searchInfo = String.format("Поиск: %d/%d", 
                    currentIndex + 1, searchResults.size());
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(searchInfo);
            }
        }
    }


    private void scrollToSearchResult(DocSearchResult result) {
        // Проверяем, что WebView инициализирован
        if (webView == null) {
            android.util.Log.e("PageIndicator", "WebView не инициализирован в scrollToSearchResult!");
            return;
        }
        
        // Прокручиваем к найденному результату
        webView.post(() -> {
            // Используем JavaScript для прокрутки к элементу с задержкой
            String script = "setTimeout(function() {" +
                    "var elements = document.querySelectorAll('.highlight');" +
                    "console.log('Found ' + elements.length + ' highlighted elements');" +
                    "if (elements.length > 0 && elements.length > " + result.getIndex() + ") {" +
                    "var targetElement = elements[" + result.getIndex() + "];" +
                    "console.log('Scrolling to element ' + " + result.getIndex() + ");" +
                    "if (targetElement) {" +
                    "// Прокручиваем к элементу с фиксированным отступом от верха" +
                    "var elementRect = targetElement.getBoundingClientRect();" +
                    "var windowHeight = window.innerHeight;" +
                    "var topOffset = 600; // Фиксированный отступ от верха экрана" +
                    "var targetScrollTop = window.pageYOffset + elementRect.top - topOffset;" +
                    "// Проверяем границы документа" +
                    "var documentHeight = document.body.scrollHeight;" +
                    "var maxScrollTop = documentHeight - windowHeight;" +
                    "if (targetScrollTop < 0) targetScrollTop = 0;" +
                    "if (targetScrollTop > maxScrollTop) targetScrollTop = maxScrollTop;" +
                    "window.scrollTo({top: targetScrollTop, behavior: 'smooth'});" +
                    "// Обновляем позицию флажка после прокрутки" +
                    "setTimeout(function() {" +
                    "var scrollTop = window.pageYOffset || document.documentElement.scrollTop;" +
                    "var windowHeight = window.innerHeight;" +
                    "var documentHeight = document.body.scrollHeight;" +
                    "var scrollPercent = scrollTop / (documentHeight - windowHeight);" +
                    "if (scrollPercent < 0) scrollPercent = 0;" +
                    "if (scrollPercent > 1) scrollPercent = 1;" +
                    "if (typeof Android !== 'undefined') {" +
                    "Android.updatePageIndicatorFromJS(" + result.getPage() + ", 1, scrollPercent);" +
                    "}" +
                    "}, 500);" +
                    "} else {" +
                    "console.log('Target element not found');" +
                    "}" +
                    "} else {" +
                    "console.log('Not enough elements: ' + elements.length + ', need: " + (result.getIndex() + 1) + "');" +
                    "}" +
                    "}, 200);";
            webView.evaluateJavascript(script, null);
            
            // Альтернативный метод прокрутки через позицию символа
            scrollToPosition(result.getPosition());
        });
    }
    
    private void scrollToPosition(int charPosition) {
        // Проверяем, что WebView инициализирован
        if (webView == null) {
            android.util.Log.e("PageIndicator", "WebView не инициализирован в scrollToPosition!");
            return;
        }
        
        // Альтернативный метод прокрутки на основе позиции символа
        webView.post(() -> {
            String script = "var documentHeight = document.body.scrollHeight;" +
                    "var windowHeight = window.innerHeight;" +
                    "var documentLength = document.body.innerText.length;" +
                    "var scrollPercent = " + charPosition + " / documentLength;" +
                    "var targetScrollTop = scrollPercent * (documentHeight - windowHeight);" +
                    "window.scrollTo({top: targetScrollTop, behavior: 'smooth'});" +
                    "setTimeout(function() {" +
                    "var scrollTop = window.pageYOffset || document.documentElement.scrollTop;" +
                    "var scrollPercent = scrollTop / (documentHeight - windowHeight);" +
                    "if (scrollPercent < 0) scrollPercent = 0;" +
                    "if (scrollPercent > 1) scrollPercent = 1;" +
                    "if (typeof Android !== 'undefined') {" +
                    "Android.updatePageIndicatorFromJS(1, 1, scrollPercent);" +
                    "}" +
                    "}, 500);";
            webView.evaluateJavascript(script, null);
        });
    }
    
    private void setActiveResult(int index) {
        // Проверяем, что WebView инициализирован
        if (webView == null) {
            android.util.Log.e("PageIndicator", "WebView не инициализирован в setActiveResult!");
            return;
        }
        
        // Устанавливаем активный класс на конкретный результат
        webView.post(() -> {
            String script = "setTimeout(function() {" +
                    "var allElements = document.querySelectorAll('.highlight');" +
                    "console.log('setActiveResult: Found ' + allElements.length + ' highlight elements, setting active to index ' + " + index + ");" +
                    "for (var i = 0; i < allElements.length; i++) {" +
                    "allElements[i].classList.remove('active');" +
                    "}" +
                    "if (allElements.length > " + index + ") {" +
                    "allElements[" + index + "].classList.add('active');" +
                    "console.log('setActiveResult: Added active class to element ' + " + index + ");" +
                    "} else {" +
                    "console.log('setActiveResult: Element index ' + " + index + " + ', total elements: ' + allElements.length);" +
                    "}" +
                    "}, 50);";
            webView.evaluateJavascript(script, null);
        });
    }
    
    private void jumpToBookmarkPage(int pageIndex) {
        if (webView == null) return;
        
        android.util.Log.d("BookmarkJump", "jumpToBookmarkPage вызван с pageIndex: " + pageIndex + ", totalPages: " + totalPages);
        
        // Проверяем, что totalPages инициализирован
        if (totalPages <= 0) {
            android.util.Log.w("BookmarkJump", "totalPages не инициализирован, ждем инициализации...");
            // Повторяем попытку через 500мс
            webView.postDelayed(() -> jumpToBookmarkPage(pageIndex), 500);
            return;
        }
        
        // Добавляем небольшую задержку, чтобы WebView успел полностью загрузиться
        webView.postDelayed(() -> {
            webView.evaluateJavascript(
                "(function() {" +
                "var windowHeight = window.innerHeight;" +
                "var documentHeight = document.body.scrollHeight;" +
                "var targetPage = " + (pageIndex + 1) + ";" + // pageIndex начинается с 0, но страницы с 1
                "var totalPages = " + totalPages + ";" +
                "var scrollPercent = (targetPage - 1) / (totalPages - 1);" +
                "var targetScrollTop = scrollPercent * (documentHeight - windowHeight);" +
                "console.log('Jumping to page ' + targetPage + ', scrollPercent: ' + scrollPercent + ', targetScrollTop: ' + targetScrollTop);" +
                "window.scrollTo(0, targetScrollTop);" +
                "return targetPage;" +
                "})()",
                (result) -> {
                    try {
                        String cleanResult = result.replaceAll("\"", "");
                        int newPage = Integer.parseInt(cleanResult);
                        currentPage = newPage;
                        updatePageIndicator();
                        showPageIndicator();
                        
                        android.util.Log.d("BookmarkJump", "Переход выполнен к странице: " + newPage);
                        
                        // Показываем уведомление о переходе к закладке
                        runOnUiThread(() -> {
                            Toast.makeText(FullView.this, "Переход к закладке на странице " + newPage, Toast.LENGTH_SHORT).show();
                        });
                    } catch (NumberFormatException e) {
                        android.util.Log.e("BookmarkJump", "Ошибка парсинга результата: " + result, e);
                    }
                }
            );
        }, 1000); // Задержка 1 секунда для полной загрузки
    }
    
    // Методы для работы с индикатором страниц
    private void initializePageIndicator() {
        if (pageIndicator == null) return;
        
        // Получаем информацию о страницах из WebView
        webView.post(() -> {
            webView.evaluateJavascript(
                "(function() {" +
                "var windowHeight = window.innerHeight;" +
                "var documentHeight = document.body.scrollHeight;" +
                "var pagesPerScreen = documentHeight / windowHeight;" +
                "var estimatedPages = Math.max(1, Math.ceil(pagesPerScreen));" +
                "estimatedPages = Math.min(estimatedPages, 500);" +
                "return estimatedPages;" +
                "})()",
                (result) -> {
                    try {
                        String cleanResult = result.replaceAll("\"", "");
                        totalPages = Integer.parseInt(cleanResult);
                        currentPage = 1;
                        updatePageIndicator();
                        showPageIndicator();
                    } catch (NumberFormatException e) {
                        totalPages = 1;
                        currentPage = 1;
                        updatePageIndicator();
                    }
                }
            );
        });
        
        // Добавляем слушатель прокрутки
        addScrollListener();
    }
    
    private void addScrollListener() {
        if (webView == null) return;
        
        webView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastScrollTime < SCROLL_THROTTLE_MS) {
                return; // Ограничиваем частоту обновлений
            }
            lastScrollTime = currentTime;
            
            updatePageIndicatorFromScroll(scrollY);
        });
    }
    
    private void updatePageIndicatorFromScroll(int scrollY) {
        if (webView == null) return;
        
        webView.post(() -> {
            webView.evaluateJavascript(
                "(function() {" +
                "var scrollTop = " + scrollY + ";" +
                "var windowHeight = window.innerHeight;" +
                "var documentHeight = document.body.scrollHeight;" +
                "var scrollPercent = scrollTop / (documentHeight - windowHeight);" +
                "var pagesPerScreen = documentHeight / windowHeight;" +
                "var estimatedPages = Math.max(1, Math.ceil(pagesPerScreen));" +
                "estimatedPages = Math.min(estimatedPages, 500);" +
                "var estimatedPage = Math.max(1, Math.round(scrollPercent * estimatedPages) + 1);" +
                "return estimatedPage;" +
                "})()",
                (result) -> {
                    try {
                        String cleanResult = result.replaceAll("\"", "");
                        int newPage = Integer.parseInt(cleanResult);
                        if (newPage != currentPage) {
                            currentPage = newPage;
                            updatePageIndicator();
                            showPageIndicator();
                        }
                    } catch (NumberFormatException e) {
                        // Игнорируем ошибки парсинга
                    }
                }
            );
        });
    }
    
    private void updatePageIndicator() {
        if (pageIndicator == null) return;
        
        if (currentPage >= 1 && currentPage <= totalPages) {
            pageIndicator.setText(String.valueOf(currentPage));
        }
    }
    
    private void showPageIndicator() {
        if (pageIndicator == null) return;
        
        pageIndicator.setVisibility(View.VISIBLE);
        
        // Скрываем индикатор через 3 секунды
        pageIndicator.removeCallbacks(hideIndicatorRunnable);
        pageIndicator.postDelayed(hideIndicatorRunnable, 3000);
    }
    
    private final Runnable hideIndicatorRunnable = () -> {
        if (pageIndicator != null) {
            pageIndicator.setVisibility(View.GONE);
        }
    };
    
    private void setupPageIndicatorTouchListener() {
        if (pageIndicator == null) return;
        
        pageIndicator.setOnClickListener(v -> {
            // При нажатии на индикатор показываем диалог выбора страницы
            showPageSelectionDialog();
        });
    }
    
    private void showPageSelectionDialog() {
        if (totalPages <= 1) {
            Toast.makeText(this, "Документ содержит только одну страницу", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Создаем массив страниц для выбора
        String[] pageNumbers = new String[totalPages];
        for (int i = 0; i < totalPages; i++) {
            pageNumbers[i] = "Страница " + (i + 1);
        }
        
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Перейти к странице")
            .setItems(pageNumbers, (dialog, which) -> {
                scrollToPage(which + 1);
            })
            .setNegativeButton("Отмена", null)
            .show();
    }
    
    private void scrollToPage(int pageNumber) {
        if (webView == null || pageNumber < 1 || pageNumber > totalPages) return;
        
        webView.post(() -> {
            webView.evaluateJavascript(
                "(function() {" +
                "var windowHeight = window.innerHeight;" +
                "var documentHeight = document.body.scrollHeight;" +
                "var targetPage = " + pageNumber + ";" +
                "var totalPages = " + totalPages + ";" +
                "var scrollPercent = (targetPage - 1) / (totalPages - 1);" +
                "var targetScrollTop = scrollPercent * (documentHeight - windowHeight);" +
                "window.scrollTo(0, targetScrollTop);" +
                "return targetPage;" +
                "})()",
                (result) -> {
                    try {
                        String cleanResult = result.replaceAll("\"", "");
                        int newPage = Integer.parseInt(cleanResult);
                        currentPage = newPage;
                        updatePageIndicator();
                        showPageIndicator();
                    } catch (NumberFormatException e) {
                        // Игнорируем ошибки парсинга
                    }
                }
            );
        });
    }
    
    // Методы для управления прогрессом загрузки
    private void showLoadingProgress() {
        if (loadingProgress != null) {
            loadingProgress.setVisibility(View.VISIBLE);
        }
        if (webView != null) {
            webView.setVisibility(View.GONE);
        }
    }
    
    private void hideLoadingProgress() {
        if (loadingProgress != null) {
            loadingProgress.setVisibility(View.GONE);
        }
        if (webView != null) {
            webView.setVisibility(View.VISIBLE);
        }
    }
    
    // Диалог поиска
    private void showSearchDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Поиск в документе");
        
        // Создаем EditText для ввода поискового запроса
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Введите текст для поиска");
        input.setSingleLine(true);
        input.setText(lastSearchQuery); // Показываем последний поисковый запрос
        
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
                runSearch(query);
            }
        });
        
        builder.setNegativeButton("Отмена", (dialog, which) -> dialog.cancel());
        
        // Кнопка "Очистить"
        builder.setNeutralButton("Очистить", (dialog, which) -> {
            // Очищаем результаты поиска
            searchResults.clear();
            currentResultIndex = -1;
            lastSearchQuery = "";
            updateTitleWithSearchInfo(-1);
            updateNavigationButtons();
            // Показываем документ без выделений
            displayDocument();
        });
        
        android.app.AlertDialog dialog = builder.create();
        dialog.show();
        
        // Фокус на поле ввода
        input.requestFocus();
        input.selectAll();
    }
}