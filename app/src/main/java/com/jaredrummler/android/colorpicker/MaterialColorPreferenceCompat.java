package com.jaredrummler.android.colorpicker;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.util.AttributeSet;

/**
 * A {@link ColorPreferenceCompat} that shows a {@link MaterialColorPickerDialog}.
 */
public class MaterialColorPreferenceCompat extends ColorPreferenceCompat {
    // Attributes from ColorPreferenceCompat, where they are private
    private final int dialogType;
    private final int colorShape;
    private final boolean allowPresets;
    private final boolean allowCustom;
    private final boolean showAlphaSlider;
    private final boolean showColorShades;
    private final int[] presets;
    private final int dialogTitle;

    public MaterialColorPreferenceCompat(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        @SuppressWarnings("resource") // AutoCloseable is only available in API 31
        final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ColorPreference);
        dialogType = a.getInt(R.styleable.ColorPreference_cpv_dialogType, ColorPickerDialog.TYPE_PRESETS);
        colorShape = a.getInt(R.styleable.ColorPreference_cpv_colorShape, ColorShape.CIRCLE);
        allowPresets = a.getBoolean(R.styleable.ColorPreference_cpv_allowPresets, true);
        allowCustom = a.getBoolean(R.styleable.ColorPreference_cpv_allowCustom, true);
        showAlphaSlider = a.getBoolean(R.styleable.ColorPreference_cpv_showAlphaSlider, false);
        showColorShades = a.getBoolean(R.styleable.ColorPreference_cpv_showColorShades, true);
        final int presetsResId = a.getResourceId(R.styleable.ColorPreference_cpv_colorPresets, 0);
        presets = presetsResId != 0 ? context.getResources().getIntArray(presetsResId) : ColorPickerDialog.MATERIAL_COLORS;
        dialogTitle = a.getResourceId(R.styleable.ColorPreference_cpv_dialogTitle, R.string.cpv_default_title);
        a.recycle();
    }

    @Override
    protected void onClick() {
        final ColorPickerDialog.Builder builder = ColorPickerDialog.newBuilder()
            .setDialogType(dialogType)
            .setDialogTitle(dialogTitle)
            .setColorShape(colorShape)
            .setPresets(presets)
            .setAllowPresets(allowPresets)
            .setAllowCustom(allowCustom)
            .setShowAlphaSlider(showAlphaSlider)
            .setShowColorShades(showColorShades)
            .setColor(getPersistedInt(Color.BLACK));
        final MaterialColorPickerDialog dialog = MaterialColorPickerDialog.create(builder);
        dialog.setColorPickerDialogListener(this);
        getActivity().getSupportFragmentManager()
            .beginTransaction()
            .add(dialog, getFragmentTag())
            .commitAllowingStateLoss();
    }
}
