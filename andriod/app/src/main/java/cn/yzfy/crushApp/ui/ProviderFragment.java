package cn.yzfy.crushApp.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import cn.yzfy.crushApp.api.ProviderApi;
import cn.yzfy.crushApp.api.Rest;
import cn.yzfy.crushApp.model.AiProvider;

/** 自定义 LLM 供应商管理：对话大模型（文本聊天）+ 语音大模型（CosyVoice 合成） */
public class ProviderFragment extends Fragment {

    private final List<AiProvider> items = new ArrayList<>();
    private ProviderAdapter adapter;
    private TextView empty;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        android.content.Context ctx = requireContext();

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFFBF3F5);

        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(0xFFFFFFFF);
        header.setElevation(Ui.dp(ctx, 2));
        header.setPadding(Ui.dp(ctx, 6), Ui.dp(ctx, 8), Ui.dp(ctx, 6), Ui.dp(ctx, 8));
        TextView back = new TextView(ctx);
        back.setText("‹");
        back.setTextSize(32);
        back.setTextColor(0xFF4A4052);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> requireActivity().onBackPressed());
        header.addView(back, Ui.dp(ctx, 44), Ui.dp(ctx, 44));
        TextView title = new TextView(ctx);
        title.setText("模型供应商");
        title.setTextSize(17);
        title.setTextColor(0xFF2A2233);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView add = new TextView(ctx);
        add.setText("＋ 新增");
        add.setTextSize(13);
        add.setTextColor(0xFFFFFFFF);
        add.setGravity(Gravity.CENTER);
        add.setBackground(Ui.rounded(0xFFFF5A7A, 12));
        add.setPadding(Ui.dp(ctx, 10), Ui.dp(ctx, 6), Ui.dp(ctx, 10), Ui.dp(ctx, 6));
        add.setOnClickListener(v -> edit(null));
        header.addView(add);
        root.addView(header);

        TextView tip = new TextView(ctx);
        tip.setText("对话大模型用于文本聊天（OpenAI 兼容协议）；语音大模型只需填 API KEY，接入地址/模型从配置读取，音色在暗恋对象页面单独设置。运行时立即生效。");
        tip.setTextSize(12);
        tip.setTextColor(0xFFA5929C);
        tip.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 8), Ui.dp(ctx, 16), Ui.dp(ctx, 4));
        root.addView(tip);

        RecyclerView list = new RecyclerView(ctx);
        list.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        list.setLayoutManager(new LinearLayoutManager(ctx));
        list.setClipToPadding(false);
        list.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 4), Ui.dp(ctx, 12), Ui.dp(ctx, 12));
        adapter = new ProviderAdapter();
        list.setAdapter(adapter);
        root.addView(list);

        empty = new TextView(ctx);
        empty.setText("还没有自定义供应商\n点右上角「＋ 新增」接入第一个模型");
        empty.setTextSize(14);
        empty.setTextColor(0xFFB8A5AC);
        empty.setGravity(Gravity.CENTER);
        empty.setVisibility(View.GONE);
        root.addView(empty, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        ProviderApi.list(new Rest.Callback<List<AiProvider>>() {
            @Override
            public void ok(List<AiProvider> data) {
                items.clear();
                if (data != null) items.addAll(data);
                adapter.notifyDataSetChanged();
                empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void fail(String message) {
                Ui.toast(requireContext(), message, true);
            }
        });
    }

    private void edit(final AiProvider p) {
        final android.content.Context ctx = requireContext();
        final boolean isVoice = p != null && "voice".equals(p.type);

        Dialog d = new Dialog(ctx);
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(ctx, 22), Ui.dp(ctx, 18), Ui.dp(ctx, 22), Ui.dp(ctx, 18));

        TextView head = new TextView(ctx);
        head.setText(p == null ? "新增供应商" : "编辑供应商");
        head.setTextSize(17);
        head.setTextColor(0xFF2A2233);
        head.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        box.addView(head);

        // 供应商类型单选（编辑时不可改）
        RadioGroup typeGroup = new RadioGroup(ctx);
        typeGroup.setOrientation(LinearLayout.HORIZONTAL);
        typeGroup.setPadding(0, Ui.dp(ctx, 10), 0, 0);
        RadioButton chatBtn = new RadioButton(ctx);
        chatBtn.setText("💬 对话大模型");
        chatBtn.setTextSize(13);
        chatBtn.setPadding(0, 0, Ui.dp(ctx, 16), 0);
        RadioButton voiceBtn = new RadioButton(ctx);
        voiceBtn.setText("🎙 语音大模型");
        voiceBtn.setTextSize(13);
        typeGroup.addView(chatBtn);
        typeGroup.addView(voiceBtn);
        if (isVoice) {
            voiceBtn.setChecked(true);
            typeGroup.setEnabled(false);
            chatBtn.setEnabled(false);
            voiceBtn.setEnabled(false);
        } else {
            chatBtn.setChecked(true);
        }
        box.addView(typeGroup);

        // 语音类型说明
        final TextView voiceTip = new TextView(ctx);
        voiceTip.setText("语音大模型只需填 API KEY，接入地址和模型从服务端配置读取；音色（voice_id）在暗恋对象页面单独设置。");
        voiceTip.setTextSize(11);
        voiceTip.setTextColor(0xFFA5929C);
        voiceTip.setPadding(0, Ui.dp(ctx, 6), 0, 0);
        voiceTip.setVisibility(isVoice ? View.VISIBLE : View.GONE);
        box.addView(voiceTip);

        // 对话大模型专属字段容器（语音类型时隐藏）
        final LinearLayout chatFields = new LinearLayout(ctx);
        chatFields.setOrientation(LinearLayout.VERTICAL);
        chatFields.setVisibility(isVoice ? View.GONE : View.VISIBLE);

        final EditText name = field(chatFields, ctx, "名称（如 deepseek）", p == null ? null : p.name);
        final EditText key = field(chatFields, ctx, "供应商代号（providerKey）", p == null ? null : p.providerKey);
        final EditText baseUrl = field(chatFields, ctx, "Base URL", p == null ? null : p.baseUrl);
        final EditText apiKeyChat = field(chatFields, ctx, "apiKey", p == null ? null : p.apiKey);
        final EditText model = field(chatFields, ctx, "model 名称", p == null ? null : p.model);
        final EditText temp = field(chatFields, ctx, "temperature（选填）", p == null || p.temperature == null ? null : String.valueOf(p.temperature));
        final EditText topP = field(chatFields, ctx, "topP（选填）", p == null || p.topP == null ? null : String.valueOf(p.topP));
        final EditText maxTokens = field(chatFields, ctx, "maxTokens（选填）", p == null || p.maxTokens == null ? null : String.valueOf(p.maxTokens));

        final CheckBox vision = new CheckBox(ctx);
        vision.setText("视觉（看图识图）");
        vision.setChecked(p != null && p.has("vision"));
        chatFields.addView(vision);

        final CheckBox audio = new CheckBox(ctx);
        audio.setText("音频（听语音/语音输入）");
        audio.setChecked(p != null && p.has("audio"));
        chatFields.addView(audio);

        box.addView(chatFields);

        // 语音大模型的 API KEY（独立字段，语音类型时显示）
        final EditText apiKeyVoice = field(box, ctx, "API KEY（必填）", p == null ? null : p.apiKey);
        apiKeyVoice.setVisibility(isVoice ? View.VISIBLE : View.GONE);
        apiKeyVoice.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        // 类型切换时显示/隐藏字段
        typeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean voice = checkedId == voiceBtn.getId();
            chatFields.setVisibility(voice ? View.GONE : View.VISIBLE);
            apiKeyVoice.setVisibility(voice ? View.VISIBLE : View.GONE);
            voiceTip.setVisibility(voice ? View.VISIBLE : View.GONE);
        });

        final CheckBox def = new CheckBox(ctx);
        def.setText("设为默认（同一类型仅一个）");
        def.setChecked(p != null && Boolean.TRUE.equals(p.isDefault));
        def.setPadding(0, Ui.dp(ctx, 6), 0, 0);
        box.addView(def);

        LinearLayout btns = new LinearLayout(ctx);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setGravity(Gravity.END);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.dp(ctx, 6);
        btns.setLayoutParams(blp);

        TextView cancel = new TextView(ctx);
        cancel.setText("取消");
        cancel.setTextSize(14);
        cancel.setTextColor(0xFFA5929C);
        cancel.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 8), Ui.dp(ctx, 14), Ui.dp(ctx, 8));
        cancel.setOnClickListener(v -> d.dismiss());
        btns.addView(cancel);

        TextView ok = new TextView(ctx);
        ok.setText(p == null ? "创建" : "保存");
        ok.setTextSize(14);
        ok.setTextColor(0xFFFFFFFF);
        ok.setBackground(Ui.rounded(0xFFFF5A7A, 12));
        ok.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 8), Ui.dp(ctx, 16), Ui.dp(ctx, 8));
        ok.setOnClickListener(v -> {
            boolean selectedVoice = typeGroup.getCheckedRadioButtonId() == voiceBtn.getId();
            AiProvider n = new AiProvider();
            if (p != null) n.id = p.id;
            n.type = selectedVoice ? "voice" : "chat";

            if (selectedVoice) {
                // 语音大模型：只填 API KEY，名称/代号自动生成
                String ak = apiKeyVoice.getText().toString().trim();
                if (ak.isEmpty()) {
                    Ui.toast(ctx, "API KEY 为必填");
                    return;
                }
                n.apiKey = ak;
                if (p == null) {
                    long ts = System.currentTimeMillis();
                    n.providerKey = "voice-" + ts;
                    n.name = "语音供应商 " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date(ts));
                } else {
                    n.providerKey = p.providerKey;
                    n.name = p.name;
                }
            } else {
                // 对话大模型：完整字段
                n.name = name.getText().toString().trim();
                n.providerKey = key.getText().toString().trim();
                n.baseUrl = baseUrl.getText().toString().trim();
                n.apiKey = apiKeyChat.getText().toString().trim();
                n.model = model.getText().toString().trim();
                try {
                    if (!temp.getText().toString().trim().isEmpty()) n.temperature = Double.parseDouble(temp.getText().toString().trim());
                    if (!topP.getText().toString().trim().isEmpty()) n.topP = Double.parseDouble(topP.getText().toString().trim());
                    if (!maxTokens.getText().toString().trim().isEmpty()) n.maxTokens = Integer.parseInt(maxTokens.getText().toString().trim());
                } catch (NumberFormatException ignored) {
                }
                List<String> caps = new ArrayList<>();
                if (vision.isChecked()) caps.add("vision");
                if (audio.isChecked()) caps.add("audio");
                n.capabilities = caps;
                if (n.name.isEmpty() || n.providerKey.isEmpty() || n.baseUrl.isEmpty() || n.model.isEmpty()) {
                    Ui.toast(ctx, "名称/代号/BaseURL/model 必填");
                    return;
                }
            }

            n.isDefault = def.isChecked();
            d.dismiss();
            android.app.Dialog dlg = Ui.loading(ctx, "保存中…");
            if (p == null) {
                ProviderApi.create(n, saveCb(dlg));
            } else {
                ProviderApi.update(p.id, n, saveCb(dlg));
            }
        });
        btns.addView(ok);
        box.addView(btns);

        d.setContentView(box);
        d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        d.show();
    }

    private Rest.Callback<AiProvider> saveCb(final Dialog dlg) {
        return new Rest.Callback<AiProvider>() {
            @Override
            public void ok(AiProvider data) {
                Ui.dismiss(dlg);
                Ui.toast(requireContext(), "已保存，即时生效");
                load();
            }

            @Override
            public void fail(String message) {
                Ui.dismiss(dlg);
                Ui.toast(requireContext(), message, true);
            }
        };
    }

    private EditText field(LinearLayout parent, android.content.Context ctx, String label, String value) {
        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setTextSize(12);
        tv.setTextColor(0xFF6B5E70);
        LinearLayout.LayoutParams lpl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpl.topMargin = Ui.dp(ctx, 8);
        tv.setLayoutParams(lpl);
        parent.addView(tv);

        EditText input = new EditText(ctx);
        input.setTextSize(14);
        input.setTextColor(0xFF2A2233);
        if (value != null) input.setText(value);
        input.setBackground(Ui.rounded(0xFFFBF6F7, 10));
        input.setPadding(Ui.dp(ctx, 10), Ui.dp(ctx, 8), Ui.dp(ctx, 10), Ui.dp(ctx, 8));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.topMargin = Ui.dp(ctx, 4);
        input.setLayoutParams(ilp);
        parent.addView(input);
        return input;
    }

    private class ProviderAdapter extends RecyclerView.Adapter<ProviderAdapter.Holder> {
        class Holder extends RecyclerView.ViewHolder {
            final LinearLayout row;

            Holder(View v) {
                super(v);
                row = (LinearLayout) v;
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            android.content.Context ctx = parent.getContext();
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setBackground(Ui.rounded(0xFFFFFFFF, 16));
            row.setElevation(Ui.dp(ctx, 1));
            row.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 12), Ui.dp(ctx, 14), Ui.dp(ctx, 12));
            RecyclerView.LayoutParams rp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.bottomMargin = Ui.dp(ctx, 8);
            row.setLayoutParams(rp);

            TextView nameBox = new TextView(ctx);
            nameBox.setTag("name");
            nameBox.setTextSize(15);
            nameBox.setTextColor(0xFF2A2233);
            nameBox.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            row.addView(nameBox);
            TextView sub = new TextView(ctx);
            sub.setTag("sub");
            sub.setTextSize(12);
            sub.setTextColor(0xFF6B5E70);
            row.addView(sub);
            LinearLayout caps = new LinearLayout(ctx);
            caps.setTag("caps");
            caps.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams cplp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cplp.topMargin = Ui.dp(ctx, 6);
            caps.setLayoutParams(cplp);
            row.addView(caps);
            return new Holder(row);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            final AiProvider p = items.get(position);
            TextView name = (TextView) h.row.getChildAt(0);
            TextView sub = (TextView) h.row.getChildAt(1);
            LinearLayout capsBox = (LinearLayout) h.row.getChildAt(2);

            boolean isVoice = "voice".equals(p.type);
            name.setText((Boolean.TRUE.equals(p.isDefault) ? "⭐ " : "") + (p.name == null ? "" : p.name));
            if (isVoice) {
                sub.setText((p.providerKey == null ? "" : p.providerKey) + " · 语音合成");
            } else {
                sub.setText((p.providerKey == null ? "" : p.providerKey) + " · " + (p.model == null ? "" : p.model));
            }

            capsBox.removeAllViews();
            if (isVoice) {
                addCap(capsBox, "🎙 语音", 0xFF9C27B0, 0xFFF3E5F5);
            } else {
                if (p.has("vision")) addCap(capsBox, "👀 看图", 0xFFFF5A7A, 0xFFFFF0F3);
                if (p.has("audio")) addCap(capsBox, "🎙 听语音", 0xFFFF5A7A, 0xFFFFF0F3);
                addCap(capsBox, "🅃 文本", 0xFFFF5A7A, 0xFFFFF0F3);
            }

            h.row.setOnClickListener(v -> edit(p));
            h.row.setOnLongClickListener(v -> {
                Ui.confirm(requireContext(), "删除供应商", "确定删除「" + p.name + "」？", "删除", () ->
                        ProviderApi.delete(p.id, new Rest.Callback<Void>() {
                            @Override
                            public void ok(Void data) {
                                load();
                            }

                            @Override
                            public void fail(String message) {
                                Ui.toast(requireContext(), message);
                            }
                        }));
                return true;
            });
        }

        private void addCap(LinearLayout box, String label, int textColor, int bgColor) {
            TextView t = new TextView(box.getContext());
            t.setText(label);
            t.setTextSize(11);
            t.setTextColor(textColor);
            t.setPadding(Ui.dp(box.getContext(), 8), Ui.dp(box.getContext(), 3),
                    Ui.dp(box.getContext(), 8), Ui.dp(box.getContext(), 3));
            t.setBackground(Ui.rounded(bgColor, 999));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(box.getContext(), 6);
            t.setLayoutParams(lp);
            box.addView(t);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }
}
