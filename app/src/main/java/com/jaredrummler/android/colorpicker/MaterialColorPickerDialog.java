package com.jaredrummler.android.colorpicker;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * A {@link ColorPickerDialog} that uses a Material 3 alert dialog.
 */
public class MaterialColorPickerDialog extends ColorPickerDialog {
    // Argument keys from ColorPickerDialog, where they are private
    private static final String ARG_DIALOG_TITLE = "dialogTitle";
    private static final String ARG_ALLOW_PRESETS = "allowPresets";
    private static final String ARG_ALLOW_CUSTOM = "allowCustom";
    private static final String ARG_PRESETS_BUTTON_TEXT = "presetsButtonText";
    private static final String ARG_CUSTOM_BUTTON_TEXT = "customButtonText";
    private static final String ARG_SELECTED_BUTTON_TEXT = "selectedButtonText";

    public static MaterialColorPickerDialog create(final Builder builder) {
        final MaterialColorPickerDialog dialog = new MaterialColorPickerDialog();
        dialog.setArguments(builder.create().getArguments());
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        // The superclass reads the arguments and creates rootView. Its AppCompat dialog is not used.
        super.onCreateDialog(savedInstanceState);

        final Bundle args = requireArguments();
        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity())
            .setView(rootView)
            .setPositiveButton(
                orDefault(args.getInt(ARG_SELECTED_BUTTON_TEXT), R.string.cpv_select),
                (dialog, which) -> notifyColorSelected()
            );

        final int dialogTitle = args.getInt(ARG_DIALOG_TITLE);
        if (dialogTitle != 0) {
            builder.setTitle(dialogTitle);
        }

        // onStart of the superclass sets the click listener of the neutral button
        if (dialogType == TYPE_CUSTOM && args.getBoolean(ARG_ALLOW_PRESETS)) {
            builder.setNeutralButton(orDefault(args.getInt(ARG_PRESETS_BUTTON_TEXT), R.string.cpv_presets), null);
        } else if (dialogType == TYPE_PRESETS && args.getBoolean(ARG_ALLOW_CUSTOM)) {
            builder.setNeutralButton(orDefault(args.getInt(ARG_CUSTOM_BUTTON_TEXT), R.string.cpv_custom), null);
        }

        return builder.create();
    }

    private void notifyColorSelected() {
        if (colorPickerDialogListener != null) {
            colorPickerDialogListener.onColorSelected(dialogId, color);
            return;
        }
        final FragmentActivity activity = getActivity();
        if (activity instanceof final ColorPickerDialogListener listener) {
            listener.onColorSelected(dialogId, color);
        }
    }

    @StringRes
    private static int orDefault(@StringRes final int stringRes, @StringRes final int defaultRes) {
        return stringRes != 0 ? stringRes : defaultRes;
    }
}
