package ru.romanbrazhnikov.sourceprovider.cookies;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;

public class Cookie {
    private String name;
    private String value;
    private String domain;

    public Cookie(@NotNull String name, @NotNull String value, @Nullable String domain) {
        this.name = name;
        this.value = value;
        this.domain = domain;
    }

    public String getHeader() {
        StringBuilder builder = new StringBuilder();
        // name=value;
        builder.append(name).append("=").append(value).append("; ");

        // domain=<domain>;
        if (domain != null)
            builder.append("domain=").append(domain).append("; ").toString();

        return builder.toString();
    }
}
