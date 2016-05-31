package kg.delletenebre.serialmanager.Commands;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Collections;

import kg.delletenebre.serialmanager.App;
import kg.delletenebre.serialmanager.R;
import kg.delletenebre.serialmanager.helper.ItemTouchHelperAdapter;

public class CommandsListAdapter extends RecyclerView.Adapter<CommandsListAdapter.ViewHolder>
        implements ItemTouchHelperAdapter {
    private static CommandsDatabase database;

    public CommandsListAdapter() {
        Commands.loadCommands();
        database = Commands.getDatabase();
    }

    @Override
    public CommandsListAdapter.ViewHolder onCreateViewHolder(ViewGroup parent,
                                                             int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.command_item, parent, false);
        // set the view's size, margins, paddings and layout parameters

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Command command = Commands.getCommands().get(position);

        holder.id.setText(String.valueOf(command.getId()));

        holder.key.setText(String.format(
                App.getContext().getString(R.string.command_item_key_text),
                command.getKey(), command.getValue(),
                (command.getScatter() != 0 && App.isNumber(command.getValue()))
                        ? " ± " + command.getScatter() : ""));

        holder.action.setText(command.getActionString());
    }

    @Override
    public int getItemCount() {
        return Commands.getCommands().size();
    }

    @Override
    public void onItemDismiss(int position) {
//        mItems.remove(position);
//        notifyItemRemoved(position);
    }



    public Command createItem(Command command) {
        command.setPosition(getItemCount());
        command = database.create(command);
        if (command != null) {
            Commands.getCommands().add(command);
            notifyItemInserted(command.getPosition());

            return command;
        }

        return null;
    }

    public void updateItem(Command command) {
        int position = getItemPositionById(command.getId());
        if (position > -1) {
            if (database.update(command) > 0) {
                Commands.getCommands().set(position, command);
                notifyItemChanged(position);
            }
        }
    }

    public void removeItemById(long id) {
        database.updatePositions(Commands.getCommands());
        int position = getItemPositionById(id);
        if (position > -1) {
            Commands.getCommands().remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, getItemCount());
        }
    }

    public int getItemPositionById(long id) {
        int i = 0;
        for (Command command: Commands.getCommands()) {
            if (command.getId() == id) {
                return i;
            }
            i++;
        }

        return -1;
    }



    @Override
    public boolean onItemMove(int fromPosition, int toPosition) {
        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                Collections.swap(Commands.getCommands(), i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                Collections.swap(Commands.getCommands(), i, i - 1);
            }
        }

        database.updatePositions(Commands.getCommands());
        notifyItemMoved(fromPosition, toPosition);
        return true;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public TextView id, key, action;

        public ViewHolder(View view) {
            super(view);

            id = (TextView) view.findViewById(R.id.command_id);
            key = (TextView) view.findViewById(R.id.command_key);
            action = (TextView) view.findViewById(R.id.command_action);

            view.setOnClickListener(onClickListener);
            view.findViewById(R.id.command_delete).setOnClickListener(onClickListenerDelete);
        }

        private View.OnClickListener onClickListener = new View.OnClickListener() {
            @Override
            public void onClick (View view) {
                long id = Long.parseLong(
                        ((TextView) view.findViewById(R.id.command_id)).getText().toString());

                Intent intent = new Intent(view.getContext(), CommandSettingsActivity.class);
                intent.putExtra("command", Commands.getCommands().get(getItemPositionById(id)));

                ((Activity) view.getContext()).startActivityForResult(intent,
                        App.REQUEST_CODE_UPDATE_COMMAND);
            }
        };

        private View.OnClickListener onClickListenerDelete = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final Context context = view.getContext();
                View parent = (View) view.getParent();
                if (parent != null) {

                    String name = ((TextView) parent.findViewById(R.id.command_key))
                            .getText().toString();
                    final long id = Long.parseLong(
                            ((TextView) parent.findViewById(R.id.command_id)).getText().toString());

                    new AlertDialog.Builder(context)
                            .setTitle(R.string.dialog_title_confirm_delete_command)
                            .setMessage(String.format(
                                    context.getString(R.string.dialog_content_delete_command),
                                    name))
                            .setNegativeButton(R.string.dialog_negative, null)
                            .setPositiveButton(R.string.dialog_delete_positive,
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            if (database.delete(id)) {
                                                removeItemById(id);
                                            }
                                        }

                                    })
                            .show();
                }

            }
        };
    }
}
