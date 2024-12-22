package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.touchSlop;
import static org.telegram.messenger.LocaleController.getString;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Build;
import android.os.Parcelable;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.WindowInsetsCompat;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationNotificationsLocker;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.camera.CameraController;
import org.telegram.messenger.camera.CameraView;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stories.DarkThemeResourceProvider;
import org.telegram.ui.Stories.StoryWaveEffectView;
import org.telegram.ui.Stories.recorder.CollageLayout;
import org.telegram.ui.Stories.recorder.CollageLayoutButton;
import org.telegram.ui.Stories.recorder.CollageLayoutView2;
import org.telegram.ui.Stories.recorder.DownloadButton;
import org.telegram.ui.Stories.recorder.DraftSavedHint;
import org.telegram.ui.Stories.recorder.DualCameraView;
import org.telegram.ui.Stories.recorder.FlashViews;
import org.telegram.ui.Stories.recorder.GalleryListView;
import org.telegram.ui.Stories.recorder.HintTextView;
import org.telegram.ui.Stories.recorder.HintView2;
import org.telegram.ui.Stories.recorder.PhotoVideoSwitcherView;
import org.telegram.ui.Stories.recorder.PreviewView;
import org.telegram.ui.Stories.recorder.RecordControl;
import org.telegram.ui.Stories.recorder.SliderView;
import org.telegram.ui.Stories.recorder.StoryPrivacyBottomSheet;
import org.telegram.ui.Stories.recorder.ToggleButton;
import org.telegram.ui.Stories.recorder.ToggleButton2;
import org.telegram.ui.Stories.recorder.Touchable;
import org.telegram.ui.Stories.recorder.TrashView;
import org.telegram.ui.Stories.recorder.VideoTimerView;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Consumer;

public class ChatRecorder implements NotificationCenter.NotificationCenterDelegate {

    private final Theme.ResourcesProvider resourcesProvider = new DarkThemeResourceProvider();

    private final Activity activity;

    private boolean isShown;

    WindowManager windowManager;
    private final WindowManager.LayoutParams windowLayoutParams;
    private WindowView windowView;
    private ContainerView containerView;
    private FlashViews flashViews;
    private ThanosEffect thanosEffect;

    private boolean wasSend;
    private long wasSendPeer = 0;
    private ClosingViewProvider closingSourceProvider;
    private Runnable closeListener;

    public boolean isVisible() {
        return this.isShown;
    }
    public ChatRecorder(Activity activity) {
        this.activity = activity;

        windowLayoutParams = new WindowManager.LayoutParams();
        windowLayoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.format = PixelFormat.TRANSLUCENT;
        windowLayoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        windowLayoutParams.type = WindowManager.LayoutParams.LAST_APPLICATION_WINDOW;
        if (Build.VERSION.SDK_INT >= 28) {
            windowLayoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        windowLayoutParams.flags = (
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
            WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
        );
        if (Build.VERSION.SDK_INT >= 21) {
            windowLayoutParams.flags |= WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
        }
        windowLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;

        windowManager = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);

        initViews();
    }

    private ValueAnimator openCloseAnimator;
    private SourceView fromSourceView;
    private float fromRounding;
    private final RectF fromRect = new RectF();
    private float openProgress;
    private int openType;
    private float dismissProgress;
    private Float frozenDismissProgress;
    private boolean canChangePeer = true;
    long selectedDialogId;

    public static class SourceView {

        int type = 0;
        float rounding;
        RectF screenRect = new RectF();
        Drawable backgroundDrawable;
        ImageReceiver backgroundImageReceiver;
        boolean hasShadow;
        Paint backgroundPaint;
        Drawable iconDrawable;
        int iconSize;
        View view;

        protected void show(boolean sent) {}
        protected void hide() {}
        protected void drawAbove(Canvas canvas, float alpha) {}

        public static SourceView fromChatAttachLayout(FrameLayout cameraIcon) {
            SourceView src = new SourceView();
            int[] loc = new int[2];
            cameraIcon.getLocationOnScreen(loc);
            src.screenRect.set(loc[0], loc[1], loc[0] + cameraIcon.getWidth(), loc[1] + cameraIcon.getHeight());
            src.hasShadow = true;
            return src;
        }
    }

    public ChatRecorder closeToWhenSent(ClosingViewProvider closingSourceProvider) {
        this.closingSourceProvider = closingSourceProvider;
        return this;
    }

    public void replaceSourceView(SourceView sourceView) {
        if (sourceView != null) {
            fromSourceView = sourceView;
            openType = sourceView.type;
            fromRect.set(sourceView.screenRect);
            fromRounding = sourceView.rounding;
        } else {
            fromSourceView = null;
            openType = 0;
            fromRect.set(0, dp(100), AndroidUtilities.displaySize.x, dp(100) + AndroidUtilities.displaySize.y);
            fromRounding = dp(8);
        }
        previewContainer.setBackgroundColor(openType == 1 || openType == 0 ? 0 : 0xff1f1f1f);
    }

    public void open(SourceView sourceView) {
        open(sourceView, true);
    }

