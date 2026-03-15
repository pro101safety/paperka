package by.instruction.papera;

import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class AiChatAdapter extends RecyclerView.Adapter<AiChatAdapter.ChatViewHolder> {
    private static final int COLLAPSED_SOURCES_COUNT = 3;
    private final List<AiChatMessage> items = new ArrayList<>();
    private final OnSourceClickListener onSourceClickListener;

    public interface OnSourceClickListener {
        void onSourceClick(AiSource source);
    }

    public AiChatAdapter(OnSourceClickListener onSourceClickListener) {
        this.onSourceClickListener = onSourceClickListener;
    }

    public void submit(List<AiChatMessage> messages) {
        items.clear();
        items.addAll(messages);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        AiChatMessage message = items.get(position);
        holder.messageText.setText(message.getText());

        if (message.getRole() == AiChatMessage.Role.USER) {
            setAlignment(holder.messageRoot, Gravity.END);
            holder.messageCard.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), android.R.color.holo_blue_light));
            holder.messageText.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), android.R.color.white));
            holder.messageSources.setVisibility(View.GONE);
            holder.messageSourcesToggle.setVisibility(View.GONE);
        } else {
            setAlignment(holder.messageRoot, Gravity.START);
            holder.messageCard.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), android.R.color.darker_gray));
            holder.messageText.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), android.R.color.black));
            List<AiSource> allSources = message.getSources();
            int visibleCount = message.isSourcesExpanded()
                    ? allSources.size()
                    : Math.min(COLLAPSED_SOURCES_COUNT, allSources.size());
            CharSequence sourcesText = formatSources(
                    holder.itemView.getContext().getString(R.string.ai_chat_sources_prefix),
                    allSources,
                    visibleCount
            );
            if (sourcesText.length() == 0) {
                holder.messageSources.setVisibility(View.GONE);
                holder.messageSourcesToggle.setVisibility(View.GONE);
            } else {
                holder.messageSources.setText(sourcesText);
                holder.messageSources.setMovementMethod(LinkMovementMethod.getInstance());
                holder.messageSources.setHighlightColor(ContextCompat.getColor(holder.itemView.getContext(), android.R.color.transparent));
                holder.messageSources.setVisibility(View.VISIBLE);

                if (!message.isSourcesExpanded() && allSources.size() > COLLAPSED_SOURCES_COUNT) {
                    holder.messageSourcesToggle.setVisibility(View.VISIBLE);
                    holder.messageSourcesToggle.setOnClickListener(v -> {
                        message.setSourcesExpanded(true);
                        int adapterPos = holder.getBindingAdapterPosition();
                        if (adapterPos != RecyclerView.NO_POSITION) {
                            notifyItemChanged(adapterPos);
                        } else {
                            notifyDataSetChanged();
                        }
                    });
                } else {
                    holder.messageSourcesToggle.setVisibility(View.GONE);
                    holder.messageSourcesToggle.setOnClickListener(null);
                }
            }
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private CharSequence formatSources(String prefix, List<AiSource> sources, int limit) {
        if (sources == null || sources.isEmpty()) {
            return "";
        }
        SpannableStringBuilder sb = new SpannableStringBuilder(prefix);
        int visible = Math.min(limit, sources.size());
        for (int i = 0; i < visible; i++) {
            AiSource source = sources.get(i);
            sb.append("\n\n");
            int lineStart = sb.length();
            String title = (i + 1) + ") [Раздел] " + source.getDocumentName();
            sb.append(title);
            int lineEnd = sb.length();
            sb.setSpan(new SourceClickableSpan(source), lineStart, lineEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (source.getSnippet() != null && !source.getSnippet().trim().isEmpty()) {
                sb.append("\n");
                sb.append("Цитата: ").append("\"").append(source.getSnippet()).append("\"");
            }
        }
        return sb;
    }

    private void setAlignment(View root, int gravity) {
        ViewGroup.LayoutParams lp = root.getLayoutParams();
        if (lp instanceof RecyclerView.LayoutParams) {
            RecyclerView.LayoutParams rlp = (RecyclerView.LayoutParams) lp;
            if (rlp instanceof ViewGroup.MarginLayoutParams) {
                ((ViewGroup.MarginLayoutParams) rlp).width = ViewGroup.LayoutParams.MATCH_PARENT;
            }
            root.setLayoutParams(rlp);
        }
        if (root instanceof LinearLayout) {
            ((LinearLayout) root).setGravity(gravity);
        }
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        final LinearLayout messageRoot;
        final MaterialCardView messageCard;
        final TextView messageText;
        final TextView messageSources;
        final TextView messageSourcesToggle;

        ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            messageRoot = itemView.findViewById(R.id.messageRoot);
            messageCard = itemView.findViewById(R.id.messageCard);
            messageText = itemView.findViewById(R.id.messageText);
            messageSources = itemView.findViewById(R.id.messageSources);
            messageSourcesToggle = itemView.findViewById(R.id.messageSourcesToggle);
        }
    }

    private class SourceClickableSpan extends ClickableSpan {
        private final AiSource source;

        SourceClickableSpan(AiSource source) {
            this.source = source;
        }

        @Override
        public void onClick(@NonNull View widget) {
            if (onSourceClickListener != null) {
                onSourceClickListener.onSourceClick(source);
            }
        }

        @Override
        public void updateDrawState(@NonNull TextPaint ds) {
            super.updateDrawState(ds);
            ds.setUnderlineText(true);
            ds.setFakeBoldText(true);
        }
    }
}
