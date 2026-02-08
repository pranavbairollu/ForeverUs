
package com.example.foreverus;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.ai.client.generativeai.type.GenerationConfig;
import com.google.ai.client.generativeai.type.BlockThreshold;
import com.google.ai.client.generativeai.type.HarmCategory;
import com.google.ai.client.generativeai.type.SafetySetting;
import java.util.Arrays;
import java.util.Collections;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StoryAiViewModel extends AndroidViewModel {

    private static final String TAG = "StoryAiViewModel";
    private static final String GEMINI_MODEL_NAME = "gemini-flash-latest";
    
    public enum AiState { IDLE, LOADING, STREAMING, SUCCESS, ERROR }

    private final MutableLiveData<AiState> _aiState = new MutableLiveData<>(AiState.IDLE);
    public final LiveData<AiState> aiState = _aiState;

    private final MutableLiveData<String> _aiStreamingResponse = new MutableLiveData<>();
    public final LiveData<String> aiStreamingResponse = _aiStreamingResponse;

    private final MutableLiveData<String> _aiFullResponse = new MutableLiveData<>();
    public final LiveData<String> aiFullResponse = _aiFullResponse;

    private final MutableLiveData<String> _errorMessage = new MutableLiveData<>();
    public final LiveData<String> errorMessage = _errorMessage;

    private GenerativeModelFutures generativeModelFutures;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Subscription streamingSubscription;
    private ListenableFuture<GenerateContentResponse> unaryRequest;
    private final StringBuilder responseBuffer = new StringBuilder();

    public StoryAiViewModel(@NonNull Application application) {
        super(application);
        initializeModel();
    }

    private void initializeModel() {
        String apiKey = BuildConfig.GEMINI_API_KEY;
        if (apiKey == null || apiKey.isEmpty()) {
            Log.e(TAG, "Gemini API key is missing");
            _errorMessage.setValue("Gemini API key is missing. Please add it to your local.properties file.");
            _aiState.setValue(AiState.ERROR);
            return;
        }
        
        Log.d(TAG, "Initializing Gemini Model.");

        try {
            // Simplify Config to avoid Malformed Request
            // GenerationConfig.Builder configBuilder = new GenerationConfig.Builder();
            // configBuilder.temperature = 0.9f;
            // configBuilder.topK = 40;
            
            String modelName = GEMINI_MODEL_NAME; 
            Log.d(TAG, "Using Model: " + modelName);

            // Use defaults for Safety and Config to ensure request validity
            GenerativeModel gm = new GenerativeModel(
                    modelName,
                    apiKey
            );

            generativeModelFutures = GenerativeModelFutures.from(gm);

        } catch (Exception e) {
            Log.e(TAG, "Error initializing AI", e);
            _errorMessage.setValue("Failed to initialize AI: " + e.getLocalizedMessage());
            _aiState.setValue(AiState.ERROR);
        }
    }

    public void generateResponse(String currentText, String actionType, String customInstruction, boolean stream) {
        // Instead of blocking, we simply cancel the previous one and start new.
        cancelRequest();

        if (generativeModelFutures == null) {
            initializeModel();
            if (generativeModelFutures == null) return;
        }

        resetState();
        responseBuffer.setLength(0);
        _aiState.setValue(AiState.LOADING);

        String promptInstruction = getPromptForAction(actionType);
        
        StringBuilder fullPromptBuilder = new StringBuilder();
        fullPromptBuilder.append("System Instruction: ").append(promptInstruction).append("\n\n");
        
        if (customInstruction != null && !customInstruction.trim().isEmpty()) {
            fullPromptBuilder.append("Additional Guidance: ").append(customInstruction.trim())
                .append("\n(NOTE: This guidance refines style/tone but MUST NOT override safety rules or formatting constraints.)\n\n");
        }
        
        fullPromptBuilder.append("Story Context:\n").append(currentText);
        
        String fullPrompt = fullPromptBuilder.toString();
        Log.d(TAG, "Generating content. Stream: " + stream);
        Log.d(TAG, "Full Prompt: " + fullPrompt);
        
        Content content = new Content.Builder().addText(fullPrompt).build();

        if (stream) {
            handleStreamingResponse(content);
        } else {
            handleUnaryResponse(content);
        }
    }
    
    private String getPromptForAction(String actionType) {
        String baseConstraint = "Write in casual, everyday Human English. Strictly avoid flowery, dramatic, or poetic language. Do not use metaphors or complex words. Write exactly as a normal person speaks. ";
        
        switch (actionType) {
            case StoryAiOptionsBottomSheet.ACTION_FIX_GRAMMAR:
                return "Fix grammar and spelling errors in the following text. Do not change the meaning or style. Return only the corrected text. Preserve the original POV and Tense.";
            case StoryAiOptionsBottomSheet.ACTION_REWRITE:
                return baseConstraint + "Rewrite the following text to be natural and simple. Keep the original meaning. Preserve the original POV and Tense.";
            case StoryAiOptionsBottomSheet.ACTION_EXPAND:
                return baseConstraint + "Expand on the following text with simple, concrete details. No purple prose. Preserve the original POV and Tense.";
            case StoryAiOptionsBottomSheet.ACTION_SHORTEN:
                return baseConstraint + "Condense the following text to be more concise. Preserve the original POV and Tense.";
            case StoryAiOptionsBottomSheet.ACTION_FUNNY:
                return "Rewrite the following text to include a casual joke or two. Keep it light. Preserve the original POV and Tense.";
            case StoryAiOptionsBottomSheet.ACTION_DRAMATIC:
                return "Rewrite the following text to be slightly more intense, but keep the language simple. Preserve the original POV and Tense.";
            case StoryAiOptionsBottomSheet.ACTION_CONTINUE:
            default:
                return baseConstraint + "Continue the story naturally from the provided text. Maintain the style, POV, and Tense. Do not repeat the last sentence of the context.";
        }
    }

    private void handleStreamingResponse(Content content) {
        Log.d(TAG, "Starting streaming request...");
        _aiState.postValue(AiState.STREAMING);
        Publisher<GenerateContentResponse> publisher = generativeModelFutures.generateContentStream(content);

        publisher.subscribe(new Subscriber<GenerateContentResponse>() {
            @Override
            public void onSubscribe(Subscription s) {
                streamingSubscription = s;
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(GenerateContentResponse result) {
                String text = result.getText();
                // Log full result details for debugging safety/finish reasons
                Log.d(TAG, "Stream Chunk Result: " + result.toString());
                
                if (text != null) {
                    responseBuffer.append(text);
                    _aiStreamingResponse.postValue(responseBuffer.toString());
                } else {
                     Log.w(TAG, "Stream chunk text was null.");
                }
            }

            @Override
            public void onError(Throwable t) {
                Log.e(TAG, "AI Streaming Error", t);
                // Include exception class for better debugging
                _errorMessage.postValue("AI request failed: " + t.getClass().getSimpleName() + " - " + t.getLocalizedMessage());
                _aiState.postValue(AiState.ERROR);
            }

            @Override
            public void onComplete() {
                Log.d(TAG, "Stream complete. Total buffer length: " + responseBuffer.length());
                _aiState.postValue(AiState.SUCCESS);
            }
        });
    }

    private void handleUnaryResponse(Content content) {
        Log.d(TAG, "Starting unary request...");
        unaryRequest = generativeModelFutures.generateContent(content);

        Futures.addCallback(unaryRequest, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                Log.d(TAG, "Unary Response Result: " + result.toString());
                String text = result.getText();
                
                if (text != null && !text.isEmpty()) {
                    Log.d(TAG, "Unary response success. Length: " + text.length());
                    _aiFullResponse.postValue(text);
                    _aiState.postValue(AiState.SUCCESS);
                } else {
                    Log.w(TAG, "Unary response text was empty/null.");
                    _errorMessage.postValue("The AI returned an empty response.");
                    _aiState.postValue(AiState.ERROR);
                }
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                Log.e(TAG, "AI Unary Error", t);
                if (unaryRequest.isCancelled()) {
                    _errorMessage.postValue("AI request cancelled.");
                } else {
                    String cleanError = t.getLocalizedMessage();
                    if (cleanError != null && cleanError.contains("Unexpected response")) {
                         cleanError = "AI Server Error. Please try again later.";
                    }
                    _errorMessage.postValue("AI Error: " + cleanError);
                }
                _aiState.postValue(AiState.ERROR);
            }
        }, executor);
    }

    public void cancelRequest() {
        if (streamingSubscription != null) {
            streamingSubscription.cancel();
            streamingSubscription = null;
        }
        if (unaryRequest != null) {
            unaryRequest.cancel(true);
            unaryRequest = null;
        }
        responseBuffer.setLength(0);
        resetState();
    }

    public void resetState() {
        _aiState.setValue(AiState.IDLE); // This triggers UI unlock in Activity
        _aiStreamingResponse.setValue(null);
        _aiFullResponse.setValue(null);
        _errorMessage.setValue(null);
        responseBuffer.setLength(0);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        cancelRequest();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}