    public void open(SourceView sourceView, boolean animated) {
        if (isShown) {
            return;
        }

        isReposting = false;
//        privacySelectorHintOpened = false;
        forceBackgroundVisible = false;
        videoTextureHolder.active = false;

        if (windowManager != null && windowView != null && windowView.getParent() == null) {
            AndroidUtilities.setPreferredMaxRefreshRate(windowManager, windowView, windowLayoutParams);
            windowManager.addView(windowView, windowLayoutParams);
        }

        cameraViewThumb.setImageDrawable(getCameraThumb());

        navigateTo(PAGE_CAMERA, false);

        if (sourceView != null) {
            fromSourceView = sourceView;
            openType = sourceView.type;
            fromRect.set(sourceView.screenRect);
            fromRounding = sourceView.rounding;
            fromSourceView.hide();
        } else {
            openType = 0;
            fromRect.set(0, dp(100), AndroidUtilities.displaySize.x, dp(100) + AndroidUtilities.displaySize.y);
            fromRounding = dp(8);
        }
        containerView.updateBackground();
        previewContainer.setBackgroundColor(openType == 1 || openType == 0 ? 0 : 0xff1f1f1f);

        containerView.setTranslationX(0);
        containerView.setTranslationY(0);
        containerView.setTranslationY2(0);
        containerView.setScaleX(1f);
        containerView.setScaleY(1f);
        dismissProgress = 0;

        AndroidUtilities.lockOrientation(activity, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        animateOpenTo(1, animated, this::onOpenDone);

        addNotificationObservers();

        botId = 0;
        botLang = "";
        botEdit = null;

        GlobalNotifier.setObserver(p -> {
            final boolean granted = p.grantResults != null && p.grantResults.length == 1 && p.grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (p.requestCode == 112) {
                if (!granted) {
                    new AlertDialog.Builder(getContext(), resourcesProvider)
                            .setTopAnimation(R.raw.permission_request_camera, AlertsCreator.PERMISSIONS_REQUEST_TOP_ICON_SIZE, false, Theme.getColor(Theme.key_dialogTopBackground))
                            .setMessage(AndroidUtilities.replaceTags(getString(R.string.PermissionNoCameraMicVideo)))
                            .setPositiveButton(getString(R.string.PermissionOpenSettings), (dialogInterface, i) -> {
                                try {
                                    Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                    intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                                    activity.startActivity(intent);
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                            })
                            .setNegativeButton(getString(R.string.ContactsPermissionAlertNotNow), null)
                            .create()
                            .show();
                }
            }
        }, this::onPause, this::onResume);
    }

    private static boolean firstOpen = true;

    public void close(boolean animated) {
        if (!isShown) {
            return;
        }

        animateOpenTo(0, animated, this::onCloseDone);
        if (openType == 1 || openType == 0) {
            windowView.setBackgroundColor(0x00000000);
        }

        removeNotificationObservers();
    }

    private AnimationNotificationsLocker notificationsLocker = new AnimationNotificationsLocker();
    private StoryWaveEffectView waveEffect;

    private InnerHook innerHook;

    public void setInnerHook(InnerHook innerHook) {
        this.innerHook = innerHook;
    }

    private void animateOpenTo(final float value, boolean animated, Runnable onDone) {
        if (openCloseAnimator != null) {
            openCloseAnimator.cancel();
            openCloseAnimator = null;
        }

        if (animated) {
            notificationsLocker.lock();
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 512);
            frozenDismissProgress = dismissProgress;
            openCloseAnimator = ValueAnimator.ofFloat(openProgress, value);
            openCloseAnimator.addUpdateListener(anm -> {
                openProgress = (float) anm.getAnimatedValue();
                checkBackgroundVisibility();
                containerView.invalidate();
                windowView.invalidate();
                if (openProgress < .3f && waveEffect != null) {
                    waveEffect.start();
                    waveEffect = null;
                }
            });
            openCloseAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    frozenDismissProgress = null;
                    openProgress = value;
                    applyOpenProgress();
                    containerView.invalidate();
                    windowView.invalidate();
                    if (onDone != null) {
                        onDone.run();
                    }
                    if (fromSourceView != null && waveEffect != null) {
                        waveEffect.start();
                        waveEffect = null;
                    }
                    notificationsLocker.unlock();
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 512);
                    NotificationCenter.getGlobalInstance().runDelayedNotifications();
                    checkBackgroundVisibility();

                    if (onFullyOpenListener != null) {
                        onFullyOpenListener.run();
                        onFullyOpenListener = null;
                    }

                    containerView.invalidate();
                    previewContainer.invalidate();
                }
            });
            if (value < 1 && wasSend) {
                openCloseAnimator.setDuration(250);
                openCloseAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            } else {
                if (value > 0 || containerView.getTranslationY1() < AndroidUtilities.dp(20)) {
                    openCloseAnimator.setDuration(300L);
                    openCloseAnimator.setInterpolator(new FastOutSlowInInterpolator());
                } else {
                    openCloseAnimator.setDuration(400L);
                    openCloseAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                }
            }
            openCloseAnimator.start();
        } else {
            frozenDismissProgress = null;
            openProgress = value;
            applyOpenProgress();
            containerView.invalidate();
            windowView.invalidate();
            if (onDone != null) {
                onDone.run();
            }
            checkBackgroundVisibility();
        }
        if (value > 0) {
            firstOpen = false;
        }
    }

    private void onOpenDone() {
        isShown = true;
        wasSend = false;
        if (openType == 1) {
            previewContainer.setAlpha(1f);
            previewContainer.setTranslationX(0);
            previewContainer.setTranslationY(0);
            actionBarContainer.setAlpha(1f);
            controlContainer.setAlpha(1f);
            windowView.setBackgroundColor(0xff000000);
        }

        if (whenOpenDone != null) {
            whenOpenDone.run();
            whenOpenDone = null;
        } else {
            onResumeInternal();
        }
        if (innerHook != null) {
            innerHook.onOpenDone();
        }
    }

    private void onCloseDone() {
        isShown = false;
        AndroidUtilities.unlockOrientation(activity);
        if (cameraView != null) {
            if (takingVideo) {
                CameraController.getInstance().stopVideoRecording(cameraView.getCameraSession(), false);
            }
            destroyCameraView(false);
        }
        if (outputFile != null && !wasSend) {
            try {
                outputFile.delete();
            } catch (Exception ignore) {}
        }
        outputFile = null;
        AndroidUtilities.runOnUIThread(() -> {
            if (windowManager != null && windowView != null && windowView.getParent() != null) {
                windowManager.removeView(windowView);
            }
        }, 16);
        if (fromSourceView != null) {
            fromSourceView.show(false);
        }
        if (whenOpenDone != null) {
            whenOpenDone = null;
        }
        lastGalleryScrollPosition = null;
        close(false);

        if (onCloseListener != null) {
            onCloseListener.run();
            onCloseListener = null;
        }
        if (windowView != null) {
            Bulletin.removeDelegate(windowView);
        }
        if (collageLayoutView != null) {
            collageLayoutView.clear(true);
        }
        if (innerHook != null) {
            innerHook.onCloseDone();
        }
    }

    private Runnable onCloseListener;
    public void setOnCloseListener(Runnable listener) {
        onCloseListener = listener;
    }

    private Runnable onFullyOpenListener;
    public void setOnFullyOpenListener(Runnable listener) {
        onFullyOpenListener = listener;
    }

    private Utilities.Callback4<Long, Runnable, Boolean, Long> onClosePrepareListener;
    public void setOnPrepareCloseListener(Utilities.Callback4<Long, Runnable, Boolean, Long> listener) {
        onClosePrepareListener = listener;
    }

    private int previewW, previewH;
    private int underControls;
    private boolean underStatusBar;
    private boolean scrollingY, scrollingX;

    private int insetLeft, insetTop, insetRight, insetBottom;

    private final RectF rectF = new RectF(), fullRectF = new RectF();
    private final Path clipPath = new Path();
    private final Rect rect = new Rect();
    private void applyOpenProgress() {
        if (openType != 1) return;
        fullRectF.set(previewContainer.getLeft(), previewContainer.getTop(), previewContainer.getMeasuredWidth(), previewContainer.getMeasuredHeight());
        fullRectF.offset(containerView.getX(), containerView.getY());
        AndroidUtilities.lerp(fromRect, fullRectF, openProgress, rectF);
        previewContainer.setAlpha(openProgress);
        previewContainer.setTranslationX(rectF.left - previewContainer.getLeft() - containerView.getX());
        previewContainer.setTranslationY(rectF.top - previewContainer.getTop() - containerView.getY());
        if (fromSourceView != null && fromSourceView.view != null) {
            fromSourceView.view.setTranslationX((fullRectF.left - fromRect.left) * openProgress);
            fromSourceView.view.setTranslationY((fullRectF.top - fromRect.top) * openProgress);
        }
        previewContainer.setScaleX(rectF.width() / previewContainer.getMeasuredWidth());
        previewContainer.setScaleY(rectF.height() / previewContainer.getMeasuredHeight());
        actionBarContainer.setAlpha(openProgress);
        controlContainer.setAlpha(openProgress);
    }

    public class WindowView extends SizeNotifierFrameLayout {

        private GestureDetectorFixDoubleTap gestureDetector;
        private ScaleGestureDetector scaleGestureDetector;

        public WindowView(Context context) {
            super(context);
            gestureDetector = new GestureDetectorFixDoubleTap(context, new GestureListener());
            scaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());
        }

        private int lastKeyboardHeight;

        @Override
        public int getBottomPadding() {
            return getHeight() - containerView.getBottom() + underControls;
        }

        public int getBottomPadding2() {
            return getHeight() - containerView.getBottom();
        }

        public int getPaddingUnderContainer() {
            return getHeight() - insetBottom - containerView.getBottom();
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            float dismiss = frozenDismissProgress != null ? frozenDismissProgress : dismissProgress;
            if (openType == 0) {
                canvas.drawColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * openProgress * (1f - dismiss))));
            }
            boolean restore = false;
            final float r = AndroidUtilities.lerp(fromRounding, 0, openProgress);
            if (openProgress != 1) {
                if (openType == 0) {
                    fullRectF.set(0, 0, getWidth(), getHeight());
                    fullRectF.offset(containerView.getTranslationX(), containerView.getTranslationY());
                    AndroidUtilities.lerp(fromRect, fullRectF, openProgress, rectF);

                    canvas.save();
                    clipPath.rewind();
                    clipPath.addRoundRect(rectF, r, r, Path.Direction.CW);
                    canvas.clipPath(clipPath);

                    final float alpha = Utilities.clamp(openProgress * 3, 1, 0);
                    canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), (int) (0xFF * alpha), Canvas.ALL_SAVE_FLAG);
                    canvas.translate(rectF.left, rectF.top - containerView.getTranslationY() * openProgress);
                    final float s = Math.max(rectF.width() / getWidth(), rectF.height() / getHeight());
                    canvas.scale(s, s);
                    restore = true;
                } else if (openType == 1) {
                    applyOpenProgress();
                }
            }
            super.dispatchDraw(canvas);
            if (restore) {
                canvas.restore();
                canvas.restore();

                if (fromSourceView != null) {
                    final float alpha = Utilities.clamp(1f - openProgress * 1.5f, 1, 0);
                    final float bcx = rectF.centerX(),
                            bcy = rectF.centerY(),
                            br = Math.min(rectF.width(), rectF.height()) / 2f;
                    if (fromSourceView.backgroundImageReceiver != null) {
                        fromSourceView.backgroundImageReceiver.setImageCoords(rectF);
                        int prevRoundRadius = fromSourceView.backgroundImageReceiver.getRoundRadius()[0];
                        fromSourceView.backgroundImageReceiver.setRoundRadius((int) r);
                        fromSourceView.backgroundImageReceiver.setAlpha(alpha);
                        fromSourceView.backgroundImageReceiver.draw(canvas);
                        fromSourceView.backgroundImageReceiver.setRoundRadius(prevRoundRadius);
                    } else if (fromSourceView.backgroundDrawable != null) {
                        fromSourceView.backgroundDrawable.setBounds((int) rectF.left, (int) rectF.top, (int) rectF.right, (int) rectF.bottom);
                        fromSourceView.backgroundDrawable.setAlpha((int) (0xFF * alpha * alpha * alpha));
                        fromSourceView.backgroundDrawable.draw(canvas);
                    } else if (fromSourceView.backgroundPaint != null) {
                        if (fromSourceView.hasShadow) {
                            fromSourceView.backgroundPaint.setShadowLayer(dp(2), 0, dp(3), Theme.multAlpha(0x33000000, alpha));
                        }
                        fromSourceView.backgroundPaint.setAlpha((int) (0xFF * alpha));
                        canvas.drawRoundRect(rectF, r, r, fromSourceView.backgroundPaint);
                    }
                    if (fromSourceView.iconDrawable != null) {
                        rect.set(fromSourceView.iconDrawable.getBounds());
                        fromSourceView.iconDrawable.setBounds(
                            (int) (bcx - fromSourceView.iconSize / 2),
                            (int) (bcy - fromSourceView.iconSize / 2),
                            (int) (bcx + fromSourceView.iconSize / 2),
                            (int) (bcy + fromSourceView.iconSize / 2)
                        );
                        int wasAlpha = fromSourceView.iconDrawable.getAlpha();
                        fromSourceView.iconDrawable.setAlpha((int) (wasAlpha * alpha));
                        fromSourceView.iconDrawable.draw(canvas);
                        fromSourceView.iconDrawable.setBounds(rect);
                        fromSourceView.iconDrawable.setAlpha(wasAlpha);
                    }

                    canvas.save();
                    canvas.translate(fromRect.left, fromRect.top);
                    fromSourceView.drawAbove(canvas, alpha);
                    canvas.restore();
                }
            }
        }

        private boolean flingDetected;
        private boolean touchInCollageList;

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            flingDetected = false;
            if (collageListView != null && collageListView.isVisible()) {
                final float y = containerView.getY() + actionBarContainer.getY() + collageListView.getY();
                if (ev.getY() >= y && ev.getY() <= y + collageListView.getHeight() || touchInCollageList) {
                    touchInCollageList = ev.getAction() != MotionEvent.ACTION_UP && ev.getAction() != MotionEvent.ACTION_CANCEL;
                    return super.dispatchTouchEvent(ev);
                } else {
                    collageListView.setVisible(false, true);
                    updateActionBarButtons(true);
                }
            }
            if (touchInCollageList && (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL)) {
                touchInCollageList = false;
            }
            scaleGestureDetector.onTouchEvent(ev);
            gestureDetector.onTouchEvent(ev);
            if (ev.getAction() == MotionEvent.ACTION_UP && !flingDetected) {
                allowModeScroll = true;
                if (containerView.getTranslationY() > 0) {
                    if (dismissProgress > .4f) {
                        close(true);
                    } else {
                        animateContainerBack();
                    }
                } else if (galleryListView != null && galleryListView.getTranslationY() > 0 && !galleryClosing) {
                    animateGalleryListView(!takingVideo && galleryListView.getTranslationY() < galleryListView.getPadding());
                }
                galleryClosing = false;
                modeSwitcherView.stopScroll(0);
                scrollingY = false;
                scrollingX = false;
            }
            return super.dispatchTouchEvent(ev);
        }

        public void cancelGestures() {
            scaleGestureDetector.onTouchEvent(AndroidUtilities.emptyMotionEvent());
            gestureDetector.onTouchEvent(AndroidUtilities.emptyMotionEvent());
        }

        @Override
        public boolean dispatchKeyEventPreIme(KeyEvent event) {
            if (event != null && event.getKeyCode()
                    == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                onBackPressed();
                return true;
            }
            return super.dispatchKeyEventPreIme(event);
        }

        private boolean scaling = false;
        private final class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (!scaling || cameraView == null || currentPage != PAGE_CAMERA || cameraView.isDualTouch() || collageLayoutView.getFilledProgress() >= 1) {
                    return false;
                }
                final float deltaScaleFactor = (detector.getScaleFactor() - 1.0f) * .75f;
                cameraZoom += deltaScaleFactor;
                cameraZoom = Utilities.clamp(cameraZoom, 1, 0);
                cameraView.setZoom(cameraZoom);
                if (zoomControlView != null) {
                    zoomControlView.setZoom(cameraZoom, false);
                }
                showZoomControls(true, true);
                return true;
            }

            @Override
            public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
                if (cameraView == null || currentPage != PAGE_CAMERA || wasGalleryOpen) {
                    return false;
                }
                scaling = true;
                return super.onScaleBegin(detector);
            }

            @Override
            public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
                scaling = false;
                animateGalleryListView(false);
                animateContainerBack();
                super.onScaleEnd(detector);
            }
        }

        private float ty, sty, stx;
        private boolean allowModeScroll = true;

        private final class GestureListener extends GestureDetectorFixDoubleTap.OnGestureListener {
            @Override
            public boolean onDown(@NonNull MotionEvent e) {
                sty = 0;
                stx = 0;
                return false;
            }

            @Override
            public void onShowPress(@NonNull MotionEvent e) {

            }

            @Override
            public boolean onSingleTapUp(@NonNull MotionEvent e) {
                scrollingY = false;
                scrollingX = false;
                if (!hasDoubleTap(e)) {
                    if (onSingleTapConfirmed(e)) {
                        return true;
                    }
                }
                if (isGalleryOpen() && e.getY() < galleryListView.top()) {
                    animateGalleryListView(false);
                    return true;
                }
                return false;
            }

            @Override
            public boolean onScroll(@NonNull MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
                if (openCloseAnimator != null && openCloseAnimator.isRunning() || galleryOpenCloseSpringAnimator != null || galleryOpenCloseAnimator != null || recordControl.isTouch() || cameraView != null && cameraView.isDualTouch() || scaling || zoomControlView != null && zoomControlView.isTouch() || inCheck()) {
                    return false;
                }
                if (takingVideo || takingPhoto || currentPage != PAGE_CAMERA) {
                    return false;
                }
                if (!scrollingX) {
                    sty += distanceY;
                    if (!scrollingY && Math.abs(sty) >= touchSlop) {
                        if (collageLayoutView != null) {
                            collageLayoutView.cancelTouch();
                        }
                        scrollingY = true;
                    }
                }
                if (scrollingY) {
                    int galleryMax = windowView.getMeasuredHeight() - (int) (AndroidUtilities.displaySize.y * 0.35f) - (AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight());
                    if (galleryListView == null || galleryListView.getTranslationY() >= galleryMax) {
                        ty = containerView.getTranslationY1();
                    } else {
                        ty = galleryListView.getTranslationY() - galleryMax;
                    }
                    if (galleryListView != null && galleryListView.listView.canScrollVertically(-1)) {
                        distanceY = Math.max(0, distanceY);
                    }
                    ty -= distanceY;
                    ty = Math.max(-galleryMax, ty);
                    if (currentPage == PAGE_PREVIEW) {
                        ty = Math.max(0, ty);
                    }
                    if (ty >= 0) {
                        containerView.setTranslationY(ty);
                        if (galleryListView != null) {
                            galleryListView.setTranslationY(galleryMax);
                        }
                    } else {
                        containerView.setTranslationY(0);
                        if (galleryListView == null) {
                            createGalleryListView();
                        }
                        galleryListView.setTranslationY(galleryMax + ty);
                    }
                }
                if (!scrollingY) {
                    stx += distanceX;
                    if (!scrollingX && Math.abs(stx) >= touchSlop) {
                        if (collageLayoutView != null) {
                            collageLayoutView.cancelTouch();
                        }
                        scrollingX = true;
                    }
                }
                if (scrollingX) {
                    modeSwitcherView.scrollX(distanceX);
                }
                return true;
            }

            @Override
            public void onLongPress(@NonNull MotionEvent e) {

            }

            @Override
            public boolean onFling(@NonNull MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
                if (openCloseAnimator != null && openCloseAnimator.isRunning() || recordControl.isTouch() || cameraView != null && cameraView.isDualTouch() || scaling || zoomControlView != null && zoomControlView.isTouch() || inCheck()) {
                    return false;
                }
                flingDetected = true;
                allowModeScroll = true;
                boolean r = false;
                if (scrollingY) {
                    if (Math.abs(containerView.getTranslationY1()) >= dp(1)) {
                        if (velocityY > 0 && Math.abs(velocityY) > 2000 && Math.abs(velocityY) > Math.abs(velocityX) || dismissProgress > .4f) {
                            close(true);
                        } else {
                            animateContainerBack();
                        }
                        r = true;
                    } else if (galleryListView != null && !galleryClosing) {
                        if (Math.abs(velocityY) > 200 && (!galleryListView.listView.canScrollVertically(-1) || !wasGalleryOpen)) {
                            animateGalleryListView(!takingVideo && velocityY < 0);
                            r = true;
                        } else {
                            animateGalleryListView(!takingVideo && galleryListView.getTranslationY() < galleryListView.getPadding());
                            r = true;
                        }
                    }
                }
                if (scrollingX) {
                    r = modeSwitcherView.stopScroll(velocityX) || r;
                }
                galleryClosing = false;
                scrollingY = false;
                scrollingX = false;
                if (r && collageLayoutView != null) {
                    collageLayoutView.cancelTouch();
                }
                return r;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (cameraView != null) {
                    cameraView.allowToTapFocus();
                    return true;
                }
                return false;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (cameraView == null || awaitingPlayer || takingPhoto || !cameraView.isInited() || currentPage != PAGE_CAMERA) {
                    return false;
                }
                cameraView.switchCamera();
                recordControl.rotateFlip(180);
                saveCameraFace(cameraView.isFrontface());
                if (useDisplayFlashlight()) {
                    flashViews.flashIn(null);
                } else {
                    flashViews.flashOut();
                }
                return true;
            }

            @Override
            public boolean onDoubleTapEvent(MotionEvent e) {
                if (cameraView != null) {
                    cameraView.clearTapFocus();
                }
                return false;
            }

            @Override
            public boolean hasDoubleTap(MotionEvent e) {
                return currentPage == PAGE_CAMERA && cameraView != null && !awaitingPlayer && cameraView.isInited() && !takingPhoto && !recordControl.isTouch() && !isGalleryOpen() && galleryListViewOpening == null;
            }
        };

        private boolean ignoreLayout;

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            if (Build.VERSION.SDK_INT < 21) {
                insetTop = AndroidUtilities.statusBarHeight;
                insetBottom = AndroidUtilities.navigationBarHeight;
            }

            final int W = MeasureSpec.getSize(widthMeasureSpec);
            final int H = MeasureSpec.getSize(heightMeasureSpec);
            final int w = W - insetLeft - insetRight;

            final int statusbar = insetTop;
            final int navbar = insetBottom;

            final int hFromW = (int) Math.ceil(w / 9f * 16f);
            underControls = dp(48);
            if (hFromW + underControls <= H - navbar) {
                previewW = w;
                previewH = hFromW;
                underStatusBar = previewH + underControls > H - navbar - statusbar;
            } else {
                underStatusBar = false;
                previewH = H - underControls - navbar - statusbar;
                previewW = (int) Math.ceil(previewH * 9f / 16f);
            }
            underControls = Utilities.clamp(H - previewH - (underStatusBar ? 0 : statusbar), dp(68), dp(48));

            int flags = getSystemUiVisibility();
            if (underStatusBar) {
                flags |= View.SYSTEM_UI_FLAG_FULLSCREEN;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_FULLSCREEN;
            }
            setSystemUiVisibility(flags);

            containerView.measure(
                MeasureSpec.makeMeasureSpec(previewW, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(previewH + underControls, MeasureSpec.EXACTLY)
            );
            flashViews.backgroundView.measure(
                MeasureSpec.makeMeasureSpec(W, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(H, MeasureSpec.EXACTLY)
            );
            if (thanosEffect != null) {
                thanosEffect.measure(
                    MeasureSpec.makeMeasureSpec(W, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(H, MeasureSpec.EXACTLY)
                );
            }

            if (galleryListView != null) {
                galleryListView.measure(MeasureSpec.makeMeasureSpec(previewW, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(H, MeasureSpec.EXACTLY));
            }

            for (int i = 0; i < getChildCount(); ++i) {
                View child = getChildAt(i);
                if (child instanceof DownloadButton.PreparingVideoToast) {
                    child.measure(
                        MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(H, MeasureSpec.EXACTLY)
                    );
                } else if (child instanceof Bulletin.ParentLayout) {
                    child.measure(
                        MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(Math.min(dp(340), H - (underStatusBar ? 0 : statusbar)), MeasureSpec.EXACTLY)
                    );
                }
            }

            setMeasuredDimension(W, H);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            if (ignoreLayout) {
                return;
            }
            final int W = right - left;
            final int H = bottom - top;

            final int statusbar = insetTop;
            final int underControls = navbarContainer.getMeasuredHeight();

            final int T = underStatusBar ? 0 : statusbar;
            int l = insetLeft + (W - insetRight - previewW) / 2,
                r = insetLeft + (W - insetRight + previewW) / 2, t, b;
            if (underStatusBar) {
                t = T;
                b = T + previewH + underControls;
            } else {
                t = T + ((H - T - insetBottom) - previewH - underControls) / 2;
                if (openType == 1 && fromRect.top + previewH + underControls < H - insetBottom) {
                    t = (int) fromRect.top;
                } else if (t - T < dp(40)) {
                    t = T;
                }
                b = t + previewH + underControls;
            }

            containerView.layout(l, t, r, b);
            flashViews.backgroundView.layout(0, 0, W, H);
            if (thanosEffect != null) {
                thanosEffect.layout(0, 0, W, H);
            }

            if (galleryListView != null) {
                galleryListView.layout((W - galleryListView.getMeasuredWidth()) / 2, 0, (W + galleryListView.getMeasuredWidth()) / 2, H);
            }

            for (int i = 0; i < getChildCount(); ++i) {
                View child = getChildAt(i);
                if (child instanceof DownloadButton.PreparingVideoToast) {
                    child.layout(0, 0, W, H);
                } else if (child instanceof Bulletin.ParentLayout) {
                    child.layout(0, t, child.getMeasuredWidth(), t + child.getMeasuredHeight());
                }
            }
        }
    }

    private class ContainerView extends FrameLayout {
        public ContainerView(Context context) {
            super(context);
        }

        public void updateBackground() {
            if (openType == 0) {
                setBackground(Theme.createRoundRectDrawable(dp(12), 0xff000000));
            } else {
                setBackground(null);
            }
        }

        @Override
        public void invalidate() {
            if (openCloseAnimator != null && openCloseAnimator.isRunning()) {
                return;
            }
            super.invalidate();
        }

        private float translationY1;
        private float translationY2;

        public void setTranslationY2(float translationY2) {
            super.setTranslationY(this.translationY1 + (this.translationY2 = translationY2));
        }

        public float getTranslationY1() {
            return translationY1;
        }

        public float getTranslationY2() {
            return translationY2;
        }

        @Override
        public void setTranslationY(float translationY) {
            super.setTranslationY((this.translationY1 = translationY) + translationY2);

            dismissProgress = Utilities.clamp(translationY / getMeasuredHeight() * 4, 1, 0);
            checkBackgroundVisibility();
            windowView.invalidate();

            final float scale = 1f - .1f * Utilities.clamp(getTranslationY() / AndroidUtilities.dp(320), 1, 0);
            setScaleX(scale);
            setScaleY(scale);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            final int t = underStatusBar ? insetTop : 0;

            final int w = right - left;
            final int h = bottom - top;

            previewContainer.layout(0, 0, previewW, previewH);
            previewContainer.setPivotX(previewW * .5f);
            actionBarContainer.layout(0, t, previewW, t + actionBarContainer.getMeasuredHeight());
            controlContainer.layout(0, previewH - controlContainer.getMeasuredHeight(), previewW, previewH);
            navbarContainer.layout(0, previewH, previewW, previewH + navbarContainer.getMeasuredHeight());
            if (captionEditOverlay != null) {
                captionEditOverlay.layout(0, 0, w, h);
            }
            flashViews.foregroundView.layout(0, 0, w, h);

            for (int i = 0; i < getChildCount(); ++i) {
                View child = getChildAt(i);
                if (child instanceof ItemOptions.DimView) {
                    child.layout(0, 0, w, h);
                }
            }

            setPivotX((right - left) / 2f);
            setPivotY(-h * .2f);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final int W = MeasureSpec.getSize(widthMeasureSpec);
            final int H = MeasureSpec.getSize(heightMeasureSpec);

            measureChildExactly(previewContainer, previewW, previewH);
            measureChildExactly(actionBarContainer, previewW, dp(56 + 56 + 38));
            measureChildExactly(controlContainer, previewW, dp(220));
            measureChildExactly(navbarContainer, previewW, underControls);
            measureChildExactly(flashViews.foregroundView, W, H);
            if (captionEditOverlay != null) {
                measureChildExactly(captionEditOverlay, W, H);
            }

            for (int i = 0; i < getChildCount(); ++i) {
                View child = getChildAt(i);
                if (child instanceof ItemOptions.DimView) {
                    measureChildExactly(child, W, H);
                }
            }

            setMeasuredDimension(W, H);
        }

        private void measureChildExactly(View child, int width, int height) {
            child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }

        private final Paint topGradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private LinearGradient topGradient;

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            boolean r = super.drawChild(canvas, child, drawingTime);
            if (child == previewContainer) {
                final float top = underStatusBar ? AndroidUtilities.statusBarHeight : 0;
                if (topGradient == null) {
                    topGradient = new LinearGradient(0, top, 0, top + dp(72), new int[] {0x40000000, 0x00000000}, new float[] { top / (top + dp(72)), 1 }, Shader.TileMode.CLAMP );
                    topGradientPaint.setShader(topGradient);
                }
                topGradientPaint.setAlpha(0xFF);
                AndroidUtilities.rectTmp.set(0, 0, getWidth(), dp(72 + 12) + top);
                canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(12), dp(12), topGradientPaint);
            }
            return r;
        }
    }

    public static final int PAGE_CAMERA = 0;
    public static final int PAGE_PREVIEW = 1;
    public static final int PAGE_COVER = 2;
    private int currentPage = PAGE_CAMERA;

    public static final int EDIT_MODE_NONE = -1;
    public static final int EDIT_MODE_PAINT = 0;
    public static final int EDIT_MODE_FILTER = 1;
    public static final int EDIT_MODE_TIMELINE = 2;
    private int currentEditMode = EDIT_MODE_NONE;

    private FrameLayout previewContainer;
    private FrameLayout actionBarContainer;
    private LinearLayout actionBarButtons;
    private FrameLayout controlContainer;
    private FrameLayout navbarContainer;

    private FlashViews.ImageViewInvertable backButton;
    private SimpleTextView titleTextView;
    private StoryPrivacyBottomSheet privacySheet;
    private BlurringShader.BlurManager blurManager;
    private PreviewView.TextureViewHolder videoTextureHolder;
    private View captionEditOverlay;

    private boolean isReposting;
    private long botId;
    private String botLang;
    private TLRPC.InputMedia botEdit;

    private CollageLayout lastCollageLayout;

    /* PAGE_CAMERA */
    private CollageLayoutView2 collageLayoutView;
    private ImageView cameraViewThumb;
    private DualCameraView cameraView;

    private int flashButtonResId;
    private ToggleButton2 flashButton;
    private ToggleButton dualButton;
    private CollageLayoutButton collageButton;
    private ToggleButton2 collageRemoveButton;
    private CollageLayoutButton.CollageLayoutListView collageListView;
    private VideoTimerView videoTimerView;
    private boolean wasGalleryOpen;
    private boolean galleryClosing;
    private GalleryListView galleryListView;
    private DraftSavedHint draftSavedHint;
    private RecordControl recordControl;
    private PhotoVideoSwitcherView modeSwitcherView;
    private HintTextView hintTextView;
    private HintTextView collageHintTextView;
    private ZoomControlView zoomControlView;
    private HintView2 cameraHint;


    private HintView2 muteHint;
    private HintView2 dualHint;
    private HintView2 savedDualHint;
    private HintView2 removeCollageHint;
