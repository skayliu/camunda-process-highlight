package org.camunda.bpm.getstarted.highlight.constant;

public final class HighlightConstants {
    private HighlightConstants() {}

    // element_type
    public static final String ELEMENT_TASK        = "TASK";
    public static final String ELEMENT_GATEWAY     = "GATEWAY";
    public static final String ELEMENT_START_EVENT = "START_EVENT";
    public static final String ELEMENT_END_EVENT   = "END_EVENT";
    public static final String ELEMENT_FLOW        = "FLOW";
    public static final String ELEMENT_SUBPROCESS  = "SUBPROCESS";
    public static final String ELEMENT_OTHER       = "OTHER";

    // event_type
    public static final String EVENT_START = "start";
    public static final String EVENT_END   = "end";
    public static final String EVENT_TAKE  = "take";
}