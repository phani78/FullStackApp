package com.bank.origination.batch;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.bank.origination.security.PiiMasker;

public class PiiMaskingMessageConverter extends MessageConverter {
    @Override
    public String convert(ILoggingEvent event) {
        return PiiMasker.mask(event.getFormattedMessage())
                .replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r")
                .replace("\n", "\\n").replace("\t", "\\t");
    }
}