//    private StoryPrivacySelector privacySelector;
//    private boolean privacySelectorHintOpened;
//    private StoryPrivacySelector.StoryPrivacyHint privacySelectorHint;
    private TrashView trash;



    private File outputFile;
    private boolean fromGallery;

    private boolean isVideo = false;
    private boolean takingPhoto = false;
    private boolean takingVideo = false;
    private boolean stoppingTakingVideo = false;
    private boolean awaitingPlayer = false;

    private float cameraZoom;

    private int shiftDp = -3;
    private boolean showSavedDraftHint;

    public Context getContext() {
        return activity;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initViews() {
        Context context = getContext();

        windowView = new WindowView(context);
        if (Build.VERSION.SDK_INT >= 21) {
            windowView.setFitsSystemWindows(true);
            windowView.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @NonNull
                @Override
                public WindowInsets onApplyWindowInsets(@NonNull View v, @NonNull WindowInsets insets) {
                    final WindowInsetsCompat insetsCompat = WindowInsetsCompat.toWindowInsetsCompat(insets, v);
                    final androidx.core.graphics.Insets i = insetsCompat.getInsets(WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.systemBars());
                    insetTop    = Math.max(i.top, insets.getStableInsetTop());
                    insetBottom = Math.max(i.bottom, insets.getStableInsetBottom());
                    insetLeft   = Math.max(i.left, insets.getStableInsetLeft());
                    insetRight  = Math.max(i.right, insets.getStableInsetRight());
                    insetTop = Math.max(insetTop, AndroidUtilities.statusBarHeight);
                    windowView.requestLayout();
                    if (Build.VERSION.SDK_INT >= 30) {
                        return WindowInsets.CONSUMED;
                    } else {
                        return insets.consumeSystemWindowInsets();
                    }
                }
            });
        }
        windowView.setFocusable(true);

        flashViews = new FlashViews(context, windowManager, windowView, windowLayoutParams);
        flashViews.add(new FlashViews.Invertable() {
            @Override
            public void setInvert(float invert) {
                AndroidUtilities.setLightNavigationBar(windowView, invert > 0.5f);
                AndroidUtilities.setLightStatusBar(windowView, invert > 0.5f);
            }
            @Override
            public void invalidate() {}
        });
        windowView.addView(flashViews.backgroundView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        windowView.addView(containerView = new ContainerView(context));
        containerView.addView(previewContainer = new FrameLayout(context) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (previewTouchable != null) {
                    previewTouchable.onTouch(event);
                    return true;
                }
                return super.onTouchEvent(event);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }

            private final Rect leftExclRect = new Rect();
            private final Rect rightExclRect = new Rect();

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    final int w = right - left;
                    final int h = bottom - top;
                    leftExclRect.set(0, h - dp(120), dp(40), h);
                    rightExclRect.set(w - dp(40), h - dp(120), w, h);
                    setSystemGestureExclusionRects(Arrays.asList(leftExclRect, rightExclRect));
                }
            }

            @Override
            public void invalidate() {
                if (openCloseAnimator != null && openCloseAnimator.isRunning()) {
                    return;
                }
                super.invalidate();
            }

            private RenderNode renderNode;
            @Override
            protected void dispatchDraw(@NonNull Canvas c) {
                boolean endRecording = false;
                Canvas canvas = c;
                if (Build.VERSION.SDK_INT >= 31 && c.isHardwareAccelerated() && !AndroidUtilities.makingGlobalBlurBitmap) {
                    if (renderNode == null) {
                        renderNode = new RenderNode("StoryRecorder.PreviewView");
                    }
                    renderNode.setPosition(0, 0, getWidth(), getHeight());
                    canvas = renderNode.beginRecording();
                    endRecording = true;
                }
                super.dispatchDraw(canvas);
                if (endRecording && Build.VERSION.SDK_INT >= 31) {
                    renderNode.endRecording();
                    if (blurManager != null) {
                        blurManager.setRenderNode(this, renderNode, 0xFF1F1F1F);
                    }
                    c.drawRenderNode(renderNode);
                }
            }
        });
        containerView.addView(flashViews.foregroundView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        blurManager = new BlurringShader.BlurManager(previewContainer);
        videoTextureHolder = new PreviewView.TextureViewHolder();
        containerView.addView(actionBarContainer = new FrameLayout(context)); // 150dp
        containerView.addView(controlContainer = new FrameLayout(context)); // 220dp
        containerView.addView(navbarContainer = new FrameLayout(context)); // 48dp

        Bulletin.addDelegate(windowView, new Bulletin.Delegate() {
            @Override
            public int getTopOffset(int tag) {
                return dp(56);
            }

            @Override
            public int getBottomOffset(int tag) {
                return Bulletin.Delegate.super.getBottomOffset(tag);
            }

            @Override
            public boolean clipWithGradient(int tag) {
                return true;
            }
        });

        collageLayoutView = new CollageLayoutView2(context, blurManager, containerView, resourcesProvider) {
            @Override
            protected void onLayoutUpdate(CollageLayout layout) {
                collageListView.setVisible(false, true);
                if (layout != null && layout.parts.size() > 1) {
                    collageButton.setIcon(new CollageLayoutButton.CollageLayoutDrawable(lastCollageLayout = layout), true);
                    collageButton.setSelected(true, true);
                } else {
                    collageButton.setSelected(false, true);
                }
                updateActionBarButtons(true);
            }
        };
        collageLayoutView.setCancelGestures(windowView::cancelGestures);
        collageLayoutView.setResetState(() -> {
            updateActionBarButtons(true);
        });
        previewContainer.addView(collageLayoutView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        cameraViewThumb = new ImageView(context);
        cameraViewThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cameraViewThumb.setOnClickListener(v -> {
            if (noCameraPermission) {
                requestCameraPermission(true);
            }
        });
        cameraViewThumb.setClickable(true);
//        previewContainer.addView(cameraViewThumb, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        previewContainer.setBackgroundColor(openType == 1 || openType == 0 ? 0 : 0xff1f1f1f);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            previewContainer.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight(), dp(12));
                }
            });
            previewContainer.setClipToOutline(true);
        }


        backButton = new FlashViews.ImageViewInvertable(context);
        backButton.setContentDescription(getString(R.string.AccDescrGoBack));
        backButton.setScaleType(ImageView.ScaleType.CENTER);
        backButton.setImageResource(R.drawable.msg_photo_back);
        backButton.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY));
        backButton.setBackground(Theme.createSelectorDrawable(0x20ffffff));
        backButton.setOnClickListener(e -> {
            if (awaitingPlayer) {
                return;
            }
            onBackPressed();
        });
        actionBarContainer.addView(backButton, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.LEFT));
        flashViews.add(backButton);

        titleTextView = new SimpleTextView(context);
        titleTextView.setTextSize(20);
        titleTextView.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        titleTextView.setTextColor(0xffffffff);
        titleTextView.setTypeface(AndroidUtilities.bold());
        titleTextView.setText(getString(R.string.RecorderNewStory));
        titleTextView.getPaint().setShadowLayer(dpf2(1), 0, 1, 0x40000000);
        titleTextView.setAlpha(0f);
        titleTextView.setVisibility(View.GONE);
        titleTextView.setEllipsizeByGradient(true);
        titleTextView.setRightPadding(AndroidUtilities.dp(144));
        actionBarContainer.addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 56, Gravity.TOP | Gravity.FILL_HORIZONTAL, 71, 0, 0, 0));

        actionBarButtons = new LinearLayout(context);
        actionBarButtons.setOrientation(LinearLayout.HORIZONTAL);
        actionBarButtons.setGravity(Gravity.RIGHT);
        actionBarContainer.addView(actionBarButtons, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 56, Gravity.RIGHT | Gravity.FILL_HORIZONTAL, 0, 0, 8, 0));

        muteHint = new HintView2(activity, HintView2.DIRECTION_TOP)
            .setJoint(1, -77 + 8 - 2)
            .setDuration(2000)
            .setBounce(false)
            .setAnimatedTextHacks(true, true, false);
        muteHint.setPadding(dp(8), 0, dp(8), 0);
        actionBarContainer.addView(muteHint, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 52, 0, 0));





        flashButton = new ToggleButton2(context);
        flashButton.setBackground(Theme.createSelectorDrawable(0x20ffffff));
        flashButton.setOnClickListener(e -> {
            if (cameraView == null || awaitingPlayer) {
                return;
            }
            String current = getCurrentFlashMode();
            String next = getNextFlashMode();
            if (current == null || current.equals(next)) {
                return;
            }
            setCurrentFlashMode(next);
            setCameraFlashModeIcon(next, true);
        });
        flashButton.setOnLongClickListener(e -> {
            if (cameraView == null || !cameraView.isFrontface()) {
                return false;
            }

            checkFrontfaceFlashModes();
            flashButton.setSelected(true);
            flashViews.previewStart();
            ItemOptions.makeOptions(containerView, resourcesProvider, flashButton)
                .addView(
                    new SliderView(getContext(), SliderView.TYPE_WARMTH)
                        .setValue(flashViews.warmth)
                        .setOnValueChange(v -> {
                            flashViews.setWarmth(v);
                        })
                )
                .addSpaceGap()
                .addView(
                    new SliderView(getContext(), SliderView.TYPE_INTENSITY)
                        .setMinMax(.65f, 1f)
                        .setValue(flashViews.intensity)
                        .setOnValueChange(v -> {
                            flashViews.setIntensity(v);
                        })
                )
                .setOnDismiss(() -> {
                    saveFrontFaceFlashMode();
                    flashViews.previewEnd();
                    flashButton.setSelected(false);
                })
                .setDimAlpha(0)
                .setGravity(Gravity.RIGHT)
                .translate(dp(46), -dp(4))
                .setBackgroundColor(0xbb1b1b1b)
                .show();
            return true;
        });
        flashButton.setVisibility(View.GONE);
        flashButton.setAlpha(0f);
        flashViews.add(flashButton);
        actionBarContainer.addView(flashButton, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.RIGHT));

        dualButton = new ToggleButton(context, R.drawable.media_dual_camera2_shadow, R.drawable.media_dual_camera2);
        dualButton.setOnClickListener(v -> {
            if (cameraView == null || currentPage != PAGE_CAMERA) {
                return;
            }
            cameraView.toggleDual();
            dualButton.setValue(cameraView.isDual());

            dualHint.hide();
            MessagesController.getGlobalMainSettings().edit().putInt("storydualhint", 2).apply();
            if (savedDualHint.shown()) {
                MessagesController.getGlobalMainSettings().edit().putInt("storysvddualhint", 2).apply();
            }
            savedDualHint.hide();
        });
        final boolean dualCameraAvailable = DualCameraView.dualAvailableStatic(context);
        dualButton.setVisibility(dualCameraAvailable ? View.VISIBLE : View.GONE);
        dualButton.setAlpha(dualCameraAvailable ? 1.0f : 0.0f);
        flashViews.add(dualButton);
        actionBarContainer.addView(dualButton, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.RIGHT));

        collageButton = new CollageLayoutButton(context);
        collageButton.setBackground(Theme.createSelectorDrawable(0x20ffffff));
        if (lastCollageLayout == null) {
            lastCollageLayout = CollageLayout.getLayouts().get(6);
        }
        collageButton.setOnClickListener(v -> {
            if (currentPage != PAGE_CAMERA || animatedRecording) return;
            if (cameraView != null && cameraView.isDual()) {
                cameraView.toggleDual();
            }
            if (!collageListView.isVisible() && !collageLayoutView.hasLayout()) {
                collageLayoutView.setLayout(lastCollageLayout, true);
                collageListView.setSelected(lastCollageLayout);
                collageButton.setIcon(new CollageLayoutButton.CollageLayoutDrawable(lastCollageLayout), true);
                collageButton.setSelected(true);
                if (cameraView != null) {
                    cameraView.recordHevc = !collageLayoutView.hasLayout();
                }
            }
            collageListView.setVisible(!collageListView.isVisible(), true);
            updateActionBarButtons(true);
        });
        collageButton.setIcon(new CollageLayoutButton.CollageLayoutDrawable(lastCollageLayout), false);
        collageButton.setSelected(false);
        collageButton.setVisibility(View.VISIBLE);
        collageButton.setAlpha(1.0f);
        flashViews.add(collageButton);
        actionBarContainer.addView(collageButton, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.RIGHT));

        collageRemoveButton = new ToggleButton2(context);
        collageRemoveButton.setBackground(Theme.createSelectorDrawable(0x20ffffff));
        collageRemoveButton.setIcon(new CollageLayoutButton.CollageLayoutDrawable(new CollageLayout("../../.."), true), false);
        collageRemoveButton.setVisibility(View.GONE);
        collageRemoveButton.setAlpha(0.0f);
        collageRemoveButton.setOnClickListener(v -> {
            collageLayoutView.setLayout(null, true);
            collageLayoutView.clear(true);
            collageListView.setSelected(null);
            if (cameraView != null) {
                cameraView.recordHevc = !collageLayoutView.hasLayout();
            }
            collageListView.setVisible(false, true);
            updateActionBarButtons(true);
        });
        flashViews.add(collageRemoveButton);
        actionBarContainer.addView(collageRemoveButton, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.RIGHT));

        collageListView = new CollageLayoutButton.CollageLayoutListView(context, flashViews);
        collageListView.listView.scrollToPosition(6);
        collageListView.setSelected(null);
        collageListView.setOnLayoutClick(layout -> {
            collageLayoutView.setLayout(lastCollageLayout = layout, true);
            collageListView.setSelected(layout);
            if (cameraView != null) {
                cameraView.recordHevc = !collageLayoutView.hasLayout();
            }
            collageButton.setDrawable(new CollageLayoutButton.CollageLayoutDrawable(layout));
            setActionBarButtonVisible(collageRemoveButton, collageListView.isVisible(), true);
            recordControl.setCollageProgress(collageLayoutView.hasLayout() ? collageLayoutView.getFilledProgress() : 0.0f, true);
        });
        actionBarContainer.addView(collageListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 56, Gravity.TOP | Gravity.RIGHT));

        dualHint = new HintView2(activity, HintView2.DIRECTION_TOP)
            .setJoint(1, -20)
            .setDuration(5000)
            .setCloseButton(true)
            .setText(getString(R.string.StoryCameraDualHint))
            .setOnHiddenListener(() -> MessagesController.getGlobalMainSettings().edit().putInt("storydualhint", MessagesController.getGlobalMainSettings().getInt("storydualhint", 0) + 1).apply());
        dualHint.setPadding(dp(8), 0, dp(8), 0);
        actionBarContainer.addView(dualHint, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 52, 0, 0));

        savedDualHint = new HintView2(activity, HintView2.DIRECTION_RIGHT)
                .setJoint(0, 56 / 2)
                .setDuration(5000)
                .setMultilineText(true);
        actionBarContainer.addView(savedDualHint, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 0, 52, 0));

        removeCollageHint = new HintView2(activity, HintView2.DIRECTION_TOP)
                .setJoint(1, -20)
                .setDuration(5000)
                .setText(LocaleController.getString(R.string.StoryCollageRemoveGrid));
        removeCollageHint.setPadding(dp(8), 0, dp(8), 0);
        actionBarContainer.addView(removeCollageHint, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 52, 0, 0));

        videoTimerView = new VideoTimerView(context);
        showVideoTimer(false, false);
        actionBarContainer.addView(videoTimerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 45, Gravity.TOP | Gravity.FILL_HORIZONTAL, 56, 0, 56, 0));
        flashViews.add(videoTimerView);

        if (Build.VERSION.SDK_INT >= 21) {
            MediaController.loadGalleryPhotosAlbums(0);
        }

        recordControl = new RecordControl(context);
        recordControl.setDelegate(recordControlDelegate);
        recordControl.setUnlimitedVideoRecord(true);
        recordControl.startAsVideo(isVideo);
        controlContainer.addView(recordControl, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 100, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));
        flashViews.add(recordControl);
        recordControl.setCollageProgress(collageLayoutView.hasLayout() ? collageLayoutView.getFilledProgress() : 0.0f, true);

        cameraHint = new HintView2(activity, HintView2.DIRECTION_BOTTOM)
                .setMultilineText(true)
                .setText(getString(R.string.StoryCameraHint2))
                .setMaxWidth(320)
                .setDuration(5000L)
                .setTextAlign(Layout.Alignment.ALIGN_CENTER);
        controlContainer.addView(cameraHint, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.BOTTOM, 0, 0, 0, 100));

        zoomControlView = new ZoomControlView(context);
        zoomControlView.enabledTouch = false;
        zoomControlView.setAlpha(0.0f);
        controlContainer.addView(zoomControlView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, 100 + 8));
        zoomControlView.setDelegate(zoom -> {
            if (cameraView != null) {
                cameraView.setZoom(cameraZoom = zoom);
            }
            showZoomControls(true, true);
        });
        zoomControlView.setZoom(cameraZoom = 0, false);

        modeSwitcherView = new PhotoVideoSwitcherView(context) {
            @Override
            protected boolean allowTouch() {
                return !inCheck();
            }
        };
        modeSwitcherView.setOnSwitchModeListener(newIsVideo -> {
            if (takingPhoto || takingVideo) {
                return;
            }

            isVideo = newIsVideo;
            showVideoTimer(isVideo && !collageListView.isVisible(), true);
            modeSwitcherView.switchMode(isVideo);
            recordControl.startAsVideo(isVideo);
        });
        modeSwitcherView.setOnSwitchingModeListener(t -> {
            recordControl.startAsVideoT(t);
        });
        navbarContainer.addView(modeSwitcherView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));
        flashViews.add(modeSwitcherView);

        hintTextView = new HintTextView(context);
        navbarContainer.addView(hintTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 32, Gravity.CENTER, 8, 0, 8, 8));
        flashViews.add(hintTextView);

        collageHintTextView = new HintTextView(context);
        collageHintTextView.setText(LocaleController.getString(R.string.StoryCollageReorderHint), false);
        collageHintTextView.setAlpha(0.0f);
        navbarContainer.addView(collageHintTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 32, Gravity.CENTER, 8, 0, 8, 8));
        flashViews.add(collageHintTextView);

        trash = new TrashView(context);
        trash.setAlpha(0f);
        trash.setVisibility(View.GONE);
        previewContainer.addView(trash, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 120, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 16));

        updateActionBarButtonsOffsets();
    }

    private DraftSavedHint getDraftSavedHint() {
        if (draftSavedHint == null) {
            draftSavedHint = new DraftSavedHint(getContext());
            controlContainer.addView(draftSavedHint, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, 0, 0, 0, 66 + 12));
        }
        return draftSavedHint;
    }


    private String flashButtonMode;
    private void setCameraFlashModeIcon(String mode, boolean animated) {
        flashButton.clearAnimation();
        if (cameraView != null && cameraView.isDual() || animatedRecording) {
            mode = null;
        }
        flashButtonMode = mode;
        if (mode == null) {
            setActionBarButtonVisible(flashButton, false, animated);
            return;
        }
        final int resId;
        switch (mode) {
            case Camera.Parameters.FLASH_MODE_ON:
                resId = R.drawable.media_photo_flash_on2;
                flashButton.setContentDescription(getString(R.string.AccDescrCameraFlashOn));
                break;
            case Camera.Parameters.FLASH_MODE_AUTO:
                resId = R.drawable.media_photo_flash_auto2;
                flashButton.setContentDescription(getString(R.string.AccDescrCameraFlashAuto));
                break;
            default:
            case Camera.Parameters.FLASH_MODE_OFF:
                resId = R.drawable.media_photo_flash_off2;
                flashButton.setContentDescription(getString(R.string.AccDescrCameraFlashOff));
                break;
        }
        flashButton.setIcon(flashButtonResId = resId, animated && flashButtonResId != resId);
        setActionBarButtonVisible(flashButton, currentPage == PAGE_CAMERA && !collageListView.isVisible() && flashButtonMode != null && !inCheck(), animated);
    }

    private final RecordControl.Delegate recordControlDelegate = new RecordControl.Delegate() {
        @Override
        public boolean canRecordAudio() {
            return requestAudioPermission();
        }

        @Override
        public void onPhotoShoot() {
            if (takingPhoto || awaitingPlayer || currentPage != PAGE_CAMERA || cameraView == null || !cameraView.isInited()) {
                return;
            }
            if (innerHook != null && !innerHook.beforeTakingPhotos()) {
                return;
            }
            outputFile = AndroidUtilities.generatePicturePath(innerHook.provideParentAlert().baseFragment instanceof ChatActivity && ((ChatActivity) innerHook.provideParentAlert().baseFragment).isSecretChat(), null);
            cameraHint.hide();
            takingPhoto = true;
            checkFrontfaceFlashModes();
            isDark = false;
            if (cameraView.isFrontface() && frontfaceFlashMode == 1) {
                checkIsDark();
            }
            if (useDisplayFlashlight()) {
                flashViews.flash(this::takePicture);
            } else {
                takePicture(null);
            }
        }

        @Override
        public void onCheckClick() {
            // TODO CollageLayout
//            ArrayList<StoryEntry> entries = collageLayoutView.getContent();
//            if (entries.size() == 1) {
//                outputEntry = entries.get(0);
//            } else {
//                outputEntry = StoryEntry.asCollage(collageLayoutView.getLayout(), collageLayoutView.getContent());
//            }
//            isVideo = outputEntry != null && outputEntry.isVideo;
//            if (modeSwitcherView != null) {
//                modeSwitcherView.switchMode(isVideo);
//            }


        }

        private void takePicture(Utilities.Callback<Runnable> done) {

            boolean savedFromTextureView = false;
            if (!useDisplayFlashlight()) {
                cameraView.startTakePictureAnimation(true);
            }
            if (cameraView.isDual() && TextUtils.equals(cameraView.getCameraSession().getCurrentFlashMode(), Camera.Parameters.FLASH_MODE_OFF) || collageLayoutView.hasLayout()) {
                if (!collageLayoutView.hasLayout()) {
                    cameraView.pauseAsTakingPicture();
                }
                final Bitmap bitmap = cameraView.getTextureView().getBitmap();
                try (FileOutputStream out = new FileOutputStream(outputFile.getAbsoluteFile())) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
                    savedFromTextureView = true;
                } catch (Exception e) {
                    FileLog.e(e);
                }
                bitmap.recycle();
            }
            if (!savedFromTextureView) {
                takingPhoto = CameraController.getInstance().takePicture(outputFile, true, cameraView.getCameraSessionObject(), (orientation) -> {
                    if (useDisplayFlashlight()) {
                        try {
                            windowView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                        } catch (Exception ignore) {}
                    }
                    takingPhoto = false;
                    if (outputFile == null) {
                        return;
                    }
                    int w = -1, h = -1;
                    try {
                        BitmapFactory.Options opts = new BitmapFactory.Options();
                        opts.inJustDecodeBounds = true;
                        BitmapFactory.decodeFile(outputFile.getAbsolutePath(), opts);
                        w = opts.outWidth;
                        h = opts.outHeight;
                    } catch (Exception ignore) {}

                    int rotate = orientation == -1 ? 0 : 90;
                    if (orientation == -1) {
                        if (w > h) {
                            rotate = 270;
                        }
                    } else if (h > w && rotate != 0) {
                        rotate = 0;
                    }

                    MediaController.PhotoEntry photoEntry = new MediaController.PhotoEntry(0, innerHook.modifyLastImage(), 0, outputFile.getAbsolutePath(), orientation == -1 ? 0 : orientation, false, w, h, 0);
                    photoEntry.canDeleteAfter = true;
                    if (collageLayoutView.hasLayout()) {
                        outputFile = null;
                        // TODO CollageLayout
//                        if (collageLayoutView.push(entry)) {
//                            outputEntry = StoryEntry.asCollage(collageLayoutView.getLayout(), collageLayoutView.getContent());
//                            StoryPrivacySelector.applySaved(currentAccount, outputEntry);
//                            fromGallery = false;
//
//                            if (done != null) {
//                                done.run(null);
//                            }
////                            if (done != null) {
////                                done.run(() -> navigateTo(PAGE_PREVIEW, true));
////                            } else {
////                                navigateTo(PAGE_PREVIEW, true);
////                            }
//                        } else if (done != null) {
//                            done.run(null);
//                        }
                        updateActionBarButtons(true);
                    } else {

                        fromGallery = false;

                        if (done != null) {
                            done.run(() -> {
                                innerHook.afterTakingPhotos(photoEntry, cameraView.getCameraSession().isSameTakePictureOrientation());
                            });
                        } else {
                            innerHook.afterTakingPhotos(photoEntry, cameraView.getCameraSession().isSameTakePictureOrientation());
                        }
                    }
                });
            } else {
                takingPhoto = false;

                if (collageLayoutView.hasLayout()) {
                    // TODO CollapseLayout
//                    outputFile = null;
//                    if (collageLayoutView.push(entry)) {
//                        outputEntry = StoryEntry.asCollage(collageLayoutView.getLayout(), collageLayoutView.getContent());
//                        StoryPrivacySelector.applySaved(currentAccount, outputEntry);
//                        fromGallery = false;
//                        if (done != null) {
//                            done.run(null);
//                        }
//                    } else if (done != null) {
//                        done.run(null);
//                    }
                    updateActionBarButtons(true);
                } else {
                    fromGallery = false;
                    int width = 0, height = 0;
                    try {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inJustDecodeBounds = true;
                        BitmapFactory.decodeFile(new File(outputFile.getAbsolutePath()).getAbsolutePath(), options);
                        width = options.outWidth;
                        height = options.outHeight;
                    } catch (Exception ignore) {}
                    int orientation = 0;
                    MediaController.PhotoEntry photoEntry = new MediaController.PhotoEntry(0, innerHook.modifyLastImage(), 0, outputFile.getAbsolutePath(), orientation == -1 ? 0 : orientation, false, width, height, 0);
                    photoEntry.canDeleteAfter = true;
                    if (done != null) {
                        done.run(() -> {
                            innerHook.afterTakingPhotos(photoEntry, cameraView.getCameraSession().isSameTakePictureOrientation());
                        });
                    } else {
                        innerHook.afterTakingPhotos(photoEntry, cameraView.getCameraSession().isSameTakePictureOrientation());
                    }
                }
            }
        }

        @Override
        public void onVideoRecordStart(boolean byLongPress, Runnable whenStarted) {
            if (takingVideo || stoppingTakingVideo || awaitingPlayer || currentPage != PAGE_CAMERA || cameraView == null || cameraView.getCameraSession() == null) {
                return;
            }
            if (innerHook != null && !innerHook.beforeRecordingVideos()) {
                return;
            }
            if (dualHint != null) {
                dualHint.hide();
            }
            if (savedDualHint != null) {
                savedDualHint.hide();
            }
            cameraHint.hide();
            takingVideo = true;
            if (outputFile != null) {
                try {
                    outputFile.delete();
                } catch (Exception ignore) {}
                outputFile = null;
            }
            ChatAttachAlert parentAlert = innerHook.provideParentAlert();
            outputFile = makeCacheFile(UserConfig.selectedAccount, "mp4");
            checkFrontfaceFlashModes();
            isDark = false;
            if (cameraView.isFrontface() && frontfaceFlashMode == 1) {
                checkIsDark();
            }
            if (useDisplayFlashlight()) {
                flashViews.flashIn(() -> startRecording(byLongPress, whenStarted));
            } else {
                startRecording(byLongPress, whenStarted);
            }
        }

        private File makeCacheFile(int account, String ext) {
            TLRPC.TL_fileLocationToBeDeprecated location = new TLRPC.TL_fileLocationToBeDeprecated();
            location.volume_id = Integer.MIN_VALUE;
            location.dc_id = Integer.MIN_VALUE;
            location.local_id = SharedConfig.getLastLocalId();
            location.file_reference = new byte[0];

            TLObject object;
            if ("mp4".equals(ext) || "webm".equals(ext)) {
                TLRPC.VideoSize videoSize = new TLRPC.TL_videoSize_layer127();
                videoSize.location = location;
                object = videoSize;
            } else {
                TLRPC.PhotoSize photoSize = new TLRPC.TL_photoSize_layer127();
                photoSize.location = location;
                object = photoSize;
            }

            return FileLoader.getInstance(account).getPathToAttach(object, ext, true);
        }

        private void startRecording(boolean byLongPress, Runnable whenStarted) {
            if (cameraView == null) {
                return;
            }
            CameraController.getInstance().recordVideo(cameraView.getCameraSessionObject(), outputFile, false, (thumbPath, duration) -> {
                if (recordControl != null) {
                    recordControl.stopRecordingLoading(true);
                }
                if (useDisplayFlashlight()) {
                    flashViews.flashOut();
                }
                if (outputFile == null || cameraView == null) {
                    return;
                }

                takingVideo = false;
                stoppingTakingVideo = false;

                if (duration <= 800) {
                    animateRecording(false, true);
                    setAwakeLock(false);
                    videoTimerView.setRecording(false, true);
                    if (recordControl != null) {
                        recordControl.stopRecordingLoading(true);
                    }
                    try {
                        outputFile.delete();
                        outputFile = null;
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    if (thumbPath != null) {
                        try {
                            new File(thumbPath).delete();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                    return;
                }

                showVideoTimer(false, true);

                animateRecording(false, true);
                setAwakeLock(false);
                videoTimerView.setRecording(false, true);
                if (recordControl != null) {
                    recordControl.stopRecordingLoading(true);
                }
                if (collageLayoutView.hasLayout()) {
                    outputFile = null;
                    // TODO CollapseLayout
//
//                    if (collageLayoutView.push(entry)) {
//                        outputEntry = StoryEntry.asCollage(collageLayoutView.getLayout(), collageLayoutView.getContent());
//                        StoryPrivacySelector.applySaved(currentAccount, outputEntry);
//                        fromGallery = false;
//                        int width = cameraView.getVideoWidth(), height = cameraView.getVideoHeight();
//                        if (width > 0 && height > 0) {
//                            outputEntry.width = width;
//                            outputEntry.height = height;
//                            outputEntry.setupMatrix();
//                        }
//                    }
                    updateActionBarButtons(true);
                } else {

                    fromGallery = false;
                    int width = cameraView.getVideoWidth(), height = cameraView.getVideoHeight();

                    MediaController.PhotoEntry photoEntry = new MediaController.PhotoEntry(0, innerHook.modifyLastImage(), 0, outputFile.getAbsolutePath(), 0, true, width, height, 0);
                    photoEntry.duration = (int) (duration / 1000f);
                    photoEntry.thumbPath = thumbPath;
                    ChatAttachAlert parentAlert = innerHook.provideParentAlert();
                    if (parentAlert.avatarPicker != 0 && cameraView.isFrontface()) {
                        photoEntry.cropState = new MediaController.CropState();
                        photoEntry.cropState.mirrored = true;
                        photoEntry.cropState.freeform = false;
                        photoEntry.cropState.lockedAspectRatio = 1.0f;
                    }

                    navigateToPreviewWithPlayerAwait(() -> {
                        innerHook.afterRecordingVideos(photoEntry);
                    }, 0);
                }
            }, () /* onVideoStart */ -> {
                whenStarted.run();

                hintTextView.setText(getString(byLongPress ? R.string.StoryHintSwipeToZoom : R.string.StoryHintPinchToZoom), false);
                animateRecording(true, true);
                setAwakeLock(true);

                collageListView.setVisible(false, true);
                videoTimerView.setRecording(true, true);
                showVideoTimer(true, true);
            }, cameraView, true);

            if (!isVideo) {
                isVideo = true;
                collageListView.setVisible(false, true);
                showVideoTimer(isVideo, true);
                modeSwitcherView.switchMode(isVideo);
                recordControl.startAsVideo(isVideo);
            }
        }

        @Override
        public void onVideoRecordLocked() {
            hintTextView.setText(getString(R.string.StoryHintPinchToZoom), true);
        }

        @Override
        public void onVideoRecordPause() {

        }

        @Override
        public void onVideoRecordResume() {

        }

        @Override
        public void onVideoRecordEnd(boolean byDuration) {
            if (stoppingTakingVideo || !takingVideo) {
                return;
            }
            stoppingTakingVideo = true;
            AndroidUtilities.runOnUIThread(() -> {
                if (takingVideo && stoppingTakingVideo && cameraView != null) {
                    showZoomControls(false, true);
//                    animateRecording(false, true);
//                    setAwakeLock(false);
                    CameraController.getInstance().stopVideoRecording(cameraView.getCameraSessionRecording(), false, false);
                }
            }, byDuration ? 0 : 400);
        }

        @Override
        public void onVideoDuration(long duration) {
            videoTimerView.setDuration(duration, true);
        }

        @Override
        public void onGalleryClick() {
            if (currentPage == PAGE_CAMERA && !takingPhoto && !takingVideo && requestGalleryPermission()) {
                animateGalleryListView(true);
            }
        }

        @Override
        public void onFlipClick() {
            if (cameraView == null || awaitingPlayer || takingPhoto || !cameraView.isInited() || currentPage != PAGE_CAMERA) {
                return;
            }
            if (savedDualHint != null) {
                savedDualHint.hide();
            }
            if (useDisplayFlashlight() && frontfaceFlashModes != null && !frontfaceFlashModes.isEmpty()) {
                final String mode = frontfaceFlashModes.get(frontfaceFlashMode);
                SharedPreferences sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("camera", Activity.MODE_PRIVATE);
                sharedPreferences.edit().putString("flashMode", mode).commit();
            }
            cameraView.switchCamera();
            saveCameraFace(cameraView.isFrontface());
            if (useDisplayFlashlight()) {
                flashViews.flashIn(null);
            } else {
                flashViews.flashOut();
            }
        }

        @Override
        public void onFlipLongClick() {
            if (cameraView != null) {
                cameraView.toggleDual();
            }
        }

        @Override
        public void onZoom(float zoom) {
            zoomControlView.setZoom(zoom, true);
            showZoomControls(false, true);
        }
    };

    private void setAwakeLock(boolean lock) {
        if (lock) {
            windowLayoutParams.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        } else {
            windowLayoutParams.flags &= ~WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        }
        try {
            windowManager.updateViewLayout(windowView, windowLayoutParams);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private AnimatorSet recordingAnimator;
    private boolean animatedRecording;
    private boolean animatedRecordingWasInCheck;
    private void animateRecording(boolean recording, boolean animated) {
        if (recording) {
            if (dualHint != null) {
                dualHint.hide();
            }
            if (savedDualHint != null) {
                savedDualHint.hide();
            }
            if (muteHint != null) {
                muteHint.hide();
            }
            if (cameraHint != null) {
                cameraHint.hide();
            }
        }
        if (animatedRecording == recording && animatedRecordingWasInCheck == inCheck()) {
            return;
        }
        if (recordingAnimator != null) {
            recordingAnimator.cancel();
            recordingAnimator = null;
        }
        animatedRecording = recording;
        animatedRecordingWasInCheck = inCheck();
        if (recording && collageListView != null && collageListView.isVisible()) {
            collageListView.setVisible(false, animated);
        }
        updateActionBarButtons(animated);
        if (animated) {
            recordingAnimator = new AnimatorSet();
            recordingAnimator.playTogether(
                ObjectAnimator.ofFloat(hintTextView, View.ALPHA, recording && currentPage == PAGE_CAMERA && !inCheck() ? 1 : 0),
                ObjectAnimator.ofFloat(hintTextView, View.TRANSLATION_Y, recording && currentPage == PAGE_CAMERA && !inCheck() ? 0 : dp(16)),
                ObjectAnimator.ofFloat(collageHintTextView, View.ALPHA, !recording && currentPage == PAGE_CAMERA && inCheck() ? 0.6f : 0),
                ObjectAnimator.ofFloat(collageHintTextView, View.TRANSLATION_Y, !recording && currentPage == PAGE_CAMERA && inCheck() ? 0 : dp(16)),
                ObjectAnimator.ofFloat(modeSwitcherView, View.ALPHA, recording || currentPage != PAGE_CAMERA || inCheck() ? 0 : 1),
                ObjectAnimator.ofFloat(modeSwitcherView, View.TRANSLATION_Y, recording || currentPage != PAGE_CAMERA || inCheck() ? dp(16) : 0)
            );
            recordingAnimator.setDuration(260);
            recordingAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            recordingAnimator.start();
        } else {
            hintTextView.setAlpha(recording && currentPage == PAGE_CAMERA && !inCheck() ? 1f : 0);
            hintTextView.setTranslationY(recording && currentPage == PAGE_CAMERA && !inCheck() ? 0 : dp(16));
            collageHintTextView.setAlpha(!recording && currentPage == PAGE_CAMERA && inCheck() ? 0.6f : 0);
            collageHintTextView.setTranslationY(!recording && currentPage == PAGE_CAMERA && inCheck() ? 0 : dp(16));
            modeSwitcherView.setAlpha(recording || currentPage != PAGE_CAMERA || inCheck() ? 0 : 1f);
            modeSwitcherView.setTranslationY(recording || currentPage != PAGE_CAMERA || inCheck() ? dp(16) : 0);
        }
    }

    private boolean isDark;
    private void checkIsDark() {
        if (cameraView == null || cameraView.getTextureView() == null) {
            isDark = false;
            return;
        }
        final Bitmap bitmap = cameraView.getTextureView().getBitmap();
        if (bitmap == null) {
            isDark = false;
            return;
        }
        float l = 0;
        final int sx = bitmap.getWidth() / 12;
        final int sy = bitmap.getHeight() / 12;
        for (int x = 0; x < 10; ++x) {
            for (int y = 0; y < 10; ++y) {
                l += AndroidUtilities.computePerceivedBrightness(bitmap.getPixel((1 + x) * sx, (1 + y) * sy));
            }
        }
        l /= 100;
        bitmap.recycle();
        isDark = l < .22f;
    }

    private boolean useDisplayFlashlight() {
        return (takingPhoto || takingVideo) && (cameraView != null && cameraView.isFrontface()) && (frontfaceFlashMode == 2 || frontfaceFlashMode == 1 && isDark);
    }

    private boolean videoTimerShown = true;
    private void showVideoTimer(boolean show, boolean animated) {
        if (videoTimerShown == show) {
            return;
        }

        videoTimerShown = show;
        if (animated) {
            videoTimerView.animate().alpha(show ? 1 : 0).setDuration(350).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).withEndAction(() -> {
                if (!show) {
                    videoTimerView.setRecording(false, false);
                }
            }).start();
        } else {
            videoTimerView.clearAnimation();
            videoTimerView.setAlpha(show ? 1 : 0);
            if (!show) {
                videoTimerView.setRecording(false, false);
            }
        }
    }

    private Runnable zoomControlHideRunnable;
    private AnimatorSet zoomControlAnimation;

    private void showZoomControls(boolean show, boolean animated) {
        if (zoomControlView.getTag() != null && show || zoomControlView.getTag() == null && !show) {
            if (show) {
                if (zoomControlHideRunnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(zoomControlHideRunnable);
                }
                AndroidUtilities.runOnUIThread(zoomControlHideRunnable = () -> {
                    showZoomControls(false, true);
                    zoomControlHideRunnable = null;
                }, 2000);
            }
            return;
        }
        if (zoomControlAnimation != null) {
            zoomControlAnimation.cancel();
        }
        zoomControlView.setTag(show ? 1 : null);
        zoomControlAnimation = new AnimatorSet();
        zoomControlAnimation.setDuration(180);
        if (show) {
            zoomControlView.setVisibility(View.VISIBLE);
        }
        zoomControlAnimation.playTogether(ObjectAnimator.ofFloat(zoomControlView, View.ALPHA, show ? 1.0f : 0.0f));
        zoomControlAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!show) {
                    zoomControlView.setVisibility(View.GONE);
                }
                zoomControlAnimation = null;
            }
        });
        zoomControlAnimation.start();
        if (show) {
            AndroidUtilities.runOnUIThread(zoomControlHideRunnable = () -> {
                showZoomControls(false, true);
                zoomControlHideRunnable = null;
            }, 2000);
        }
    }

    public boolean onBackPressed() {
        if (openCloseAnimator != null && openCloseAnimator.isRunning()) {
            return false;
        }
        if (takingVideo) {
            recordControl.stopRecording();
            return false;
        }
        if (takingPhoto) {
            return false;
        }
        if (galleryListView != null) {
            if (galleryListView.onBackPressed()) {
                return false;
            }
            animateGalleryListView(false);
            lastGallerySelectedAlbum = null;
            return false;
        } else if (currentPage == PAGE_CAMERA && collageLayoutView.hasContent()) {
            collageLayoutView.clear(true);
            updateActionBarButtons(true);
            return false;
        } else {
            close(true);
            return true;
        }
    }

    private Runnable afterPlayerAwait;
    private boolean previewAlreadySet;
    public void navigateToPreviewWithPlayerAwait(Runnable open, long seekTo) {
        navigateToPreviewWithPlayerAwait(open, seekTo, 800);
    }
    public void navigateToPreviewWithPlayerAwait(Runnable open, long seekTo, long ms) {
        if (awaitingPlayer) {
            return;
        }
        if (afterPlayerAwait != null) {
            AndroidUtilities.cancelRunOnUIThread(afterPlayerAwait);
        }
        previewAlreadySet = true;
        awaitingPlayer = true;
        afterPlayerAwait = () -> {
            animateGalleryListView(false);
            AndroidUtilities.cancelRunOnUIThread(afterPlayerAwait);
            afterPlayerAwait = null;
            awaitingPlayer = false;
            open.run();
        };
        AndroidUtilities.runOnUIThread(afterPlayerAwait, ms);
    }

    private AnimatorSet pageAnimator;
    public void navigateTo(int page, boolean animated) {
        if (page == currentPage) {
            return;
        }

        final int oldPage = currentPage;
        currentPage = page;

        if (pageAnimator != null) {
            pageAnimator.cancel();
        }

        onNavigateStart(oldPage, page);
        showVideoTimer(page == PAGE_CAMERA && isVideo && !collageListView.isVisible() && !inCheck(), animated);
        setActionBarButtonVisible(backButton, !collageListView.isVisible(), animated);
        setActionBarButtonVisible(flashButton, page == PAGE_CAMERA && !collageListView.isVisible() && flashButtonMode != null && !inCheck(), animated);
        setActionBarButtonVisible(dualButton, page == PAGE_CAMERA && cameraView != null && cameraView.dualAvailable() && !collageListView.isVisible() && !collageLayoutView.hasLayout(), true);
        setActionBarButtonVisible(collageButton, page == PAGE_CAMERA && !collageListView.isVisible(), animated);
        updateActionBarButtons(animated);
        if (animated) {
            pageAnimator = new AnimatorSet();

            ArrayList<Animator> animators = new ArrayList<>();

            if (cameraView != null) {
                animators.add(ObjectAnimator.ofFloat(cameraView, View.ALPHA, page == PAGE_CAMERA ? 1 : 0));
            }
            cameraViewThumb.setVisibility(View.VISIBLE);
            animators.add(ObjectAnimator.ofFloat(cameraViewThumb, View.ALPHA, page == PAGE_CAMERA ? 1 : 0));
            animators.add(ObjectAnimator.ofFloat(collageLayoutView, View.ALPHA, page == PAGE_CAMERA || page == PAGE_PREVIEW && collageLayoutView.hasLayout() ? 1 : 0));

            animators.add(ObjectAnimator.ofFloat(recordControl, View.ALPHA, page == PAGE_CAMERA ? 1 : 0));
//            animators.add(ObjectAnimator.ofFloat(flashButton, View.ALPHA, page == PAGE_CAMERA ? 1 : 0));
//            animators.add(ObjectAnimator.ofFloat(dualButton, View.ALPHA, page == PAGE_CAMERA && cameraView != null && cameraView.dualAvailable() ? 1 : 0));
            animators.add(ObjectAnimator.ofFloat(recordControl, View.TRANSLATION_Y, page == PAGE_CAMERA ? 0 : dp(24)));
            animators.add(ObjectAnimator.ofFloat(modeSwitcherView, View.ALPHA, page == PAGE_CAMERA && !inCheck() ? 1 : 0));
            animators.add(ObjectAnimator.ofFloat(modeSwitcherView, View.TRANSLATION_Y, page == PAGE_CAMERA && !inCheck() ? 0 : dp(24)));
//            backButton.setVisibility(View.VISIBLE);
//            animators.add(ObjectAnimator.ofFloat(backButton, View.ALPHA, 1));
            animators.add(ObjectAnimator.ofFloat(hintTextView, View.ALPHA, page == PAGE_CAMERA && animatedRecording && !inCheck() ? 1 : 0));
            animators.add(ObjectAnimator.ofFloat(collageHintTextView, View.ALPHA, page == PAGE_CAMERA && !animatedRecording && inCheck() ? 0.6f : 0));
            animators.add(ObjectAnimator.ofFloat(titleTextView, View.ALPHA, page == PAGE_PREVIEW || page == PAGE_COVER ? 1f : 0));
//            animators.add(ObjectAnimator.ofFloat(privacySelector, View.ALPHA, page == PAGE_PREVIEW ? 1f : 0));

            animators.add(ObjectAnimator.ofFloat(zoomControlView, View.ALPHA, 0));

            pageAnimator.playTogether(animators);
            pageAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    onNavigateEnd(oldPage, page);
                }
            });
            pageAnimator.setDuration(460);
            pageAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            pageAnimator.start();
        } else {
            if (cameraView != null) {
                cameraView.setAlpha(page == PAGE_CAMERA ? 1 : 0);
            }
            cameraViewThumb.setAlpha(page == PAGE_CAMERA ? 1f : 0);
            cameraViewThumb.setVisibility(page == PAGE_CAMERA ? View.VISIBLE : View.GONE);
            collageLayoutView.setAlpha(page == PAGE_CAMERA || page == PAGE_PREVIEW && collageLayoutView.hasLayout() ? 1 : 0);
            recordControl.setAlpha(page == PAGE_CAMERA ? 1f : 0);
            recordControl.setTranslationY(page == PAGE_CAMERA ? 0 : dp(16));
            modeSwitcherView.setAlpha(page == PAGE_CAMERA && !inCheck() ? 1f : 0);
            modeSwitcherView.setTranslationY(page == PAGE_CAMERA && !inCheck() ? 0 : dp(16));
            hintTextView.setAlpha(page == PAGE_CAMERA && animatedRecording && !inCheck() ? 1f : 0);
            collageHintTextView.setAlpha(page == PAGE_CAMERA && !animatedRecording && inCheck() ? 0.6f : 0);
            titleTextView.setAlpha(page == PAGE_PREVIEW || page == PAGE_COVER ? 1f : 0f);
            onNavigateEnd(oldPage, page);
        }
    }

    private ValueAnimator containerViewBackAnimator;
    private boolean applyContainerViewTranslation2 = true;
    private void animateContainerBack() {
        if (containerViewBackAnimator != null) {
            containerViewBackAnimator.cancel();
            containerViewBackAnimator = null;
        }
        applyContainerViewTranslation2 = false;
        float y1 = containerView.getTranslationY1(), y2 = containerView.getTranslationY2(), a = containerView.getAlpha();
        containerViewBackAnimator = ValueAnimator.ofFloat(1, 0);
        containerViewBackAnimator.addUpdateListener(anm -> {
            final float t = (float) anm.getAnimatedValue();
            containerView.setTranslationY(y1 * t);
            containerView.setTranslationY2(y2 * t);
//            containerView.setAlpha(AndroidUtilities.lerp(a, 1f, t));
        });
        containerViewBackAnimator.setDuration(340);
        containerViewBackAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        containerViewBackAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                containerViewBackAnimator = null;
                containerView.setTranslationY(0);
                containerView.setTranslationY2(0);
            }
        });
        containerViewBackAnimator.start();
    }

    private Parcelable lastGalleryScrollPosition;
    private MediaController.AlbumEntry lastGallerySelectedAlbum;

    private void createGalleryListView() {
        createGalleryListView(false);
    }

    private void destroyGalleryListView() {
        if (galleryListView == null) {
            return;
        }
        windowView.removeView(galleryListView);
        galleryListView = null;
        if (galleryOpenCloseAnimator != null) {
            galleryOpenCloseAnimator.cancel();
            galleryOpenCloseAnimator = null;
        }
        if (galleryOpenCloseSpringAnimator != null) {
            galleryOpenCloseSpringAnimator.cancel();
            galleryOpenCloseSpringAnimator = null;
        }
        galleryListViewOpening = null;
    }

    private void createGalleryListView(boolean forAddingPart) {
        if (galleryListView != null || getContext() == null) {
            return;
        }

        galleryListView = new GalleryListView(UserConfig.selectedAccount, getContext(), resourcesProvider, lastGallerySelectedAlbum, forAddingPart) {
            @Override
            public void setTranslationY(float translationY) {
                super.setTranslationY(translationY);
                if (applyContainerViewTranslation2) {
                    final float amplitude = windowView.getMeasuredHeight() - galleryListView.top();
                    float t = Utilities.clamp(1f - translationY / amplitude, 1, 0);
                    containerView.setTranslationY2(t * dp(-32));
                    containerView.setAlpha(1 - .6f * t);
                    actionBarContainer.setAlpha(1f - t);
                }
            }

            @Override
            public void firstLayout() {
                galleryListView.setTranslationY(windowView.getMeasuredHeight() - galleryListView.top());
                if (galleryLayouted != null) {
                    galleryLayouted.run();
                    galleryLayouted = null;
                }
            }

            @Override
            protected void onFullScreen(boolean isFullscreen) {
                if (currentPage == PAGE_CAMERA && isFullscreen) {
                    AndroidUtilities.runOnUIThread(() -> {
                        destroyCameraView(true);
                        cameraViewThumb.setImageDrawable(getCameraThumb());
                    });
                }
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (ev.getAction() == MotionEvent.ACTION_DOWN && ev.getY() < top()) {
                    galleryClosing = true;
                    animateGalleryListView(false);
                    return true;
                }
                return super.dispatchTouchEvent(ev);
            }
        };
        galleryListView.allowSearch(false);
        galleryListView.setOnBackClickListener(() -> {
            animateGalleryListView(false);
            lastGallerySelectedAlbum = null;
        });
        galleryListView.setOnSelectListener((entry, blurredBitmap) -> {
            if (entry == null || galleryListViewOpening != null || scrollingY || !isGalleryOpen()) {
                return;
            }


            showVideoTimer(false, true);
            modeSwitcherView.switchMode(isVideo);
            recordControl.startAsVideo(isVideo);

            animateGalleryListView(false);
            if (entry instanceof MediaController.PhotoEntry) {
                MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) entry;
                isVideo = photoEntry.isVideo;

                fromGallery = true;

                if (collageLayoutView.hasLayout()) {
                    // TODO CollageLayout
//                    outputFile = null;
//                    storyEntry.videoVolume = 1.0f;
//                    if (collageLayoutView.push(storyEntry)) {
//                        outputEntry = StoryEntry.asCollage(collageLayoutView.getLayout(), collageLayoutView.getContent());
////                            StoryPrivacySelector.applySaved(currentAccount, outputEntry);
////                            navigateTo(PAGE_PREVIEW, true);
//                    }
//                    updateActionBarButtons(true);
                } else {
                    innerHook.afterSelectPhotos(photoEntry);
                }
            } else {
                return;
            }

            if (galleryListView != null) {
                lastGalleryScrollPosition = galleryListView.layoutManager.onSaveInstanceState();
                lastGallerySelectedAlbum = galleryListView.getSelectedAlbum();
            }
        });
        if (lastGalleryScrollPosition != null) {
            galleryListView.layoutManager.onRestoreInstanceState(lastGalleryScrollPosition);
        }
        windowView.addView(galleryListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
    }

    private boolean isGalleryOpen() {
        return !scrollingY && galleryListView != null && galleryListView.getTranslationY() < (windowView.getMeasuredHeight() - (int) (AndroidUtilities.displaySize.y * 0.35f) - (AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight()));
    }

    private ValueAnimator galleryOpenCloseAnimator;
    private SpringAnimation galleryOpenCloseSpringAnimator;
    private Boolean galleryListViewOpening;
    private Runnable galleryLayouted;

    private void animateGalleryListView(boolean open) {
        wasGalleryOpen = open;
        if (galleryListViewOpening != null && galleryListViewOpening == open) {
            return;
        }

        if (galleryListView == null) {
            if (open) {
                createGalleryListView();
            }
            if (galleryListView == null) {
                return;
            }
        }

        if (galleryListView.firstLayout) {
            galleryLayouted = () -> animateGalleryListView(open);
            return;
        }

        if (galleryOpenCloseAnimator != null) {
            galleryOpenCloseAnimator.cancel();
            galleryOpenCloseAnimator = null;
        }
        if (galleryOpenCloseSpringAnimator != null) {
            galleryOpenCloseSpringAnimator.cancel();
            galleryOpenCloseSpringAnimator = null;
        }

        if (galleryListView == null) {
            if (open) {
                createGalleryListView();
            }
            if (galleryListView == null) {
                return;
            }
        }
        if (galleryListView != null) {
            galleryListView.ignoreScroll = false;
        }

        if (open && draftSavedHint != null) {
            draftSavedHint.hide(true);
        }

        galleryListViewOpening = open;

        float from = galleryListView.getTranslationY();
        float to = open ? 0 : windowView.getHeight() - galleryListView.top() + AndroidUtilities.navigationBarHeight * 2.5f;
        float fulldist = Math.max(1, windowView.getHeight());

        galleryListView.ignoreScroll = !open;

        applyContainerViewTranslation2 = containerViewBackAnimator == null;
        if (open) {
            galleryOpenCloseSpringAnimator = new SpringAnimation(galleryListView, DynamicAnimation.TRANSLATION_Y, to);
            galleryOpenCloseSpringAnimator.getSpring().setDampingRatio(0.75f);
            galleryOpenCloseSpringAnimator.getSpring().setStiffness(350.0f);
            galleryOpenCloseSpringAnimator.addEndListener((a, canceled, c, d) -> {
                if (canceled) {
                    return;
                }
                galleryListView.setTranslationY(to);
                galleryListView.ignoreScroll = false;
                galleryOpenCloseSpringAnimator = null;
                galleryListViewOpening = null;
            });
            galleryOpenCloseSpringAnimator.start();
        } else {
            galleryOpenCloseAnimator = ValueAnimator.ofFloat(from, to);
            galleryOpenCloseAnimator.addUpdateListener(anm -> {
                galleryListView.setTranslationY((float) anm.getAnimatedValue());
            });
            galleryOpenCloseAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    windowView.removeView(galleryListView);
                    galleryListView = null;
                    galleryOpenCloseAnimator = null;
                    galleryListViewOpening = null;
                }
            });
            galleryOpenCloseAnimator.setDuration(450L);
            galleryOpenCloseAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            galleryOpenCloseAnimator.start();
        }

        if (!open && !awaitingPlayer) {
            lastGalleryScrollPosition = null;
        }

        if (!open && currentPage == PAGE_CAMERA && !noCameraPermission) {
            createCameraView();
        }
    }

    private void onNavigateStart(int fromPage, int toPage) {
        if (toPage == PAGE_CAMERA) {
            requestCameraPermission(false);
            recordControl.setVisibility(View.VISIBLE);
            if (recordControl != null) {
                recordControl.stopRecordingLoading(false);
            }
            modeSwitcherView.setVisibility(View.VISIBLE);
            zoomControlView.setVisibility(View.VISIBLE);
            zoomControlView.setAlpha(0);
            videoTimerView.setDuration(0, true);
            if (collageLayoutView != null) {
                collageLayoutView.clear(true);
                recordControl.setCollageProgress(0.0f, false);
            }
        }
        if (fromPage == PAGE_CAMERA) {
            setCameraFlashModeIcon(null, true);
            saveLastCameraBitmap(() -> cameraViewThumb.setImageDrawable(getCameraThumb()));
            if (draftSavedHint != null) {
                draftSavedHint.setVisibility(View.GONE);
            }
            cameraHint.hide();
            if (dualHint != null) {
                dualHint.hide();
            }
        }
        cameraViewThumb.setClickable(false);
        if (savedDualHint != null) {
            savedDualHint.hide();
        }
        Bulletin.hideVisible();
        if (removeCollageHint != null) {
            removeCollageHint.hide();
        }
        collageLayoutView.setPreview(toPage == PAGE_PREVIEW && collageLayoutView.hasLayout());
    }

    private void onNavigateEnd(int fromPage, int toPage) {
        if (fromPage == PAGE_CAMERA) {
            destroyCameraView(false);
            recordControl.setVisibility(View.GONE);
            zoomControlView.setVisibility(View.GONE);
            modeSwitcherView.setVisibility(View.GONE);
//            dualButton.setVisibility(View.GONE);
            animateRecording(false, false);
            setAwakeLock(false);
        }
        cameraViewThumb.setClickable(toPage == PAGE_CAMERA);



        if (toPage == PAGE_CAMERA && showSavedDraftHint) {
            getDraftSavedHint().setVisibility(View.VISIBLE);
            getDraftSavedHint().show();
            recordControl.updateGalleryImage();
        }
        showSavedDraftHint = false;
//        if (toPage == PAGE_PREVIEW && !privacySelectorHintOpened) {
//            privacySelectorHint.show(false);
//            privacySelectorHintOpened = true;
//        }
    }



