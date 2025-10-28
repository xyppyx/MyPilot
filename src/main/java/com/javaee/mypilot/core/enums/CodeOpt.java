package com.javaee.mypilot.core.enums;

/**
 * llm代码操作枚举
 */
public enum CodeOpt {
    INSERT("INSERT"),
    DELETE("DELETE"),
    REPLACE("REPLACE");

    private String opt;

    CodeOpt(String opt) {
        this.opt = opt;
    }

    public String getOpt() {
        return opt;
    }

    public void setOpt(String opt) {
        this.opt = opt;
    }

    public static CodeOpt getCodeOpt(String opt) {
        for (CodeOpt codeOpt : CodeOpt.values()) {
            if (codeOpt.getOpt().equalsIgnoreCase(opt)) {
                return codeOpt;
            }
        }
        return null;
    }
}
