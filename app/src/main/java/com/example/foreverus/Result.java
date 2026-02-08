package com.example.foreverus;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class Result<T> {

    private Result() {
    }

    @Override
    public String toString() {
        if (this instanceof Success) {
            Success success = (Success) this;
            return "Success[data=" + success.getData().toString() + "]";
        }
        if (this instanceof Error) {
            Error error = (Error) this;
            return "Error[exception=" + error.getError().toString() + "]";
        }
        return "";
    }

    public final static class Success<T> extends Result {
        private final T data;

        public Success(T data) {
            this.data = data;
        }

        public T getData() {
            return this.data;
        }
    }

    public final static class Error<T> extends Result {
        private final Exception error;

        public Error(Exception error) {
            this.error = error;
        }

        public Exception getError() {
            return this.error;
        }
    }
}
