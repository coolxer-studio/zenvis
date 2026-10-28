package com.coolxer.service.dih.attachment;

@FunctionalInterface
public interface AttachmentProgress {
    void report(int percent, String message);

    AttachmentProgress NOOP = (percent, message) -> {
    };
}
