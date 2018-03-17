package ru.romanbrazhnikov.sourceprovider;

public class TerminateHttpException extends RuntimeException {
    private String mMessage = "";

    public TerminateHttpException(){

    }

    public TerminateHttpException(String message){

        mMessage = message;
    }

    @Override
    public String getMessage() {
        return mMessage;
    }
}
