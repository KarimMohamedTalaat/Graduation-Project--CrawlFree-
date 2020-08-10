/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.crawlfree.tf.app.detection;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.util.Size;
import android.util.TypedValue;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import org.tensorflow.lite.examples.detection.R;
import com.crawlfree.tf.app.detection.customview.OverlayView;
import com.crawlfree.tf.app.detection.customview.OverlayView.DrawCallback;
import com.crawlfree.tf.app.detection.env.BorderedText;
import com.crawlfree.tf.app.detection.env.ImageUtils;
import com.crawlfree.tf.app.detection.env.Logger;
import com.crawlfree.tf.app.detection.tflite.Classifier;
import com.crawlfree.tf.app.detection.tflite.TFLiteObjectDetectionAPIModel;
import com.crawlfree.tf.app.detection.tracking.MultiBoxTracker;

/**
 * An activity that uses a TensorFlowMultiBoxDetector and ObjectTracker to detect and then track
 * objects.
 */
public class DetectorActivity extends CameraActivity implements OnImageAvailableListener {
  private static final Logger LOGGER = new Logger();

  // Configuration values for the prepackaged SSD model.
  private static final int TF_OD_API_INPUT_SIZE = 300;
  private static final boolean TF_OD_API_IS_QUANTIZED = true;
  private static final String TF_OD_API_MODEL_FILE = "detect.tflite";
  private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/labelmap.txt";
  private static final DetectorMode MODE = DetectorMode.TF_OD_API;
  // Minimum detection confidence to track a detection.
  private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.5f;
  private static final boolean MAINTAIN_ASPECT = false;
  private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);
  private static final boolean SAVE_PREVIEW_BITMAP = false;
  private static final float TEXT_SIZE_DIP = 10;
  OverlayView trackingOverlay;
  private Integer sensorOrientation;

  private Classifier detector;

  private long lastProcessingTimeMs;
  private Bitmap rgbFrameBitmap = null;
  private Bitmap croppedBitmap = null;
  private Bitmap cropCopyBitmap = null;

  private boolean computingDetection = false;

  private long timestamp = 0;

  private Matrix frameToCropTransform;
  private Matrix cropToFrameTransform;

  private MultiBoxTracker tracker;

  private BorderedText borderedText;

  private TextToSpeech textToSpeech;

  private ArrayList <String> supportedObjects = new ArrayList<>();

  @Override
  public void onPreviewSizeChosen(final Size size, final int rotation) {
    final float textSizePx =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
    borderedText = new BorderedText(textSizePx);
    borderedText.setTypeface(Typeface.MONOSPACE);

    tracker = new MultiBoxTracker(this);

    int cropSize = TF_OD_API_INPUT_SIZE;

    try {
      detector =
          TFLiteObjectDetectionAPIModel.create(
              getAssets(),
              TF_OD_API_MODEL_FILE,
              TF_OD_API_LABELS_FILE,
              TF_OD_API_INPUT_SIZE,
              TF_OD_API_IS_QUANTIZED);
      cropSize = TF_OD_API_INPUT_SIZE;
    } catch (final IOException e) {
      e.printStackTrace();
      LOGGER.e(e, "Exception initializing classifier!");
      Toast toast =
          Toast.makeText(
              getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
      toast.show();
      finish();
    }

    previewWidth = size.getWidth();
    previewHeight = size.getHeight();

    sensorOrientation = rotation - getScreenOrientation();
    LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

    LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
    rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
    croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);

    frameToCropTransform =
        ImageUtils.getTransformationMatrix(
            previewWidth, previewHeight,
            cropSize, cropSize,
            sensorOrientation, MAINTAIN_ASPECT);

    cropToFrameTransform = new Matrix();
    frameToCropTransform.invert(cropToFrameTransform);

    trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
    trackingOverlay.addCallback(
        new DrawCallback() {
          @Override
          public void drawCallback(final Canvas canvas) {
            tracker.draw(canvas);
            if (isDebug()) {
              tracker.drawDebug(canvas);
            }
          }
        });

    tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);
  }

  @Override
  protected void processImage() {
    ++timestamp;
    final long currTimestamp = timestamp;
    trackingOverlay.postInvalidate();

    // No mutex needed as this method is not reentrant.
    if (computingDetection) {
      readyForNextImage();
      return;
    }
    computingDetection = true;
    LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

    rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

    readyForNextImage();

    final Canvas canvas = new Canvas(croppedBitmap);
    canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
    // For examining the actual TF input.
    if (SAVE_PREVIEW_BITMAP) {
      ImageUtils.saveBitmap(croppedBitmap);
    }

    runInBackground(
        new Runnable() {
          @Override
          public void run() {
            LOGGER.i("Running detection on image " + currTimestamp);
            final long startTime = SystemClock.uptimeMillis();
            final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);
            lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

            cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
            final Canvas canvas = new Canvas(cropCopyBitmap);
            final Paint paint = new Paint();
            paint.setColor(Color.RED);
            paint.setStyle(Style.STROKE);
            paint.setStrokeWidth(2.0f);

            float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
            switch (MODE) {
              case TF_OD_API:
                minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                break;
            }

            final List<Classifier.Recognition> mappedRecognitions = new LinkedList<Classifier.Recognition>();

            supportedObjects.add("handbag");
            supportedObjects.add("umbrella");
            supportedObjects.add("laptop");
            supportedObjects.add("mouse");
            supportedObjects.add("remote");
            supportedObjects.add("keyboard");
            supportedObjects.add("book");
            supportedObjects.add("cup");
            supportedObjects.add("backpack");
            supportedObjects.add("suitcase");
            supportedObjects.add("glass");
            supportedObjects.add("fork");
            supportedObjects.add("knife");
            supportedObjects.add("spoon");
            supportedObjects.add("toothbrush");
            supportedObjects.add("bottle");
            supportedObjects.add("chair");

            final List <Classifier.Recognition> finalFrameScene = results;
            List <Classifier.Recognition> uniqueScene     = detector.recognizeImage(croppedBitmap);
            Classifier.Recognition desiredObject = null;

            textToSpeech = new TextToSpeech(DetectorActivity.this, new TextToSpeech.OnInitListener() {
              @Override
              public void onInit(int i) {
                if(i == TextToSpeech.SUCCESS){
                  int lang = textToSpeech.setLanguage(Locale.ENGLISH);
                }
              }
            });

            String currLabelFromVoice = getIntent().getStringExtra("VOICE_ID");
            currLabelFromVoice.toLowerCase();

            for (final Classifier.Recognition result : results) {
              final RectF location = result.getLocation();
              if (location != null && result.getConfidence() >= minimumConfidence) {
                canvas.drawRect(location, paint);

                String currLabel = result.getTitle();

                for(int i=0; i<supportedObjects.size(); i++){
                  if(currLabel.equals(supportedObjects.get(i)) && currLabelFromVoice.equals(currLabel)){
                    Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                      v.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                      v.vibrate(100);
                    }
                    desiredObject = result;
                    System.out.println("Desired Object: "+desiredObject.getTitle()+", "+desiredObject.getConfidence());
                    int speech = textToSpeech.speak("stop moving, I found your " +currLabelFromVoice+" here, " +
                                    "it's in your walking direction", TextToSpeech.QUEUE_ADD,null);
                    if(speech == textToSpeech.SUCCESS){
                      System.out.println("Results List o/p:");
                      for (int obj = 0; obj < results.size(); obj++){
                        //int speech2 = textToSpeech.speak(results.get(obj).getTitle(), TextToSpeech.QUEUE_FLUSH,null);
                        System.out.println(results.get(obj).getTitle() + ", " + results.get(obj).getConfidence() +
                                ", " + results.get(obj).getLocation());
                      }
                      //textToSpeech.stop();
                      //break;
                      ///*************************
                      //finalFrameScene = results;
                      ///*************************
                      uniqueScene     = results;

                      // getting the targeted object with the highest confidence:
                      ///*************************
                      Classifier.Recognition targetObjectFromLastSceneMaxConfidence = finalFrameScene.get(0);
                      //System.out.println("Final Frame Scene List o/p:");
                      for (int objj = 0; objj < finalFrameScene.size(); objj++){
                        if (finalFrameScene.get(objj).getTitle().equals(currLabelFromVoice)){
                            targetObjectFromLastSceneMaxConfidence = finalFrameScene.get(objj);
                        }

                        if ((targetObjectFromLastSceneMaxConfidence != null) &&
                                (targetObjectFromLastSceneMaxConfidence.getTitle().equals(finalFrameScene.get(objj).getTitle())) &&
                                (targetObjectFromLastSceneMaxConfidence.getConfidence() <= finalFrameScene.get(objj).getConfidence())){
                            targetObjectFromLastSceneMaxConfidence = finalFrameScene.get(objj);
                        }
                      }
                      System.out.println("highest confidence targeted object: " +
                              targetObjectFromLastSceneMaxConfidence.getTitle() + ", " +
                              targetObjectFromLastSceneMaxConfidence.getConfidence());
                      ///*************************

                      System.out.println("Describing the scene:");
                      for (int objj = 0; objj < uniqueScene.size(); objj++){
                          if (uniqueScene.get(objj).getTitle().equals(currLabelFromVoice)){
                              uniqueScene.remove(uniqueScene.get(objj));
                          }
                      }
                      for (int objj2 = 0; objj2 < uniqueScene.size(); objj2++){
                          Classifier.Recognition object = uniqueScene.get(objj2);
                          for (int objj3 = 0; objj3 < uniqueScene.size(); objj3++) {
                              if ( (object.getTitle().equals(uniqueScene.get(objj3).getTitle())
                                      || object.equals(uniqueScene.get(objj3))) &&
                                      object.getConfidence() > uniqueScene.get(objj3).getConfidence()) {
                                  uniqueScene.remove(uniqueScene.get(objj3));
                              }
                          }
                      }

                      System.out.println("Unique List:");
                      for (int objj4 = 0; objj4 < uniqueScene.size(); objj4++) {
                          System.out.println(uniqueScene.get(objj4).getTitle() +
                                          ", " + uniqueScene.get(objj4).getConfidence() +
                                  ", " + uniqueScene.get(objj4).getLocation());
                      }
                      ///*************************

                      Classifier.Recognition neighbouringObject = null;
                      for (int objj5 = 0; objj5 < uniqueScene.size(); objj5++) {
                          if (uniqueScene.get(objj5).getTitle().equals(currLabelFromVoice)) {
                              continue;
                          } else {
                              neighbouringObject = uniqueScene.get(objj5);
                              if (uniqueScene.get(objj5++).getConfidence() > neighbouringObject.getConfidence()) {
                                  neighbouringObject = uniqueScene.get(objj5++);
                              }
                          }
                      }
                      System.out.println("neighbouring object: " + neighbouringObject.getTitle() + ", " +
                              neighbouringObject.getConfidence());


                      ///*************************
                      // conditions on frames intersection ........
                      int speech2 = -5, speech5 = -5;
                      for (int objj6 = 0; objj6 < uniqueScene.size(); objj6++) {
                          if (uniqueScene.get(objj6).getLocation().contains(desiredObject.getLocation())){
                              speech5 = textToSpeech.speak(" It's on the " + uniqueScene.get(objj6).getTitle(),
                                      TextToSpeech.QUEUE_ADD,null);
                              if (speech5 == TextToSpeech.SUCCESS) {
                                  break;
                              }
                          } else {
                              speech2 = textToSpeech.speak(" It's besides the " + neighbouringObject.getTitle(),
                                      TextToSpeech.QUEUE_ADD,null);
                              if (speech2 == TextToSpeech.SUCCESS){
                                  break;
                              }
                          }
                      }

                      /*int speech2 = textToSpeech.speak(" It's besides the " + finalFrameScene.get(2).getTitle(),
                              TextToSpeech.QUEUE_ADD,null);*/

                      if(speech2 == TextToSpeech.SUCCESS || speech5 == TextToSpeech.SUCCESS){
                          int speech3 = textToSpeech.speak("if you want to find another object, you are ready to do it now.",
                                  TextToSpeech.QUEUE_ADD,null);
                          Intent backToVoiceActivity = new Intent(getBaseContext(), VoiceActivity.class);
                          startActivity(backToVoiceActivity);
                          new Handler().postDelayed(new Runnable() {
                              @Override
                              public void run() {
                                  finish();
                              }
                          }, 5000);
                          //finish();
                        return;
                      }
                      //return;
                    }
                  }
                }
                cropToFrameTransform.mapRect(location);
                result.setLocation(location);
                mappedRecognitions.add(result);
              }
            }

            tracker.trackResults(mappedRecognitions, currTimestamp);
            trackingOverlay.postInvalidate();

            computingDetection = false;

            runOnUiThread(
                new Runnable() {
                  @Override
                  public void run() {
                    showFrameInfo(previewWidth + "x" + previewHeight);
                    showCropInfo(cropCopyBitmap.getWidth() + "x" + cropCopyBitmap.getHeight());
                    showInference(lastProcessingTimeMs + "ms");
                  }
                });
          }
        });
  }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        startActivity(new Intent(getBaseContext(), VoiceActivity.class));
        finish();
    }

  @Override
  protected int getLayoutId() {
    return R.layout.camera_connection_fragment_tracking;
  }

  @Override
  protected Size getDesiredPreviewFrameSize() {
    return DESIRED_PREVIEW_SIZE;
  }

  // Which detection model to use: by default uses Tensorflow Object Detection API frozen
  // checkpoints.
  private enum DetectorMode {
    TF_OD_API;
  }

  @Override
  protected void setUseNNAPI(final boolean isChecked) {
    runInBackground(() -> detector.setUseNNAPI(isChecked));
  }

  @Override
  protected void setNumThreads(final int numThreads) {
    runInBackground(() -> detector.setNumThreads(numThreads));
  }
}
