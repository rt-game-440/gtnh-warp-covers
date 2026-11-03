package com.rtgame.warpcovers.selfcheck;

/** 自检阶段的结果。 */
public final class SelfCheckResult {

    private final boolean passed;
    private final String detail;

    private SelfCheckResult(boolean passed, String detail) {
        this.passed = passed;
        this.detail = detail == null ? "" : detail;
    }

    public static SelfCheckResult pass(String detail) {
        return new SelfCheckResult(true, detail);
    }

    public static SelfCheckResult fail(String detail) {
        return new SelfCheckResult(false, detail);
    }

    public boolean passed() {
        return passed;
    }

    public String detail() {
        return detail;
    }
}