//    private Matrix photoFilterStartMatrix, photoFilterEndMatrix;


    private boolean noCameraPermission;

    @SuppressLint("ClickableViewAccessibility")
    private void createCameraView() {
        if (cameraView != null || getContext() == null) {
            return;
        }
        cameraView = new DualCameraView(getContext(), getCameraFace(), false) {
            @Override
            public void onEntityDraggedTop(boolean value) {

            }

            @Override
            public void onEntityDraggedBottom(boolean value) {

            }

            @Override
            public void toggleDual() {
                super.toggleDual();
                dualButton.setValue(isDual());
//                recordControl.setDual(isDual());
                setCameraFlashModeIcon(getCurrentFlashMode(), true);
            }

            @Override
            protected void onSavedDualCameraSuccess() {
                if (MessagesController.getGlobalMainSettings().getInt("storysvddualhint", 0) < 2) {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (takingVideo || takingPhoto || cameraView == null || currentPage != PAGE_CAMERA) {
                            return;
                        }
                        if (savedDualHint != null) {
                            CharSequence text = isFrontface() ? getString(R.string.StoryCameraSavedDualBackHint) : getString(R.string.StoryCameraSavedDualFrontHint);
                            savedDualHint.setMaxWidthPx(HintView2.cutInFancyHalf(text, savedDualHint.getTextPaint()));
                            savedDualHint.setText(text);
                            savedDualHint.show();
                            MessagesController.getGlobalMainSettings().edit().putInt("storysvddualhint", MessagesController.getGlobalMainSettings().getInt("storysvddualhint", 0) + 1).apply();
                        }
                    }, 340);
                }
                dualButton.setValue(isDual());
            }

            @Override
            protected void receivedAmplitude(double amplitude) {
                if (recordControl != null) {
                    recordControl.setAmplitude(Utilities.clamp((float) (amplitude / WaveDrawable.MAX_AMPLITUDE), 1, 0), true);
                }
            }
        };
        if (recordControl != null) {
            recordControl.setAmplitude(0, false);
        }
        cameraView.recordHevc = !collageLayoutView.hasLayout();
        cameraView.setThumbDrawable(getCameraThumb());
        cameraView.initTexture();
        cameraView.setDelegate(() -> {
            String currentFlashMode = getCurrentFlashMode();
            if (TextUtils.equals(currentFlashMode, getNextFlashMode())) {
                currentFlashMode = null;
            }
            setCameraFlashModeIcon(currentPage == PAGE_CAMERA ? currentFlashMode : null, true);
            if (zoomControlView != null) {
                zoomControlView.setZoom(cameraZoom = 0, false);
            }
            updateActionBarButtons(true);
        });
        setActionBarButtonVisible(dualButton, cameraView.dualAvailable() && currentPage == PAGE_CAMERA, true);
        collageButton.setTranslationX(cameraView.dualAvailable() ? 0 : dp(46));
