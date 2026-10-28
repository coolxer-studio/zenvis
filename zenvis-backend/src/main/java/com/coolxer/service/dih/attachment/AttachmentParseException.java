package com.coolxer.service.dih.attachment;

public class AttachmentParseException extends RuntimeException {

    public AttachmentParseException(String message) {
        super(message);
    }

    public AttachmentParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
