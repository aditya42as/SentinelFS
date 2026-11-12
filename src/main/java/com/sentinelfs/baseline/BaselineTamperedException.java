package com.sentinelfs.baseline;

import java.io.IOException;

/** Thrown when a baseline file does not match its integrity seal. */
public class BaselineTamperedException extends IOException {
    public BaselineTamperedException(String message) { super(message); }
}
