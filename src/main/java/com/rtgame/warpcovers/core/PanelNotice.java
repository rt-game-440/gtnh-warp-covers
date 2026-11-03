package com.rtgame.warpcovers.core;

/** 界面操作的反馈，显示在状态栏里（不发聊天）。界面按 {@code warpcovers.notice.<key>} 显示，参数为 {@link #arg()}。 */
public final class PanelNotice {

    private final String key;
    private final String arg;
    private final boolean error;

    public PanelNotice(String key, String arg, boolean error) {
        this.key = key;
        this.arg = arg == null ? "" : arg;
        this.error = error;
    }

    static PanelNotice info(String key, String arg) {
        return new PanelNotice(key, arg, false);
    }

    static PanelNotice error(String key, String arg) {
        return new PanelNotice(key, arg, true);
    }

    public String key() {
        return key;
    }

    public String arg() {
        return arg;
    }

    public boolean isError() {
        return error;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PanelNotice)) {
            return false;
        }
        PanelNotice other = (PanelNotice) o;
        return key.equals(other.key) && arg.equals(other.arg) && error == other.error;
    }

    @Override
    public int hashCode() {
        return (key.hashCode() * 31 + arg.hashCode()) * 31 + (error ? 1 : 0);
    }

    @Override
    public String toString() {
        return (error ? "!" : "") + key + "(" + arg + ")";
    }
}
