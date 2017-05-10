package com.github.guilhermesgb.marqueeto;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.support.design.widget.TextInputLayout;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v7.widget.AppCompatEditText;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationSet;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.joanzapata.iconify.Icon;
import com.joanzapata.iconify.IconDrawable;
import com.joanzapata.iconify.IconFontDescriptor;
import com.joanzapata.iconify.Iconify;
import com.joanzapata.iconify.fonts.MaterialModule;
import com.joanzapata.iconify.widget.IconTextView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static android.util.TypedValue.COMPLEX_UNIT_DIP;

public class LabelledMarqueeEditText extends FrameLayout {

    private static final String TAG = LabelledMarqueeEditText.class.getSimpleName();
    public static final int MODE_EDIT = 0;
    public static final int MODE_MARQUEE = 1;
    public static final int ICON_GRAVITY_RIGHT = 0;
    public static final int ICON_GRAVITY_LEFT = 1;

    private static final int ICON_CHARACTER_SECTION_LENGHT = 4;

    private static final AtomicInteger sNextGeneratedId = new AtomicInteger(1);

    private static Icon sNullIcon = new Icon() {
        @Override
        public String key() {
            return "null";
        }

        @Override
        public char character() {
            return ' ';
        }
    };
    static {
        try {
            Iconify.with(new IconFontDescriptor() {

                @Override
                public String ttfFileName() {
                    return "fonts/nullDescriptor.otf";
                }

                @Override
                public Icon[] characters() {
                    return new Icon[]{sNullIcon};
                }

            });
            Iconify.with(new MaterialModule());
        }
        catch (IllegalArgumentException exception) {
            Log.d(TAG, "Iconify modules already set.");
        }
    }

    private final IconDrawable mNullIconDrawable;

    private Resources.Theme mContextTheme;
    private AttributeSet mAttrs;

    private TextInputLayout mTextInputLayout;
    private AppCompatEditText mEditText;
    private IconTextView mTextView;

    private String mText;
    private int mTextColor;
    private float mTextSize;
    private int mTextStyle;
    private String mHint;
    private int mBaseColor;
    private int mHighlightColor;
    private int mIconColor;
    private String mIconKey;
    private IconDrawable mIconDrawable;
    private CharSequence mIconCharacter;
    private int mIconGravity;
    private int mErrorColor;
    private String mError;
    private boolean mErrorEnabled;
    private String mErrorCached;
    private int mPreferredMode;
    private int mCurrentMode;
    private int mInputType;
    private int mTextMaxLength;
    private boolean mTextAllCaps;
    private int mCurrentCustomStyle;
    private boolean mEditViaLongPress;

    private boolean mTextChanged = true;
    private boolean mTextColorChanged = true;
    private boolean mTextStyleChanged = true;
    private boolean mHintChanged = true;
    private boolean mIconChanged = true;
    private boolean mErrorChanged = true;
    private boolean mInputTypeChanged = true;
    private boolean mTextFiltersChanged = true;
    private boolean mStyleColorsChanged = true;

    private boolean mAnimationEnded = false;

    private TextWatcher mTextWatcher;

    public LabelledMarqueeEditText(Context context) {
        this(context, null);
    }

    public LabelledMarqueeEditText(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LabelledMarqueeEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        if (isInEditMode()) {
            mNullIconDrawable = null;
        } else {
            mNullIconDrawable = new IconDrawable(getContext(), sNullIcon) {

                private int width, height;

                public IconDrawable adjustBounds() {
                    this.width = (int) TypedValue.applyDimension(COMPLEX_UNIT_DIP, 5,
                            getContext().getResources().getDisplayMetrics());
                    height = getContext().getResources().getDimensionPixelSize(R.dimen
                            .labelled_marquee_edit_text_default_icon_size_big);
                    setBounds(0, 0, width, height);
                    invalidateSelf();
                    return this;
                }

                @Override
                public int getIntrinsicWidth() {
                    return this.width;
                }

                @Override
                public int getIntrinsicHeight() {
                    return this.height;
                }

            }.adjustBounds();
        }
        mAttrs = attrs;
        TypedArray customAttributes = context.obtainStyledAttributes(mAttrs,
                R.styleable.LabelledMarqueeEditText);
        mCurrentCustomStyle = customAttributes
                .getResourceId(R.styleable.LabelledMarqueeEditText_labelledMarqueeEditTextStyle, -1);
        Resources.Theme theme = overrideThemeWithCustomStyle(context, mCurrentCustomStyle);
        final TypedArray themeAttributes = theme.obtainStyledAttributes(mAttrs, new int[]{
                R.attr.baseColor, R.attr.highlightColor, R.attr.iconColor, R.attr.errorColor
        }, 0, 0);
        retrieveAttributesValues(customAttributes, themeAttributes);
        buildEditAndMarqueeViews(context);
        initEditAndMarqueeViews(true);
        resetContextTheme(theme);
    }

