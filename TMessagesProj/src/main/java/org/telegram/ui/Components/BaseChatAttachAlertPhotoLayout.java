package org.telegram.ui.Components;

import android.content.Context;
import android.content.Intent;
import android.widget.FrameLayout;

import androidx.annotation.Keep;

import org.telegram.messenger.camera.CameraView;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.PhotoAttachCameraCell;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * @author yanghao
 * @Date 2024/12/19
 */
public abstract class BaseChatAttachAlertPhotoLayout extends ChatAttachAlert.AttachAlertLayout {
    public BaseChatAttachAlertPhotoLayout(ChatAttachAlert alert, Context context, Theme.ResourcesProvider resourcesProvider) {
        super(alert, context, resourcesProvider);
    }

    public final static int group = 0;
    public final static int compress = 1;
    public final static int spoiler = 2;
    public final static int open_in = 3;
    public final static int preview_gap = 4;
    public final static int media_gap = 5;
    public final static int preview = 6;
    public final static int caption = 7;
    public final static int stars = 8;

    public RecyclerListView gridView;

    protected CameraView cameraView;
    protected FrameLayout cameraIcon;
    protected PhotoAttachCameraCell cameraCell;
    protected ActionBarMenuSubItem previewItem;

    boolean cameraExpanded;

    public MessagePreviewView.ToggleButton captionItem;

    public abstract void updateAvatarPicker();

    public abstract void showAvatarConstructorFragment(AvatarConstructorPreviewCell view, TLRPC.VideoSize emojiMarkupStrat);

    public abstract long getStarsPrice();

    public abstract void setStarsPrice(long stars);

    public abstract void clearSelectedPhotos();

    public abstract void checkCamera(boolean request);

    public abstract void loadGalleryPhotos();

    public abstract void showCamera();

    public abstract void hideCamera(boolean async);

    public abstract void onActivityResultFragment(int requestCode, Intent data, String currentPicturePath);

    public abstract void closeCamera(boolean animated);

    @Keep
    public abstract void setCameraOpenProgress(float value);

    @Keep
    public abstract float getCameraOpenProgress();

    public abstract HashMap<Object, Object> getSelectedPhotos();

    public abstract ArrayList<Object> getSelectedPhotosOrder();

    public abstract void updateSelected(HashMap<Object, Object> newSelectedPhotos, ArrayList<Object> newPhotosOrder, boolean updateLayout);

    public abstract void checkStorage();

    public abstract boolean captionForAllMedia();

    public abstract void setCheckCameraWhenShown(boolean checkCameraWhenShown);

    public abstract void pauseCamera(boolean pause);

    protected abstract void checkCameraViewPosition();
}
