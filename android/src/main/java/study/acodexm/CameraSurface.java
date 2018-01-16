package study.acodexm;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

import java.io.ByteArrayOutputStream;
import java.util.List;

import study.acodexm.control.AndroidSphereControl;
import study.acodexm.control.CameraControl;
import study.acodexm.control.ViewControl;
import study.acodexm.settings.PictureQuality;
import study.acodexm.settings.SettingsControl;
import study.acodexm.utils.ImageRW;

@SuppressWarnings("deprecation")
public class CameraSurface extends SurfaceView implements SurfaceHolder.Callback, Camera.PictureCallback, CameraControl {
    private static final String TAG = CameraSurface.class.getSimpleName();
    private List<Integer> ids;
    private Camera camera;
    private byte[] mPicture;
    private boolean safeToTakePicture = false;
    private ViewControl mViewControl;
    private SphereControl mSphereControl;
    private SettingsControl mSettingsControl;
    private int currentPictureId;
    private int PHOTO_WIDTH;
    private int PHOTO_HEIGHT;

    public CameraSurface(MainActivity activity, SettingsControl settingsControl) {
        super(activity.getContext());
        mViewControl = activity;
        mSettingsControl = settingsControl;
        getHolder().addCallback(this);
        getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        mSphereControl = new AndroidSphereControl(this);
        //this section sets height and width variables for resizing image for textures on sphere
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager != null) {
            windowManager.getDefaultDisplay().getMetrics(metrics);
            PHOTO_HEIGHT = metrics.heightPixels / 4;
            PHOTO_WIDTH = metrics.widthPixels / 4;
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        camera = Camera.open(0);
        try {
            camera.setPreviewDisplay(holder);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * here are the camera settings such as preview size or picture resolution and quality
     */
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {
        Log.d(TAG, "surfaceChanged called");
        Camera.Parameters myParameters = camera.getParameters();
        Camera.Size myBestSize = getBestPreviewSize(myParameters);
        if (myBestSize != null) {
            myParameters.setPreviewSize(myBestSize.width, myBestSize.height);
            if (mSettingsControl.getPictureQuality() == PictureQuality.LOW) {
                myParameters.set("jpeg-quality", 70);
                myParameters.setPictureFormat(PixelFormat.JPEG);
                myParameters.setPictureSize(1280, 720);
            }
            camera.setParameters(myParameters);
            camera.setDisplayOrientation(0);
            camera.startPreview();
        }
        safeToTakePicture = true;
    }

    /**
     * this method finds the best resolution for preview for current device
     */
    private Camera.Size getBestPreviewSize(Camera.Parameters parameters) {
        Camera.Size bestSize;
        List<Camera.Size> sizeList = parameters.getSupportedPreviewSizes();
        bestSize = sizeList.get(0);
        for (int i = 1; i < sizeList.size(); i++) {
            if ((sizeList.get(i).width * sizeList.get(i).height) >
                    (bestSize.width * bestSize.height)) {
                bestSize = sizeList.get(i);
            }
        }
        return bestSize;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        camera.stopPreview();
        camera.release();
        camera = null;
    }

    /**
     * this method saves taken picture to external storage and sends it to sphere for a texture needs
     */
    @Override
    public void onPictureTaken(final byte[] bytes, Camera camera) {
        long time = System.currentTimeMillis();

        Runnable saveImage = new Runnable() {
            @Override
            public void run() {
                ImageRW.saveImageExternal(bytes, currentPictureId);
            }
        };
        mViewControl.post(saveImage);

        Runnable processTexture = new Runnable() {
            @Override
            public void run() {
                mViewControl.showProcessingDialog();
                mPicture = resizeImage(bytes);
                mSphereControl.setPicture(mPicture);
                mViewControl.hideProcessingDialog();
                mViewControl.updateRender();
                safeToTakePicture = true;
            }
        };
        mViewControl.post(processTexture);

        camera.startPreview();
        Log.d(TAG, "onPictureTaken process time: " + (System.currentTimeMillis() - time));

    }

    /**
     * this method resize image to a given resolution
     */
    private byte[] resizeImage(byte[] bytes) {
        Bitmap original = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        Bitmap resized = Bitmap.createScaledBitmap(original, PHOTO_WIDTH, PHOTO_HEIGHT, false);
        ByteArrayOutputStream blob = new ByteArrayOutputStream();
        resized.compress(Bitmap.CompressFormat.PNG, 0, blob);
        byte[] resizedImg = blob.toByteArray();
        resized.recycle();
        original.recycle();
        return resizedImg;
    }


    @Override
    public void takePicture(int id) {
        if (camera != null && safeToTakePicture) {
            if (ids == null)
                ids = mSphereControl.getIdTable();
            currentPictureId = id;
            mSphereControl.setLastPosition(id);
            safeToTakePicture = false;
            camera.takePicture(null, null, this);
        }
    }

    @Override
    public void startPreview() {
        if (camera != null)
            camera.startPreview();
    }

    @Override
    public void stopPreview() {
        if (camera != null)
            camera.stopPreview();
    }

    @Override
    public CameraSurface getSurface() {
        return this;
    }

    @Override
    public SphereControl getSphereControl() {
        return mSphereControl;
    }

    @Override
    public List<Integer> getIdsTable() {
        return mSphereControl.getTakenPicturesIds();
    }

}