    private void resetContextTheme(Resources.Theme theme) {
        theme.setTo(getResources().newTheme());
        theme.setTo(mContextTheme);
        mContextTheme = null;
    }

    private Resources.Theme overrideThemeWithCustomStyle(Context context, int customAttributesStyle) {
        final Resources.Theme theme = context.getTheme();
        mContextTheme = getResources().newTheme();
        mContextTheme.setTo(theme);
        theme.applyStyle(R.style.LabelledMarqueeEditTextTheme, true);
        if (customAttributesStyle != -1) {
            theme.applyStyle(customAttributesStyle, true);
        }
        return theme;
    }

    private void retrieveAttributesValues(TypedArray customAttributes, TypedArray themeAttributes) {
        retrieveThemeAttributeValues(themeAttributes);
        retrieveCustomAttributeValues(customAttributes);
    }

    private void retrieveThemeAttributeValues(TypedArray themeAttributes) {
        TypedValue typedValue = new TypedValue();
        if (themeAttributes.getValue(themeAttributes.getIndex(0), typedValue)) {
            mBaseColor = typedValue.data;
        }
        else {
            mBaseColor = ContextCompat.getColor(getContext(), android.R.color.black);
        }
        if (themeAttributes.getValue(themeAttributes.getIndex(1), typedValue)) {
            mHighlightColor = typedValue.data;
        }
        else {
            mHighlightColor = ContextCompat.getColor(getContext(), android.R.color.black);
        }
        if (themeAttributes.getValue(themeAttributes.getIndex(2), typedValue)) {
            mIconColor = typedValue.data;
        }
        else {
            mIconColor = mBaseColor;
        }
        if (themeAttributes.getValue(themeAttributes.getIndex(3), typedValue)) {
            mErrorColor = typedValue.data;
        }
        else {
            mErrorColor = mHighlightColor;
        }
        themeAttributes.recycle();
    }