//        collageLayoutView.getLast().addView(cameraView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        collageLayoutView.setCameraView(cameraView);
        if (MessagesController.getGlobalMainSettings().getInt("storyhint2", 0) < 1) {
            cameraHint.show();
            MessagesController.getGlobalMainSettings().edit().putInt("storyhint2", MessagesController.getGlobalMainSettings().getInt("storyhint2", 0) + 1).apply();
        } else if (!cameraView.isSavedDual() && cameraView.dualAvailable() && MessagesController.getGlobalMainSettings().getInt("storydualhint", 0) < 2) {
            dualHint.show();
        }
    }

    private int frontfaceFlashMode = -1;
    private ArrayList<String> frontfaceFlashModes;
    private void checkFrontfaceFlashModes() {
        if (frontfaceFlashMode < 0) {
            frontfaceFlashMode = MessagesController.getGlobalMainSettings().getInt("frontflash", 1);
            frontfaceFlashModes = new ArrayList<>();
            frontfaceFlashModes.add(Camera.Parameters.FLASH_MODE_OFF);
            frontfaceFlashModes.add(Camera.Parameters.FLASH_MODE_AUTO);
            frontfaceFlashModes.add(Camera.Parameters.FLASH_MODE_ON);

            flashViews.setWarmth(MessagesController.getGlobalMainSettings().getFloat("frontflash_warmth", .9f));
            flashViews.setIntensity(MessagesController.getGlobalMainSettings().getFloat("frontflash_intensity", 1));
        }
    }
    private void saveFrontFaceFlashMode() {
        if (frontfaceFlashMode >= 0) {
            MessagesController.getGlobalMainSettings().edit()
                .putFloat("frontflash_warmth", flashViews.warmth)
                .putFloat("frontflash_intensity", flashViews.intensity)
                .apply();
        }
    }

    private String getCurrentFlashMode() {
        if (cameraView == null || cameraView.getCameraSession() == null) {
            return null;
        }
        if (cameraView.isFrontface() && !cameraView.getCameraSession().hasFlashModes()) {
            checkFrontfaceFlashModes();
            return frontfaceFlashModes.get(frontfaceFlashMode);
        }
        return cameraView.getCameraSession().getCurrentFlashMode();
    }

    private String getNextFlashMode() {
        if (cameraView == null || cameraView.getCameraSession() == null) {
            return null;
        }
        if (cameraView.isFrontface() && !cameraView.getCameraSession().hasFlashModes()) {
            checkFrontfaceFlashModes();
            return frontfaceFlashModes.get(frontfaceFlashMode + 1 >= frontfaceFlashModes.size() ? 0 : frontfaceFlashMode + 1);
        }
        return cameraView.getCameraSession().getNextFlashMode();
    }

    private void setCurrentFlashMode(String mode) {
        if (cameraView == null || cameraView.getCameraSession() == null) {
            return;
        }
        if (cameraView.isFrontface() && !cameraView.getCameraSession().hasFlashModes()) {
            int index = frontfaceFlashModes.indexOf(mode);
            if (index >= 0) {
                frontfaceFlashMode = index;
                MessagesController.getGlobalMainSettings().edit().putInt("frontflash", frontfaceFlashMode).apply();
            }
            return;
        }
        cameraView.getCameraSession().setCurrentFlashMode(mode);
    }


    private Drawable getCameraThumb() {
        Bitmap bitmap = null;
        try {
            File file = new File(ApplicationLoader.getFilesDirFixed(), "cthumb.jpg");
            bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        } catch (Throwable ignore) {}
        if (bitmap != null) {
            return new BitmapDrawable(bitmap);
        } else {
            return getContext().getResources().getDrawable(R.drawable.icplaceholder);
        }
    }

    private void saveLastCameraBitmap(Runnable whenDone) {
        if (cameraView == null || cameraView.getTextureView() == null) {
            return;
        }
        try {
            TextureView textureView = cameraView.getTextureView();
            final Bitmap bitmap = textureView.getBitmap();
            Utilities.themeQueue.postRunnable(() -> {
                try {
                    if (bitmap != null) {
                        Bitmap newBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), cameraView.getMatrix(), true);
                        bitmap.recycle();
                        Bitmap bitmap2 = newBitmap;
                        Bitmap lastBitmap = Bitmap.createScaledBitmap(bitmap2, 80, (int) (bitmap2.getHeight() / (bitmap2.getWidth() / 80.0f)), true);
                        if (lastBitmap != null) {
                            if (lastBitmap != bitmap2) {
                                bitmap2.recycle();
                            }
                            Utilities.blurBitmap(lastBitmap, 7, 1, lastBitmap.getWidth(), lastBitmap.getHeight(), lastBitmap.getRowBytes());
                            File file = new File(ApplicationLoader.getFilesDirFixed(), "cthumb.jpg");
                            FileOutputStream stream = new FileOutputStream(file);
                            lastBitmap.compress(Bitmap.CompressFormat.JPEG, 87, stream);
                            lastBitmap.recycle();
                            stream.close();
                        }
                    }
                } catch (Throwable ignore) {

                } finally {
                    AndroidUtilities.runOnUIThread(whenDone);
                }
            });
        } catch (Throwable ignore) {}
    }



    private void destroyCameraView(boolean waitForThumb) {
        if (cameraView != null) {
            if (waitForThumb) {
                saveLastCameraBitmap(() -> {
                    cameraViewThumb.setImageDrawable(getCameraThumb());
                    if (cameraView != null) {
                        cameraView.destroy(true, null);
                        AndroidUtilities.removeFromParent(cameraView);
                        if (collageLayoutView != null) {
                            collageLayoutView.setCameraView(null);
                        }
                        cameraView = null;
                    }
                });
            } else {
                saveLastCameraBitmap(() -> {
                    cameraViewThumb.setImageDrawable(getCameraThumb());
                });
                cameraView.destroy(true, null);
                AndroidUtilities.removeFromParent(cameraView);
                if (collageLayoutView != null) {
                    collageLayoutView.setCameraView(null);
                }
                cameraView = null;
            }
        }
    }



    private Touchable previewTouchable;
    private boolean requestedCameraPermission;

    private void requestCameraPermission(boolean force) {
        if (requestedCameraPermission && !force) {
            return;
        }
        noCameraPermission = false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && activity != null) {
            noCameraPermission = activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED;
            if (noCameraPermission) {
                Drawable iconDrawable = getContext().getResources().getDrawable(R.drawable.story_camera).mutate();
                iconDrawable.setColorFilter(new PorterDuffColorFilter(0x3dffffff, PorterDuff.Mode.MULTIPLY));
                CombinedDrawable drawable = new CombinedDrawable(new ColorDrawable(0xff222222), iconDrawable);
                drawable.setIconSize(dp(64), dp(64));
                cameraViewThumb.setImageDrawable(drawable);
                if (activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                    new AlertDialog.Builder(getContext(), resourcesProvider)
                        .setTopAnimation(R.raw.permission_request_camera, AlertsCreator.PERMISSIONS_REQUEST_TOP_ICON_SIZE, false, Theme.getColor(Theme.key_dialogTopBackground))
                        .setMessage(AndroidUtilities.replaceTags(getString(R.string.PermissionNoCameraWithHint)))
                        .setPositiveButton(getString(R.string.PermissionOpenSettings), (dialogInterface, i) -> {
                            try {
                                Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                                activity.startActivity(intent);
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        })
                        .setNegativeButton(getString(R.string.ContactsPermissionAlertNotNow), null)
                        .create()
                        .show();
                    return;
                }
                activity.requestPermissions(new String[]{Manifest.permission.CAMERA}, 111);
                requestedCameraPermission = true;
            }
        }

        if (!noCameraPermission) {
            if (CameraController.getInstance().isCameraInitied()) {
                createCameraView();
            } else {
                CameraController.getInstance().initCamera(this::createCameraView);
            }
        }
    }

    private boolean requestGalleryPermission() {
        if (activity != null) {
            boolean noGalleryPermission = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                noGalleryPermission = (
                    activity.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED ||
                    activity.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED
                );
                if (noGalleryPermission) {
                    activity.requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO}, 114);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                noGalleryPermission = activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED;
                if (noGalleryPermission) {
                    activity.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 114);
                }
            }
            return !noGalleryPermission;
        }
        return true;
    }

    private boolean requestAudioPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && activity != null) {
            boolean granted = activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
            if (!granted) {
                activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 112);
                return false;
            }
        }
        return true;
    }

    public void onResume() {
        onResumeInternal();
    }

    private Runnable whenOpenDone;
    private void onResumeInternal() {
        if (currentPage == PAGE_CAMERA) {
//            requestedCameraPermission = false;
            if (openCloseAnimator != null && openCloseAnimator.isRunning()) {
                whenOpenDone = () -> requestCameraPermission(false);
            } else {
                requestCameraPermission(false);
            }
        }
        if (recordControl != null) {
            recordControl.updateGalleryImage();
        }
    }

    public void onPause() {
        onPauseInternal();
    }
    private void onPauseInternal() {
        destroyCameraView(false);
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
       onRequestPermissionsResultInternal(requestCode, permissions, grantResults);
    }

    private Runnable audioGrantedCallback;
    private void onRequestPermissionsResultInternal(int requestCode, String[] permissions, int[] grantResults) {
        final boolean granted = grantResults != null && grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (requestCode == 111) {
            noCameraPermission = !granted;
            if (granted && currentPage == PAGE_CAMERA) {
                cameraViewThumb.setImageDrawable(null);
                if (CameraController.getInstance().isCameraInitied()) {
                    createCameraView();
                } else {
                    CameraController.getInstance().initCamera(this::createCameraView);
                }
            }
        } else if (requestCode == 114) {
            if (granted) {
                MediaController.loadGalleryPhotosAlbums(0);
                animateGalleryListView(true);
            } else {
                new AlertDialog.Builder(getContext(), resourcesProvider)
                    .setTopAnimation(R.raw.permission_request_folder, AlertsCreator.PERMISSIONS_REQUEST_TOP_ICON_SIZE, false, Theme.getColor(Theme.key_dialogTopBackground))
                    .setMessage(AndroidUtilities.replaceTags(getString(R.string.PermissionStorageWithHint)))
                    .setPositiveButton(getString(R.string.PermissionOpenSettings), (dialogInterface, i) -> {
                        try {
                            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                            activity.startActivity(intent);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    })
                    .setNegativeButton(getString(R.string.ContactsPermissionAlertNotNow), null)
                    .create()
                    .show();
            }
        } else if (requestCode == 112) {
            if (!granted) {
                new AlertDialog.Builder(getContext(), resourcesProvider)
                    .setTopAnimation(R.raw.permission_request_camera, AlertsCreator.PERMISSIONS_REQUEST_TOP_ICON_SIZE, false, Theme.getColor(Theme.key_dialogTopBackground))
                    .setMessage(AndroidUtilities.replaceTags(getString(R.string.PermissionNoCameraMicVideo)))
                    .setPositiveButton(getString(R.string.PermissionOpenSettings), (dialogInterface, i) -> {
                        try {
                            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                            activity.startActivity(intent);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    })
                    .setNegativeButton(getString(R.string.ContactsPermissionAlertNotNow), null)
                    .create()
                    .show();
            }
        } else if (requestCode == 115) {
            if (!granted) {
                new AlertDialog.Builder(getContext(), resourcesProvider)
                    .setTopAnimation(R.raw.permission_request_folder, AlertsCreator.PERMISSIONS_REQUEST_TOP_ICON_SIZE, false, Theme.getColor(Theme.key_dialogTopBackground))
                    .setMessage(AndroidUtilities.replaceTags(getString(R.string.PermissionNoAudioStorageStory)))
                    .setPositiveButton(getString(R.string.PermissionOpenSettings), (dialogInterface, i) -> {
                        try {
                            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                            activity.startActivity(intent);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    })
                    .setNegativeButton(getString(R.string.ContactsPermissionAlertNotNow), null)
                    .create()
                    .show();
            }
            if (granted && audioGrantedCallback != null) {
                audioGrantedCallback.run();
            }
            audioGrantedCallback = null;
        }
    }

    private void saveCameraFace(boolean frontface) {
        MessagesController.getGlobalMainSettings().edit().putBoolean("stories_camera", frontface).apply();
    }

    private boolean getCameraFace() {
        return MessagesController.getGlobalMainSettings().getBoolean("stories_camera", false);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.albumsDidLoad) {
            if (recordControl != null) {
                recordControl.updateGalleryImage();
            }
            if (lastGallerySelectedAlbum != null && MediaController.allMediaAlbums != null) {
                for (int a = 0; a < MediaController.allMediaAlbums.size(); a++) {
                    MediaController.AlbumEntry entry = MediaController.allMediaAlbums.get(a);
                    if (entry.bucketId == lastGallerySelectedAlbum.bucketId && entry.videoOnly == lastGallerySelectedAlbum.videoOnly) {
                        lastGallerySelectedAlbum = entry;
                        break;
                    }
                }
            }
        }
    }

    public void addNotificationObservers() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.albumsDidLoad);
        NotificationCenter.getInstance(UserConfig.selectedAccount).addObserver(this, NotificationCenter.storiesDraftsUpdated);
        NotificationCenter.getInstance(UserConfig.selectedAccount).addObserver(this, NotificationCenter.storiesLimitUpdate);
    }

    public void removeNotificationObservers() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.albumsDidLoad);
        NotificationCenter.getInstance(UserConfig.selectedAccount).removeObserver(this, NotificationCenter.storiesDraftsUpdated);
        NotificationCenter.getInstance(UserConfig.selectedAccount).removeObserver(this, NotificationCenter.storiesLimitUpdate);
    }


    private boolean isBackgroundVisible;
    private boolean forceBackgroundVisible;
    private void checkBackgroundVisibility() {
        boolean shouldBeVisible = dismissProgress != 0 || openProgress < 1 || forceBackgroundVisible;
        if (shouldBeVisible == isBackgroundVisible) {
            return;
        }
        if (activity instanceof LaunchActivity) {
            LaunchActivity launchActivity = (LaunchActivity) activity;
            launchActivity.drawerLayoutContainer.setAllowDrawContent(shouldBeVisible);
        }
        isBackgroundVisible = shouldBeVisible;
    }

    public interface ClosingViewProvider {
        void preLayout(long dialogId, Runnable runnable);
        SourceView getView(long dialogId);
    }

    public ChatRecorder selectedPeerId(long dialogId) {
        this.selectedDialogId = dialogId;
        return this;
    }

    public ChatRecorder canChangePeer(boolean b) {
        canChangePeer = b;
        return this;
    }

    public static CharSequence cameraBtnSpan(Context context) {
        SpannableString cameraStr = new SpannableString("c");
        Drawable cameraDrawable = context.getResources().getDrawable(R.drawable.story_camera).mutate();
        final int sz = AndroidUtilities.dp(35);
        cameraDrawable.setBounds(-sz / 4, -sz, sz / 4 * 3, 0);
        cameraStr.setSpan(new ImageSpan(cameraDrawable) {
            @Override
            public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
                return super.getSize(paint, text, start, end, fm) / 3 * 2;
            }

            @Override
            public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
                canvas.save();
                canvas.translate(0, (bottom - top) / 2 + dp(1));
                cameraDrawable.setAlpha(paint.getAlpha());
                super.draw(canvas, text, start, end, x, top, y, bottom, paint);
                canvas.restore();
            }
        }, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return cameraStr;
    }

    public ThanosEffect getThanosEffect() {
        if (!ThanosEffect.supports()) {
            return null;
        }
        if (thanosEffect == null) {
            windowView.addView(thanosEffect = new ThanosEffect(getContext(), () -> {
                ThanosEffect thisThanosEffect = thanosEffect;
                if (thisThanosEffect != null) {
                    thanosEffect = null;
                    windowView.removeView(thisThanosEffect);
                }
            }));
        }
        return thanosEffect;
    }

    public void setActionBarButtonVisible(View view, boolean visible, boolean animated) {
        if (view == null) return;
        if (animated) {
            view.setVisibility(View.VISIBLE);
            view.animate()
                .alpha(visible ? 1.0f : 0.0f)
                .setUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(@NonNull ValueAnimator animation) {
                        updateActionBarButtonsOffsets();
                    }
                })
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        updateActionBarButtonsOffsets();
                        if (!visible) {
                            view.setVisibility(View.GONE);
                        }
                    }
                })
                .setDuration(320)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .start();
        } else {
            view.animate().cancel();
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
            view.setAlpha(visible ? 1.0f : 0.0f);
            updateActionBarButtonsOffsets();
        }
    }

    private boolean inCheck() {
        final float collageProgress = collageLayoutView.hasLayout() ? collageLayoutView.getFilledProgress() : 0.0f;
        return !animatedRecording && collageProgress >= 1.0f;
    }

    private void updateActionBarButtons(boolean animated) {
        showVideoTimer(currentPage == PAGE_CAMERA && isVideo && !collageListView.isVisible() && !inCheck(), animated);
        collageButton.setSelected(collageLayoutView.hasLayout());
        setActionBarButtonVisible(backButton, collageListView == null || !collageListView.isVisible(), animated);
        setActionBarButtonVisible(flashButton, !animatedRecording && currentPage == PAGE_CAMERA && flashButtonMode != null && !collageListView.isVisible() && !inCheck(), animated);
        setActionBarButtonVisible(dualButton, !animatedRecording && currentPage == PAGE_CAMERA && cameraView != null && cameraView.dualAvailable() && !collageListView.isVisible() && !collageLayoutView.hasLayout(), animated);
        setActionBarButtonVisible(collageButton, currentPage == PAGE_CAMERA && !collageListView.isVisible(), animated);
        setActionBarButtonVisible(collageRemoveButton, collageListView.isVisible(), animated);
        final float collageProgress = collageLayoutView.hasLayout() ? collageLayoutView.getFilledProgress() : 0.0f;
        recordControl.setCollageProgress(collageProgress, animated);
        removeCollageHint.show(collageListView.isVisible());
        animateRecording(animatedRecording, animated);
    }

    private void updateActionBarButtonsOffsets() {
        float right = 0;
        collageRemoveButton.setTranslationX(-right); right += dp(46) * collageRemoveButton.getAlpha();
        dualButton.setTranslationX(-right);          right += dp(46) * dualButton.getAlpha();
        collageButton.setTranslationX(-right);       right += dp(46) * collageButton.getAlpha();
        flashButton.setTranslationX(-right);         right += dp(46) * flashButton.getAlpha();

        float left = 0;
        backButton.setTranslationX(left); left += dp(46) * backButton.getAlpha();

        collageListView.setBounds(left + dp(8), right + dp(8));
    }

    public void onSend() {
        wasSend = true;
    }

    public void resetViewState() {
        showVideoTimer(false, false);
    }

    interface InnerHook {
        void onOpenDone();
        void onCloseDone();
        void afterTakingPhotos(MediaController.PhotoEntry photoEntry, boolean sameTakingOrientation);
        void afterRecordingVideos(MediaController.PhotoEntry photoEntry);

        boolean beforeRecordingVideos();

        int modifyLastImage();


        ChatAttachAlert provideParentAlert();

        boolean beforeTakingPhotos();

        void afterSelectPhotos(MediaController.PhotoEntry photoEntry);
    }

    public static class GlobalNotifier {
        private static Consumer<PermissionResult> consumer = null;
        private static Runnable onPauseRunnable = null;
        private static Runnable onResumeRunnable = null;

        public static void setObserver(Consumer<PermissionResult> c, Runnable onPauseRunnable, Runnable onResumeRunnable) {
            GlobalNotifier.consumer = c;
            GlobalNotifier.onResumeRunnable = onResumeRunnable;
            GlobalNotifier.onPauseRunnable = onPauseRunnable;
        }

        public static void destroyPermissionCallback() {
            consumer = null;
        }

        public static void destroyOnPauseRunnable() {
            onPauseRunnable = null;
        }

        public static void destroyOnResumeRunnable() {
            onResumeRunnable = null;
        }

        public static void notifyOnPause() {
            if (onPauseRunnable != null) {
                onPauseRunnable.run();
            }
        }

        public static void notifyOnResume() {
            if (onResumeRunnable != null) {
                onResumeRunnable.run();
            }
        }

        public static void notifyPermissionResult(int requestCode, String[] permissions, int[] grantResults) {
            if (consumer != null) {
                PermissionResult permissionResult = new PermissionResult();
                permissionResult.grantResults = grantResults;
                permissionResult.permissions = permissions;
                permissionResult.requestCode = requestCode;
                consumer.accept(permissionResult);
            }
        }
    }

    private static class PermissionResult {
        int requestCode;
        String[] permissions;
        int[] grantResults;
    }
}
