package org.telegram.ui.Stories.recorder;

import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;

/**
 * @author yanghao
 * @Date 2024/12/16
 */
public interface IWindowView {

    void addView(View child, ViewGroup.LayoutParams layoutParams);
    void addView(View child);
    void invalidate();
    void drawBlurBitmap(Bitmap bitmap, float amount);
    int getPaddingUnderContainer();
    int getMeasuredHeight();int getMeasuredWidth();

    int getBottomPadding2();
    void removeView(View view);
    View asView();
}
