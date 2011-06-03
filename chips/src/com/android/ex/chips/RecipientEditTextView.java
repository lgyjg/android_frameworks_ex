/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ex.chips;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.QwertyKeyListener;
import android.text.style.ImageSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ActionMode.Callback;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListPopupWindow;
import android.widget.MultiAutoCompleteTextView;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import java.util.ArrayList;

/**
 * RecipientEditTextView is an auto complete text view for use with applications
 * that use the new Chips UI for addressing a message to recipients.
 */
public class RecipientEditTextView extends MultiAutoCompleteTextView
    implements OnItemClickListener, Callback {

    private static final String TAG = "RecipientEditTextView";

    private Drawable mChipBackground = null;

    private Drawable mChipDelete = null;

    private int mChipPadding;

    private Tokenizer mTokenizer;

    private Drawable mChipBackgroundPressed;

    private RecipientChip mSelectedChip;

    private int mChipDeleteWidth;

    private ArrayList<RecipientChip> mRecipients;

    private int mAlternatesLayout;

    private int mAlternatesSelectedLayout;

    public RecipientEditTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mRecipients = new ArrayList<RecipientChip>();
        setSuggestionsEnabled(false);
        setOnItemClickListener(this);
        setCustomSelectionActionModeCallback(this);
        // When the user starts typing, make sure we unselect any selected
        // chips.
        addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                // Do nothing.
            }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Do nothing.
            }
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (mSelectedChip != null) {
                    clearSelectedChip();
                    setSelection(getText().length());
                }
            }
        });
    }

    @Override
    public void onSelectionChanged(int start, int end) {
        // When selection changes, see if it is inside the chips area.
        // If so, move the cursor back after the chips again.
        if (mRecipients != null && mRecipients.size() > 0) {
            Spannable span = getSpannable();
            RecipientChip[] chips = span.getSpans(start, getText().length(), RecipientChip.class);
            if (chips != null && chips.length > 0) {
                // Grab the last chip and set the cursor to after it.
                setSelection(chips[chips.length - 1].getChipEnd() + 1);
            }
        }
        super.onSelectionChanged(start, end);
    }

    @Override
    public void onFocusChanged(boolean hasFocus, int direction, Rect previous) {
        if (!hasFocus) {
            clearSelectedChip();
            // TODO: commit the default when focus changes. Need an API change
            // to be able to still get the popup suggestions when focus is lost.
        } else {
            setCursorVisible(true);
            Editable text = getText();
            setSelection(text != null && text.length() > 0 ? text.length() : 0);
        }
        super.onFocusChanged(hasFocus, direction, previous);
    }

    public RecipientChip constructChipSpan(RecipientEntry contact, int offset, boolean pressed)
        throws NullPointerException {
        if (mChipBackground == null) {
            throw new NullPointerException
                ("Unable to render any chips as setChipDimensions was not called.");
        }
        String text = contact.getDisplayName();
        Layout layout = getLayout();
        int line = layout.getLineForOffset(offset);
        int lineTop = layout.getLineTop(line);

        TextPaint paint = getPaint();
        float defaultSize = paint.getTextSize();

        // Reduce the size of the text slightly so that we can get the "look" of
        // padding.
        paint.setTextSize((float) (paint.getTextSize() * .9));

        // Ellipsize the text so that it takes AT MOST the entire width of the
        // autocomplete text entry area. Make sure to leave space for padding
        // on the sides.
        CharSequence ellipsizedText = TextUtils.ellipsize(text, paint,
                calculateAvailableWidth(pressed), TextUtils.TruncateAt.END);

        int height = getLineHeight();
        // Make sure there is a minimum chip width so the user can ALWAYS
        // tap a chip without difficulty.
        int width = Math.max(mChipDeleteWidth * 2, (int) Math.floor(paint.measureText(
                ellipsizedText, 0, ellipsizedText.length()))
                + (mChipPadding * 2));

        // Create the background of the chip.
        Bitmap tmpBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(tmpBitmap);
        if (pressed) {
            if (mChipBackgroundPressed != null) {
                mChipBackgroundPressed.setBounds(0, 0, width, height);
                mChipBackgroundPressed.draw(canvas);

                // Align the display text with where the user enters text.
                canvas.drawText(ellipsizedText, 0, ellipsizedText.length(), mChipPadding, height
                        - layout.getLineDescent(line), paint);
                mChipDelete.setBounds(width - mChipDeleteWidth, 0, width, height);
                mChipDelete.draw(canvas);
            } else {
                Log.w(TAG,
                        "Unable to draw a background for the chips as it was never set");
            }
        } else {
            if (mChipBackground != null) {
                mChipBackground.setBounds(0, 0, width, height);
                mChipBackground.draw(canvas);

                // Align the display text with where the user enters text.
                canvas.drawText(ellipsizedText, 0, ellipsizedText.length(), mChipPadding, height
                        - layout.getLineDescent(line), paint);
            } else {
                Log.w(TAG,
                        "Unable to draw a background for the chips as it was never set");
            }
        }


        // Get the location of the widget so we can properly offset
        // the anchor for each chip.
        int[] xy = new int[2];
        getLocationOnScreen(xy);
        // Pass the full text, un-ellipsized, to the chip.
        Drawable result = new BitmapDrawable(getResources(), tmpBitmap);
        result.setBounds(0, 0, width, height);
        Rect bounds = new Rect(xy[0] + offset, xy[1] + lineTop, xy[0] + width,
                calculateLineBottom(xy[1], line));
        RecipientChip recipientChip = new RecipientChip(result, contact, offset, bounds);

        // Return text to the original size.
        paint.setTextSize(defaultSize);

        return recipientChip;
    }

    // The bottom of the line the chip will be located on is calculated by 4 factors:
    // 1) which line the chip appears on
    // 2) the height of a line in the autocomplete view
    // 3) padding built into the edit text view will move the bottom position
    // 4) the position of the autocomplete view on the screen, taking into account
    // that any top padding will move this down visually
    private int calculateLineBottom(int yOffset, int line) {
        int bottomPadding = 0;
        if (line == getLineCount() - 1) {
            bottomPadding += getPaddingBottom();
        }
        return ((line + 1) * getLineHeight()) + (yOffset + getPaddingTop()) + bottomPadding;
    }

    // Get the max amount of space a chip can take up. The formula takes into
    // account the width of the EditTextView, any view padding, and padding
    // that will be added to the chip.
    private float calculateAvailableWidth(boolean pressed) {
        int paddingRight = 0;
        if (pressed) {
            paddingRight = mChipDeleteWidth;
        }
        return getWidth() - getPaddingLeft() - getPaddingRight() - (mChipPadding * 2)
                - paddingRight;
    }

    /**
     * Set all chip dimensions and resources. This has to be done from the application
     * as this is a static library.
     * @param chipBackground drawable
     * @param padding Padding around the text in a chip
     * @param offset Offset between the chip and the dropdown of alternates
     */
    public void setChipDimensions(Drawable chipBackground, Drawable chipBackgroundPressed,
            Drawable chipDelete, int alternatesLayout, int alternatesSelectedLayout, float padding) {
        mChipBackground = chipBackground;
        mChipBackgroundPressed = chipBackgroundPressed;
        mChipDelete = chipDelete;
        mChipDeleteWidth = chipDelete.getIntrinsicWidth();
        mChipPadding = (int) padding;
        mAlternatesLayout = alternatesLayout;
        mAlternatesSelectedLayout = alternatesSelectedLayout;
    }

    @Override
    public void setTokenizer(Tokenizer tokenizer) {
        mTokenizer = tokenizer;
        super.setTokenizer(mTokenizer);
    }

    // We want to handle replacing text in the onItemClickListener
    // so we can get all the associated contact information including
    // display text, address, and id.
    @Override
    protected void replaceText(CharSequence text) {
        return;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_TAB:
                if (event.hasNoModifiers()) {
                    if (commitDefault()) {
                        return true;
                    }
                }
        }
        return super.onKeyUp(keyCode, event);
    }

    // If the popup is showing, the default is the first item in the popup
    // suggestions list. Otherwise, it is whatever the user had typed in.
    private boolean commitDefault() {
        if (isPopupShowing()) {
            // choose the first entry.
            submitItemAtPosition(0);
            dismissDropDown();
            return true;
        } else {
            int end = getSelectionEnd();
            int start = mTokenizer.findTokenStart(getText(), end);
            String text = getText().toString().substring(start, end);
            clearComposingText();
            if (text != null && text.length() > 0) {
                Editable editable = getText();
                RecipientEntry entry = RecipientEntry.constructFakeEntry(text);
                QwertyKeyListener.markAsReplaced(editable, start, end, "");
                editable.replace(start, end, createChip(entry));
                dismissDropDown();
            }
            return false;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mSelectedChip != null) {
            mSelectedChip.onKeyDown(keyCode, event);
        }

        if (keyCode == KeyEvent.KEYCODE_ENTER && event.hasNoModifiers()) {
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    private Spannable getSpannable() {
        return (Spannable) getText();
    }

    /**
     * Instead of filtering on the entire contents of the edit box,
     * this subclass method filters on the range from
     * {@link Tokenizer#findTokenStart} to {@link #getSelectionEnd}
     * if the length of that range meets or exceeds {@link #getThreshold}
     * and makes sure that the range is not already a Chip.
     */
    @Override
    protected void performFiltering(CharSequence text, int keyCode) {
        if (enoughToFilter()) {
            int end = getSelectionEnd();
            int start = mTokenizer.findTokenStart(text, end);
            // If this is a RecipientChip, don't filter
            // on its contents.
            Spannable span = getSpannable();
            RecipientChip[] chips = span.getSpans(start, end, RecipientChip.class);
            if (chips != null && chips.length > 0) {
                return;
            }
        }
        super.performFiltering(text, keyCode);
    }

    private void clearSelectedChip() {
        if (mSelectedChip != null) {
            mSelectedChip.unselectChip();
            mSelectedChip = null;
        }
        setCursorVisible(true);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isFocused()) {
            // Ignore any chip taps until this view is focused.
            return super.onTouchEvent(event);
        }

        boolean handled = super.onTouchEvent(event);
        int action = event.getAction();
        boolean chipWasSelected = false;

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
            float x = event.getX();
            float y = event.getY();
            int offset = putOffsetInRange(getOffsetForPosition(x, y));
            RecipientChip currentChip = findChip(offset);
            if (currentChip != null) {
                if (action == MotionEvent.ACTION_UP) {
                    if (mSelectedChip != null && mSelectedChip != currentChip) {
                        clearSelectedChip();
                        mSelectedChip = currentChip.selectChip();
                    } else if (mSelectedChip == null) {
                        mSelectedChip = currentChip.selectChip();
                    } else {
                        mSelectedChip.onClick(this, offset, x, y);
                    }
                }
                chipWasSelected = true;
            }
        }
        if (action == MotionEvent.ACTION_UP && !chipWasSelected) {
            clearSelectedChip();
        }
        return handled;
    }

    // TODO: This algorithm will need a lot of tweaking after more people have used
    // the chips ui. This attempts to be "forgiving" to fat finger touches by favoring
    // what comes before the finger.
    private int putOffsetInRange(int o) {
        int offset = o;
        Editable text = getText();
        int length = text.length();
        // Remove whitespace from end to find "real end"
        int realLength = length;
        for (int i = length - 1; i >= 0; i--) {
            if (text.charAt(i) == ' ') {
                realLength--;
            } else {
                break;
            }
        }

        // If the offset is beyond or at the end of the text,
        // leave it alone.
        if (offset >= realLength) {
            return offset;
        }
        Editable editable = getText();
        while (offset >= 0 && findText(editable, offset) == -1 && findChip(offset) == null) {
            // Keep walking backward!
            offset--;
        }
        return offset;
    }

    private int findText(Editable text, int offset) {
        if (text.charAt(offset) != ' ') {
            return offset;
        }
        return -1;
    }

    private RecipientChip findChip(int offset) {
        RecipientChip[] chips = getSpannable().getSpans(0, getText().length(), RecipientChip.class);
        // Find the chip that contains this offset.
        for (int i = 0; i < chips.length; i++) {
            RecipientChip chip = chips[i];
            if (chip.matchesChip(offset)) {
                return chip;
            }
        }
        return null;
    }

    private CharSequence createChip(RecipientEntry entry) {
        CharSequence displayText = mTokenizer.terminateToken(entry.getDestination());
        // Always leave a blank space at the end of a chip.
        int textLength = displayText.length();
        if (displayText.charAt(textLength - 1) == ' ') {
            textLength--;
        } else {
            displayText = displayText.toString().concat(" ");
            textLength = displayText.length();
        }
        SpannableString chipText = new SpannableString(displayText);
        int end = getSelectionEnd();
        int start = mTokenizer.findTokenStart(getText(), end);
        try {
            chipText.setSpan(constructChipSpan(entry, start, false), 0, textLength,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } catch (NullPointerException e) {
            Log.e(TAG, e.getMessage(), e);
            return null;
        }

        return chipText;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        submitItemAtPosition(position);
    }

    private void submitItemAtPosition(int position) {
        RecipientEntry entry = (RecipientEntry) getAdapter().getItem(position);
        clearComposingText();

        int end = getSelectionEnd();
        int start = mTokenizer.findTokenStart(getText(), end);

        Editable editable = getText();
        editable.replace(start, end, createChip(entry));
        QwertyKeyListener.markAsReplaced(editable, start, end, "");
    }

    /** Returns a collection of contact Id for each chip inside this View. */
    /* package */ Collection<Long> getContactIds() {
        final Set<Long> result = new HashSet<Long>();
        for (RecipientChip chip : mRecipients) {
            result.add(chip.getContactId());
        }
        return result;
    }

    /** Returns a collection of data Id for each chip inside this View. May be null. */
    /* package */ Collection<Long> getDataIds() {
        final Set<Long> result = new HashSet<Long>();
        for (RecipientChip chip : mRecipients) {
            result.add(chip.getDataId());
        }
        return result;
    }

    /**
     * RecipientChip defines an ImageSpan that contains information relevant to
     * a particular recipient.
     */
    public class RecipientChip extends ImageSpan implements OnItemClickListener {
        private final CharSequence mDisplay;

        private final CharSequence mValue;

        private final int mOffset;

        private ListPopupWindow mPopup;

        private View mAnchorView;

        private int mLeft;

        private final long mContactId;

        private final long mDataId;

        private RecipientEntry mEntry;

        private boolean mSelected = false;

        private RecipientAlternatesAdapter mAlternatesAdapter;

        private Rect mBounds;

        public RecipientChip(Drawable drawable, RecipientEntry entry, int offset, Rect bounds) {
            super(drawable);
            mDisplay = entry.getDisplayName();
            mValue = entry.getDestination();
            mContactId = entry.getContactId();
            mDataId = entry.getDataId();
            mOffset = offset;
            mEntry = entry;
            mBounds = bounds;

            mAnchorView = new View(getContext());
            mAnchorView.setLeft(bounds.left);
            mAnchorView.setRight(bounds.left);
            mAnchorView.setTop(bounds.bottom);
            mAnchorView.setBottom(bounds.bottom);
            mAnchorView.setVisibility(View.GONE);
            mRecipients.add(this);
        }

        public void unselectChip() {
            if (getChipStart() == -1 || getChipEnd() == -1) {
                mSelectedChip = null;
                return;
            }
            clearComposingText();
            RecipientChip newChipSpan = null;
            try {
                newChipSpan = constructChipSpan(mEntry, mOffset, false);
            } catch (NullPointerException e) {
                Log.e(TAG, e.getMessage(), e);
                return;
            }
            replace(newChipSpan);
            if (mPopup != null && mPopup.isShowing()) {
                mPopup.dismiss();
            }
            return;
        }

        public void onKeyDown(int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_DEL) {
                if (mPopup != null && mPopup.isShowing()) {
                    mPopup.dismiss();
                }
                removeChip();
            }
        }

        public boolean isCompletedContact() {
            return mContactId != -1;
        }

        private void replace(RecipientChip newChip) {
            Spannable spannable = getSpannable();
            int spanStart = getChipStart();
            int spanEnd = getChipEnd();
            QwertyKeyListener.markAsReplaced(getText(), spanStart, spanEnd, "");
            spannable.removeSpan(this);
            mRecipients.remove(this);
            spannable.setSpan(newChip, spanStart, spanEnd, 0);
        }

        public void removeChip() {
            Spannable spannable = getSpannable();
            int spanStart = getChipStart();
            int spanEnd = getChipEnd();
            Editable text = getText();
            int toDelete = spanEnd;
            // Always remove trailing spaces when removing a chip.
            while (toDelete >= 0 && toDelete < text.length() - 1 && text.charAt(toDelete) == ' ') {
                toDelete++;
            }
            QwertyKeyListener.markAsReplaced(getText(), spanStart, spanEnd, "");
            spannable.removeSpan(this);
            mRecipients.remove(this);
            spannable.setSpan(null, spanStart, spanEnd, 0);
            text.delete(spanStart, toDelete);
            if (this == mSelectedChip) {
                mSelectedChip = null;
                clearSelectedChip();
            }
        }

        public int getChipStart() {
            return getSpannable().getSpanStart(this);
        }

        public int getChipEnd() {
            return getSpannable().getSpanEnd(this);
        }

        public void replaceChip(RecipientEntry entry) {
            clearComposingText();

            RecipientChip newChipSpan = null;
            try {
                newChipSpan = constructChipSpan(entry, mOffset, false);
            } catch (NullPointerException e) {
                Log.e(TAG, e.getMessage(), e);
                return;
            }
            replace(newChipSpan);
            if (mPopup != null && mPopup.isShowing()) {
                mPopup.dismiss();
            }
        }

        public RecipientChip selectChip() {
            clearComposingText();
            RecipientChip newChipSpan = null;
            if (isCompletedContact()) {
                try {
                    newChipSpan = constructChipSpan(mEntry, mOffset, true);
                    newChipSpan.setSelected(true);
                } catch (NullPointerException e) {
                    Log.e(TAG, e.getMessage(), e);
                    return newChipSpan;
                }
                replace(newChipSpan);
                if (mPopup != null && mPopup.isShowing()) {
                    mPopup.dismiss();
                }
                mSelected = true;
                // Make sure we call edit on the new chip span.
                newChipSpan.showAlternates();
                setCursorVisible(false);
            } else {
                CharSequence text = getValue();
                removeChip();
                Editable editable = getText();
                editable.append(text);
                setCursorVisible(true);
                setSelection(editable.length());
            }
            return newChipSpan;
        }

        private void showAlternates() {
            mPopup = new ListPopupWindow(RecipientEditTextView.this.getContext());

            if (!mPopup.isShowing()) {
                mAlternatesAdapter = new RecipientAlternatesAdapter(
                        RecipientEditTextView.this.getContext(),
                        mEntry.getContactId(), mEntry.getDataId(),
                        mAlternatesLayout, mAlternatesSelectedLayout);
                mAnchorView.setLeft(mLeft);
                mAnchorView.setRight(mLeft);
                mPopup.setAnchorView(mAnchorView);
                mPopup.setAdapter(mAlternatesAdapter);
                mPopup.setWidth(getWidth());
                mPopup.setOnItemClickListener(this);
                mPopup.show();
            }
        }

        private void setSelected(boolean selected) {
            mSelected = selected;
        }

        public CharSequence getDisplay() {
            return mDisplay;
        }

        public CharSequence getValue() {
            return mValue;
        }

        private boolean isInDelete(int offset, float x, float y) {
            // Figure out the bounds of this chip and whether or not
            // the user clicked in the X portion.
            return mSelected
                    && (offset == getChipEnd()
                            || (x > (mBounds.right - mChipDeleteWidth) && x < mBounds.right));
        }

        public boolean matchesChip(int offset) {
            int start = getChipStart();
            int end = getChipEnd();
            return (offset >= start && offset <= end);
        }

        public void onClick(View widget, int offset, float x, float y) {
            if (mSelected) {
                if (isInDelete(offset, x, y)) {
                    removeChip();
                } else {
                    clearSelectedChip();
                }
            }
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top,
                int y, int bottom, Paint paint) {
            // Shift the bounds of this span to where it is actually drawn on the screeen.
            mLeft = (int) x;
            super.draw(canvas, text, start, end, x, top, y, bottom, paint);
        }

        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int position, long rowId) {
            mPopup.dismiss();
            clearComposingText();
            replaceChip(mAlternatesAdapter.getRecipientEntry(position));
        }

        public long getContactId() {
            return mContactId;
        }

        public long getDataId() {
            return mDataId;
        }
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    // Prevent selection of chips.
    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        return false;
    }
}
