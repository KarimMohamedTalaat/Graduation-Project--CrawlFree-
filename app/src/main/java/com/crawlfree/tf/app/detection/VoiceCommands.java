package com.crawlfree.tf.app.detection;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Button;

import org.tensorflow.lite.examples.detection.R;

import java.util.Locale;

public class VoiceCommands extends AppCompatActivity {

    private Button mTextView;

    private TextToSpeech mTextToSpeech;

    private String mWelcomeVoiceMessage =
            "Welcome to crawl free, our app will help you finding your attachments around you, " +
                    "it's quite simple, just touch the screen anywhere and tell us what are you looking for " +
                    "and then start moving your phone slowly in a circular movement, let's start !";

    private Intent toVoiceActivity;

    private static boolean active = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_commands);

        mTextView = findViewById(R.id.button);

        mTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                System.out.println("clicked");
                mTextToSpeech = new TextToSpeech(VoiceCommands.this, new TextToSpeech.OnInitListener() {
                    @Override
                    public void onInit(int i) {
                        if(i == TextToSpeech.SUCCESS){
                            int mLang = mTextToSpeech.setLanguage(Locale.ENGLISH);
                        }
                    }
                });
                int mSpeech = mTextToSpeech.speak(mWelcomeVoiceMessage, TextToSpeech.QUEUE_FLUSH,null);

                if(mSpeech == TextToSpeech.SUCCESS){
                    System.out.println("voice success");
                    toVoiceActivity = new Intent(getBaseContext(), VoiceActivity.class);
                    startActivity(toVoiceActivity);
                    finish();
                }
            }
        });
    }

    private void mWelcomeVoiceMessageMethod (){
        mTextToSpeech = new TextToSpeech(VoiceCommands.this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int i) {
                if(i == TextToSpeech.SUCCESS){
                    int mLang = mTextToSpeech.setLanguage(Locale.ENGLISH);
                }
            }
        });
        int mSpeech = mTextToSpeech.speak(mWelcomeVoiceMessage, TextToSpeech.QUEUE_FLUSH,null);

        if(mSpeech == TextToSpeech.SUCCESS){
            return;
        }
    }
}
