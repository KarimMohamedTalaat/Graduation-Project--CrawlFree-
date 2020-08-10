package com.crawlfree.tf.app.detection;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import org.tensorflow.lite.examples.detection.R;

import java.util.ArrayList;
import java.util.Locale;

public class VoiceActivity extends AppCompatActivity {

    private TextView txvResult;
    private TextToSpeech textToSpeech;
    private TextToSpeech textToSpeechWelcome;
    private String inputVoiceWordFromBlind;
    Intent intentPassBlindInput;

    private String welcomeVoiceMessage =
            /*"Welcome to crawl free, our app will help you finding your attachments around you, " +
                    "it's quite simple" +*/
                            " touch the screen anywhere and tell us what you are looking for,  " +
                    //"and then start moving your phone slowly, " +
                                    " let's start !";

    private ArrayList <String> supportedObjects = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice);

        txvResult = findViewById(R.id.txvResult);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }

    public void getSpeechInput(View view) {

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());

        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, 10);

        } else {
            Toast.makeText(this, "Your Device Don't Support Speech Input", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        boolean sorry = false;

        switch (requestCode) {
            case 10:
                if (resultCode == RESULT_OK && data != null) {
                    ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    txvResult.setText(result.get(0));

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

                    String targetedObjWord = "";
                    inputVoiceWordFromBlind = result.get(0);
                    System.out.println(inputVoiceWordFromBlind);
                    String [] splittedSentece = inputVoiceWordFromBlind.split(" ");
                    for (String split : splittedSentece){
                        System.out.println(split);
                    }
                    for (int word = 0; word < splittedSentece.length; word++){
                        if (supportedObjects.contains(splittedSentece[word])) {
                            targetedObjWord = splittedSentece[word];
                            sorry = false;
                            break;
                        } else {
                            System.out.println("sentence does not contain any supported objects.");
                            sorry = true;
                        }
                    }
                    targetedObjWord.toLowerCase();
                    System.out.println("targeted object: " + targetedObjWord);
                    System.out.println(sorry);
                    // ****
                    if (sorry == false){
                        textToSpeech = new TextToSpeech(VoiceActivity.this, new TextToSpeech.OnInitListener() {
                            @Override
                            public void onInit(int i) {
                                if(i == TextToSpeech.SUCCESS){
                                    int lang = textToSpeech.setLanguage(Locale.ENGLISH);
                                    int speech = textToSpeech.speak(" please start moving your phone around slowly ",
                                            TextToSpeech.QUEUE_ADD,null);
                                    if (speech == TextToSpeech.SUCCESS){
                                        System.out.println("said start moving");
                                    } else {
                                        System.out.println("not said start moving");
                                    }
                                }
                            }
                        });
                        intentPassBlindInput = new Intent(getBaseContext(), DetectorActivity.class);
                        intentPassBlindInput.putExtra("VOICE_ID", targetedObjWord);
                        startActivity(intentPassBlindInput);
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                finish();
                            }
                        }, 3000);
                    } else {
                        System.out.println("sorry is true !!");
                        textToSpeech = new TextToSpeech(VoiceActivity.this, new TextToSpeech.OnInitListener() {
                            @Override
                            public void onInit(int i) {
                                if(i == TextToSpeech.SUCCESS){
                                    int lang = textToSpeech.setLanguage(Locale.ENGLISH);
                                    int speech = textToSpeech.speak(" sorry for that, our app does not support finding this object " +
                                                    "but you can try finding another one.",
                                            TextToSpeech.QUEUE_ADD,null);
                                    if (speech == TextToSpeech.SUCCESS){
                                        System.out.println("said sorry");
                                    } else {
                                        System.out.println("not said sorry");
                                    }
                                }
                            }
                        });
                    }
                }
                break;
        }
    }


    @Override
    public void onBackPressed()
    {
        super.onBackPressed();
    }

    @Override
    protected void onStart() {
        super.onStart();
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                textToSpeechWelcome = new TextToSpeech(VoiceActivity.this, new TextToSpeech.OnInitListener() {
                    @Override
                    public void onInit(int i) {
                        if(i == TextToSpeech.SUCCESS){
                            int lang = textToSpeechWelcome.setLanguage(Locale.ENGLISH);
                            int speech = textToSpeechWelcome.speak(welcomeVoiceMessage,
                                    TextToSpeech.QUEUE_FLUSH,null);
                            if (speech == TextToSpeech.SUCCESS){
                                System.out.println("said welcome");
                                new Handler().postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
                                    }
                                }, 3500);
                            } else {
                                System.out.println("not said welcome");
                            }
                        }
                    }
                });
            }
        }, 250);
    }

}