package io.nekohasekai.sagernet.fmt.internal;

import io.nekohasekai.sagernet.fmt.AbstractBean;

public abstract class InternalBean extends AbstractBean {

    @Override
    public String displayAddress() {
        return "";
    }

    @Override
    public boolean canMapping() {
        return false;
    }
}
