package by.instruction.papera;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ExpandableListView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import android.content.res.Configuration;
import android.util.TypedValue;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

// Класс для хранения результатов поиска
class SearchResultItem {
    private final String title;
    private final String fileName;
    private final String chapterName;
    
    SearchResultItem(String title, String fileName, String chapterName) {
        this.title = title;
        this.fileName = fileName;
        this.chapterName = chapterName;
    }
    
    public String getTitle() {
        return title;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public String getChapterName() {
        return chapterName;
    }
    
    @Override
    public String toString() {
        return title;
    }
}

public class MainActivity extends AppCompatActivity {

    //объявляем переменные для кнопки назад
    private long backPressedTime;
    private Toast backToast;
    //конец объявления переменных для кнопки назад

    ExpandableListView expandableListView;
    CustomAdapter customAdapter;
    List<Chapter> chapterList;
    List<Topics> topicsList;

    // Добавляем недостающие переменные для поиска
    ListView listView;
    ArrayAdapter<SearchResultItem> arrayAdapter;
    List<SearchResultItem> searchResults;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Включаем стандартную раскладку без налезания под статус-бар
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);

        // Toolbar для стабильного отображения меню/поиска в светлой теме
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null && getSupportActionBar() == null) {
            setSupportActionBar(toolbar);
            // Устанавливаем кастомный заголовок с уменьшенным шрифтом
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(getString(R.string.app_name));
                getSupportActionBar().setDisplayShowTitleEnabled(true);
            }
        }

        // Инициализируем список для поиска
        searchResults = new ArrayList<>();

        //пишем код поиска
        listView = findViewById(R.id.listView);
        arrayAdapter = new ArrayAdapter<SearchResultItem>(this, R.layout.list_customtext, searchResults) {
            @Override
            public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                android.view.View view = super.getView(position, convertView, parent);
                SearchResultItem item = getItem(position);
                if (item != null) {
                    TextView textView = view.findViewById(R.id.list_customeText);
                    textView.setText(item.getTitle());
                }
                return view;
            }
        };
        listView.setAdapter(arrayAdapter);
        
        // Добавляем обработчик нажатий на элементы списка поиска
        listView.setOnItemClickListener((parent, view, position, id) -> {
            SearchResultItem item = searchResults.get(position);
            openDocument(item.getFileName(), item.getTitle());
        });
        //финал кода поиска

        expandableListView = (ExpandableListView) findViewById(R.id.expandableListView);

        // Убираем ручные отступы — теперь тулбар в разметке

        // Не меняем флаги статус-бара вручную — оставляем управление теме
        addData();
        sendData();

        // Проверяем поддержку архитектуры
        checkArchitectureSupport();
    }

    private void checkArchitectureSupport() {
        String arch = System.getProperty("os.arch");
        String abi = Build.SUPPORTED_ABIS[0];

        Log.d("Architecture", "Current architecture: " + arch);
        Log.d("Architecture", "Primary ABI: " + abi);

        // Проверяем поддержку 16 КБ страниц (Android 14+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Log.d("Architecture", "16 KB page size support: Available but disabled for compatibility");
        } else {
            Log.d("Architecture", "16 KB page size support: Not available (requires Android 14+)");
        }

        Log.d("Architecture", "Native libraries compatibility mode: Enabled");
    }

    private void addActionBarPadding() {
        // Получаем высоту ActionBar
        int actionBarHeight = 0;
        if (getSupportActionBar() != null) {
            actionBarHeight = getSupportActionBar().getHeight();
        }

        // Если ActionBar еще не измерен, получаем его высоту из темы
        if (actionBarHeight == 0) {
            TypedValue tv = new TypedValue();
            if (getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
                actionBarHeight = TypedValue.complexToDimensionPixelSize(tv.data, getResources().getDisplayMetrics());
            }
        }

        // Добавляем отступ сверху для ExpandableListView
        if (expandableListView != null && actionBarHeight > 0) {
            int topPadding = actionBarHeight + getResources().getDimensionPixelSize(android.R.dimen.app_icon_size) / 4;
            expandableListView.setPadding(
                    expandableListView.getPaddingLeft(),
                    topPadding,
                    expandableListView.getPaddingRight(),
                    expandableListView.getPaddingBottom()
            );

            // Также добавляем отступ для ListView (результаты поиска)
            if (listView != null) {
                listView.setPadding(
                        listView.getPaddingLeft(),
                        topPadding,
                        listView.getPaddingRight(),
                        listView.getPaddingBottom()
                );
            }
        }
    }

    void addData() {
        //single time
        chapterList = new ArrayList<>();

        //multiple time
        topicsList = new ArrayList<>();

        //chapter 1 - добавлять по ходу сколько надо t01~10
        topicsList.add(new Topics("1. Конституция Республики Беларусь", "t001"));
        topicsList.add(new Topics("1.1. Закон об охране труда №356-З", "t01"));
        topicsList.add(new Topics("1.2. Трудовой кодекс Республики Беларусь", "t02"));
        topicsList.add(new Topics("1.3. 2025!Кодекс об административных правонарушениях", "t03"));
        topicsList.add(new Topics("1.4. 2025!Уголовный кодекс Республики Беларусь ст.1-308", "t04"));
        topicsList.add(new Topics("1.4. 2025!Уголовный кодекс Республики Беларусь ст.309-466", "t411"));
        topicsList.add(new Topics("1.5. Декрет №7 О развитии предпринимательства", "t05"));
        topicsList.add(new Topics("1.6. Закон о пожарной безопасности №2403-XII", "t06"));
        topicsList.add(new Topics("1.7. Закон о радиационной безопасности №198-З", "t07"));
        topicsList.add(new Topics("1.8. Закон об атомной энергии №208-З", "t08"));
        topicsList.add(new Topics("1.9. Указ №510 Контрольная (надзорная) деятельность", "t09"));
        topicsList.add(new Topics("1.10. Закон о промышленной безопасности №354-З", "t10"));
        topicsList.add(new Topics("1.11. Закон о санитарно-эпидемиологическом благополучии населения №340-З", "t400"));
        topicsList.add(new Topics("1.12. Закон о профессиональных союзах №1605-XII", "t002"));
        chapterList.add(new Chapter("1. Кодексы, Законы, Директивы...", topicsList));

        //chapter 2 t11~89
        topicsList = new ArrayList<>();
        topicsList.add(new Topics("2.1. 2025!Правила по охране труда №53", "t11"));
        topicsList.add(new Topics("2.2. МПОТ погрузочно-разгрузочные №12", "t12"));
        topicsList.add(new Topics("2.3. МПОТ при холодной обработке металлов №24/11", "t13"));
        topicsList.add(new Topics("2.4. ПОТ авто и горэлектро транспорт №78/104", "t14"));
        topicsList.add(new Topics("2.5. ПОТ при работе на высоте №52", "t15"));
        topicsList.add(new Topics("2.5.1. С 2026!ПОТ при работе на высоте №11", "t27"));
        topicsList.add(new Topics("2.6. МПОТ мобильные подъемные рабочие платформы №78", "t16"));
        topicsList.add(new Topics("2.7. МПОТ напольный безрельс и грузовые тележки №165", "t17"));
        topicsList.add(new Topics("2.8. ПОТ в сельском и рыбном хозяйствах №29/44", "t18"));
        topicsList.add(new Topics("2.9. ПОТ при выполнении строительный работ №24/33", "t19"));
        topicsList.add(new Topics("2.10. ПОТ лесное хозяйство, обработка древесины №32/5", "t20"));
        topicsList.add(new Topics("2.13. ПОТ при производстве пищевой продукции №122", "t23"));
        topicsList.add(new Topics("2.14. 2025!ПОТ при проведении полиграфических работ №84/11", "t24"));
        topicsList.add(new Topics("2.15. МПОТ промышленный альпинизм №184", "t25"));
        topicsList.add(new Topics("2.16. ПОТ при производстве резиновых и пластмассовых изделий №20", "t26"));
        topicsList.add(new Topics("2.18. МПОТ при эксплуатации строительных подъемников №12/2", "t28"));
        topicsList.add(new Topics("2.19. МПОТ при термической обработке металлов №99/9", "t29"));
        topicsList.add(new Topics("2.20. 2025!МПОТ при эксплуатации конвейерных, трубопроводных и др. №88", "t30"));
        topicsList.add(new Topics("2.23. 2025!ПОТ при оказании психиатрической помощи №86/89", "t33"));
        topicsList.add(new Topics("2.25. МПОТ при работе с химвеществами, проявляющими опасные свойства №90/9", "t35"));
        topicsList.add(new Topics("2.33. ПОТ при выполнении работ на объектах радиосвязи №7/3", "t43"));
        topicsList.add(new Topics("2.42. ПОТ для зоопарков №33", "t52"));
        topicsList.add(new Topics("2.43. ПОТ при выполнении работ в цирках №14/7", "t53"));
        topicsList.add(new Topics("2.44. ПОТ для театров и концертных залов №4", "t54"));
        topicsList.add(new Topics("2.45. ПОТ при производстве фильмов №31", "t55"));
        topicsList.add(new Topics("2.46. ПТБиОТ выправительные работы на внутренних водных путях №15", "t56"));
        topicsList.add(new Topics("2.50. ОПОТ строительство и ремонт автодорог №14", "t60"));
        topicsList.add(new Topics("2.51. ОПОТ в зелёном хозяйстве РБ №9", "t61"));
        topicsList.add(new Topics("2.58. ОПОТ машины для уборки улиц, дорог, спецсооружения №10", "t68"));
        topicsList.add(new Topics("2.60. ПОТ распространение издательской продукции №16", "t70"));
        chapterList.add(new Chapter("2. Правила по охране труда", topicsList));

        //chapter 3 t90~99
        topicsList = new ArrayList<>();
        topicsList.add(new Topics("3.1. Инструкция обучения, инструктажа и ПЗ по ОТ №175", "t90"));
        topicsList.add(new Topics("3.2. Положения о комиссиях для ПЗ по ОТ №210", "t91"));
        topicsList.add(new Topics("3.3. 2025!Перечень профессий для подготовки рабочих №7/14", "t92"));
        topicsList.add(new Topics("3.4. Стажировка водителей транспортных средств №46", "t93"));
        topicsList.add(new Topics("3.5. Перечень профессий рабочих - разряды после переподготовки №84/63", "t94"));
        chapterList.add(new Chapter("3. Обучение, инструктажи и ПЗ по ОТ", topicsList));

        //chapter 4 t100~104
        topicsList = new ArrayList<>();
        topicsList.add(new Topics("4.1. Контроль за соблюдением требований по ОТ №51", "t100"));
        chapterList.add(new Chapter("4. Контроль за состоянием ОТ", topicsList));

        //chapter 5 t105~115
        topicsList = new ArrayList<>();
        topicsList.add(new Topics("5.1. Рекомендации по разработке СУОТ №108", "t105"));
        topicsList.add(new Topics("5.2. Vision Zero: Концепция нулевого травматизма", "t107"));
        chapterList.add(new Chapter("5. Система управления охраной труда", topicsList));

        //chapter 6 t116~124
        topicsList = new ArrayList<>();
        topicsList.add(new Topics("6.1. Обязательные и внеочередные медосмотры №74", "t116"));
        topicsList.add(new Topics("6.2. Предсменный медосмотр или освидетельствование №116/119", "t117"));
        topicsList.add(new Topics("6.3. Предрейсовые медосмотры водителей (лицензия) №84", "t118"));
        topicsList.add(new Topics("6.4. Контроль водителей на алкоголь (без лицензии) №25/28", "t119"));
        topicsList.add(new Topics("6.5. Предрейсовые медосмотры трактористов №87", "t120"));
        topicsList.add(new Topics("6.6. Аптечки (перечни вложений) №178", "t121"));
        topicsList.add(new Topics("6.7. Медсправка (форма) №87", "t122"));
        chapterList.add(new Chapter("6. Медосмотры", topicsList));

        //chapter 7 t125~129
        topicsList = new ArrayList<>();
        topicsList.add(new Topics("7.1. Правила расследования НС №30", "t125"));
        topicsList.add(new Topics("7.2. 2025!Формы документов для расследования НС №81/144", "t126"));
        topicsList.add(new Topics("7.3. Правила определения тяжести производственных травм №9", "t127"));
        topicsList.add(new Topics("7.4. Соглашение о расследовании НС ЕАЭС №80", "t128"));
        topicsList.add(new Topics("7.5. О вынесении требования по спецрасследованию НС №5", "t129"));
        chapterList.add(new Chapter("7. Расследование несчастных случаев", topicsList));

        //chapter 8 t130~139
        topicsList = new ArrayList<>();
        topicsList.add(new Topics("8.1. Инструкция о порядке обеспечения СИЗ №209", "t130"));
        topicsList.add(new Topics("8.2. Перечень СИЗ непосредственно для безопасности труда №145", "t131"));
        topicsList.add(new Topics("8.3. Смывающие и обезвреживающие средства №208", "t132"));
        topicsList.add(new Topics("8.4. Рекомендации: Комиссия по качеству СИЗ №12", "t133"));
        topicsList.add(new Topics("8.4. ТРТС 019/2011 Безопасность СИЗ", "t134"));
        chapterList.add(new Chapter("8. Средства индивидуальной защиты", topicsList));

        //chapter 9 t140~149
        topicsList = new ArrayList<>();
        topicsList.add(new Topics("9.1. Типовой положение о службе ОТ №98", "t140"));
        chapterList.add(new Chapter("9. Служба охраны труда", topicsList));

        //chapter 10 t150~170
        topicsList = new ArrayList<>();
        topicsList.add(new Topics("10.1. Порядок осуществления общественного контроля №180", "t150"));
        topicsList.add(new Topics("10.2. 2025!Положение о технической инспекции труда ФПБ №153", "t151"));
        topicsList.add(new Topics("10.3. 2025!Порядок участия профсоюзов в расследовании НС №87", "t152"));
        topicsList.add(new Topics("10.4. Указ об общественном контроле №240", "t153"));
        topicsList.add(new Topics("10.5. Положение об общественном инспекторе по ОТ №132", "t154"));
        topicsList.add(new Topics("10.7. ЛПА по ОТ для согласования с профсоюзом", "t156"));
        topicsList.add(new Topics("10.8. Примерная программа обучения общественных инспекторов по ОТ №158", "t157"));
        topicsList.add(new Topics("10.9. Положение об общественной комиссии по ОТ №180", "t158"));
        topicsList.add(new Topics("10.10. Типовое положение о наставничестве №2", "t159"));
        topicsList.add(new Topics("10.11. Рекомендации по коллективно-договорному регулированию №221", "t160"));
        topicsList.add(new Topics("10.12. Комиссии по трудовым спорам (метод.рекомендации)", "t161"));
        topicsList.add(new Topics("10.13. Положение о правовой инспекции труда ФПБ №4", "t162"));
        topicsList.add(new Topics("10.14. Смотр-конкурсы по ОТ ФПБ №308", "t163"));
        topicsList.add(new Topics("10.15. Методические рекомендации соблюдения законодательства о труде", "t164"));
        topicsList.add(new Topics("10.16. Указ о защите трудовых прав работников №327", "t165"));
        chapterList.add(new Chapter("10. Профсоюзы", topicsList));

        //chapter 11 t171~
        topicsList = new ArrayList<>();
        topicsList.add(new Topics("11.1. О разработке инструкций по ОТ №176", "t170"));
        topicsList.add(new Topics("11.2. О планировании мероприятий по ОТ №111", "t171"));
        topicsList.add(new Topics("11.3. О нормах подъёма женщинами №133", "t172"));
        topicsList.add(new Topics("11.4. Запрещённые работы для женщин №35", "t173"));
        topicsList.add(new Topics("11.5. О нормах подъема детьми №134", "t174"));
        topicsList.add(new Topics("11.6. 2025!Запрет на труд до 18 лет №12", "t175"));
        topicsList.add(new Topics("11.7. 2025!Лёгкие работы от 14 до 16 лет №144", "t176"));
        topicsList.add(new Topics("11.8. Сроки хранения ЛПА по ОТ", "t177"));
        chapterList.add(new Chapter("11. Организация охраны труда", topicsList));

        //chapter 12 t180~
        topicsList = new ArrayList<>();
        topicsList.add(new Topics("12.1. Порядок аттестации по условиям труда №253", "t180"));
        topicsList.add(new Topics("12.2. 2025!Инструкция по оценке условий труда №35", "t181"));
        topicsList.add(new Topics("12.3. Оценка тяжести и напряжённости труда №027-2012", "t182"));
        topicsList.add(new Topics("12.5. Списки №1 и №2. Пост. №536", "t184"));
        topicsList.add(new Topics("12.5. Как применять Списки №1 и №2. Пост. №86", "t185"));
        chapterList.add(new Chapter("12. Аттестация рабочих мест", topicsList));

        //chapter 13 t190~
        topicsList = new ArrayList<>();
        topicsList.add(new Topics("13.1. Об обеспечении пожарной безопасности №82", "t190"));
        topicsList.add(new Topics("13.2. О внештатных пожарных формированиях №296", "t191"));
        topicsList.add(new Topics("13.3. Спецтребования по ПБ прибывание детей №561", "t192"));
        topicsList.add(new Topics("13.4. Спецтребования ПБ взрыво-пожароопасных производств №779", "t193"));
        chapterList.add(new Chapter("13. Пожарная безопасность", topicsList));

        //chapter 14 t210~
        topicsList = new ArrayList<>();
        topicsList.add(new Topics("14.2. Отчёт по форме 2-условия труда", "t211"));
        topicsList.add(new Topics("14.3. Отчёт 4 Улучшение условий и ОТ", "t212"));
        chapterList.add(new Chapter("14. Отчётность по охране труда", topicsList));

        //chapter 15 t220~
        topicsList = new ArrayList<>();
        topicsList.add(new Topics("15.1. СН 1.04.01-2020 Техсостояние зданий и сооружений", "t220"));
        chapterList.add(new Chapter("15. Здания и сооружения", topicsList));

        //chapter 16 t230~
        topicsList = new ArrayList<>();
        topicsList.add(new Topics("16.1. Правила обеспечения МОЛОКОМ №260", "t230"));
        topicsList.add(new Topics("16.2. Перечень вредных веществ для молока №34/12", "t231"));
        topicsList.add(new Topics("16.3. Перечень лечебно-профилактическое питание № 51/41", "t232"));
        topicsList.add(new Topics("16.4. Положение о лечебном питании №№ 491", "t233"));
        chapterList.add(new Chapter("16. Молоко и лечебное питание", topicsList));

        //chapter 17 t240~
        topicsList = new ArrayList<>();
        topicsList.add(new Topics("17.1. УКАЗ о лицензировании атомной энергии №137", "t240"));
        topicsList.add(new Topics("17.2. О реализации Закона о радиационной безопасности №497", "t241"));
        topicsList.add(new Topics("17.3. Критерии оценки радиационного воздействия №829 (вступает в силу с 08.03.2023)", "t242"));
        topicsList.add(new Topics("17.4. Радиационно-гигиенический паспорт №443", "t243"));
        topicsList.add(new Topics("17.5. Реестр аттестованных консультантов радиационной безопасности №19", "t244"));
        topicsList.add(new Topics("17.6. Безопасность при обращении с источниками ионизирующего излучения №79", "t245"));
        topicsList.add(new Topics("17.7. Категории источников ионизирующего излучения №4", "t246"));
        topicsList.add(new Topics("17.8. Учёт и контроль источников ионизирующего излучения №16", "t247"));
        topicsList.add(new Topics("17.9. Экспертиза безопасности ионизирующего излучения №17", "t248"));
        topicsList.add(new Topics("17.10. Обучение и ПЗ по вопросам ядерной и радиационной безопасности №18", "t249"));
        topicsList.add(new Topics("17.11. Документы ядерной и радиационной безопасности №64", "t250"));
        topicsList.add(new Topics("17.12. Учёт доз облучения населения и персонала №110", "t251"));
        topicsList.add(new Topics("17.13. СНиП Радиационная безопасность №213", "t252"));
        topicsList.add(new Topics("17.14. СНиП Обеспечение радиационной безопасности персонала и населения №137", "t253"));
        topicsList.add(new Topics("17.15. СНиП Обращение с лучевыми досмотровыми установками №134", "t254"));
        topicsList.add(new Topics("17.16. СНиП Линейные ускорители электронов до 100 МэВ №165", "t255"));
        chapterList.add(new Chapter("17. Радиационная безопасность", topicsList));

        //chapter 18 t260~
        topicsList = new ArrayList<>();
        topicsList.add(new Topics("18.1. 2025!Указ О СТРАХОВАНИИ №108", "t260"));
        topicsList.add(new Topics("18.2. Пособия по НС на производстве №393", "t261"));
        topicsList.add(new Topics("18.3. Положение об уплате страховых взносов №1297", "t262"));
        chapterList.add(new Chapter("18. Страховая деятельность", topicsList));

        //chapter 19 t270~
        topicsList = new ArrayList<>();
        topicsList.add(new Topics("19.1. 2025!Гражданский кодекс ст.1-309 N 218-З", "t270"));
        topicsList.add(new Topics("19.1.1 2025!Гражданский кодекс ст.310-655 N 218-З", "t272"));
        topicsList.add(new Topics("19.1.2 2025!Гражданский кодекс ст.656-1153 N 218-З", "t273"));
        topicsList.add(new Topics("19.2. Указ О защите прав граждан работающих по ГПД №314", "t271"));
        chapterList.add(new Chapter("19. Договор подряда (ГПД)", topicsList));

        //chapter 20 t280~
        topicsList = new ArrayList<>();
        topicsList.add(new Topics("20.1. О порядке осуществления мероприятий технического характера N33", "t280"));
        topicsList.add(new Topics("20.2. О разработке и функционировании систем контроля №78", "t281"));
        topicsList.add(new Topics("20.3. О порядке проведения идентификации опасных производственных объектов №613", "t282"));
        topicsList.add(new Topics("20.4. О порядке аттестации экспертов №614", "t283"));
        topicsList.add(new Topics("20.5. О порядке разработки, оформления... деклараций №627", "t284"));
        topicsList.add(new Topics("20.6. Положение об организации осуществления производственного контроля №37", "t285"));
        topicsList.add(new Topics("20.7. Правила аттестации сварщиков №100 ред.МЧС", "t286"));
        topicsList.add(new Topics("20.8. Инструкция о порядке подготовки и ПЗ №31", "t288"));
        topicsList.add(new Topics("20.9. Учебно-программная документация... №54", "t289"));
        topicsList.add(new Topics("20.10. О вопросах перевозки опасных грузов №376", "t290"));
        topicsList.add(new Topics("20.11. О подготовке и переподготовке работников, занятых перевозкой опасных грузов №37", "t291"));
        topicsList.add(new Topics("20.12. Программы подготовки, переподготовки... перевозки опасных грузов №76", "t292"));
        topicsList.add(new Topics("20.13. АБ УСТАНАЎЛЕННI ФОРМАЎ ПАСВЕДЧАННЯЎ №28", "t293"));
        topicsList.add(new Topics("20.14. Положение о порядке регистрации...транспортных средств №117", "t294"));
        topicsList.add(new Topics("20.15. Правила перевозки опасных грузов автотранспортом №9", "t295"));
        topicsList.add(new Topics("20.16. Правила перевозки опасных грузов водным транспортом №71", "t296"));
        topicsList.add(new Topics("20.17. Правила перевозки опасных грузов воздушными судами №62", "t297"));
        topicsList.add(new Topics("20.18. Правила перевозки опасных грузов ЖД №85", "t298"));
        topicsList.add(new Topics("20.19. Правила промбеза грузоподъёмных кранов №66", "t299"));
        topicsList.add(new Topics("20.20. Правила промбеза оборудования под избыточным давлением №84", "t300"));
        topicsList.add(new Topics("20.21. Правила промбеза в области газоснабжения №66", "t301"));
        topicsList.add(new Topics("20.22. Инструкция ... использование и учет взрывчатых материалов №5", "t302"));
        topicsList.add(new Topics("20.23. Правила промбеза разработка месторождений полезных ископаемых открытым способом №25", "t303"));
        topicsList.add(new Topics("20.24. Правила промбеза разработка подземным способом соляных месторождений №45", "t304"));
        topicsList.add(new Topics("20.25. Правила промбеза при переработке соляных руд №20", "t305"));
        topicsList.add(new Topics("20.26. Правила промбеза эксплуатация гидротехнических сооружений №15", "t306"));
        topicsList.add(new Topics("20.27. Правила промбеза при использовании и хранении хлора №31", "t307"));
        topicsList.add(new Topics("20.28. Правила промбеза при добыче нефти и газа №55", "t308"));
        topicsList.add(new Topics("20.29. Правила промбеза при проходке горных выработок... №30", "t309"));
        topicsList.add(new Topics("20.30. Правила промбеза взрывоопасных производств и объектов хранения и переработки зерна №35", "t310"));
        topicsList.add(new Topics("20.31. Правила промбеза взрывоопасных химических производств №54", "t311"));
        topicsList.add(new Topics("20.32. Правила промбеза при проходке стволов (рудников, шахт)... №26", "t312"));
        topicsList.add(new Topics("20.33. Правила промбеза... расплавы чёрных и цветных металлов... №19", "t313"));
        topicsList.add(new Topics("20.34. Правила промбеза при бурении скважин №34", "t314"));
        topicsList.add(new Topics("20.35. Правила промбеза аттракционов №67", "t315"));
        topicsList.add(new Topics("20.36. Правила промбеза лифтов, подъёмников, эсклаторов... №56", "t316"));
        topicsList.add(new Topics("20.37. Правила промбеза амиачных холодильных установок №46", "t317"));
        topicsList.add(new Topics("20.38. Инструкция по дейсвтвиям в аварийных ситуациях (амиак) №23", "t318"));
        topicsList.add(new Topics("20.39. Инструкция о срока и сборе инфы о возникновении аварии №33", "t319"));
        topicsList.add(new Topics("20.40. Правила промбеза котельные не более 0,07 не выше 115 С №5", "t320"));
        topicsList.add(new Topics("20.41. Правила промбеза эксплуатация технологических трубопроводов №21", "t321"));
        topicsList.add(new Topics("20.42. Охранная зона объектов газораспределительной системы №1474", "t322"));
        topicsList.add(new Topics("20.43. Охранная зона магистральных трубопроводов №800", "t323"));
        topicsList.add(new Topics("20.44. Порядок расследования причин аварий, инцидентов №36", "t324"));
        topicsList.add(new Topics("20.45. Порядок расследования аварий при перевозке опасных грузов №67", "t325"));
        topicsList.add(new Topics("20.46. Порядок учёта аварий, инцидентов при перевозке опасных грузов №24", "t326"));
        topicsList.add(new Topics("20.47. Порядок расследования и учёта несчастных случаев ПОО №6", "t327"));
        topicsList.add(new Topics("20.48. Перечень организаций уничтожающих пиротехнику №61", "t328"));
        topicsList.add(new Topics("20.49. Единая книжка взрывника №33", "t329"));
        topicsList.add(new Topics("20.50. Правила промбеза грузоподъёмные краны военного применения №26", "t330"));
        topicsList.add(new Topics("20.51. Правила промбеза ОПО военного применения №22", "t331"));
        topicsList.add(new Topics("20.52. Правила безопасности: перевозка опасных грузов Минобороны №15", "t332"));
        chapterList.add(new Chapter("20. Промышленная безопасность", topicsList));

        //chapter 21 t350~
        topicsList = new ArrayList<>();
        topicsList.add(new Topics("21.1. Спецсанэпид требования к условиям труда работающих N66", "t350"));
        topicsList.add(new Topics("21.2. Спецсанэпид требования к содер. и экспл. радиационных объектов N168", "t351"));
        topicsList.add(new Topics("21.3. Спецсанэпид требования к содер. и экспл. общежитий и иных мест проживания №740", "t352"));
        topicsList.add(new Topics("21.4. Спецсанэпид требования к содер. и экспл. санаторно-курортных и оздоровительных организаций №663", "t353"));
        topicsList.add(new Topics("21.5. Спецсанэпид требования к содер. и экспл. учреждений образования №525", "t354"));
        topicsList.add(new Topics("21.6. Спецсанэпид требования к содер. и экспл. организаций здравоохранения... №130", "t355"));
        topicsList.add(new Topics("21.7. Спецсанэпид требования к содер. и экспл. объектов АПК... №42", "t356"));
        topicsList.add(new Topics("21.8. Спецсанэпид требования к установлению санитарно-защитных зон объектов... №847", "t357"));
        topicsList.add(new Topics("21.9. Спецсанэпид требования к содер. и экспл. объектов неионизирующего излучения №360", "t358"));
        topicsList.add(new Topics("21.10. Спецсанэпид требования к объектам пром.по переработке сельхозпродукции... №146", "t359"));
        topicsList.add(new Topics("21.11. Спецсанэпид требования к содер. и экспл. источников и систем водоснабжения №914", "t360"));
        topicsList.add(new Topics("21.12. Гигиенический норматив Микроклимат на рабочих местах №37", "t361"));
        topicsList.add(new Topics("21.12.1. Гигиенический норматив ПЭВМ №37", "t403"));
        topicsList.add(new Topics("21.12.2. Гигиенический норматив Ультразвук №37", "t404"));
        topicsList.add(new Topics("21.12.3. Гигиенический норматив Инфразвук №37", "t405"));
        topicsList.add(new Topics("21.12.4. Гигиенический норматив Воздух №37", "t406"));
        topicsList.add(new Topics("21.13. Гигиеническая классификация условий труда №211", "t362"));
        topicsList.add(new Topics("21.14. Перечень работ и услуг, представляющих потенциальную опасность для жизни и здоровья населения №104", "t363"));
        topicsList.add(new Topics("21.15. Организация и проведение производственного контроля... №183", "t364"));
        topicsList.add(new Topics("21.17. Положение о КГОУТ", "t366"));
        topicsList.add(new Topics("21.18. Положение о проведения государственной санитарно-гигиенической экспертизы №119", "t367"));
        topicsList.add(new Topics("21.19. Саннормы к условиям труда, содержанию и эксплуатации производственных объектов №114", "t368"));
        chapterList.add(new Chapter("21. Санитария и гигиена", topicsList));

        //chapter 22 t401~
        topicsList = new ArrayList<>();
        topicsList.add(new Topics("22.1. Правила безопасности организации образовательного процесса N227", "t401"));
        topicsList.add(new Topics("22.2. Правила безопасности проведения занятий физкультурой и спортом N60", "t402"));
        chapterList.add(new Chapter("22. Правила безопасности", topicsList));

        //chapter 23 t420~
        topicsList = new ArrayList<>();
        topicsList.add(new Topics("23.1. При эксплуатации машин неприрывного действия", "t420"));
        topicsList.add(new Topics("23.2. При выполнении окрасочных (малярных) работ", "t421"));
        topicsList.add(new Topics("23.3. При эксплуатации кузнечо-прессового оборудования", "t422"));
        topicsList.add(new Topics("23.4. При эксплуатации сельскохозяйственных машин", "t423"));
        topicsList.add(new Topics("23.5. При эксплуатации средств подмащивания", "t424"));
        topicsList.add(new Topics("23.6. При эксплуатации деревообрабатывающего оборудования", "t425"));
        topicsList.add(new Topics("23.7. При эксплуатации строительных машин", "t426"));
        topicsList.add(new Topics("23.8. При выполнении работ по ремонту и обслуживанию электрооборудования", "t427"));
        topicsList.add(new Topics("23.9. При эксплуатации напольного безрельсового транспорта", "t428"));
        topicsList.add(new Topics("23.10. При выполнении работ внутри колодцев, цистерн и других емкостных сооружений", "t429"));
        topicsList.add(new Topics("23.11. При выполнении работ по косьбе травы", "t430"));
        topicsList.add(new Topics("23.12. При производстве слесарных, слесарно-сборочных и столярных работ", "t431"));
        topicsList.add(new Topics("23.13. При производстве работ на высоте", "t432"));
        topicsList.add(new Topics("23.14. При переработке пластмасс", "t433"));
        topicsList.add(new Topics("23.15. При выполнении ремонта транспортных средств", "t434"));
        topicsList.add(new Topics("23.16. При обеспечении работников СИЗ", "t435"));
        topicsList.add(new Topics("23.17. При эксплуатации автотракторной техники", "t436"));
        topicsList.add(new Topics("23.18. При выполнении земляных работ", "t437"));
        topicsList.add(new Topics("23.19. При выполнении строительных работ", "t438"));
        topicsList.add(new Topics("23.20. При выполнении кровельных работ", "t439"));
        topicsList.add(new Topics("23.21. При эксплуатации МПРП", "t440"));
        topicsList.add(new Topics("23.22. При эксплуатации сверлильных и заточных станков", "t441"));
        topicsList.add(new Topics("23.23. При производстве работ с лестниц и стремянок", "t442"));
        topicsList.add(new Topics("23.24. При выполнении работ по дроблению, измельчению и обогощению полезных ископаемых", "t443"));
        topicsList.add(new Topics("23.25. При производстве электрогазосварочных работ", "t444"));
        topicsList.add(new Topics("23.26. При выполнении работ по термической обработке металлов", "t445"));
        topicsList.add(new Topics("23.27. При производстве огенвых работ", "t446"));
        topicsList.add(new Topics("23.28. При содержании территории, зданий и сооружений в зимний период", "t447"));
        chapterList.add(new Chapter("23. Типичные нарушения требований ОТ", topicsList));
    }

    void sendData() {
        customAdapter = new CustomAdapter(chapterList, MainActivity.this);
        expandableListView.setAdapter(customAdapter);
    }

    private void performSearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            // Очищаем результаты поиска
            searchResults.clear();
            arrayAdapter.notifyDataSetChanged();
            return;
        }

        searchResults.clear();
        String lowerQuery = query.toLowerCase().trim();

        // Поиск по всем главам и темам
        for (Chapter chapter : chapterList) {
            for (Topics topic : chapter.getTopicsList()) {
                if (topic.getTopicName().toLowerCase().contains(lowerQuery)) {
                    searchResults.add(new SearchResultItem(
                        topic.getTopicName(),
                        topic.getFileName(),
                        chapter.getChapterName()
                    ));
                }
            }
        }

        // Обновляем адаптер поиска
        arrayAdapter.notifyDataSetChanged();

        // Показываем результаты поиска
        expandableListView.setVisibility(View.GONE);
        listView.setVisibility(View.VISIBLE);
        
        // Если ничего не найдено, показываем toast
        if (searchResults.isEmpty()) {
            android.widget.Toast.makeText(this, "Ничего не найдено", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        getMenuInflater().inflate(R.menu.main_actionbar_menu, menu);

        // На главном экране скрываем кнопку добавления закладки
        MenuItem bm = menu.findItem(R.id.add_bookmark);
        if (bm != null) bm.setVisible(false);


        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.privacy:
                startActivity(new Intent(MainActivity.this, Privacy.class));
                return true;
            case R.id.contact:
                startActivity(new Intent(MainActivity.this, Contact.class));
                return true;
            case R.id.bookmarks:
                startActivity(new Intent(MainActivity.this, BookmarksActivity.class));
                return true;
            case R.id.notes:
                startActivity(new Intent(MainActivity.this, NotesActivity.class));
                return true;
            case R.id.iot_game:
                startActivity(new Intent(MainActivity.this, by.instruction.papera.game.IotGameActivity.class));
                return true;
            case R.id.search:
                // Показываем диалог поиска
                showSearchDialog();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    //начало кода системной кнопки назад
    @Override
    public void onBackPressed() {
        // Если показываются результаты поиска, возвращаемся к главному экрану
        if (listView != null && listView.getVisibility() == View.VISIBLE) {
            // Очищаем результаты поиска
            searchResults.clear();
            arrayAdapter.notifyDataSetChanged();
            
            // Показываем главный экран
            expandableListView.setVisibility(View.VISIBLE);
            listView.setVisibility(View.GONE);
            
            // Обновляем заголовок
            getSupportActionBar().setTitle(getString(R.string.app_name));
            
            return;
        }

        if (backPressedTime + 2000 > System.currentTimeMillis()){
            if (backToast != null) backToast.cancel();
            super.onBackPressed();
            return;
        }else{
            backToast = Toast.makeText(getBaseContext(), "Теперь можно бахнуть кофейку:)", Toast.LENGTH_SHORT);
            backToast.show();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask();
            finishAffinity();
        }
        backPressedTime = System.currentTimeMillis();
    }
    //конец кода системной кнопки назад
    
    // Диалог поиска
    private void showSearchDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Поиск по документам");
        
        // Создаем EditText для ввода поискового запроса
        final android.widget.EditText input = new android.widget.EditText(this);
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
                performSearch(query);
            }
        });
        
        builder.setNegativeButton("Отмена", (dialog, which) -> dialog.cancel());
        
        // Кнопка "Очистить"
        builder.setNeutralButton("Очистить", (dialog, which) -> {
            // Очищаем результаты поиска
            searchResults.clear();
            arrayAdapter.notifyDataSetChanged();
            // Показываем основной список
            expandableListView.setVisibility(View.VISIBLE);
            listView.setVisibility(View.GONE);
        });
        
        android.app.AlertDialog dialog = builder.create();
        dialog.show();
        
        // Фокус на поле ввода
        input.requestFocus();
        input.selectAll();
    }
    
    // Метод для открытия документа
    private void openDocument(String fileName, String docTitle) {
        // Проверяем наличие файла с расширениями .doc или .docx
        String actualFileName = null;
        try {
            InputStream test = getAssets().open(fileName + ".doc");
            test.close();
            actualFileName = fileName + ".doc";
        } catch (IOException e1) {
            try {
                InputStream test = getAssets().open(fileName + ".docx");
                test.close();
                actualFileName = fileName + ".docx";
            } catch (IOException e2) {
                Toast.makeText(this, "Файл не найден: " + fileName + ".doc/.docx", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        Intent intent = new Intent(this, FullView.class);
        intent.putExtra("fileName", actualFileName);
        intent.putExtra("docTitle", docTitle);
        startActivity(intent);
    }
}