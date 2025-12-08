package by.instruction.papera;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import java.io.InputStream;
import java.io.IOException;

import java.util.List;

public class CustomAdapter extends BaseExpandableListAdapter {

    List<Chapter> chapterList;
    Context context;

    public CustomAdapter(List<Chapter> chapterList, Context context) {
        this.chapterList = chapterList;
        this.context = context;
    }

    //parent size count количество родительских размеров
    @Override
    public int getGroupCount() {
        return chapterList.size();
    }

    @Override
    public int getChildrenCount(int i) {
        return chapterList.get(i).getTopicsList().size();
    }

    //position set
    @Override
    public Object getGroup(int i) {
        return chapterList.get(i);
    }

    @Override
    public Object getChild(int i, int i1) {
        return chapterList.get(i).getTopicsList().get(i1);
    }

    //id
    @Override
    public long getGroupId(int i) {
        return i;
    }

    @Override
    public long getChildId(int i, int i1) {
        return i1;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getGroupView(int i, boolean b, View view, ViewGroup viewGroup) {

        view = LayoutInflater.from(context).inflate(R.layout.chapter_item, viewGroup, false);
        TextView chapterName=(TextView)view.findViewById(R.id.chapterTitle);
        chapterName.setText(chapterList.get(i).getChapterName());

        ImageView imageView=(ImageView)view.findViewById(R.id.arrow);

        if (b == true)
        {
            imageView.setRotation(90);
        }
        else if (b == false)
        {
            imageView.setRotation(0);
        }



        return view;
    }

    @Override
    public View getChildView(int i, int i1, boolean b, View view, ViewGroup viewGroup) {
        view= LayoutInflater.from(context).inflate(R.layout.topics_item, viewGroup, false);
        TextView topicName=(TextView)view.findViewById(R.id.topicTitle);
        topicName.setText(chapterList.get(i).getTopicsList().get(i1).getTopicName());

        CardView cardView=(CardView)view.findViewById(R.id.topicClick);

        cardView.setOnClickListener(view1 -> {
            String fileCode = chapterList.get(i).getTopicsList().get(i1).getFileName();
            String docTitle = chapterList.get(i).getTopicsList().get(i1).getTopicName();
            
            // Проверяем наличие файла с расширениями .doc или .docx
            String fileName = null;
            try {
                InputStream test = context.getAssets().open(fileCode + ".doc");
                test.close();
                fileName = fileCode + ".doc";
            } catch (IOException e1) {
                try {
                    InputStream test = context.getAssets().open(fileCode + ".docx");
                    test.close();
                    fileName = fileCode + ".docx";
                } catch (IOException e2) {
                    android.widget.Toast.makeText(context, "Файл не найден: " + fileCode + ".doc/.docx", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            Intent intent =(new Intent(context, FullView.class));
            intent.putExtra("fileName", fileName);
            intent.putExtra("docTitle", docTitle);
            context.startActivity(intent);
        });

        return view;
    }

    @Override
    public boolean isChildSelectable(int i, int i1) { return true; }
}