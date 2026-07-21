package io.atworks.apiintelligence.adapter.openai;

public class OpenAiAdapterException extends RuntimeException {

    public OpenAiAdapterException(String m) {
        super(m);
    }

    public OpenAiAdapterException(String m, Throwable t) {
        super(m, t);
    }
}