    private void retrieveCustomAttributeValues(TypedArray customAttributes) {
        mText = customAttributes.getString(R.styleable.LabelledMarqueeEditText_android_text);
        mTextColor = customAttributes.getColor(R.styleable.LabelledMarqueeEditText_android_textColor,
                ContextCompat.getColor(getContext(), android.R.color.black));
        mTextStyle = customAttributes.getInt(R.styleable.LabelledMarqueeEditText_android_textStyle, Typeface.NORMAL);
        float maxSize = getResources().getDimension(R.dimen.labelled_marquee_edit_text_max_text_size);
        float maxSizeConverted = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, maxSize,
                getResources().getDisplayMetrics());
        mTextSize = customAttributes.getDimension(R.styleable.LabelledMarqueeEditText_android_textSize,
                getResources().getDimension(R.dimen.labelled_marquee_edit_text_default_text_size));
        float textSizeConverted = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, mTextSize,
                getResources().getDisplayMetrics());
        if (textSizeConverted > maxSizeConverted) {
            mTextSize = maxSize;
        }
        mHint = customAttributes.getString(R.styleable.LabelledMarqueeEditText_android_hint);
        mIconKey = customAttributes.getString(R.styleable.LabelledMarqueeEditText_iconKey);
        mIconGravity = customAttributes.getInt(R.styleable.LabelledMarqueeEditText_iconGravity, ICON_GRAVITY_RIGHT);
        if (mIconKey == null || isInEditMode()) {
            mIconDrawable = mNullIconDrawable;
            mIconCharacter = "";
        }
        else {
            mIconDrawable = new IconDrawable(getContext(), mIconKey)
                    .color(mIconColor).sizeRes(R.dimen.labelled_marquee_edit_text_default_icon_size_big);
            mIconCharacter = String.format(getResources()
                    .getString(R.string.labelled_marquee_edit_text_layout_icon_definition_template),
                    mIconKey, String.format("#%06X", (0xFFFFFF & mIconColor)),
                    "@dimen/labelled_marquee_edit_text_default_icon_size_small");
            if (mIconGravity == ICON_GRAVITY_RIGHT) {
                mIconCharacter = "   " + mIconCharacter;
            } else if (mIconGravity == ICON_GRAVITY_LEFT) {
                mIconCharacter = "  " + mIconCharacter + "   ";
            }
        }
        mErrorEnabled = customAttributes.getBoolean(R.styleable.LabelledMarqueeEditText_errorEnabled, true);
        String error = customAttributes.getString(R.styleable.LabelledMarqueeEditText_error);
        if (error == null || error.trim().isEmpty()) {
            mErrorEnabled = false;
        }
        if (mErrorEnabled) {
            mError = error;
        }
        else {
            mError = "";
            mErrorCached = error;
        }
        mPreferredMode = customAttributes.getInt(R.styleable.LabelledMarqueeEditText_mode, MODE_MARQUEE);
        mInputType = customAttributes.getInt(R.styleable.LabelledMarqueeEditText_android_inputType,
                EditorInfo.TYPE_CLASS_TEXT);
        mTextMaxLength = customAttributes.getInt(R.styleable.LabelledMarqueeEditText_android_maxLength, -1);
        if (mTextMaxLength != -1) {
            mTextMaxLength += ICON_CHARACTER_SECTION_LENGHT;
        }
        mTextAllCaps = customAttributes.getBoolean(R.styleable.LabelledMarqueeEditText_android_textAllCaps, false);
        mEditViaLongPress = customAttributes.getBoolean(R.styleable.LabelledMarqueeEditText_editViaLongPress, false);
        customAttributes.recycle();
    }

    private void buildEditAndMarqueeViews(Context context) {
        View editViewSource;
        if (isInEditMode()) {
            editViewSource = LayoutInflater.from(context).inflate(R.layout.layout_editable_edit_mode, this, false);
        } else {
            editViewSource = LayoutInflater.from(context).inflate(R.layout.layout_editable, this, false);
        }
        mTextInputLayout = (TextInputLayout) editViewSource.findViewById(R.id.labelled_marquee_edit_text_layout_label_wrapper);
        mTextInputLayout.setId(doGenerateViewId());
        mEditText = (AppCompatEditText) editViewSource.findViewById(R.id.labelled_marquee_edit_text_layout_edit_text);
        mEditText.setId(doGenerateViewId());
        addView(mTextInputLayout);
        View marqueeViewSource = LayoutInflater.from(context).inflate(R.layout.layout_marquee, this, false);
        mTextView = (IconTextView) marqueeViewSource.findViewById(R.id.labelled_marquee_edit_text_layout_marquee_text);
        mTextView.setId(doGenerateViewId());
        addView(mTextView, new ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        if (mTextWatcher != null) {
            mEditText.addTextChangedListener(mTextWatcher);
        }
        mEditText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        mEditText.setSingleLine();
    }

    private static synchronized int doGenerateViewId() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
            while (true) {
                final int result = sNextGeneratedId.get();
                int newValue = result + 1;
                if (newValue > 0x00FFFFFF) newValue = 1;
                if (sNextGeneratedId.compareAndSet(result, newValue)) {
                    return result;
                }
            }
        }
        else {
            return View.generateViewId();
        }
    }

    private void initEditAndMarqueeViews(final boolean animate) {
        if (mTextChanged || mIconChanged || mTextFiltersChanged || mStyleColorsChanged) {
            if (mIconGravity == ICON_GRAVITY_RIGHT) {
                mEditText.setCompoundDrawablesWithIntrinsicBounds(null, null, mIconDrawable, null);
                mEditText.setCompoundDrawablePadding(0);
            } else if (mIconGravity == ICON_GRAVITY_LEFT) {
                mEditText.setCompoundDrawablesWithIntrinsicBounds(mIconDrawable, null, null, null);
                mEditText.setCompoundDrawablePadding(4);
            }
            setTextFilters();
            setText();
            setTextSize();
            setLabelColor();
            setCursorDrawableColor();
            tintIconWithIconColor();
            mTextChanged = false;
            mIconChanged = false;
            mTextFiltersChanged = false;
            mStyleColorsChanged = false;
        }
        if (mTextColorChanged) {
            setTextColor();
            mTextColorChanged = false;
        }
        if (mTextStyleChanged) {
            setTextStyle();
            mTextStyleChanged = false;
        }
        if (mHintChanged) {
            setHint();
            mHintChanged = false;
        }
        if (mErrorChanged) {
            setErrorEnabled();
            setError();
        }
        if (mInputTypeChanged) {
            setInputType();
            mInputTypeChanged = false;
        }
        mTextView.setSelected(true);
        if (mPreferredMode == MODE_MARQUEE) {
            enableMarqueeMode(mIconCharacter, animate);
        }
        else if (mPreferredMode == MODE_EDIT) {
            enableEditMode(animate);
        }
        final GestureDetectorCompat detector = new GestureDetectorCompat(getContext(),
                new GestureDetector.OnGestureListener() {

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public void onShowPress(MotionEvent e) {}

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (!mEditViaLongPress) {
                    enableEditMode(false);
                }
                return false;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                return false;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                if (mEditViaLongPress) {
                    enableEditMode(false);
                }
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                return false;
            }

        });
        mTextView.setOnTouchListener(new OnTouchListener() {

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                return detector.onTouchEvent(event);
            }

        });
        final View.OnFocusChangeListener existingListener = mEditText.getOnFocusChangeListener();
        if (!(existingListener instanceof DisableEditModeOnFocusChangeListener)) {
            mEditText.setOnFocusChangeListener(
                    DisableEditModeOnFocusChangeListener.newInstance(this, existingListener)
            );
        }
    }

    private void setText() {
        String text = mText;
        if (text != null && mTextMaxLength != -1) {
            text = text.substring(0, mTextMaxLength - ICON_CHARACTER_SECTION_LENGHT);
        }
        mEditText.setText(text);
        if (text != null) {
            try {
                mEditText.setSelection(text.length());
            } catch (Throwable ignore) {}
        }
        if (isInputTypePassword()) {
            if (text != null && text.length() > 0) {
                text = String.format(Locale.US, "%0" + text.length() + "d", 0).replace('0', '*');
            }
        }
        if (mIconGravity == ICON_GRAVITY_RIGHT) {
            mTextView.setText(String.format("%s%s", text == null ? "" : text, mIconCharacter));
        } else if (mIconGravity == ICON_GRAVITY_LEFT) {
            mTextView.setText(String.format("%s%s", mIconCharacter, text == null ? "" : text));
        }
    }

    private boolean isInputTypePassword() {
        switch (mInputType) {
            case InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD:
            case InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD:
            case InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD:
            case InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD:
                return true;
            default:
                return false;
        }
    }

    private void setTextSize() {
        mEditText.setTextSize(TypedValue.COMPLEX_UNIT_PX, mTextSize);
        mTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, mTextSize);
    }

    private void setTextColor() {
        mEditText.setTextColor(mTextColor);
        mTextView.setTextColor(mTextColor);
    }

    private void setTextStyle() {
        mEditText.setTypeface(Typeface.create(mEditText.getTypeface(), mTextStyle));
        mTextView.setTypeface(Typeface.create(mTextView.getTypeface(), mTextStyle));
    }

    private void setHint() {
        mTextInputLayout.setHint(mHint);
    }

    private void setLabelColor() {
        mEditText.setHighlightColor(mHighlightColor);
    }

    private void setError() {
        mTextInputLayout.setError(mError);
    }

    private void setErrorEnabled() {
        mTextInputLayout.setErrorEnabled(mErrorEnabled);
    }

    private void setCursorDrawableColor() {
        try {
            Field fCursorDrawableRes = TextView.class.getDeclaredField("mCursorDrawableRes");
            fCursorDrawableRes.setAccessible(true);
            int mCursorDrawableRes = fCursorDrawableRes.getInt(mEditText);
            final Drawable[] drawables = new Drawable[2];
            drawables[0] = ContextCompat.getDrawable(mEditText.getContext(), mCursorDrawableRes);
            drawables[1] = ContextCompat.getDrawable(mEditText.getContext(), mCursorDrawableRes);
            drawables[0].setColorFilter(mHighlightColor, PorterDuff.Mode.SRC_IN);
            drawables[1].setColorFilter(mHighlightColor, PorterDuff.Mode.SRC_IN);
            try {
                Field fEditor = TextView.class.getDeclaredField("mEditor");
                fEditor.setAccessible(true);
                Object editor = fEditor.get(mEditText);
                Field fCursorDrawable = editor.getClass().getDeclaredField("mCursorDrawable");
                fCursorDrawable.setAccessible(true);
                fCursorDrawable.set(editor, drawables);
            }
            catch (Throwable ignored) {
                Field fCursorDrawable = TextView.class.getDeclaredField("mCursorDrawable");
                fCursorDrawable.setAccessible(true);
                fCursorDrawable.set(mEditText, drawables);
            }
        }
        catch (Throwable ignored) {
            Log.d(TAG, "Could not override EditText's cursor drawable color via reflection: "
                    + ignored.getMessage());
        }
    }

    private void setInputType() {
        mEditText.setInputType(mInputType);
    }

    private void setTextFilters() {
        List<InputFilter> editTextFilters = new ArrayList<>();
        List<InputFilter> textViewFilters = new ArrayList<>();
        if (mTextMaxLength >= 0) {
            editTextFilters.add(new InputFilter.LengthFilter(mTextMaxLength - ICON_CHARACTER_SECTION_LENGHT));
            textViewFilters.add(new InputFilter.LengthFilter(mTextMaxLength));
        }
        if (mTextAllCaps) {
            editTextFilters.add(new InputFilter.AllCaps());
            textViewFilters.add(new InputFilter.AllCaps());
        }
        mEditText.setFilters(editTextFilters.toArray(new InputFilter[editTextFilters.size()]));
        mTextView.setFilters(textViewFilters.toArray(new InputFilter[editTextFilters.size()]));
    }

    private static final class DisableEditModeOnFocusChangeListener implements View.OnFocusChangeListener {

        private final WeakReference<LabelledMarqueeEditText> labelledMarqueeEditTextWeakReference;
        private final View.OnFocusChangeListener previousOnFocusChangeListener;

        public static DisableEditModeOnFocusChangeListener newInstance(final LabelledMarqueeEditText target,
                                                                       final View.OnFocusChangeListener listener) {
            WeakReference<LabelledMarqueeEditText> reference = new WeakReference<>(target);
            return new DisableEditModeOnFocusChangeListener(reference, listener);
        }

        private DisableEditModeOnFocusChangeListener(final WeakReference<LabelledMarqueeEditText> reference,
                                                     final View.OnFocusChangeListener listener) {
            labelledMarqueeEditTextWeakReference = reference;
            previousOnFocusChangeListener = listener;
        }

        @Override
        public void onFocusChange(View view, boolean hasFocus) {
            if (previousOnFocusChangeListener != null) {
                previousOnFocusChangeListener.onFocusChange(view, hasFocus);
            }
            LabelledMarqueeEditText labelledMarqueeEditText = labelledMarqueeEditTextWeakReference.get();
            if (labelledMarqueeEditText != null) {
                if (!hasFocus) {
                    labelledMarqueeEditText.tintIconWithIconColor();
                    labelledMarqueeEditText.enableMarqueeMode(labelledMarqueeEditText.getIconCharacter(), false);
                    labelledMarqueeEditText.invalidate();
                    labelledMarqueeEditText.requestLayout();
                }
                else {
                    labelledMarqueeEditText.tintIconWithHighlightColorIfApplicable();
                    labelledMarqueeEditText.tintSelectorDrawableWithHighlightColor();
                }
            }
        }

    }

    private void tintIconWithIconColor() {
        setIconColorTemporarily(mIconColor);
    }

    private void tintIconWithHighlightColorIfApplicable() {
        if (mIconColor != mHighlightColor) {
            setIconColorTemporarily(mHighlightColor);
        }
    }

    private void setIconColorTemporarily(int color) {
        final int oldIconColor = mIconColor;
        mIconColor = color;
        setIcon(mIconKey, false);
        if (mIconGravity == ICON_GRAVITY_RIGHT) {
            mEditText.setCompoundDrawablesWithIntrinsicBounds(null, null, mIconDrawable, null);
            mEditText.setCompoundDrawablePadding(0);
        } else if (mIconGravity == ICON_GRAVITY_LEFT) {
            mEditText.setCompoundDrawablesWithIntrinsicBounds(mIconDrawable, null, null, null);
            mEditText.setCompoundDrawablePadding(4);
        }
        mIconColor = oldIconColor;
    }

    private void tintSelectorDrawableWithHighlightColor() {
        setSelectorDrawableColorTemporarily(mHighlightColor);
    }

    private void setSelectorDrawableColorTemporarily(int color) {
        Drawable middleHandle = ContextCompat.getDrawable(mEditText.getContext(),
                R.drawable.text_select_handle_middle_material);
        Drawable middleHandleWrapper = DrawableCompat.wrap(middleHandle);
        DrawableCompat.setTint(middleHandleWrapper, color);

        Drawable leftHandle = ContextCompat.getDrawable(mEditText.getContext(),
                R.drawable.text_select_handle_left_material);
        Drawable leftHandleWrapper = DrawableCompat.wrap(leftHandle);
        DrawableCompat.setTint(leftHandleWrapper, color);

        Drawable rightHandle = ContextCompat.getDrawable(mEditText.getContext(),
                R.drawable.text_select_handle_right_material);
        Drawable rightHandleWrapper = DrawableCompat.wrap(rightHandle);
        DrawableCompat.setTint(rightHandleWrapper, color);
    }

    private void enableEditMode(boolean animate) {
        if (animate && !isEmpty(true)) {
            mTextInputLayout.clearAnimation();
            mTextView.clearAnimation();
            String text = mEditText.getText().toString();
            if (isInputTypePassword() && text.length() > 0) {
                text = String.format(Locale.US, "%0" + text.length() + "d", 0).replace('0', '*');
            }
            mTextView.setText(text);
            mTextView.setVisibility(View.VISIBLE);
            final AnimationSet fadeIn = new AnimationSet(true);
            fadeIn.setDuration(500);
            fadeIn.setInterpolator(new AccelerateInterpolator());
            fadeIn.addAnimation(new AlphaAnimation(0, 1));
            final AnimationSet fadeOut = new AnimationSet(true);
            fadeOut.setDuration(700);
            fadeOut.setInterpolator(new AccelerateInterpolator());
            fadeOut.addAnimation(new AlphaAnimation(1, 0));
            mTextInputLayout.setVisibility(View.VISIBLE);
            mTextInputLayout.startAnimation(fadeIn);
            mTextView.startAnimation(fadeOut);
            (new Handler()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    mAnimationEnded = true;
                    if (mCurrentMode == MODE_EDIT) {
                        mTextView.setVisibility(View.INVISIBLE);
                    }
                }
            }, 700);
        }
        else {
            mTextInputLayout.setVisibility(View.VISIBLE);
            mTextView.setVisibility(View.INVISIBLE);
        }
        mCurrentMode = MODE_EDIT;
        mEditText.setVisibility(View.VISIBLE);
        mEditText.setEnabled(true);
    }

    private void enableMarqueeMode(final CharSequence iconCharacter, boolean animate) {
        if (mEditText.getText().toString().trim().isEmpty()) {
            if (mPreferredMode == MODE_MARQUEE) {
                enableEditMode(animate);
            }
            return;
        }
        if (animate && !isEmpty(true)) {
            mTextInputLayout.clearAnimation();
            mTextView.clearAnimation();
            final AnimationSet fadeIn = new AnimationSet(true);
            {
                fadeIn.setDuration(500);
                fadeIn.setInterpolator(new AccelerateInterpolator());
                fadeIn.addAnimation(new AlphaAnimation(0, 1));
            }
            mTextInputLayout.setVisibility(View.VISIBLE);
            mTextInputLayout.startAnimation(fadeIn);
            (new Handler()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    mAnimationEnded = true;
                }
            }, 500);
        }
        else {
            mTextInputLayout.setVisibility(View.VISIBLE);
        }
        mCurrentMode = MODE_MARQUEE;
        mEditText.setVisibility(View.INVISIBLE);
        mEditText.setEnabled(false);
        mTextView.setVisibility(View.VISIBLE);
        mTextView.setSelected(true);
        mText = mEditText.getText().toString();
        String text = mText;
        if (isInputTypePassword()) {
            text = String.format(Locale.US, "%0" + text.length() + "d", 0).replace('0', '*');
        }
        if (mIconGravity == ICON_GRAVITY_RIGHT) {
            mTextView.setText(String.format("%s%s", text, iconCharacter));
        } else if (mIconGravity == ICON_GRAVITY_LEFT) {
            mTextView.setText(String.format("%s%s", iconCharacter, text));
        }
    }

    private boolean isEmpty(boolean trim) {
        return mText == null || (trim ? mText.trim().isEmpty() : mText.isEmpty());
    }

    public String getText() {
        return mEditText.getText().toString();
    }

    public void setText(String text) {
        mText = text;
        mTextChanged = true;
        reloadEditAndMarqueeViews();
    }

    public int getTextColor() {
        return mTextColor;
    }

    public void setTextColor(int color) {
        mTextColor = ContextCompat.getColor(getContext(), color);
        mTextColorChanged = true;
        reloadEditAndMarqueeViews();
    }

    public int getTextStyle() {
        return mTextStyle;
    }

    public void setTextStyle(int style) {
        mTextStyle = style;
        mTextStyleChanged = true;
        reloadEditAndMarqueeViews();
    }

    public String getHint() {
        return mHint;
    }

    public void setHint(String hint) {
        mHint = hint;
        mHintChanged = true;
        reloadEditAndMarqueeViews();
    }

    public int getBaseColor() {
        return mBaseColor;
    }

    public int getHighlightColor() {
        return mHighlightColor;
    }

    public int getIconColor() {
        return mIconColor;
    }

    public String getIcon() {
        return mIconKey;
    }

    public IconDrawable getIconDrawable() {
        return mIconDrawable;
    }

    public CharSequence getIconCharacter() {
        return mIconCharacter;
    }

    public void setIcon(String iconKey) {
        setIcon(iconKey, true);
    }

    private void setIcon(String iconKey, boolean shouldReload) {
        mIconKey = iconKey;
        if (mIconKey == null || isInEditMode()) {
            mIconDrawable = mNullIconDrawable;
            mIconCharacter = "";
        } else {
            mIconDrawable = new IconDrawable(getContext(), mIconKey)
                    .color(mIconColor).sizeRes(R.dimen.labelled_marquee_edit_text_default_icon_size_big);
            mIconCharacter = String.format(getResources()
                            .getString(R.string.labelled_marquee_edit_text_layout_icon_definition_template),
                    mIconKey, String.format("#%06X", (0xFFFFFF & mIconColor)),
                    "@dimen/labelled_marquee_edit_text_default_icon_size_small");
            if (mIconGravity == ICON_GRAVITY_RIGHT) {
                mIconCharacter = "   " + mIconCharacter;
            } else if (mIconGravity == ICON_GRAVITY_LEFT) {
                mIconCharacter = "  " + mIconCharacter + "   ";
            }
        }
        if (shouldReload) {
            mIconChanged = true;
            reloadEditAndMarqueeViews();
        }
    }

    public int getErrorColor() {
        return mErrorColor;
    }

    public String getError() {
        if (mErrorEnabled) {
            return mError;
        }
        return mErrorCached;
    }

    public void setError(String error) {
        if (mErrorEnabled) {
            mError = error;
        }
        else {
            mError = "";
            mErrorCached = error;
        }
        mErrorChanged = true;
        reloadEditAndMarqueeViews();
    }

    public boolean getErrorEnabled() {
        return mErrorEnabled;
    }

    public void setErrorEnabled(boolean errorEnabled) {
        mErrorEnabled = errorEnabled;
        if (!errorEnabled) {
            mErrorCached = mError;
            mError = "";
        }
        else {
            mError = mErrorCached;
        }
//        if (mErrorEnabled) {
            mText = mEditText.getText().toString();
            setCustomStyle(mCurrentCustomStyle);
//        }
//        else {
//            mErrorChanged = true;
//            reloadEditAndMarqueeViews();
//        }
    }

    public int getPreferredMode() {
        return mPreferredMode;
    }

    public void setMode(int mode) throws IllegalArgumentException {
        switch (mode) {
            case MODE_EDIT:
                mPreferredMode = MODE_EDIT;
                break;
            case MODE_MARQUEE:
                mPreferredMode = MODE_MARQUEE;
                break;
            default:
                throw new IllegalArgumentException(String.format(Locale.US,
                        "LabelledMarqueeEditText doesn't support this mode (%d).", mode));
        }
        reloadEditAndMarqueeViews();
    }

    public int getCurrentMode() {
        return mCurrentMode;
    }

    public int getInputType() {
        return mInputType;
    }

    public void setInputType(int inputType) {
        mInputType = inputType;
        mInputTypeChanged = true;
        reloadEditAndMarqueeViews();
    }

    public int getTextMaxLength() {
        return mTextMaxLength;
    }

    public void setTextMaxLength(int maxLength) {
        mTextMaxLength = maxLength;
        if (mTextMaxLength != -1) {
            mTextMaxLength += ICON_CHARACTER_SECTION_LENGHT;
        }
        mTextFiltersChanged = true;
        reloadEditAndMarqueeViews();
    }

    public boolean getTextAllCaps() {
        return mTextAllCaps;
    }

    public void setTextAllCaps(boolean allCaps) {
        mTextAllCaps = allCaps;
        mTextFiltersChanged = true;
        reloadEditAndMarqueeViews();
    }

    public void setCustomStyle(int customStyle) {
        mCurrentCustomStyle = customStyle;
        Context context = getContext();
        Resources.Theme theme = overrideThemeWithCustomStyle(context, mCurrentCustomStyle);
        final TypedArray themeAttributes = theme.obtainStyledAttributes(mAttrs, new int[]{
                R.attr.baseColor, R.attr.highlightColor, R.attr.iconColor, R.attr.errorColor
        }, 0, 0);
        retrieveThemeAttributeValues(themeAttributes);
        removeAllViews();
        buildEditAndMarqueeViews(context);
        mTextChanged = true;
        mTextColorChanged = true;
        mTextStyleChanged = true;
        mHintChanged = true;
        mIconChanged = true;
        mErrorChanged = true;
        mInputTypeChanged = true;
        mTextFiltersChanged = true;
        mStyleColorsChanged = true;
        initEditAndMarqueeViews(true);
        resetContextTheme(theme);
        invalidate();
        requestLayout();
    }

    public void setTextChangedListener(TextWatcher textWatcher) {
        if (textWatcher != null) {
            mEditText.addTextChangedListener(textWatcher);
        } else if (mTextWatcher != null) {
            mEditText.removeTextChangedListener(mTextWatcher);
        }
        mTextWatcher = textWatcher;
        mPreferredMode = MODE_EDIT;
    }

    public void reloadEditAndMarqueeViews() {
        initEditAndMarqueeViews(!mAnimationEnded);
        invalidate();
        requestLayout();
    }

}
