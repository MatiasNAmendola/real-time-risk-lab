package com.naranjax.poc.risk.rule;

/** Marker interface for rules that can perform an override bypass. */
public interface AllowlistOverrideable {
    boolean isOverride();
}
