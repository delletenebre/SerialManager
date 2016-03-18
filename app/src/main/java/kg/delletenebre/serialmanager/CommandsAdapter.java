package kg.delletenebre.serialmanager;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;

import org.apache.commons.lang3.math.NumberUtils;

import java.io.File;
import java.util.List;

public class CommandsAdapter extends RecyclerView.Adapter<CommandsAdapter.ViewHolder> {
    private static final String TAG = MainActivity.class.getSimpleName();
    private Context context;

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private TextView id;
        private TextView uuid;
        private TextView key;
        private TextView action;

        public ViewHolder(View itemView) {
            super(itemView);

            id = (TextView) itemView.findViewById(R.id.command_id);
            uuid = (TextView) itemView.findViewById(R.id.command_uuid);
            key = (TextView) itemView.findViewById(R.id.command_key);
            action = (TextView) itemView.findViewById(R.id.command_action);

        }

        public void setID(String id) {
            this.id.setText(id);
        }
        public void setUUID(String uuid) {
            this.uuid.setText(uuid);
        }
        public void setKey(String s) {
            this.key.setText(s);
        }
        public void setAction(String s) {
            this.action.setText(s);
        }


    }

    public List<Command> commands;

    public CommandsAdapter(Context context, List<Command> commands) {
        this.context = context;
        this.commands = commands;
    }

    private final View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            String id = ((TextView) view.findViewById(R.id.command_id)).getText().toString();
            String uuid = ((TextView) view.findViewById(R.id.command_uuid)).getText().toString();

            Intent intent = new Intent(view.getContext(), CommandSettingsActivity.class);
            intent.putExtra("pref_id", Integer.parseInt(id));
            intent.putExtra("pref_uuid", uuid);
            view.setTag("editable");
            ((Activity)view.getContext()).startActivityForResult(intent,
                    MainActivity.REQ_EDIT_COMMAND);
        }
    };

    private final View.OnClickListener mOnClickListenerDelete = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            final Context context = view.getContext();
            View parent = (View) view.getParent();
            if (parent != null) {

                String name = ((TextView) parent.findViewById(R.id.command_key)).getText().toString();
                final String uuid = ((TextView) parent.findViewById(R.id.command_uuid)).getText().toString();
                new MaterialDialog.Builder(context)
                        .content(String.format(
                                context.getResources().getString(R.string.dialog_content_delete_command),
                                name))
                        .negativeText(R.string.dialog_negative)
                        .positiveText(R.string.dialog_delete_positive)
                        .onNegative(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog,
                                                @NonNull DialogAction which) {

                            }
                        })
                        .onPositive(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                if (!uuid.isEmpty()) {
                                    context.getSharedPreferences(uuid, Context.MODE_PRIVATE)
                                            .edit()
                                            .clear()
                                            .commit();
                                    try { Thread.sleep(500); } catch (InterruptedException e) {}
                                    new File(context.getFilesDir().getParent(),
                                            "/shared_prefs/" + uuid + ".xml").delete();

                                    MainActivity.removeCommand(uuid);
                                }
                            }
                        })
                        .show();
            }

        }
    };


    @Override
    public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.command_item, parent, false);

        view.setOnClickListener(mOnClickListener);
        view.findViewById(R.id.command_delete).setOnClickListener(mOnClickListenerDelete);

        return (new ViewHolder(view));
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        holder.setID(String.valueOf(commands.get(position).id));
        holder.setUUID(commands.get(position).uuid);

        String value = commands.get(position).value;
        String scatter = commands.get(position).scatter;
        scatter = (NumberUtils.isNumber(scatter) &&
                Float.parseFloat(scatter) > 0 && NumberUtils.isNumber(value))
                ? " " + context.getString(R.string.sym_plus_minus) + " " + scatter
                : "";
        holder.setKey(String.format(
                context.getResources().getString(R.string.command_item_key_text),
                commands.get(position).key, value, scatter));

        int actionCategoryId = commands.get(position).actionCategoryId;
        String[] actionCategoryArray = context.getResources()
                .getStringArray(R.array.pref_command_action_category_titles);
        String actionCategory = actionCategoryArray[actionCategoryId];
        String action = commands.get(position).action;
        switch (actionCategoryId) {
            case 1:
                action = (context.getResources()
                        .getStringArray(R.array.pref_command_action_volume_titles))[Integer.parseInt(action)];
                break;

            case 2:
                action = (context.getResources()
                        .getStringArray(R.array.pref_command_action_media_titles))[Integer.parseInt(action)];
                break;

            case 3:
                action = String.valueOf(AppChooserPreference.getDisplayValue(context, action));
                if (action.equals("null") || action.isEmpty()) {
                    action = context.getString(R.string.pref_shortcut_default);
                }
                break;
        }

        holder.setAction(String.format(
                context.getResources().getString(R.string.command_item_action_text),
                actionCategory, (actionCategoryId == 0) ? "" : action));

    }

    @Override
    public int getItemCount() {
        return commands.size();
    }


}