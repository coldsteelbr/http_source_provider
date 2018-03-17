package ru.romanbrazhnikov.sourceprovider;

public class TryAgainHttpException extends RuntimeException {
    private String mMessage = "";

    public TryAgainHttpException(){

    }

    public TryAgainHttpException(String message){

        mMessage = message;
    }

    @Override
    public String getMessage() {
        return mMessage;
    }
}
