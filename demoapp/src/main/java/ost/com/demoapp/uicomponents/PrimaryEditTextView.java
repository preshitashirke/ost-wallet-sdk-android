/*
 * Copyright 2019 OST.com Inc
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 */

package ost.com.demoapp.uicomponents;

import android.content.Context;
import android.support.design.widget.TextInputEditText;
import android.support.design.widget.TextInputLayout;
import android.util.AttributeSet;
import android.view.ViewGroup;

import ost.com.demoapp.uicomponents.uiutils.Font;
import ost.com.demoapp.uicomponents.uiutils.FontFactory;

public class PrimaryEditTextView extends TextInputLayout {
    private TextInputEditText mTextInputEditText;

    public PrimaryEditTextView(Context context) {
        super(context);
        defineUi(context, null, 0);
    }

    public PrimaryEditTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        defineUi(context, attrs, 0);
    }

    public PrimaryEditTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        defineUi(context, attrs, defStyleAttr);
    }

    void defineUi(Context context, AttributeSet attrs, int defStyleAttr) {
        Font font = FontFactory.getInstance(context, FontFactory.FONT.LATO);
        this.setTypeface(font.getRegular());
        mTextInputEditText = new TextInputEditText(context);
        mTextInputEditText.setTypeface(font.getRegular());
        mTextInputEditText.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(mTextInputEditText);
    }

    public String getText() {
        return mTextInputEditText.getText().toString();
    }

    public void setText(String text) {
         mTextInputEditText.setText(text);
    }
}